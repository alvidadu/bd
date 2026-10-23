package com.apkbuilder;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Minimal, dependency-free replacement for androidx.core.content.FileProvider (the on-device
 * build environment here cannot reliably resolve androidx artifacts). Exposes files under the
 * app's own files/ directory as content:// URIs with FLAG_GRANT_READ_URI_PERMISSION, exactly
 * like FileProvider, without needing androidx.core on the classpath.
 */
public class ApkFileProvider extends ContentProvider {
    public static final String AUTHORITY = "com.apkbuilder.files";

    @Override public boolean onCreate() { return true; }

    /** Builds a content:// Uri for a file under the app's files dir. Mirrors FileProvider.getUriForFile(). */
    public static Uri uriFor(File file) {
        String base = new File(android.os.Environment.getDataDirectory(), "").getAbsolutePath();
        return new Uri.Builder().scheme("content").authority(AUTHORITY).path(file.getAbsolutePath()).build();
    }

    private File fileFor(Uri uri) throws FileNotFoundException {
        String path = uri.getPath();
        if (path == null) throw new FileNotFoundException("No path in " + uri);
        File f = new File(path);
        if (!f.isFile()) throw new FileNotFoundException(path);
        return f;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        int flags = "w".equals(mode) ? ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE
                : ParcelFileDescriptor.MODE_READ_ONLY;
        return ParcelFileDescriptor.open(fileFor(uri), flags);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        try {
            File f = fileFor(uri);
            MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            c.addRow(new Object[]{f.getName(), f.length()});
            return c;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public String getType(Uri uri) {
        String ext = android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        if ("apk".equalsIgnoreCase(ext)) return "application/vnd.android.package-archive";
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime != null ? mime : "application/octet-stream";
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
