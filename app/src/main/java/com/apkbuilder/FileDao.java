package com.apkbuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Centralized project file structure access. All file operations stay within project root
 * (prevents path traversal). Provides type-safe accessors for common project files.
 */
public class FileDao {
    private final File root;
    private final ProjectConfig cfg;

    public FileDao(File root, ProjectConfig cfg) {
        this.root = root;
        this.cfg = cfg;
    }

    /** Safely resolve a relative path below root. Throws if it escapes. */
    public File resolve(String rel) throws java.io.IOException {
        return Util.safeChild(root, rel);
    }

    // === PROJECT STRUCTURE ===
    public File root() { return root; }
    public File projectDir() { return root; }
    public File srcDir() { return new File(root, "src"); }
    public File srcMainDir() { return new File(root, "src/main"); }
    public File javaDir() { return new File(root, "src/main/java"); }
    public File resDir() { return new File(root, "src/main/res"); }
    public File assetsDir() { return cfg.assetsDir(); }
    public File nativeLibsDir() { return cfg.nativeLibsDir(); }
    public File libsDir() { return new File(root, "libs"); }
    public File buildDir() { return new File(root, "build"); }
    public File settingsDir() { return new File(root, "settings"); }
    public File outputDir() { return new File(root, "output"); }
    public File outputBinDir() { return new File(root, "output/bin"); }
    public File outputLogsDir() { return new File(root, "output/logs"); }

    // === MANIFEST & CONFIG ===
    public File manifest() { return new File(root, "src/main/AndroidManifest.xml"); }
    public File proguardRules() { return new File(root, "proguard-rules.pro"); }
    public File projectConfig() { return new File(root, "settings/project.json"); }
    public File signingConfig() { return new File(root, "settings/signing.json"); }

    // === RESOURCE SUBDIRS ===
    public File layoutDir() { return new File(root, "src/main/res/layout"); }
    public File valuesDir() { return new File(root, "src/main/res/values"); }
    public File drawableDir() { return new File(root, "src/main/res/drawable"); }
    public File drawableV24Dir() { return new File(root, "src/main/res/drawable-v24"); }
    public File mipmapMdpiDir() { return new File(root, "src/main/res/mipmap-mdpi"); }
    public File mipmapHdpiDir() { return new File(root, "src/main/res/mipmap-hdpi"); }
    public File mipmapXhdpiDir() { return new File(root, "src/main/res/mipmap-xhdpi"); }
    public File mipmapXxhdpiDir() { return new File(root, "src/main/res/mipmap-xxhdpi"); }
    public File mipmapXxxhdpiDir() { return new File(root, "src/main/res/mipmap-xxxhdpi"); }

    // === BUILD INTERMEDIATES ===
    public File compiledResDir() { return new File(buildDir(), "compiled_res"); }
    public File genDir() { return new File(buildDir(), "gen"); }
    public File classesDir() { return new File(buildDir(), "classes"); }
    public File dexDir() { return new File(buildDir(), "dex"); }
    public File resourcesApk() { return new File(buildDir(), "resources.apk"); }
    public File unsignedApk() { return new File(buildDir(), "unsigned.apk"); }
    public File signedApk() { return new File(buildDir(), "signed.apk"); }
    public File finalApk() { return new File(outputBinDir(), "generated-aligned.apk"); }

    // === FILE QUERIES ===
    public List<File> javaSourceFiles() {
        return Util.find(javaDir(), ".java");
    }

    public List<File> generatedJavaFiles() {
        List<File> out = new ArrayList<>(Util.find(genDir(), ".java"));
        return out;
    }

    public List<File> allJavaSourceFiles() {
        List<File> out = new ArrayList<>(javaSourceFiles());
        out.addAll(generatedJavaFiles());
        return out;
    }

    public List<File> xmlResourceFiles() {
        List<File> out = new ArrayList<>();
        for (File dir : new File[]{layoutDir(), valuesDir()}) {
            out.addAll(Util.find(dir, ".xml"));
        }
        return out;
    }

    public List<File> allResFiles() {
        return Util.find(resDir(), "");
    }

    public List<File> classFiles() {
        return Util.find(classesDir(), ".class");
    }

    public List<File> dexFiles() {
        return Util.find(dexDir(), ".dex");
    }

    public List<File> jarFiles(File dir) {
        if (dir == null || !dir.isDirectory()) return new ArrayList<>();
        List<File> out = new ArrayList<>();
        File[] jars = dir.listFiles((d, n) -> n.endsWith(".jar"));
        if (jars != null) {
            for (File j : jars) out.add(j);
        }
        return out;
    }

    public List<File> localLibJars() {
        File dir = cfg.localLibsDir();
        return dir != null ? jarFiles(dir) : new ArrayList<>();
    }

    // === FILE OPERATIONS ===
    public String readFile(File f) throws java.io.IOException {
        return Util.readText(f);
    }

    public void writeFile(File f, String text) throws java.io.IOException {
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        Util.writeAtomic(f, text);
    }

    public void copyFile(File from, File to) throws java.io.IOException {
        Util.copyFile(from, to);
    }

    public void deleteFile(File f) {
        if (f.exists()) f.delete();
    }

    public void deleteRec(File d) {
        Util.deleteRec(d);
    }

    public void ensureDir(File d) {
        if (d != null) d.mkdirs();
    }

    public void ensureDirs(File... dirs) {
        for (File d : dirs) ensureDir(d);
    }

    public void cleanBuild() {
        deleteRec(buildDir());
    }

    public void cleanOutput() {
        deleteRec(outputDir());
    }

    public void cleanAll() {
        cleanBuild();
        cleanOutput();
    }

    // === FILE EXISTENCE CHECKS ===
    public boolean hasManifest() { return manifest().isFile(); }
    public boolean hasResources() { return resDir().isDirectory() && resDir().listFiles() != null && resDir().listFiles().length > 0; }
    public boolean hasJavaSources() { return !javaSourceFiles().isEmpty(); }
    public boolean hasProguardRules() { return proguardRules().isFile(); }

    // === LOGGING ===
    public File newBuildLog() {
        outputLogsDir().mkdirs();
        return new File(outputLogsDir(), "build-" + System.currentTimeMillis() + ".log");
    }

    public void saveBuildLog(String logs) throws java.io.IOException {
        writeFile(newBuildLog(), logs);
    }

    // === COUNTERS ===
    public int countJavaFiles() {
        return Util.countFiles(javaDir());
    }

    public int countResourceFiles() {
        return Util.countFiles(resDir());
    }

    public int countClassFiles() {
        return Util.countFiles(classesDir());
    }
}
