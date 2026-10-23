package com.apkbuilder;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * D8 dexer. D8 is not bundled (Google only ships it inside r8.jar): drop r8.jar into app/libs and rebuild
 * this app. Until then selecting D8 gives a real "D8 tool not found" build error.
 */
public class D8Runner implements Dexer {
    @Override public String name() { return "D8"; }

    @Override public String unavailableReason() {
        return R8Tools.has("com.android.tools.r8.D8") ? null : "D8 tool not found (put r8.jar into app/libs, or build APK Builder with -Pr8Version=<version>, then rebuild it)";
    }

    @Override
    public void dex(List<File> inputs, File androidJar, File outDir, int minSdk, BuildLogger log) throws BuildException {
        outDir.mkdirs();
        try {
            List<Path> prog = R8Tools.programFiles(inputs);
            Class<?> cmdC = Class.forName("com.android.tools.r8.D8Command");
            Class<?> bC = Class.forName("com.android.tools.r8.D8Command$Builder");
            Object b = cmdC.getMethod("builder").invoke(null);
            bC.getMethod("addProgramFiles", java.util.Collection.class).invoke(b, prog);
            List<Path> lib = new ArrayList<>();
            lib.add(androidJar.toPath());
            bC.getMethod("addLibraryFiles", java.util.Collection.class).invoke(b, lib);
            R8Tools.setCommon(bC, b, outDir, minSdk, false);
            Object cmd = bC.getMethod("build").invoke(b);
            Class.forName("com.android.tools.r8.D8").getMethod("run", cmdC).invoke(null, cmd);
            log.log("D8", "D8 finished: " + outDir);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            throw new BuildException("Dexing", "D8 failed: " + c);
        } catch (ClassNotFoundException e) {
            throw new BuildException("Dexing", "D8 tool not found (put r8.jar into app/libs, or build APK Builder with -Pr8Version=<version>, then rebuild it)");
        } catch (Throwable t) {
            throw new BuildException("Dexing", "D8 failed: " + t);
        }
    }
}
