package com.apkbuilder;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs the real, full pipeline:
 * validate -> prepare dirs -> check toolchain -> AAPT2 compile+link (-> R.java)
 * -> javac (ECJ) -> [StringFog] -> dex (DX/D8) -> [R8/ProGuard] -> package -> sign -> zipalign -> verify.
 * Every stage reports real subprocess / compiler output to BuildLogger. No stage is faked.
 */
public class BuildManager {
    private final Context ctx;
    private final ProjectConfig cfg;
    public final BuildLogger log = new BuildLogger();
    private final AtomicReference<Process> activeProcess = new AtomicReference<>();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public BuildManager(Context ctx, ProjectConfig cfg) {
        this.ctx = ctx;
        this.cfg = cfg;
    }

    public void cancel() {
        cancelled.set(true);
        Process p = activeProcess.get();
        if (p != null) p.destroyForcibly();
    }

    private void checkCancel() throws BuildException {
        if (cancelled.get()) throw new BuildException("Cancelled", "Build cancelled by user");
    }

    public BuildResult build() {
        BuildResult res = new BuildResult();
        long t0 = System.currentTimeMillis();
        try {
            log.stage("Preparing");
            String err = cfg.validate();
            if (err != null) throw new BuildException("Validate", err);
            if (!cfg.manifest().isFile()) throw new BuildException("Validate", "AndroidManifest.xml missing");
            // Java 17 only: needs a Java 17 capable ECJ and the D8 dexer. Fail now with a
            // clear message instead of after a long compile.
            if (!JavaCompiler.supports(cfg.javaLevel)) {
                throw new BuildException("Validate", "Java " + cfg.javaLevel + " is not supported by the bundled ECJ. "
                        + "Put ecj 3.27 or newer as app/libs/ecj.jar and rebuild APK Builder.");
            }
            String d8Bad = new D8Runner().unavailableReason();
            if (d8Bad != null) {
                throw new BuildException("Validate", "Java " + cfg.javaLevel + " needs the D8 dexer, but " + d8Bad + ".");
            }
            checkCancel();

            File build = cfg.buildDir();
            Util.deleteRec(build);
            File compiledRes = new File(build, "compiled_res");
            File genRes = new File(build, "gen"); // R.java lands here
            File classes = new File(build, "classes");
            File dexOut = new File(build, "dex");
            File resApk = new File(build, "resources.apk");
            File unsignedApk = new File(build, "unsigned.apk");
            File alignedUnsignedApk = new File(build, "unsigned-aligned.apk");
            File signedApk = new File(build, "signed.apk");
            cfg.outBin().mkdirs();
            cfg.outLogs().mkdirs();

            ToolchainChecker tc = new ToolchainChecker(ctx);
            tc.ensureBundledToolchain();
            File androidJar = tc.androidJar();
            if (!androidJar.isFile()) throw new BuildException("Toolchain", "android.jar not found");

            // Catches "the same resource declared twice in one values/ folder" ourselves
            // with an exact file+line+name message. AAPT2 catches this too, but its own
            // message ("conflicting value for configuration ()") reads like a tool bug
            // to anyone who doesn't already know Android's one-definition-per-folder rule -
            // and it only becomes easy to hit once file creation inside res/values/ is this
            // easy, since two files can innocently redeclare the same style/color/string name.
            List<ResourceConflictChecker.Conflict> conflicts = ResourceConflictChecker.check(cfg.resDir());
            if (!conflicts.isEmpty()) {
                StringBuilder msg = new StringBuilder();
                for (ResourceConflictChecker.Conflict c : conflicts) {
                    msg.append(c.type).append('/').append(c.name)
                       .append(" is declared more than once in res/").append(c.bucket).append("/: ")
                       .append(String.join(", ", c.locations)).append('\n');
                }
                msg.append("Fix: keep the definition in only one of those files and remove it from the other(s).");
                throw new BuildException("Resources", msg.toString().trim());
            }

            // Real AppCompat/Material: linked together with the project's own resources in
            // ONE aapt2 link call (via --extra-packages), exactly how a real Android build
            // merges library resources - every resource, the app's and every library's, gets
            // a single stable ID in this one pass, and a fresh R.java is written per package.
            // Resolved (and any stale fallback attrs.xml cleaned up) BEFORE compiling the
            // project's own res/ below, since that compile step would otherwise bake a stale
            // file's contents into compiledRes before we ever got a chance to delete it.
            List<RealAppCompatMaterial.Lib> acmLibs = (cfg.appCompat || cfg.material)
                    ? RealAppCompatMaterial.load(ctx) : java.util.Collections.emptyList();
            if (acmLibs.isEmpty()) {
                // Fallback only: the hand-written attrs.xml + MaterialCardView/TextInputLayout
                // stand-ins from before the real library pack existed. When the real pack IS
                // loaded below, its own attrs (same names, real androidx/com.google packages)
                // are what gets linked instead - generating both would declare e.g.
                // cardCornerRadius twice across two different linked packages in the same
                // build, which is exactly the "conflicting value" class of AAPT2 error fixed
                // earlier for duplicate project resources, just between packages this time.
                MaterialCompatShim.ensureAttrs(cfg);
            } else {
                File staleAttrs = new File(cfg.resDir(), "values/material_compat_attrs.xml");
                if (staleAttrs.isFile()) staleAttrs.delete();
            }

            // Icon safety net: if the manifest points at an icon file that no longer exists,
            // fall back to the default icon instead of failing the build.
            AppIconManager.ensureValid(cfg);

            log.stage("Compiling resources");
            Aapt2Runner aapt2 = new Aapt2Runner(tc.resolvedAapt2());
            aapt2.compile(cfg.resDir(), compiledRes, log, activeProcess);
            checkCancel();

            List<File> acmCompiledDirs = new ArrayList<>();
            List<String> acmExtraPackages = new ArrayList<>();
            for (RealAppCompatMaterial.Lib lib : acmLibs) {
                if (!lib.hasRes || lib.resDir == null || !lib.resDir.isDirectory()) continue;
                File out = new File(build, "acm_compiled_res/" + lib.name);
                aapt2.compile(lib.resDir, out, log, activeProcess);
                acmCompiledDirs.add(out);
                if (lib.pkg != null && !lib.pkg.isEmpty()) acmExtraPackages.add(lib.pkg);
                checkCancel();
            }
            // Material 1.13 depends on androidx.constraintlayout, which is NOT part of the bundled
            // pack. A few Material layouts (the TimePicker ones) use its XML attributes
            // (layout_constraint*, constraint_referenced_ids, flow_*), and AAPT2 fails the link
            // with "attribute ... not found" if nothing declares them. Declare just those
            // attributes in a tiny generated resource set and link it as the lowest-priority
            // overlay. Only the attribute NAMES are needed to link; the ConstraintLayout classes
            // are only touched at runtime if the app actually shows a Material TimePicker.
            boolean hasConstraintLib = false;
            for (RealAppCompatMaterial.Lib lib : acmLibs) {
                if (lib.name != null && lib.name.startsWith("constraintlayout")) hasConstraintLib = true;
            }
            if (!acmLibs.isEmpty() && !hasConstraintLib) {
                File stubRes = new File(build, "acm_stub_res");
                Util.deleteRec(stubRes);
                File stubValues = new File(stubRes, "values/constraintlayout_stub_attrs.xml");
                stubValues.getParentFile().mkdirs();
                Util.writeAtomic(stubValues, constraintLayoutStubAttrs());
                File stubOut = new File(build, "acm_compiled_res/_constraintlayout_stub");
                aapt2.compile(stubRes, stubOut, log, activeProcess);
                acmCompiledDirs.add(stubOut); // last in the list = lowest priority (see Aapt2Runner.link)
                checkCancel();
            }
            if (!acmLibs.isEmpty()) {
                log.log("AppCompat/Material", "Linking real AppCompat/Material resources ("
                        + acmExtraPackages.size() + " library package(s)).");
            }

            log.stage("Compiling resources");
            aapt2.link(compiledRes, acmCompiledDirs, acmExtraPackages, cfg.manifest(), androidJar, resApk, genRes,
                    cfg.minSdk, cfg.targetSdk, cfg.versionName, cfg.versionCode, log, activeProcess);
            checkCancel();

            log.stage("Compiling Java");
            List<File> sources = new ArrayList<>(Util.find(cfg.javaDir(), ".java"));
            sources.addAll(Util.find(genRes, ".java"));
            if (sources.isEmpty()) throw new BuildException("JavaC", "No Java source files found");

            File compileSources = cfg.javaDir();
            if (cfg.stringFog) {
                log.stage("Compiling Java");
                File fogged = new File(build, "fog_src");
                String pkgPath = cfg.packageName.replace('.', '/');
                int n = StringFog.applyToTree(cfg.javaDir(), fogged);
                StringFog.writeHelper(new File(fogged, pkgPath), cfg.packageName);
                log.log("StringFog", "Encoded string literals in " + n + " file(s)");
                sources = new ArrayList<>(Util.find(fogged, ".java"));
                sources.addAll(Util.find(genRes, ".java"));
            }

            // Real local library jars/AARs (not our own internal compile-only stubs like the
            // lambda-metafactory jar) must end up in BOTH the compile classpath AND the final
            // dex input - previously they only ever reached the compiler, so an app that
            // referenced a local library jar would compile fine and then crash at runtime with
            // ClassNotFoundException, since that library's classes never made it into the APK.
            List<File> classpath = new ArrayList<>();
            for (File jar : bundledLibs()) classpath.add(jar); // compile-time only, never dexed
            List<File> runtimeLibJars = new ArrayList<>();
            if (cfg.localLibsDir() != null && cfg.localLibsDir().isDirectory()) {
                File[] plainJars = cfg.localLibsDir().listFiles((d, n) -> n.toLowerCase(java.util.Locale.ROOT).endsWith(".jar"));
                if (plainJars != null) for (File f : plainJars) runtimeLibJars.add(f);

                File[] aars = cfg.localLibsDir().listFiles((d, n) -> n.toLowerCase(java.util.Locale.ROOT).endsWith(".aar"));
                if (aars != null) {
                    File aarOut = new File(build, "aar_extracted");
                    for (File aar : aars) {
                        AarExtractor.Extracted ex = AarExtractor.extract(aar, aarOut);
                        if (ex.classesJar != null) runtimeLibJars.add(ex.classesJar);
                        if (ex.hadResources) {
                            log.log("AAR", "Note: " + aar.getName() + " has its own resources (themes/"
                                  + "layouts/drawables) which this on-device builder does not merge in - "
                                  + "its compiled code will work, but anything it draws from its own XML "
                                  + "resources will not be available. Build via a real Gradle tool "
                                  + "(this project's build.gradle/settings.gradle are kept up to date) "
                                  + "for full support.");
                        }
                    }
                }
            }
            // Real AppCompat/Material: each library's own classes (everything except R, which
            // we just generated fresh above) ship as a prebuilt .dex - no re-dexing needed, and
            // no stale-R-class conflict possible, since modern AndroidX AARs never bundle a
            // compiled R.class in the first place (see RealAppCompatMaterial). Using the
            // prebuilt .dex directly (instead of feeding classes.jar through our own DX) matters:
            // these are current, real-world AndroidX libraries that almost certainly contain
            // lambdas/method references, and DX can't dex invokedynamic below minSdk 26 (same
            // issue DxRunner already reports elsewhere) - the prebuilt .dex was produced by a
            // real D8, which already desugared all of that. So lib.jar goes on the COMPILE
            // classpath only; lib.dex goes straight into the final APK as its own classesN.dex,
            // bypassing our dexer entirely for these libraries.
            List<File> acmDex = new ArrayList<>();
            for (RealAppCompatMaterial.Lib lib : acmLibs) {
                if (lib.jar != null && lib.jar.isFile()) classpath.add(lib.jar); // compile-time only
                if (lib.dex != null && lib.dex.isFile()) acmDex.add(lib.dex); // final dex input, as-is
            }
            if (!acmLibs.isEmpty()) {
                // The pack is missing libraries its own classes need at runtime (kotlin-stdlib,
                // kotlinx-coroutines, androidx.arch.core, core-ktx, viewpager...). Without them the
                // app crashes at start with NoClassDefFoundError. Fetched once, then cached.
                RuntimeDeps.Result kr = RuntimeDeps.ensure(ctx, androidJar, log, containsKotlinStdlib(runtimeLibJars));
                if (kr.error == null) {
                    acmDex.addAll(kr.dex);
                    classpath.add(kr.jar); // compile-time only; the dex is added as-is
                    for (String m : kr.missingOptional) {
                        log.log("AppCompat/Material", "WARNING: optional runtime library not available: " + m);
                    }
                } else {
                    String kw = "Runtime libraries for AppCompat/Material could not be prepared (" + kr.error + "). "
                              + "Without them the app crashes at start with NoClassDefFoundError (kotlin/..., "
                              + "androidx/arch/core/...). Connect to the internet and build again.";
                    log.log("AppCompat/Material", "WARNING: " + kw);
                    res.warnings.add(kw);
                }
            }
            if (!acmLibs.isEmpty()) {
                log.log("AppCompat/Material", "Using the real AppCompat 1.7.1 / Material 1.13.0 "
                      + "libraries (" + acmLibs.size() + " artifacts incl. dependencies) you supplied. "
                      + "Other libraries beyond this AppCompat/Material closure (Firebase, Play "
                      + "Services, etc.) aren't wired in; add them the same way or build with a real "
                      + "Gradle tool for those.");
                String themeWarn = checkAppCompatTheme(cfg);
                if (themeWarn != null) {
                    log.log("AppCompat/Material", "WARNING: " + themeWarn);
                    res.warnings.add(themeWarn);
                }
            } else if (cfg.appCompat || cfg.material) {
                log.log("AppCompat/Material", "AppCompat/Material is enabled but the real library "
                      + "pack is missing from this build of APK Builder. Build this project with a "
                      + "real Gradle tool instead (its build.gradle/settings.gradle are kept ready).");
            }

            classpath.addAll(runtimeLibJars);
            List<String> warnings = new ArrayList<>();
            JavaCompiler.compile(sources, classpath, androidJar, classes, cfg.javaLevel, log, warnings);
            res.warnings.addAll(warnings);
            checkCancel();

            log.stage("Dexing");
            Dexer dexer = new D8Runner();
            String dexBad = dexer.unavailableReason();
            if (dexBad != null) throw new BuildException("Dexing", dexBad);

            List<File> dexInputs = new ArrayList<>();
            dexInputs.add(classes);
            dexInputs.addAll(runtimeLibJars);
            if (cfg.r8 || cfg.proguard) {
                log.stage("Dexing");
                List<File> rules = new ArrayList<>();
                File pgFile = new File(cfg.root, "proguard-rules.pro");
                if (pgFile.isFile()) rules.add(pgFile);
                if (cfg.r8) {
                    R8Tools.runR8(dexInputs, androidJar, rules, dexOut, cfg.minSdk, log);
                    List<File> r8DexFiles = new ArrayList<>();
                    r8DexFiles.add(new File(dexOut, "classes.dex"));
                    r8DexFiles.addAll(acmDex); // real AppCompat/Material's own prebuilt dex, untouched by R8
                    packageAndFinish(res, resApk, r8DexFiles, unsignedApk, alignedUnsignedApk, signedApk, t0);
                    return res;
                } else {
                    File shrunkJar = new File(build, "shrunk.jar");
                    R8Tools.runProguard(dexInputs, androidJar, rules, shrunkJar, log);
                    dexInputs = java.util.Collections.singletonList(shrunkJar);
                }
            }
            dexer.dex(dexInputs, androidJar, dexOut, cfg.minSdk, log);
            checkCancel();

            List<File> dexFiles = new ArrayList<>(Util.find(dexOut, ".dex"));
            if (dexFiles.isEmpty()) throw new BuildException("Dexing", "Dexer produced no classes.dex");
            dexFiles.addAll(acmDex); // real AppCompat/Material's own prebuilt dex, appended as classesN.dex
            packageAndFinish(res, resApk, dexFiles, unsignedApk, alignedUnsignedApk, signedApk, t0);
            return res;
        } catch (BuildException e) {
            res.success = false;
            res.errors.addAll(e.errors);
            res.buildTime = System.currentTimeMillis() - t0;
            log.log("APK Builder", "BUILD FAILED [" + e.stage + "]: " + e.getMessage());
            res.logs = log.text();
            return res;
        } catch (Throwable t) {
            res.success = false;
            res.errors.add(new BuildResult.BuildError("Build", "", 0, 0, String.valueOf(t)));
            res.buildTime = System.currentTimeMillis() - t0;
            log.log("APK Builder", "BUILD FAILED: " + t);
            res.logs = log.text();
            return res;
        }
    }

