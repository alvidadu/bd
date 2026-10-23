package com.apkbuilder;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

/** Runs the real zipalign binary. */
public class ZipAlignRunner {
    private final File zipalign;
    public ZipAlignRunner(File zipalign) { this.zipalign = zipalign; }

    public String unavailableReason() {
        if (zipalign == null || !zipalign.isFile()) return "zipalign not found";
        if (!zipalign.canExecute() && !zipalign.setExecutable(true)) return "zipalign is not executable on this device";
        return null;
    }

    public void align(File in, File out, BuildLogger log, AtomicReference<Process> holder) throws BuildException {
        String bad = unavailableReason();
        if (bad != null) throw new BuildException("ZipAlign", bad);
        if (out.exists()) out.delete();
        ToolRunner.Result r = ToolRunner.run(
                Arrays.asList(zipalign.getAbsolutePath(), "-f", "4", in.getAbsolutePath(), out.getAbsolutePath()),
                null, log, "ZipAlign", holder);
        if (r.code != 0 || !out.exists()) {
            throw new BuildException("ZipAlign", "zipalign failed (exit " + r.code + ")");
        }
    }

    /** -c = verify only. Returns true if aligned. */
    public boolean verify(File apk, BuildLogger log) {
        try {
            ToolRunner.Result r = ToolRunner.run(
                    Arrays.asList(zipalign.getAbsolutePath(), "-c", "4", apk.getAbsolutePath()),
                    null, log, "ZipAlign", null);
            return r.code == 0;
        } catch (BuildException e) {
            return false;
        }
    }
}
