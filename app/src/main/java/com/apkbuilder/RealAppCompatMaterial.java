package com.apkbuilder;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts and exposes the bundled real AppCompat 1.7.1 / Material 1.13.0 dependency closure
 * (assets/appcompat-material/) - built from the actual Google AARs, not a hand-written shim.
 * Each library's classes.jar ships with no compiled R.class at all (modern AndroidX AARs never
 * include one - every consumer is expected to generate it fresh from the merged resource
 * table), which is exactly what lets this work without any class-stripping or dex surgery:
 * BuildManager compiles one fresh R.java per library package (via Aapt2Runner's
 * --extra-packages) and dexes it alongside the project's own code; each library's *other*
 * already-compiled classes (from its prebuilt .dex, used as-is) read that freshly generated
 * R class at runtime via ordinary class loading, the same way a real Gradle build works.
 */
public final class RealAppCompatMaterial {
    private RealAppCompatMaterial() {}

    public static class Lib {
        public String name, pkg;
        public boolean hasRes;
        public File jar, dex, resDir;
    }

    private static final String ASSET_DIR = "appcompat-material";

    public static List<Lib> load(Context ctx) {
        List<Lib> out = new ArrayList<>();
        try {
            File dest = new File(ctx.getFilesDir(), "appcompat_material_pack");
            File marker = new File(dest, ".extracted");
            if (!marker.isFile()) {
                Util.deleteRec(dest);
                dest.mkdirs();
                Util.copyStreamToFile(ctx.getAssets().open(ASSET_DIR + "/manifest.json"), new File(dest, "manifest.json"));
            }
            String manifestText = Util.readText(new File(dest, "manifest.json"));
            JSONArray arr = new JSONArray(manifestText);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Lib l = new Lib();
                l.name = o.getString("name");
                l.pkg = o.optString("pkg", "");
                l.hasRes = o.optBoolean("hasRes", false);

                l.jar = new File(dest, "jars/" + l.name + ".jar");
                if (!marker.isFile()) {
                    l.jar.getParentFile().mkdirs();
                    Util.copyStreamToFile(ctx.getAssets().open(ASSET_DIR + "/jars/" + l.name + ".jar"), l.jar);
                }

                l.dex = new File(dest, "dex/" + l.name + ".dex");
                if (!marker.isFile()) {
                    l.dex.getParentFile().mkdirs();
                    Util.copyStreamToFile(ctx.getAssets().open(ASSET_DIR + "/dex/" + l.name + ".dex"), l.dex);
                }

                if (l.hasRes) {
                    l.resDir = new File(dest, "res/" + l.name + "/res");
                    if (!marker.isFile()) extractAssetTree(ctx, ASSET_DIR + "/res/" + l.name + "/res", l.resDir);
                }
                out.add(l);
            }
            if (!marker.isFile()) marker.createNewFile();
        } catch (Exception e) {
            return new ArrayList<>();
        }
        return out;
    }

    /** Recursively copies an assets/ subtree to a real directory. AssetManager.list() returns
     *  the child names for a real directory and an empty array for a leaf file - since none of
     *  these bundled resource folders are ever genuinely empty (aapt never packages a directory
     *  with nothing in it), that difference reliably tells directories and files apart here. */
    private static void extractAssetTree(Context ctx, String assetPath, File dest) throws Exception {
        String[] children = ctx.getAssets().list(assetPath);
        if (children == null || children.length == 0) {
            dest.getParentFile().mkdirs();
            Util.copyStreamToFile(ctx.getAssets().open(assetPath), dest);
            return;
        }
        dest.mkdirs();
        for (String child : children) {
            extractAssetTree(ctx, assetPath + "/" + child, new File(dest, child));
        }
    }
}
