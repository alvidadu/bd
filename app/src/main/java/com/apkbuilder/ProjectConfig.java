package com.apkbuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** All per-project settings. Persisted in settings/project.json (+ settings/signing.json for secrets). */
public class ProjectConfig {
    /** APK Builder compiles Java 17 only (ECJ 3.41 + D8). */
    public static final String JAVA_LEVEL = "17";
    public static final String[] JAVA_LEVELS = {JAVA_LEVEL};

    /** Java 9+ produces class files newer than Java 8 - DX cannot read those, only D8 can. */
    public static boolean isModernJava(String level) {
        return true;
    }
    public static final String[] DEXERS = {"D8"};
    private static final Pattern PKG = Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$");
    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "true", "false", "null"));

    public File root;
    public String name = "";
    public String packageName = "";
    public String versionName = "1.0";
    public int versionCode = 1;
    public int minSdk = 22;
    public int targetSdk = 34;
    public int maxSdk = 34;
    public String javaLevel = JAVA_LEVEL;
    public String dexer = "D8";
    public boolean appCompat, material, stringFog, r8, proguard;
    public String assetsPath = "", nativeLibsPath = "", localLibsPath = "";
    public boolean customKeystore;
    public String ksPath = "", ksAlias = "", ksPass = "", keyPass = "", ksType = "PKCS12";
    public long created = System.currentTimeMillis();

    public File settingsFile() { return new File(root, "settings/project.json"); }
    public File signingFile() { return new File(root, "settings/signing.json"); }
    public File srcMain() { return new File(root, "src/main"); }
    public File javaDir() { return new File(root, "src/main/java"); }
    public File resDir() { return new File(root, "src/main/res"); }
    public File manifest() { return new File(root, "src/main/AndroidManifest.xml"); }
    public File outBin() { return new File(root, "output/bin"); }
    public File outLogs() { return new File(root, "output/logs"); }
    public File buildDir() { return new File(root, "build"); }

    public File assetsDir() { return assetsPath.trim().isEmpty() ? new File(root, "assets") : new File(assetsPath.trim()); }
    public File nativeLibsDir() { return nativeLibsPath.trim().isEmpty() ? new File(root, "nativeLibs") : new File(nativeLibsPath.trim()); }
    /** Custom path if set, otherwise the project's own libs/ folder (created with every project). */
    public File localLibsDir() {
        if (!localLibsPath.trim().isEmpty()) return new File(localLibsPath.trim());
        File d = new File(root, "libs");
        return d.isDirectory() ? d : null;
    }

    public static boolean validPackage(String p) {
        if (p == null || !PKG.matcher(p).matches()) return false;
        for (String s : p.split("\\.")) if (KEYWORDS.contains(s)) return false;
        return true;
    }

    /** null = ok, otherwise a human readable problem. */
    public String validate() {
        if (name.trim().isEmpty()) return "App name is empty";
        if (!validPackage(packageName)) return "Invalid package name: " + packageName + " (example: com.example.myapp)";
        if (versionName.trim().isEmpty()) return "Version name is empty";
        if (versionCode < 1) return "Version code must be >= 1";
        if (minSdk < 1 || minSdk > 40) return "Min SDK must be between 1 and 40";
        if (targetSdk < minSdk) return "Target SDK must be >= Min SDK";
        if (maxSdk != 0 && maxSdk < targetSdk) return "Max SDK must be >= Target SDK";
        if (!Arrays.asList(JAVA_LEVELS).contains(javaLevel)) return "Unsupported Java level: " + javaLevel;
        if (!Arrays.asList(DEXERS).contains(dexer)) return "Unknown dexer: " + dexer;
        return null;
    }

    public void save() throws IOException {
        try {
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("package", packageName);
            o.put("versionName", versionName);
            o.put("versionCode", versionCode);
            o.put("minSdk", minSdk);
            o.put("targetSdk", targetSdk);
            o.put("maxSdk", maxSdk);
            o.put("javaLevel", javaLevel);
            o.put("dexer", dexer);
            o.put("appCompat", appCompat);
            o.put("material", material);
            o.put("stringFog", stringFog);
            o.put("r8", r8);
            o.put("proguard", proguard);
            o.put("assetsPath", assetsPath);
            o.put("nativeLibsPath", nativeLibsPath);
            o.put("localLibsPath", localLibsPath);
            o.put("customKeystore", customKeystore);
            o.put("created", created);
            Util.writeAtomic(settingsFile(), o.toString(2));
            JSONObject s = new JSONObject();
            s.put("ksPath", ksPath);
            s.put("ksAlias", ksAlias);
            s.put("ksPass", ksPass);
            s.put("keyPass", keyPass);
            s.put("ksType", ksType);
            Util.writeAtomic(signingFile(), s.toString(2));
        } catch (JSONException e) {
            throw new IOException(e);
        }
        // Keep settings.gradle/build.gradle in sync with whatever was just saved, so the
        // project stays buildable by a real Gradle tool with current package/SDK/AppCompat
        // settings even though this app's own pipeline never reads these gradle files itself.
        GradleFilesGenerator.generate(this);
    }

    public static ProjectConfig load(File root) throws IOException {
        ProjectConfig c = new ProjectConfig();
        c.root = root;
        try {
            JSONObject o = new JSONObject(Util.readText(c.settingsFile()));
            c.name = o.optString("name", root.getName());
            c.packageName = o.optString("package", "");
            c.versionName = o.optString("versionName", "1.0");
            c.versionCode = o.optInt("versionCode", 1);
            c.minSdk = o.optInt("minSdk", 22);
            c.targetSdk = o.optInt("targetSdk", 34);
            c.maxSdk = o.optInt("maxSdk", 34);
            // Older projects may carry 1.8 / DX - APK Builder is Java 17 + D8 only now.
            c.javaLevel = JAVA_LEVEL;
            c.dexer = "D8";
            c.appCompat = o.optBoolean("appCompat");
            c.material = o.optBoolean("material");
            c.stringFog = o.optBoolean("stringFog");
            c.r8 = o.optBoolean("r8");
            c.proguard = o.optBoolean("proguard");
            c.assetsPath = o.optString("assetsPath", "");
            c.nativeLibsPath = o.optString("nativeLibsPath", "");
            c.localLibsPath = o.optString("localLibsPath", "");
            c.customKeystore = o.optBoolean("customKeystore");
            c.created = o.optLong("created", System.currentTimeMillis());
            if (c.signingFile().exists()) {
                JSONObject s = new JSONObject(Util.readText(c.signingFile()));
                c.ksPath = s.optString("ksPath", "");
                c.ksAlias = s.optString("ksAlias", "");
                c.ksPass = s.optString("ksPass", "");
                c.keyPass = s.optString("keyPass", "");
                c.ksType = s.optString("ksType", "PKCS12");
            }
        } catch (JSONException e) {
            throw new IOException("Bad project.json: " + e.getMessage());
        }
        return c;
    }
}
