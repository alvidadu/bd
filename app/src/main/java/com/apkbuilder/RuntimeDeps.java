package com.apkbuilder;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Runtime libraries that the bundled AppCompat/Material pack needs but does not contain.
 *
 * A static scan of the pack's dex files shows classes that are referenced but not defined anywhere:
 *   - kotlin-stdlib             (activity, core, fragment, lifecycle... are written in Kotlin)
 *   - kotlinx-coroutines        (lifecycle-viewmodel-savedstate uses StateFlow, activity uses Flow)
 *   - androidx.arch.core        (LifecycleRegistry -> FastSafeIterableMap, ArchTaskExecutor)
 *   - androidx.core:core-ktx    (BundleKt / ViewKt used by savedstate + poolingcontainer)
 *   - androidx.viewpager        (used by Material TabLayout and fragment)
 *   - concurrent-futures, listenablefuture (androidx.core futures)
 * Without them the app dies at start with NoClassDefFoundError.
 *
 * First build: downloads them (Maven Central / Google Maven), takes classes.jar out of the AARs,
 * strips META-INF / module-info (DX cannot read Java 9 multi-release entries), merges everything
 * into one jar, dexes it once with the bundled DX and caches the dex in filesDir/runtime_deps.
 * Later builds reuse the cache and work offline.
 */
public final class RuntimeDeps {
    private RuntimeDeps() {}

    private static final String MC = "https://repo1.maven.org/maven2/";
    private static final String GM = "https://dl.google.com/android/maven2/";

    private static final class Dep {
        final String id, url;
        final boolean aar, critical;
        Dep(String id, String url, boolean aar, boolean critical) {
            this.id = id; this.url = url; this.aar = aar; this.critical = critical;
        }
    }

    private static final Dep[] DEPS = {
        new Dep("kotlin-stdlib-1.9.24", MC + "org/jetbrains/kotlin/kotlin-stdlib/1.9.24/kotlin-stdlib-1.9.24.jar", false, true),
        new Dep("kotlinx-coroutines-core-jvm-1.7.3", MC + "org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.7.3/kotlinx-coroutines-core-jvm-1.7.3.jar", false, true),
        new Dep("core-common-2.2.0", GM + "androidx/arch/core/core-common/2.2.0/core-common-2.2.0.jar", false, true),
        new Dep("core-runtime-2.2.0", GM + "androidx/arch/core/core-runtime/2.2.0/core-runtime-2.2.0.aar", true, true),
        new Dep("core-ktx-1.17.0", GM + "androidx/core/core-ktx/1.17.0/core-ktx-1.17.0.aar", true, true),
        new Dep("kotlinx-coroutines-android-1.7.3", MC + "org/jetbrains/kotlinx/kotlinx-coroutines-android/1.7.3/kotlinx-coroutines-android-1.7.3.jar", false, false),
        new Dep("viewpager-1.0.0", GM + "androidx/viewpager/viewpager/1.0.0/viewpager-1.0.0.aar", true, false),
        new Dep("concurrent-futures-1.1.0", GM + "androidx/concurrent/concurrent-futures/1.1.0/concurrent-futures-1.1.0.jar", false, false),
        new Dep("listenablefuture-1.0", MC + "com/google/guava/listenablefuture/1.0/listenablefuture-1.0.jar", false, false),
    };

    public static class Result {
        public final List<File> dex = new ArrayList<>();
        public File jar;      // merged jar, for the compile classpath
        public String error;  // null when ok
        public final List<String> missingOptional = new ArrayList<>();
    }

    /** @param skipKotlinStdlib true when the project already ships its own kotlin-stdlib jar */
    public static Result ensure(Context ctx, File androidJar, BuildLogger log, boolean skipKotlinStdlib) {
        Result r = new Result();
        File dir = new File(ctx.getFilesDir(), "runtime_deps");
        File raw = new File(dir, "raw");
        File dexDir = new File(dir, "dex");
        File merged = new File(dir, "merged.jar");
        File marker = new File(dir, "installed.txt");
        try {
            raw.mkdirs();
            List<Dep> want = new ArrayList<>();
            for (Dep d : DEPS) {
                if (skipKotlinStdlib && d.id.startsWith("kotlin-stdlib")) continue;
                want.add(d);
            }
            Set<String> have = readMarker(marker);
            boolean cacheOk = merged.isFile() && !Util.find(dexDir, ".dex").isEmpty();
            boolean complete = true;
            for (Dep d : want) if (!have.contains(d.id)) complete = false;

            if (!(cacheOk && complete)) {
                log.log("Runtime", "Preparing runtime libraries needed by AppCompat/Material (Kotlin, coroutines, arch-core, core-ktx...)");
                List<File> jars = new ArrayList<>();
                Set<String> got = new LinkedHashSet<>();
                for (Dep d : want) {
                    File j = fetch(d, raw, log);
                    if (j != null) { jars.add(j); got.add(d.id); }
                    else if (d.critical) throw new Exception("could not get " + d.id + " (check the internet connection)");
                    else r.missingOptional.add(d.id);
                }
                boolean sameAsCache = cacheOk && got.equals(have);
                if (!sameAsCache) {
                    merge(jars, merged);
                    dexOnce(merged, androidJar, dexDir, log);
                    writeMarker(marker, got);
                } else {
                    log.log("Runtime", "Nothing new to add, using cached runtime libraries.");
                }
            }
            r.jar = merged;
            List<File> dex = new ArrayList<>(Util.find(dexDir, ".dex"));
            Collections.sort(dex);
            if (dex.isEmpty()) throw new Exception("no dex produced for the runtime libraries");
            r.dex.addAll(dex);
            log.log("Runtime", "Runtime libraries ready (" + dex.size() + " dex).");
        } catch (Throwable t) {
            r.dex.clear();
            r.jar = null;
            r.error = String.valueOf(t.getMessage() != null ? t.getMessage() : t);
        }
        return r;
    }

