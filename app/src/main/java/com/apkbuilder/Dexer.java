package com.apkbuilder;

import java.io.File;
import java.util.List;

/** Converts .class files / jars into classes.dex. Replaceable implementation (DX, D8). */
public interface Dexer {
    String name();

    /** null when usable, otherwise the reason it is not (e.g. "D8 tool not found"). */
    String unavailableReason();

    void dex(List<File> inputs, File androidJar, File outDir, int minSdk, BuildLogger log) throws BuildException;
}
