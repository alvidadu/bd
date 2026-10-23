package com.apkbuilder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles the launcher icon of the project being built.
 *
 * - A custom icon picked from the gallery is center-cropped to a square and written as
 *   res/mipmap-{mdpi..xxxhdpi}/ic_launcher.png, and the manifest points to @mipmap/ic_launcher.
 * - With no custom icon the project uses the default vector icon (@drawable/ic_launcher),
 *   which is (re)created automatically if it is missing, so a build never fails because of the icon.
 */
public final class AppIconManager {
    private AppIconManager() {}

    private static final String[] DENSITY = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
    private static final int[] SIZE = {48, 72, 96, 144, 192};
    private static final Pattern APP_TAG = Pattern.compile("<application\\b[^>]*>", Pattern.DOTALL);
    private static final Pattern ICON_ATTR = Pattern.compile("\\sandroid:icon\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern ROUND_ATTR = Pattern.compile("\\sandroid:roundIcon\\s*=\\s*\"[^\"]*\"");

    private static File mipmap(ProjectConfig cfg, String density) {
        return new File(cfg.resDir(), "mipmap-" + density + "/ic_launcher.png");
    }

    private static File defaultVector(ProjectConfig cfg) {
        return new File(cfg.resDir(), "drawable/ic_launcher.xml");
    }

    public static boolean hasCustomIcon(ProjectConfig cfg) {
        return mipmap(cfg, "xxxhdpi").isFile();
    }

    /** Biggest saved icon, for the preview in Settings (null when the default icon is used). */
    public static File previewFile(ProjectConfig cfg) {
        File f = mipmap(cfg, "xxxhdpi");
        return f.isFile() ? f : null;
    }

    /** Reads the picked image from the gallery (downsampled so huge photos can't run out of memory). */
    public static Bitmap decode(Context ctx, Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Cannot open the selected image");
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("This file is not a valid image");
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= 1024 && bounds.outHeight / (sample * 2) >= 1024) sample *= 2;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bmp;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Cannot open the selected image");
            bmp = BitmapFactory.decodeStream(in, null, o);
        }
        if (bmp == null) throw new IOException("This file is not a valid image");
        return bmp;
    }

    /** Makes the picked bitmap the project's launcher icon. */
    public static void apply(ProjectConfig cfg, Bitmap src) throws IOException {
        int side = Math.min(src.getWidth(), src.getHeight());
        Bitmap square = Bitmap.createBitmap(src, (src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
        for (int i = 0; i < DENSITY.length; i++) {
            Bitmap scaled = Bitmap.createScaledBitmap(square, SIZE[i], SIZE[i], true);
            File out = mipmap(cfg, DENSITY[i]);
            out.getParentFile().mkdirs();
            try (FileOutputStream fo = new FileOutputStream(out)) {
                scaled.compress(Bitmap.CompressFormat.PNG, 100, fo);
            }
            if (scaled != square) scaled.recycle();
        }
        if (square != src) square.recycle();
        setManifestIcon(cfg, "@mipmap/ic_launcher", true);
    }

    /** Back to the default icon: removes the custom PNGs and points the manifest at the default vector. */
    public static void reset(ProjectConfig cfg) throws IOException {
        for (String d : DENSITY) {
            File f = mipmap(cfg, d);
            if (f.isFile()) f.delete();
        }
        writeDefaultVector(cfg);
        setManifestIcon(cfg, "@drawable/ic_launcher", false);
    }

    /**
     * Called before every build: if the manifest references ic_launcher but the file behind it is
     * gone, fall back to the default icon so AAPT2 never fails with "resource not found".
     */
    public static void ensureValid(ProjectConfig cfg) {
        try {
            File mf = cfg.manifest();
            if (!mf.isFile()) return;
            Matcher t = APP_TAG.matcher(Util.readText(mf));
            if (!t.find()) return;
            Matcher a = ICON_ATTR.matcher(t.group());
            if (!a.find()) return; // no icon attribute: Android's own default icon is used
            String ref = a.group(1);
            if (ref.equals("@mipmap/ic_launcher") && !hasCustomIcon(cfg)) {
                reset(cfg);
            } else if (ref.equals("@drawable/ic_launcher") && !defaultVector(cfg).isFile()
                    && !new File(cfg.resDir(), "drawable/ic_launcher.png").isFile()) {
                writeDefaultVector(cfg);
            }
        } catch (Exception ignored) { }
    }

    private static void writeDefaultVector(ProjectConfig cfg) throws IOException {
        File v = defaultVector(cfg);
        if (v.isFile()) return;
        v.getParentFile().mkdirs();
        Util.writeAtomic(v,
                "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
              + "    android:width=\"108dp\" android:height=\"108dp\"\n"
              + "    android:viewportWidth=\"108\" android:viewportHeight=\"108\">\n"
              + "    <path android:fillColor=\"#3D8BFF\" android:pathData=\"M0,0h108v108h-108z\" />\n"
              + "    <path android:fillColor=\"#FFFFFF\" android:pathData=\"M30,38h48v8h-48z M30,52h34v8h-34z M30,66h44v8h-44z\" />\n"
              + "</vector>\n");
    }

    private static void setManifestIcon(ProjectConfig cfg, String ref, boolean dropRoundIcon) throws IOException {
        File mf = cfg.manifest();
        String xml = Util.readText(mf);
        Matcher t = APP_TAG.matcher(xml);
        if (!t.find()) throw new IOException("No <application> tag in AndroidManifest.xml");
        String tag = t.group();
        Matcher a = ICON_ATTR.matcher(tag);
        String newTag;
        if (a.find()) {
            newTag = a.replaceFirst(Matcher.quoteReplacement(" android:icon=\"" + ref + "\""));
        } else {
            newTag = tag.replaceFirst("<application", Matcher.quoteReplacement("<application android:icon=\"" + ref + "\""));
        }
        if (dropRoundIcon) newTag = ROUND_ATTR.matcher(newTag).replaceFirst("");
        Util.writeAtomic(mf, xml.substring(0, t.start()) + newTag + xml.substring(t.end()));
    }
}
