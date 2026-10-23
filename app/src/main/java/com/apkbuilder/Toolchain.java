package com.apkbuilder;

import java.io.File;

/** Locations of the external (native / platform) build tools. ECJ, DX and apksig are bundled Java libraries. */
public class Toolchain {
    public File aapt2;
    public File zipalign;
    public File androidJar;

    public Toolchain() {}

    public Toolchain(File aapt2, File zipalign, File androidJar) {
        this.aapt2 = aapt2;
        this.zipalign = zipalign;
        this.androidJar = androidJar;
    }
}
