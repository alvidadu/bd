package com.apkbuilder;

/** Collects the real output of every build tool. Thread safe. */
public class BuildLogger {
    public interface Listener {
        void onLine(String line);
        void onStage(String stage);
    }

    private final StringBuilder all = new StringBuilder();
    private volatile Listener listener;
    private final long start = System.currentTimeMillis();

    public void setListener(Listener l) { listener = l; }

    public synchronized void log(String tag, String msg) {
        if (msg == null) return;
        for (String l : msg.split("\r?\n")) {
            if (l.trim().isEmpty()) continue;
            String line = "[" + tag + "] " + l;
            all.append(line).append('\n');
            Listener x = listener;
            if (x != null) x.onLine(line);
        }
    }

    public void stage(String stage) {
        log("Stage", "=== " + stage + " ===");
        Listener x = listener;
        if (x != null) x.onStage(stage);
    }

    public synchronized String text() { return all.toString(); }

    public long elapsed() { return System.currentTimeMillis() - start; }
}
