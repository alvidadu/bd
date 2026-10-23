package com.apkbuilder;

import org.eclipse.jdt.internal.compiler.batch.Main;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Real Java compilation with the Eclipse compiler (ECJ), running inside the builder process. */
public final class JavaCompiler {
    private JavaCompiler() {}

    /** True if the bundled ECJ can compile Java 17 (needs ecj 3.27 or newer). */
    public static boolean supports(String level) {
        if (!Arrays.asList(ProjectConfig.JAVA_LEVELS).contains(level)) return false;
        try {
            Class<?> c = Class.forName("org.eclipse.jdt.internal.compiler.impl.CompilerOptions");
            java.lang.reflect.Method m;
            try { m = c.getMethod("versionToJdkLevel", Object.class); }
            catch (NoSuchMethodException e) { m = c.getMethod("versionToJdkLevel", String.class); }
            Object r = m.invoke(null, level);
            return r instanceof Number && ((Number) r).longValue() > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Label shown in the UI. */
    public static String label(String level) {
        return "Java " + level;
    }

    public static void compile(List<File> sources, List<File> classpath, File androidJar,
                               File outDir, String level, BuildLogger log, List<String> warnings) throws BuildException {
        if (!supports(level)) {
            throw new BuildException("JavaC", "Java " + level + " is not supported by the bundled ECJ. "
                    + "APK Builder needs ecj 3.27 or newer as app/libs/ecj.jar - "
                    + "make sure no old ecj jar is left in app/libs, then rebuild APK Builder.");
        }
        outDir.mkdirs();
        // ECJ rejects -bootclasspath at compliance 9+, so android.jar goes first on -classpath instead.
        StringBuilder cp = new StringBuilder(androidJar.getAbsolutePath());
        for (File f : classpath) {
            if (cp.length() > 0) cp.append(File.pathSeparator);
            cp.append(f.getAbsolutePath());
        }
        List<String> args = new ArrayList<>();
        args.add("-source"); args.add(level);
        args.add("-target"); args.add(level);
        args.add("-encoding");
        args.add("UTF-8");
        args.add("-proc:none");
        args.add("-classpath");
        args.add(cp.toString());
        args.add("-d");
        args.add(outDir.getAbsolutePath());
        for (File s : sources) args.add(s.getAbsolutePath());

        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        boolean ok;
        try {
            Main main = new Main(new PrintWriter(out), new PrintWriter(err), false);
            ok = main.compile(args.toArray(new String[0]));
        } catch (Throwable t) {
            throw new BuildException("JavaC", "Java compiler crashed: " + t);
        }
        String all = (out.toString() + "\n" + err.toString()).trim();
        if (!all.isEmpty()) log.log("JavaC", all);
        List<BuildResult.BuildError> errs = BuildErrorParser.parseEcj(all, warnings);
        if (!ok) {
            if (errs.isEmpty()) errs.add(new BuildResult.BuildError("JavaC", "", 0, 0, all.isEmpty() ? "Compilation failed" : all));
            throw new BuildException("JavaC", errs.size() + " Java compile error(s)", errs);
        }
    }
}