    private void packageAndFinish(BuildResult res, File resApk, List<File> dexFiles, File unsignedApk,
                                  File alignedUnsignedApk, File signedApk, long t0) throws Exception {
        checkCancel();
        log.stage("Packaging");
        ApkPackager.buildUnsigned(resApk, dexFiles, cfg.assetsDir(), cfg.nativeLibsDir(), unsignedApk);
        checkCancel();

        // zipalign MUST run before signing, not after: it rewrites the zip's local-file-header
        // padding to align entry data, which changes raw file bytes. The v2/v3 signature schemes
        // sign almost the entire APK's bytes, so aligning an already-signed APK invalidates those
        // signatures. apksig's signer does not move or repad existing entries, so signing an
        // already-aligned APK preserves both the alignment and the signature.
        log.stage("ZipAlign");
        ToolchainChecker tc = new ToolchainChecker(ctx);
        ZipAlignRunner za = new ZipAlignRunner(tc.resolvedZipalign());
        za.align(unsignedApk, alignedUnsignedApk, log, activeProcess);
        checkCancel();

        log.stage("Signing");
        File ks;
        String ksType, storePass, alias, keyPass;
        if (cfg.customKeystore && !cfg.ksPath.trim().isEmpty()) {
            ks = new File(cfg.ksPath);
            ksType = cfg.ksType;
            storePass = cfg.ksPass;
            alias = cfg.ksAlias;
            keyPass = cfg.keyPass;
            if (!ks.isFile()) throw new BuildException("Signing", "Custom keystore not found: " + cfg.ksPath);
        } else {
            ks = KeystoreManager.ensureDebugKeystore(new File(cfg.root, "settings"));
            ksType = "PKCS12";
            storePass = KeystoreManager.DEBUG_PASS;
            alias = KeystoreManager.DEBUG_ALIAS;
            keyPass = KeystoreManager.DEBUG_PASS;
        }
        ApkSignerRunner.sign(alignedUnsignedApk, signedApk, ks, ksType, storePass, alias, keyPass, cfg.minSdk, log);
        checkCancel();

        File aligned = new File(cfg.outBin(), "generated-aligned.apk");
        Util.copyFile(signedApk, aligned);

        log.stage("Verifying");
        boolean verified = ApkSignerRunner.verify(aligned, cfg.minSdk, log);
        boolean alignOk = za.verify(aligned, log);
        if (!verified) throw new BuildException("Verify", "Final APK failed signature verification");
        if (!alignOk) log.log("ZipAlign", "Warning: alignment could not be verified");

        File plainOut = new File(cfg.outBin(), "generated.apk");
        Util.copyFile(signedApk, plainOut);

        Util.writeAtomic(new File(cfg.outLogs(), "build-" + System.currentTimeMillis() + ".log"), log.text());

        res.success = true;
        res.apkPath = plainOut;
        res.alignedApkPath = aligned;
        res.buildTime = System.currentTimeMillis() - t0;
        res.logs = log.text();
        log.log("APK Builder", "Build success in " + res.buildTime + "ms");
    }

