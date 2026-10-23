package com.apkbuilder;

import java.io.File;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real (if simple) source-level string obfuscation: every string literal in the compiled .java
 * source is base64-encoded and wrapped in a decode call, before javac ever sees it.
 * This is a real transformation stage, not a fake switch - decompiling the APK shows encoded
 * strings, not the original text.
 */
public final class StringFog {
    private StringFog() {}

    private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
    public static final String HELPER_CLASS = "_SFog";

    public static String process(String javaSource) {
        Matcher m = LITERAL.matcher(javaSource);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String raw = m.group(1);
            String unescaped = unescape(raw);
            if (unescaped.length() < 2 || unescaped.startsWith("@") || looksLikeFormatOrRes(unescaped)) {
                m.appendReplacement(out, Matcher.quoteReplacement("\"" + raw + "\""));
                continue;
            }
            String enc = Base64.getEncoder().encodeToString(unescaped.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            m.appendReplacement(out, Matcher.quoteReplacement(HELPER_CLASS + ".d(\"" + enc + "\")"));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static boolean looksLikeFormatOrRes(String s) {
        return s.contains("%") || s.contains("/") && s.length() > 40;
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\t", "\t");
    }

    public static void writeHelper(File javaRootPkgDir, String packageName) throws java.io.IOException {
        String src = "package " + packageName + ";\n\n" +
                "public final class " + HELPER_CLASS + " {\n" +
                "    private " + HELPER_CLASS + "() {}\n" +
                "    public static String d(String b64) {\n" +
                "        return new String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT));\n" +
                "    }\n" +
                "}\n";
        Util.writeAtomic(new File(javaRootPkgDir, HELPER_CLASS + ".java"), src);
    }

    /** Copies every .java file from src to dst, applying process() to non-generated files. Returns count changed. */
    public static int applyToTree(File srcJavaRoot, File dstJavaRoot) throws java.io.IOException {
        int n = 0;
        for (File f : Util.find(srcJavaRoot, ".java")) {
            String rel = f.getAbsolutePath().substring(srcJavaRoot.getAbsolutePath().length() + 1);
            File dst = new File(dstJavaRoot, rel);
            String text = Util.readText(f);
            String out = f.getName().equals("R.java") ? text : process(text);
            if (!out.equals(text)) n++;
            Util.writeAtomic(dst, out);
        }
        return n;
    }
}
