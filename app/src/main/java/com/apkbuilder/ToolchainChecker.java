package com.apkbuilder;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** "Build Environment" screen data source. Never fakes a green check. */
public class ToolchainChecker {
    private final Context ctx;

    public ToolchainChecker(Context ctx) { this.ctx = ctx; }

    public File androidJar() { return new File(ctx.getFilesDir(), "toolchain/android.jar"); }
    public File nativeLibDir() { return new File(ctx.getApplicationInfo().nativeLibraryDir); }
    public File aapt2() { return new File(nativeLibDir(), "libaapt2.so"); }
    public File zipalign() { return new File(nativeLibDir(), "libzipalign.so"); }
    public File toolchainDir() { return new File(ctx.getFilesDir(), "toolchain"); }
    public File importedAapt2() { return new File(toolchainDir(), "aapt2"); }
    public File importedZipalign() { return new File(toolchainDir(), "zipalign"); }

    /** Prepares the bundled toolchain into app-private storage the first time the app runs. */
    public void ensureBundledToolchain() {
        File jar = androidJar();
        if (!jar.exists()) {
            try {
                jar.getParentFile().mkdirs();
                Util.copyStreamToFile(ctx.getAssets().open("toolchain/android.jar"), jar);
            } catch (Exception ignored) { }
        }
    }

    public File resolvedAapt2() { return importedAapt2().isFile() ? importedAapt2() : aapt2(); }
    public File resolvedZipalign() { return importedZipalign().isFile() ? importedZipalign() : zipalign(); }

    public List<ToolStatus> check() {
        List<ToolStatus> out = new ArrayList<>();
        out.add(new ToolStatus("Java compiler (ECJ)", classExists("org.eclipse.jdt.internal.compiler.batch.Main"),
                "bundled in app/libs"));
        out.add(new ToolStatus("android.jar", androidJar().isFile(), androidJar().getAbsolutePath()));
        File a2 = resolvedAapt2();
        out.add(new ToolStatus("AAPT2", a2.isFile(), a2.getAbsolutePath()));
        File za = resolvedZipalign();
        out.add(new ToolStatus("zipalign", za.isFile(), za.getAbsolutePath()));
        out.add(new ToolStatus("DX dexer", classExists("com.android.dx.command.dexer.Main"), "bundled in app/libs"));
        out.add(new ToolStatus("D8 dexer", R8Tools.has("com.android.tools.r8.D8"),
                R8Tools.has("com.android.tools.r8.D8") ? "bundled" : "not bundled - drop r8.jar into app/libs"));
        out.add(new ToolStatus("R8 shrinker", R8Tools.has("com.android.tools.r8.R8"),
                R8Tools.has("com.android.tools.r8.R8") ? "bundled" : "not bundled - drop r8.jar into app/libs"));
        out.add(new ToolStatus("ProGuard", R8Tools.has("proguard.ProGuard"),
                R8Tools.has("proguard.ProGuard") ? "bundled" : "not bundled - drop proguard-base.jar into app/libs"));
        out.add(new ToolStatus("APK signer (apksig)", classExists("com.android.apksig.ApkSigner"), "bundled in app/libs"));
        return out;
    }

    private static boolean classExists(String c) {
        try { Class.forName(c); return true; } catch (Throwable t) { return false; }
    }

    /** Imports a user-picked toolchain folder (expects aapt2 / zipalign executables inside it) into app storage. */
    public void importToolchain(File externalDir) throws java.io.IOException {
        toolchainDir().mkdirs();
        File a2 = new File(externalDir, "aapt2");
        File za = new File(externalDir, "zipalign");
        if (a2.isFile()) { Util.copyFile(a2, importedAapt2()); importedAapt2().setExecutable(true); }
        if (za.isFile()) { Util.copyFile(za, importedZipalign()); importedZipalign().setExecutable(true); }
        File jar = new File(externalDir, "android.jar");
        if (jar.isFile()) Util.copyFile(jar, androidJar());
    }
}
