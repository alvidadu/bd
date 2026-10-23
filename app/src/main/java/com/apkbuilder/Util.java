package com.apkbuilder;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Small file / zip helpers used by every module. */
public final class Util {
    private Util() {}

    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] b = new byte[32768];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
    }

    public static String readText(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            copy(in, bo);
            return new String(bo.toByteArray(), "UTF-8");
        }
    }

    /** Atomic write: temp file in the same folder, fsync, rename. Never leaves a half written file. */
    public static void writeAtomic(File f, String text) throws IOException {
        File dir = f.getAbsoluteFile().getParentFile();
        if (dir != null) dir.mkdirs();
        File tmp = new File(dir, f.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes("UTF-8"));
            out.flush();
            out.getFD().sync();
        }
        if (!tmp.renameTo(f)) {
            if (f.exists() && !f.delete()) throw new IOException("Cannot replace " + f);
            if (!tmp.renameTo(f)) throw new IOException("Cannot write " + f);
        }
    }

    public static void copyFile(File a, File b) throws IOException {
        if (b.getParentFile() != null) b.getParentFile().mkdirs();
        try (FileInputStream in = new FileInputStream(a); FileOutputStream out = new FileOutputStream(b)) {
            copy(in, out);
        }
    }

    public static void copyStreamToFile(InputStream in, File b) throws IOException {
        if (b.getParentFile() != null) b.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(b)) {
            copy(in, out);
        } finally {
            in.close();
        }
    }

    public static void deleteRec(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRec(k);
        f.delete();
    }

    public static void copyDir(File a, File b, String skipTop) throws IOException {
        if (a.isFile()) {
            copyFile(a, b);
            return;
        }
        b.mkdirs();
        File[] kids = a.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (skipTop != null && k.getName().equals(skipTop)) continue;
            copyDir(k, new File(b, k.getName()), null);
        }
    }

    /** All files under dir whose name ends with suffix (case-insensitive), sorted. */
    public static List<File> find(File dir, String suffix) {
        List<File> out = new ArrayList<>();
        walk(dir, suffix.toLowerCase(Locale.ROOT), out);
        Collections.sort(out);
        return out;
    }

    private static void walk(File d, String suf, List<File> out) {
        File[] kids = d.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (k.isDirectory()) walk(k, suf, out);
            else if (k.getName().toLowerCase(Locale.ROOT).endsWith(suf)) out.add(k);
        }
    }

    public static int countFiles(File d) {
        if (d == null || !d.exists()) return 0;
        if (d.isFile()) return 1;
        int n = 0;
        File[] kids = d.listFiles();
        if (kids != null) for (File k : kids) n += countFiles(k);
        return n;
    }

    /** Resolves rel below root and refuses anything that escapes root (path traversal). */
    public static File safeChild(File root, String rel) throws IOException {
        File f = new File(root, rel);
        String rc = root.getCanonicalPath();
        String fc = f.getCanonicalPath();
        if (!fc.equals(rc) && !fc.startsWith(rc + File.separator))
            throw new IOException("Path escapes project: " + rel);
        return f;
    }

    public static void zipDir(File dir, File zip, Set<String> skip) throws IOException {
        try (ZipOutputStream zo = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip)))) {
            zipInto(zo, dir, "", skip);
        }
    }

    private static void zipInto(ZipOutputStream zo, File f, String prefix, Set<String> skip) throws IOException {
        File[] kids = f.listFiles();
        if (kids == null) return;
        Arrays.sort(kids);
        for (File k : kids) {
            String rel = prefix + k.getName();
            if (skip != null && skip.contains(rel)) continue;
            if (k.isDirectory()) {
                zo.putNextEntry(new ZipEntry(rel + "/"));
                zo.closeEntry();
                zipInto(zo, k, rel + "/", skip);
            } else {
                zo.putNextEntry(new ZipEntry(rel));
                try (FileInputStream in = new FileInputStream(k)) {
                    copy(in, zo);
                }
                zo.closeEntry();
            }
        }
    }

    /** Extracts zip into dest; rejects entries that would land outside dest (zip-slip). */
    public static void unzip(File zip, File dest) throws IOException {
        dest.mkdirs();
        try (ZipInputStream zi = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry e;
            while ((e = zi.getNextEntry()) != null) {
                File out = safeChild(dest, e.getName());
                if (e.isDirectory()) {
                    out.mkdirs();
                } else {
                    if (out.getParentFile() != null) out.getParentFile().mkdirs();
                    try (FileOutputStream fo = new FileOutputStream(out)) {
                        copy(zi, fo);
                    }
                }
            }
        }
    }

    public static String stripExt(String n) {
        int i = n.lastIndexOf('.');
        return i > 0 ? n.substring(0, i) : n;
    }
}
