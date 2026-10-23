package com.apkbuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans every res/values-style folder (values/, values-night/, etc.) for the same resource
 * (e.g. style/AppTheme) declared
 * twice in the same folder - a plain text/line scan, not a real XML parser, but accurate
 * enough to give a clear, specific error before handing things to AAPT2, whose own message
 * for this ("conflicting value for configuration ()") reads like an AAPT2 bug to someone
 * who doesn't already know Android's one-definition-per-folder resource rule.
 *
 * Different values-* folders (values/, values-night/, values-v21/...) are separate
 * "buckets": the same name may legitimately be redefined once per folder to override it
 * for that configuration. Only a true duplicate within the same folder is an error.
 */
public final class ResourceConflictChecker {
    private ResourceConflictChecker() {}

    private static final Pattern TAG = Pattern.compile("<(/?)([A-Za-z_][\\w.:-]*)([^>]*?)(/?)>");
    private static final Pattern NAME_ATTR = Pattern.compile("\\bname\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern TYPE_ATTR = Pattern.compile("\\btype\\s*=\\s*\"([^\"]*)\"");

    public static class Conflict {
        final String bucket, type, name;
        final List<String> locations = new ArrayList<>();
        Conflict(String bucket, String type, String name) { this.bucket = bucket; this.type = type; this.name = name; }
    }

    public static List<Conflict> check(File resDir) {
        List<Conflict> out = new ArrayList<>();
        File[] buckets = resDir.listFiles(f -> f.isDirectory() && f.getName().startsWith("values"));
        if (buckets == null) return out;

        for (File bucket : buckets) {
            // key "type/name" -> list of "file:line"
            Map<String, List<String>> seen = new LinkedHashMap<>();
            File[] files = bucket.listFiles(f -> f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".xml"));
            if (files == null) continue;
            for (File f : files) {
                try {
                    scanFile(f, seen);
                } catch (Exception ignored) {
                    // Best-effort diagnostic only: if a file can't be scanned, let AAPT2
                    // give the final word rather than blocking the build on our own check.
                }
            }
            for (Map.Entry<String, List<String>> e : seen.entrySet()) {
                if (e.getValue().size() > 1) {
                    String[] p = e.getKey().split("/", 2);
                    Conflict c = new Conflict(bucket.getName(), p[0], p[1]);
                    c.locations.addAll(e.getValue());
                    out.add(c);
                }
            }
        }
        return out;
    }

    private static void scanFile(File f, Map<String, List<String>> seen) throws Exception {
        String text = Util.readText(f);
        int line = 1, lastIdx = 0;
        int depth = 0; // depth 1 == direct child of <resources>
        Matcher m = TAG.matcher(text);
        while (m.find()) {
            line += countNewlines(text, lastIdx, m.start());
            lastIdx = m.start();
            boolean closing = !m.group(1).isEmpty();
            String tag = m.group(2);
            String attrs = m.group(3);
            boolean selfClosed = !m.group(4).isEmpty();

            if (tag.equalsIgnoreCase("resources")) {
                if (!closing && !selfClosed) depth = 0; // entering root; children will be depth 1
                continue;
            }
            if (closing) {
                if (depth > 0) depth--;
                continue;
            }

            if (depth == 0 && isResourceTag(tag)) {
                Matcher nm = NAME_ATTR.matcher(attrs);
                if (nm.find()) {
                    String name = nm.group(1);
                    String type = tag.equalsIgnoreCase("item") ? typeOf(attrs) : tag.toLowerCase(java.util.Locale.ROOT);
                    String key = type + "/" + name;
                    seen.computeIfAbsent(key, k -> new ArrayList<>()).add(f.getName() + ":" + line);
                }
            }
            if (!selfClosed) depth++;
        }
    }

    private static String typeOf(String attrs) {
        Matcher tm = TYPE_ATTR.matcher(attrs);
        return tm.find() ? tm.group(1) : "item";
    }

    private static boolean isResourceTag(String tag) {
        switch (tag.toLowerCase(java.util.Locale.ROOT)) {
            case "style": case "color": case "string": case "dimen": case "bool":
            case "integer": case "array": case "string-array": case "integer-array":
            case "plurals": case "drawable": case "fraction": case "attr": case "item": case "id":
                return true;
            default:
                return false;
        }
    }

    private static int countNewlines(String s, int from, int to) {
        int n = 0;
        for (int i = from; i < to && i < s.length(); i++) if (s.charAt(i) == '\n') n++;
        return n;
    }
}
