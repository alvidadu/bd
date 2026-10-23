package com.apkbuilder;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Reflection helpers shared by the optional tools (D8 / R8 / ProGuard) that live in optional jars. */
public final class R8Tools {
    private R8Tools() {}

    public static boolean has(String cls) {
        try {
            Class.forName(cls);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Directory inputs are expanded to their .class files, jars are passed as they are. */
    public static List<Path> programFiles(List<File> inputs) {
        List<Path> out = new ArrayList<>();
        for (File f : inputs) {
            if (f.isDirectory()) for (File c : Util.find(f, ".class")) out.add(c.toPath());
            else out.add(f.toPath());
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void setCommon(Class<?> bC, Object b, File outDir, int minSdk, boolean release) throws Exception {
        Class<?> modeC = Class.forName("com.android.tools.r8.CompilationMode");
        bC.getMethod("setMode", modeC).invoke(b, Enum.valueOf((Class<Enum>) modeC, release ? "RELEASE" : "DEBUG"));
        Class<?> omC = Class.forName("com.android.tools.r8.OutputMode");
        bC.getMethod("setOutput", Path.class, omC).invoke(b, outDir.toPath(), Enum.valueOf((Class<Enum>) omC, "DexIndexed"));
        bC.getMethod("setMinApiLevel", int.class).invoke(b, Math.max(minSdk, 1));
    }

    /** R8: shrink + optimise + dex in one step. */
    public static void runR8(List<File> inputs, File androidJar, List<File> rules, File outDir,
                             int minSdk, BuildLogger log) throws BuildException {
        if (!has("com.android.tools.r8.R8")) {
            throw new BuildException("Optimization", "R8 tool not found (put r8.jar into app/libs, or build APK Builder with -Pr8Version=<version>, then rebuild it)");
        }
        outDir.mkdirs();
        try {
            Class<?> cmdC = Class.forName("com.android.tools.r8.R8Command");
            Class<?> bC = Class.forName("com.android.tools.r8.R8Command$Builder");
            Object b = cmdC.getMethod("builder").invoke(null);
            bC.getMethod("addProgramFiles", Collection.class).invoke(b, programFiles(inputs));
            List<Path> lib = new ArrayList<>();
            lib.add(androidJar.toPath());
            bC.getMethod("addLibraryFiles", Collection.class).invoke(b, lib);
            List<Path> pr = new ArrayList<>();
            for (File r : rules) if (r.isFile()) pr.add(r.toPath());
            bC.getMethod("addProguardConfigurationFiles", List.class).invoke(b, pr);
            setCommon(bC, b, outDir, minSdk, true);
            Object cmd = bC.getMethod("build").invoke(b);
            Class.forName("com.android.tools.r8.R8").getMethod("run", cmdC).invoke(null, cmd);
            log.log("R8", "R8 finished (shrink + dex): " + outDir);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            throw new BuildException("Optimization", "R8 failed: " + c);
        } catch (Throwable t) {
            throw new BuildException("Optimization", "R8 failed: " + t);
        }
    }

    /** ProGuard: jar in, jar out. Uses proguard-base.jar when present in app/libs. */
    public static void runProguard(List<File> injars, File androidJar, List<File> rules, File outJar,
                                   BuildLogger log) throws BuildException {
        if (!has("proguard.ProGuard")) {
            throw new BuildException("Optimization", "ProGuard tool not found (put proguard-base.jar into app/libs and rebuild the builder)");
        }
        try {
            List<String> args = new ArrayList<>();
            for (File f : injars) { args.add("-injars"); args.add(f.getAbsolutePath()); }
            args.add("-outjars"); args.add(outJar.getAbsolutePath());
            args.add("-libraryjars"); args.add(androidJar.getAbsolutePath());
            args.add("-dontwarn");
            args.add("-ignorewarnings");
            for (File r : rules) if (r.isFile()) args.add("@" + r.getAbsolutePath());
            Class<?> cfgC = Class.forName("proguard.Configuration");
            Class<?> parC = Class.forName("proguard.ConfigurationParser");
            Object cfg = cfgC.newInstance();
            Object parser = parC.getConstructor(String[].class, java.util.Properties.class)
                    .newInstance((Object) args.toArray(new String[0]), System.getProperties());
            try {
                parC.getMethod("parse", cfgC).invoke(parser, cfg);
            } finally {
                parC.getMethod("close").invoke(parser);
            }
            Object pg = Class.forName("proguard.ProGuard").getConstructor(cfgC).newInstance(cfg);
            Class.forName("proguard.ProGuard").getMethod("execute").invoke(pg);
            log.log("ProGuard", "ProGuard finished: " + outJar);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            throw new BuildException("Optimization", "ProGuard failed: " + c);
        } catch (Throwable t) {
            throw new BuildException("Optimization", "ProGuard failed: " + t);
        }
    }
}
