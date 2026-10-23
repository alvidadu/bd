package com.apkbuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Runs one external tool as a real process and streams its real output into the build log. */
public final class ToolRunner {
    private ToolRunner() {}

    public static class Result {
        public int code = -1;
        public final List<String> lines = new ArrayList<>();
    }

    public static Result run(List<String> cmd, File dir, BuildLogger log, String tag,
                             AtomicReference<Process> holder) throws BuildException {
        Result r = new Result();
        Process p;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (dir != null) pb.directory(dir);
            pb.redirectErrorStream(true);
            p = pb.start();
        } catch (IOException e) {
            throw new BuildException(tag, "Cannot start " + new File(cmd.get(0)).getName()
                    + ": " + e.getMessage() + "\nThis build tool is not compatible with this device.");
        }
        if (holder != null) holder.set(p);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"))) {
            String l;
            while ((l = br.readLine()) != null) {
                r.lines.add(l);
                if (log != null) log.log(tag, l);
            }
            r.code = p.waitFor();
        } catch (IOException e) {
            throw new BuildException(tag, "I/O error while running " + new File(cmd.get(0)).getName() + ": " + e.getMessage());
        } catch (InterruptedException e) {
            p.destroy();
            throw new BuildException(tag, "Interrupted");
        } finally {
            if (holder != null) holder.set(null);
        }
        return r;
    }
}
