package com.apkbuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class BuildResult {
    public boolean success;
    public File apkPath;
    public File alignedApkPath;
    public long buildTime;
    public String logs = "";
    public final List<BuildError> errors = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();

    public static class BuildError {
        public String stage = "";
        public String file = "";
        public int line;
        public int column;
        public String message = "";

        public BuildError() {}

        public BuildError(String stage, String file, int line, int column, String message) {
            this.stage = stage;
            this.file = file == null ? "" : file;
            this.line = line;
            this.column = column;
            this.message = message;
        }

        @Override
        public String toString() {
            String where = "";
            if (!file.isEmpty()) {
                where = new File(file).getName() + (line > 0 ? ":" + line : "") + (column > 0 ? ":" + column : "") + ": ";
            }
            return "[" + stage + "] " + where + message;
        }
    }
}
