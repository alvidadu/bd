package com.apkbuilder;

import com.android.dx.command.dexer.DxContext;
import com.android.dx.command.dexer.Main;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** DX dexer (bundled dx-10.0.0.jar), executed in-process. */
public class DxRunner implements Dexer {
    @Override public String name() { return "DX"; }

    @Override public String unavailableReason() {
        try {
            Class.forName("com.android.dx.command.dexer.Main");
            return null;
        } catch (Throwable t) {
            return "DX tool not found (dx jar missing from app/libs)";
        }
    }

    @Override
    public void dex(List<File> inputs, File androidJar, File outDir, int minSdk, BuildLogger log) throws BuildException {
        outDir.mkdirs();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int rc;
        try {
            DxContext ctx = new DxContext(bos, bos);
            Main.Arguments a = new Main.Arguments(ctx);
            List<String> flags = new ArrayList<>();
            flags.add("--multi-dex");
            flags.add("--min-sdk-version=" + Math.max(minSdk, 13));
            flags.add("--output=" + outDir.getAbsolutePath());
            // parseFlags() only recognizes "--xxx" tokens; it never populates the input
            // file list from positional arguments (that array is left null), so the
            // input paths must be set on Arguments.fileNames directly or dx NPEs inside
            // processAllFiles() while sorting a null array.
            a.parseFlags(flags.toArray(new String[0]));
            String[] fileNames = new String[inputs.size()];
            for (int i = 0; i < inputs.size(); i++) fileNames[i] = inputs.get(i).getAbsolutePath();
            a.fileNames = fileNames;
            rc = new Main(ctx).runDx(a);
        } catch (Throwable t) {
            String out = bos.toString();
            log.log("DX", out);
            throw translate(t.toString() + "\n" + out, rc(t));
        }
        String out = bos.toString();
        log.log("DX", out);
        if (rc != 0) throw translate(out, rc);
    }

    private int rc(Throwable t) { return -1; }

    /** Turns DX's raw (often cryptic) failure text into a clear, actionable error when we
     *  recognize the cause; otherwise passes the raw output through unchanged. */
    private BuildException translate(String out, int rc) {
        if (out.contains("invokedynamic requires --min-sdk-version")) {
            return new BuildException("Dexing",
                    "Your code uses a Java 8 lambda ( -> ) or method reference, which DX can only dex "
                  + "when minSdkVersion is 26 or higher. Fix: either raise Min SDK to 26+ in Project "
                  + "Settings, or rewrite the lambda as an anonymous inner class.");
        }
        return new BuildException("Dexing", rc >= 0
                ? "DX failed with exit code " + rc + "\n" + out.trim()
                : "DX failed: " + out.trim());
    }
}
