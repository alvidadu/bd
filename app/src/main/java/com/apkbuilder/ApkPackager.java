package com.apkbuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the real unsigned APK zip: starts from AAPT2's resources.apk (has AndroidManifest.xml +
 * resources.arsc + res/* correctly compiled) and adds classes*.dex, assets/, lib/ on top.
 */
public final class ApkPackager {
    private ApkPackager() {}

    public static void buildUnsigned(File resourcesApk, List<File> dexFiles, File assetsDir,
                                     File nativeLibsDir, File outUnsigned) throws BuildException {
        try {
            // Start from the resources.apk aapt2 produced (already a valid, correctly-compressed zip).
            Util.copyFile(resourcesApk, outUnsigned);
            java.io.File tmp = File.createTempFile("pack", ".apk", outUnsigned.getParentFile());
            try (java.util.zip.ZipFile zin = new java.util.zip.ZipFile(outUnsigned);
                 ZipOutputStream zo = new ZipOutputStream(new java.io.BufferedOutputStream(new FileOutputStream(tmp)))) {
                java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zin.entries();
                while (en.hasMoreElements()) {
                    java.util.zip.ZipEntry e = en.nextElement();
                    // Must preserve aapt2's original compression method per entry. A bare
                    // "new ZipEntry(name)" has no method set, so ZipOutputStream defaults
                    // every entry to DEFLATED - including resources.arsc, which Android
                    // requires to stay STORED (uncompressed). Since Android 11 (API 30)
                    // this is strictly enforced; a compressed resources.arsc makes the
                    // whole APK fail to install, with no specific reason shown to the user.
                    copyEntryPreservingMethod(zin, e, zo);
                }
                for (int i = 0; i < dexFiles.size(); i++) {
                    String name = i == 0 ? "classes.dex" : "classes" + (i + 1) + ".dex";
                    addFile(zo, dexFiles.get(i), name);
                }
                if (assetsDir != null && assetsDir.isDirectory()) addTree(zo, assetsDir, "assets/");
                if (nativeLibsDir != null && nativeLibsDir.isDirectory()) addTree(zo, nativeLibsDir, "lib/");
            }
            if (!outUnsigned.delete() || !tmp.renameTo(outUnsigned)) {
                throw new BuildException("Packaging", "Could not finalize unsigned.apk");
            }
        } catch (BuildException e) {
            throw e;
        } catch (Exception e) {
            throw new BuildException("Packaging", "APK packaging failed: " + e);
        }
    }

    private static void copyEntryPreservingMethod(java.util.zip.ZipFile zin, java.util.zip.ZipEntry src,
                                                   ZipOutputStream zo) throws java.io.IOException {
        ZipEntry out = new ZipEntry(src.getName());
        if (src.getMethod() == ZipEntry.STORED) {
            // STORED entries require size, compressed size and CRC to be set up front -
            // ZipOutputStream will not compute them for you the way it does for DEFLATED.
            out.setMethod(ZipEntry.STORED);
            out.setSize(src.getSize());
            out.setCompressedSize(src.getSize());
            out.setCrc(src.getCrc());
        } else {
            out.setMethod(ZipEntry.DEFLATED);
        }
        zo.putNextEntry(out);
        try (java.io.InputStream in = zin.getInputStream(src)) { Util.copy(in, zo); }
        zo.closeEntry();
    }

    private static void addFile(ZipOutputStream zo, File f, String name) throws java.io.IOException {
        zo.putNextEntry(new ZipEntry(name));
        try (FileInputStream in = new FileInputStream(f)) { Util.copy(in, zo); }
        zo.closeEntry();
    }

    private static void addTree(ZipOutputStream zo, File dir, String prefix) throws java.io.IOException {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (k.isDirectory()) addTree(zo, k, prefix + k.getName() + "/");
            else addFile(zo, k, prefix + k.getName());
        }
    }
}