    private List<File> bundledLibs() {
        List<File> out = new ArrayList<>();
        try {
            File libsDir = new File(ctx.getFilesDir(), "extracted_libs");
            libsDir.mkdirs();
            // Sync every build, not just when the folder is first created: the folder
            // persists across app updates, so an app update that adds/changes a bundled
            // jar (e.g. the lambda-metafactory compile stub) must still reach it instead
            // of silently keeping whatever was extracted by an older version of the app.
            for (String n : ctx.getAssets().list("libs")) {
                // Unconditional overwrite: these are a handful of small jars, so always
                // re-copying is cheap and side-steps needing a compressed-asset-safe way
                // to detect "did this change since it was last extracted".
                Util.copyStreamToFile(ctx.getAssets().open("libs/" + n), new File(libsDir, n));
            }
            File[] jars = libsDir.listFiles((d, n) -> n.endsWith(".jar"));
            if (jars != null) for (File j : jars) out.add(j);
        } catch (Exception ignored) { }
        return out;
    }

    /** Attribute declarations matching androidx.constraintlayout 2.x, so layouts that use them link. */
    private static String constraintLayoutStubAttrs() {
        String[] sides = {"Left", "Right", "Top", "Bottom", "Start", "End", "Baseline"};
        StringBuilder b = new StringBuilder();
        b.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n");
        for (String from : sides) {
            for (String to : sides) {
                boolean fromBase = from.equals("Baseline");
                boolean toBase = to.equals("Baseline");
                if (fromBase != toBase) continue;
                if (fromBase) {
                    b.append("    <attr name=\"layout_constraintBaseline_toBaselineOf\" format=\"reference|enum\">\n")
                     .append("        <enum name=\"parent\" value=\"0\" />\n    </attr>\n");
                    continue;
                }
                b.append("    <attr name=\"layout_constraint").append(from).append("_to").append(to)
                 .append("Of\" format=\"reference|enum\">\n")
                 .append("        <enum name=\"parent\" value=\"0\" />\n    </attr>\n");
            }
        }
        b.append("    <attr name=\"constraint_referenced_ids\" format=\"string\" />\n");
        b.append("    <attr name=\"flow_horizontalGap\" format=\"dimension\" />\n");
        b.append("    <attr name=\"flow_verticalGap\" format=\"dimension\" />\n");
        b.append("</resources>\n");
        return b.toString();
    }

