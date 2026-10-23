package com.apkbuilder;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Creates, lists, copies, renames, exports and imports projects. */
public class ProjectManager {
    private final File base;

    public ProjectManager(File base) {
        this.base = base;
        base.mkdirs();
    }

    public File baseDir() { return base; }

    public List<ProjectConfig> list() {
        List<ProjectConfig> out = new ArrayList<>();
        File[] kids = base.listFiles();
        if (kids != null) for (File k : kids) {
            if (new File(k, "settings/project.json").exists()) {
                try { out.add(ProjectConfig.load(k)); } catch (IOException ignored) { }
            }
        }
        Collections.sort(out, new Comparator<ProjectConfig>() {
            @Override public int compare(ProjectConfig a, ProjectConfig b) { return Long.compare(b.created, a.created); }
        });
        return out;
    }

    private File uniqueDir(String name) {
        String s = name.replaceAll("[^A-Za-z0-9_\\-]", "");
        if (s.isEmpty()) s = "Project";
        File d = new File(base, s);
        int i = 2;
        while (d.exists()) d = new File(base, s + "_" + (i++));
        return d;
    }

    /**
     * icons: optional PNG bytes for mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi (48,72,96,144,192 px).
     * Without icons the project uses a vector launcher icon.
     */
    public ProjectConfig create(ProjectConfig cfg, byte[][] icons) throws IOException {
        String err = cfg.validate();
        if (err != null) throw new IOException(err);
        File root = uniqueDir(cfg.name);
        cfg.root = root;
        String[] dirs = {"output/bin", "output/logs", "libs", "assets", "nativeLibs", "build", "settings",
                "src/main/res/drawable", "src/main/res/drawable-v24", "src/main/res/mipmap-mdpi",
                "src/main/res/mipmap-hdpi", "src/main/res/mipmap-xhdpi", "src/main/res/mipmap-xxhdpi",
                "src/main/res/mipmap-xxxhdpi", "src/main/res/layout", "src/main/res/values"};
        for (String d : dirs) new File(root, d).mkdirs();

        boolean hasIcon = icons != null && icons.length == 5;
        String iconRef = hasIcon ? "@mipmap/ic_launcher" : "@drawable/ic_launcher";
        if (hasIcon) {
            String[] q = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
            for (int i = 0; i < 5; i++) {
                java.io.FileOutputStream o = new java.io.FileOutputStream(new File(root, "src/main/res/mipmap-" + q[i] + "/ic_launcher.png"));
                o.write(icons[i]);
                o.close();
            }
        } else {
            Util.writeAtomic(new File(root, "src/main/res/drawable/ic_launcher.xml"), L(
                    "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"",
                    "    android:width=\"108dp\" android:height=\"108dp\"",
                    "    android:viewportWidth=\"108\" android:viewportHeight=\"108\">",
                    "    <path android:fillColor=\"#3D8BFF\" android:pathData=\"M0,0h108v108h-108z\" />",
                    "    <path android:fillColor=\"#FFFFFF\" android:pathData=\"M30,38h48v8h-48z M30,52h34v8h-34z M30,66h44v8h-44z\" />",
                    "</vector>"));
        }

        // AppCompat/Material projects need AppCompatActivity + a matching theme, otherwise the
        // app crashes at start ("You need to use a Theme.AppCompat theme").
        final boolean useAc = cfg.appCompat || cfg.material;
        String pkgPath = cfg.packageName.replace('.', '/');
        Util.writeAtomic(new File(root, "src/main/java/" + pkgPath + "/MainActivity.java"), L(
                "package " + cfg.packageName + ";",
                "",
                useAc ? "import android.os.Bundle;\n\nimport androidx.appcompat.app.AppCompatActivity;"
                      : "import android.app.Activity;\nimport android.os.Bundle;",
                "",
                "public class MainActivity extends " + (useAc ? "AppCompatActivity" : "Activity") + " {",
                "",
                "    @Override",
                "    protected void onCreate(Bundle savedInstanceState) {",
                "        super.onCreate(savedInstanceState);",
                "        setContentView(R.layout.activity_main);",
                "    }",
                "}"));
        Util.writeAtomic(new File(root, "src/main/AndroidManifest.xml"), L(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"",
                "    package=\"" + cfg.packageName + "\">",
                "",
                "    <uses-sdk android:minSdkVersion=\"" + cfg.minSdk + "\" android:targetSdkVersion=\"" + cfg.targetSdk + "\" />",
                "",
                "    <application",
                "        android:allowBackup=\"true\"",
                "        android:icon=\"" + iconRef + "\"",
                "        android:label=\"@string/app_name\"",
                "        android:theme=\"@style/AppTheme\">",
                "        <activity android:name=\".MainActivity\" android:exported=\"true\">",
                "            <intent-filter>",
                "                <action android:name=\"android.intent.action.MAIN\" />",
                "                <category android:name=\"android.intent.category.LAUNCHER\" />",
                "            </intent-filter>",
                "        </activity>",
                "    </application>",
                "</manifest>"));
        Util.writeAtomic(new File(root, "src/main/res/layout/activity_main.xml"), L(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"",
                "    android:layout_width=\"match_parent\"",
                "    android:layout_height=\"match_parent\"",
                "    android:gravity=\"center\"",
                "    android:orientation=\"vertical\">",
                "",
                "    <TextView",
                "        android:layout_width=\"wrap_content\"",
                "        android:layout_height=\"wrap_content\"",
                "        android:text=\"@string/hello\"",
                "        android:textSize=\"24sp\" />",
                "</LinearLayout>"));
        Util.writeAtomic(new File(root, "src/main/res/values/strings.xml"), L(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                "<resources>",
                "    <string name=\"app_name\">" + xml(cfg.name) + "</string>",
                "    <string name=\"hello\">Hello from " + xml(cfg.name) + "!</string>",
                "</resources>"));
        Util.writeAtomic(new File(root, "src/main/res/values/colors.xml"), L(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                "<resources>",
                "    <color name=\"colorPrimary\">#3D8BFF</color>",
                "    <color name=\"colorPrimaryDark\">#1F5FC4</color>",
                "    <color name=\"colorAccent\">#FF4081</color>",
                "</resources>"));
        String parent;
        if (cfg.material) parent = "Theme.MaterialComponents.Light.DarkActionBar";
        else if (cfg.appCompat) parent = "Theme.AppCompat.Light.DarkActionBar";
        else parent = cfg.minSdk >= 21 ? "android:Theme.Material.Light.DarkActionBar" : "android:Theme.Holo.Light.DarkActionBar";
        Util.writeAtomic(new File(root, "src/main/res/values/styles.xml"), L(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                "<resources>",
                "    <style name=\"AppTheme\" parent=\"" + parent + "\" />",
                "</resources>"));
        Util.writeAtomic(new File(root, "proguard-rules.pro"), "# ProGuard / R8 rules for this project\n");
        cfg.save();
        return cfg;
    }

