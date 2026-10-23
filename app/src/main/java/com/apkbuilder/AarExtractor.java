package com.apkbuilder;

import java.io.File;

/**
 * Pulls the compiled code (classes.jar) out of an .aar library so it can join the normal
 * compile classpath and dex input, the same as a plain .jar dependency.
 *
 * What this deliberately does NOT do: merge the AAR's res/ folder into the project's
 * resources. Correctly merging library resources means assigning every resource a stable
 * ID that's consistent between what the library's own compiled .class files expect and
 * what the final APK's resource table actually contains - that's what AAPT2's static-library
 * linking (and AGP's whole resource-merging pipeline) exists to do correctly. A naive file
 * copy can produce an APK that installs fine and then crashes at runtime with the wrong
 * drawable/string/style, which is worse than clearly not supporting it. So: a library AAR
 * with no resources (most small utility libraries) works correctly end-to-end here. A
 * library that leans on its own resources (AppCompat, Material) will compile and dex, but
 * anything it draws from its own XML resources (themes, layouts, drawables) will not be
 * there - use a real Gradle build for those instead (see GradleFilesGenerator).
 */
public final class AarExtractor {
    private AarExtractor() {}

    public static class Extracted {
        public final File classesJar; // null if the AAR has no code (pure-resource AAR)
        public final boolean hadResources;
        Extracted(File classesJar, boolean hadResources) { this.classesJar = classesJar; this.hadResources = hadResources; }
    }

    /** Extracts into outDir/<aar name without extension>/. Re-extracts only if the AAR is
     *  newer than the previous extraction, so repeated builds don't re-unzip every time. */
    public static Extracted extract(File aar, File outDir) throws Exception {
        String base = Util.stripExt(aar.getName());
        File dest = new File(outDir, base);
        File marker = new File(dest, ".extracted_from_" + aar.lastModified());
        if (!marker.isFile()) {
            Util.deleteRec(dest);
            dest.mkdirs();
            Util.unzip(aar, dest);
            marker.createNewFile();
        }
        File classesJar = new File(dest, "classes.jar");
        File res = new File(dest, "res");
        boolean hasRes = res.isDirectory() && res.listFiles() != null && res.listFiles().length > 0;
        return new Extracted(classesJar.isFile() ? classesJar : null, hasRes);
    }
}