    // ---------------------------------------------------------------- download

    /** Returns a classes jar for the dependency (from cache or freshly downloaded), or null. */
    private static File fetch(Dep d, File rawDir, BuildLogger log) {
        File jar = new File(rawDir, d.id + ".jar");
        if (jar.isFile() && jar.length() > 0) return jar;
        // try the expected extension first, then the other one (a few artifacts changed type over time)
        String[] urls = { d.url, d.url.endsWith(".aar") ? d.url.replaceAll("\\.aar$", ".jar") : d.url.replaceAll("\\.jar$", ".aar") };
        for (String u : urls) {
            File dl = new File(rawDir, d.id + (u.endsWith(".aar") ? ".aar" : ".dl.jar"));
            try {
                log.log("Runtime", "Downloading " + d.id + " ...");
                download(u, dl);
                if (u.endsWith(".aar")) {
                    extractClasses(dl, jar);
                    dl.delete();
                } else {
                    if (!dl.renameTo(jar)) throw new Exception("cannot save " + jar);
                }
                if (jar.isFile() && jar.length() > 0) return jar;
            } catch (Exception e) {
                dl.delete();
                jar.delete();
                log.log("Runtime", "  failed: " + e.getMessage());
            }
        }
        return null;
    }

    private static void download(String url, File dest) throws Exception {
        File tmp = new File(dest.getPath() + ".part");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
        } catch (Exception e) {
            tmp.delete();
            throw e;
        } finally {
            c.disconnect();
        }
        if (tmp.length() < 200) { tmp.delete(); throw new Exception("download incomplete"); }
        if (dest.exists()) dest.delete();
        if (!tmp.renameTo(dest)) throw new Exception("cannot save " + dest);
    }

    private static void extractClasses(File aar, File outJar) throws Exception {
        try (ZipFile z = new ZipFile(aar)) {
            ZipEntry e = z.getEntry("classes.jar");
            if (e == null) throw new Exception("classes.jar not found in " + aar.getName());
            try (InputStream in = z.getInputStream(e)) { Util.copyStreamToFile(in, outJar); }
        }
    }

    // ---------------------------------------------------------------- jar merge + dex

    /** Merges the jars into one, keeping only real .class files (no META-INF, no module-info, no duplicates). */
    private static void merge(List<File> jars, File out) throws Exception {
        Set<String> seen = new HashSet<>();
        byte[] buf = new byte[64 * 1024];
        try (ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(out))) {
            for (File jar : jars) {
                try (ZipFile z = new ZipFile(jar)) {
                    Enumeration<? extends ZipEntry> en = z.entries();
                    while (en.hasMoreElements()) {
                        ZipEntry e = en.nextElement();
                        String n = e.getName();
                        if (e.isDirectory() || !n.endsWith(".class") || n.startsWith("META-INF/")
                                || n.equals("module-info.class") || !seen.add(n)) continue;
                        zo.putNextEntry(new ZipEntry(n));
                        try (InputStream is = z.getInputStream(e)) {
                            int k;
                            while ((k = is.read(buf)) > 0) zo.write(buf, 0, k);
                        }
                        zo.closeEntry();
                    }
                }
            }
        }
    }

    /** Try without invokedynamic support first (works on any device), then with API 26. */
    private static void dexOnce(File jar, File androidJar, File out, BuildLogger log) throws Exception {
        Exception last = null;
        for (int api : new int[]{21, 26}) {
            try {
                Util.deleteRec(out);
                new DxRunner().dex(Collections.singletonList(jar), androidJar, out, api, log);
                if (!Util.find(out, ".dex").isEmpty()) return;
            } catch (Exception e) {
                last = e;
            }
        }
        Util.deleteRec(out);
        throw new Exception("could not dex the runtime libraries: " + (last != null ? last.getMessage() : "unknown"));
    }

    // ---------------------------------------------------------------- cache marker

    private static Set<String> readMarker(File f) {
        Set<String> s = new LinkedHashSet<>();
        try {
            if (f.isFile()) for (String l : Util.readText(f).split("\n")) if (!l.trim().isEmpty()) s.add(l.trim());
        } catch (Exception ignored) { }
        return s;
    }

    private static void writeMarker(File f, Set<String> ids) throws Exception {
        StringBuilder b = new StringBuilder();
        for (String id : ids) b.append(id).append('\n');
        Util.writeAtomic(f, b.toString());
    }
}
