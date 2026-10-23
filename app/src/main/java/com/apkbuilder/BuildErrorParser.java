package com.apkbuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns raw tool output into structured errors (file / line / column / message). */
public final class BuildErrorParser {
    private BuildErrorParser() {}

    private static final Pattern AAPT = Pattern.compile("^(.*?):(\\d+): (error|warn|warning): (.*)$");
    private static final Pattern AAPT_NOFILE = Pattern.compile("^(?:aapt2 )?(error|warn|warning): (.*)$");
    private static final Pattern ECJ = Pattern.compile(
            "^\\d+\\. (ERROR|WARNING) in (.+?) \\(at line (\\d+)\\)\\r?\\n([^\\n]*)\\r?\\n([^\\n]*)\\r?\\n([^\\n]*(?:\\n(?!-{10})[^\\n]*)*)",
            Pattern.MULTILINE);

    public static List<BuildResult.BuildError> parseAapt2(List<String> lines, String stage) {
        List<BuildResult.BuildError> out = new ArrayList<>();
        for (String l : lines) {
            Matcher m = AAPT.matcher(l);
            if (m.matches()) {
                if (m.group(3).startsWith("warn")) continue;
                out.add(new BuildResult.BuildError(stage, m.group(1), Integer.parseInt(m.group(2)), 0, m.group(4)));
                continue;
            }
            m = AAPT_NOFILE.matcher(l);
            if (m.matches() && m.group(1).equals("error")) {
                out.add(new BuildResult.BuildError(stage, "", 0, 0, m.group(2)));
            }
        }
        return out;
    }

    public static List<BuildResult.BuildError> parseEcj(String text, List<String> warnings) {
        List<BuildResult.BuildError> out = new ArrayList<>();
        Matcher m = ECJ.matcher(text);
        while (m.find()) {
            String kind = m.group(1);
            String src = m.group(4);
            String caret = m.group(5);
            int col = Math.max(1, caret.indexOf('^') + 1 - (src.startsWith("\t") ? 1 : 0));
            String msg = m.group(6).trim();
            if (kind.equals("ERROR")) {
                out.add(new BuildResult.BuildError("JavaC", m.group(2), Integer.parseInt(m.group(3)), col, msg));
            } else if (warnings != null) {
                warnings.add(new java.io.File(m.group(2)).getName() + ":" + m.group(3) + ": " + msg);
            }
        }
        return out;
    }
}
