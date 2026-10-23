package com.apkbuilder;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.File;

/** Launches the real Android package installer via our ApkFileProvider content:// URI (no unsafe file://). */
public final class ApkInstaller {
    private ApkInstaller() {}

    public static void install(Context ctx, File apk) {
        Uri uri = ApkFileProvider.uriFor(apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }
}