    public ProjectConfig duplicate(ProjectConfig src) throws IOException {
        File dst = uniqueDir(src.name + "_copy");
        Util.copyDir(src.root, dst, "build");
        new File(dst, "build").mkdirs();
        ProjectConfig c = ProjectConfig.load(dst);
        c.name = src.name + " Copy";
        c.created = System.currentTimeMillis();
        c.save();
        renameInStrings(c);
        return c;
    }

    public void rename(ProjectConfig c, String newName) throws IOException {
        if (newName.trim().isEmpty()) throw new IOException("Name is empty");
        c.name = newName.trim();
        c.save();
        renameInStrings(c);
    }

    private void renameInStrings(ProjectConfig c) throws IOException {
        File s = new File(c.root, "src/main/res/values/strings.xml");
        if (!s.exists()) return;
        String t = Util.readText(s);
        String n = t.replaceAll("(<string\\s+name=\"app_name\">)[^<]*(</string>)", "$1" + java.util.regex.Matcher.quoteReplacement(xml(c.name)) + "$2");
        if (!n.equals(t)) Util.writeAtomic(s, n);
    }

    public void delete(ProjectConfig c) { Util.deleteRec(c.root); }

    public void export(ProjectConfig c, File zip) throws IOException {
        Set<String> skip = new HashSet<>(Arrays.asList("build", "settings/signing.json"));
        Util.zipDir(c.root, zip, skip);
    }

    public ProjectConfig importZip(File zip) throws IOException {
        File tmp = new File(base, ".import_" + System.currentTimeMillis());
        try {
            Util.unzip(zip, tmp);
            File root = tmp;
            if (!new File(root, "settings/project.json").exists()) {
                File[] kids = tmp.listFiles();
                if (kids != null && kids.length == 1 && new File(kids[0], "settings/project.json").exists()) root = kids[0];
                else throw new IOException("Not an APK Builder project (settings/project.json missing)");
            }
            ProjectConfig probe = ProjectConfig.load(root);
            File dst = uniqueDir(probe.name);
            Util.copyDir(root, dst, null);
            new File(dst, "build").mkdirs();
            new File(dst, "output/bin").mkdirs();
            new File(dst, "output/logs").mkdirs();
            return ProjectConfig.load(dst);
        } finally {
            Util.deleteRec(tmp);
        }
    }

    static String L(String... lines) {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) sb.append(l).append('\n');
        return sb.toString();
    }

    static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'").replace("\"", "&quot;");
    }
}