    /**
     * AppCompat/Material views crash at runtime when the app theme is a plain framework theme.
     * Returns a warning text if the manifest's theme chain starts at "android:Theme...", else null.
     */
    private static String checkAppCompatTheme(ProjectConfig cfg) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("android:theme\\s*=\\s*\"@style/([A-Za-z0-9_.]+)\"")
                    .matcher(Util.readText(cfg.manifest()));
            if (!m.find()) {
                return "The <application> has no android:theme. AppCompatActivity / Material views need a "
                     + "Theme.AppCompat.* or Theme.MaterialComponents.* theme, otherwise the app crashes on start.";
            }
            String theme = m.group(1);
            File[] xmls = new File(cfg.resDir(), "values").listFiles((d, n) -> n.endsWith(".xml"));
            if (xmls == null) return null;
            java.util.regex.Pattern sp = java.util.regex.Pattern.compile(
                    "<style\\s+name\\s*=\\s*\"" + java.util.regex.Pattern.quote(theme) + "\"\\s+parent\\s*=\\s*\"([^\"]+)\"");
            for (File f : xmls) {
                java.util.regex.Matcher pm = sp.matcher(Util.readText(f));
                if (pm.find() && pm.group(1).startsWith("android:")) {
                    return "Theme '" + theme + "' extends the framework theme '" + pm.group(1)
                         + "'. With AppCompat/Material enabled use a Theme.AppCompat.* or "
                         + "Theme.MaterialComponents.* parent and extend AppCompatActivity, or the app "
                         + "will crash on start.";
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** True if any of the jars contains the Kotlin standard library (kotlin/Unit.class). */
    private static boolean containsKotlinStdlib(List<File> jars) {
        for (File j : jars) {
            try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(j)) {
                if (z.getEntry("kotlin/Unit.class") != null) return true;
            } catch (Exception ignored) { }
        }
        return false;
    }
}
