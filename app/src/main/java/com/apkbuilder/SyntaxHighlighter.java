package com.apkbuilder;

import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.widget.EditText;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real syntax highlighting for the code editor: colors Java keywords, strings, comments
 * and numbers, or XML tags/attributes/strings, depending on the open file's extension.
 * Re-colors on every edit with a short debounce so typing stays smooth on-device.
 */
public class SyntaxHighlighter {

    private static final Set<String> JAVA_KEYWORDS = new HashSet<>(java.util.Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "true", "false", "null", "@Override"));

    private static final int KEYWORD = 0xFF6E9BFF;
    private static final int STRING = 0xFF9BD87A;
    private static final int COMMENT = 0xFF6B7686;
    private static final int NUMBER = 0xFFE0A85B;
    private static final int TAG = 0xFF6E9BFF;
    private static final int ATTR = 0xFF9BD87A;

    private static final Pattern JAVA_TOKENS = Pattern.compile(
            "(//.*?$)" +                              // line comment
            "|(/\\*.*?\\*/)" +                         // block comment
            "|(\"(?:\\\\.|[^\"\\\\])*\")" +            // string
            "|('(?:\\\\.|[^'\\\\])*')" +               // char literal
            "|\\b(\\d+(\\.\\d+)?[fFdDlL]?)\\b" +       // number
            "|\\b([A-Za-z_$][A-Za-z0-9_$]*)\\b",       // word (checked against keyword set)
            Pattern.MULTILINE | Pattern.DOTALL);

    private static final Pattern XML_TOKENS = Pattern.compile(
            "(<!--.*?-->)" +                           // comment
            "|(\"[^\"]*\")" +                          // attribute value
            "|(</?[A-Za-z][A-Za-z0-9:._]*)" +          // tag name (with < or </)
            "|([A-Za-z_:][A-Za-z0-9_:.-]*=)",          // attribute name=
            Pattern.MULTILINE | Pattern.DOTALL);

    private final EditText editor;
    private final boolean xml;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pending;
    private boolean applying;

    public SyntaxHighlighter(EditText editor, String fileName) {
        this.editor = editor;
        this.xml = fileName != null && fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".xml");
    }

    /** Attaches the watcher; call once after setText(). */
    public void attach() {
        highlightNow();
        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) {
                if (applying) return;
                if (pending != null) handler.removeCallbacks(pending);
                pending = SyntaxHighlighter.this::highlightNow;
                handler.postDelayed(pending, 180);
            }
        });
    }

    private void highlightNow() {
        Editable e = editor.getText();
        if (e == null || e.length() == 0) return;
        applying = true;
        try {
            ForegroundColorSpan[] old = e.getSpans(0, e.length(), ForegroundColorSpan.class);
            for (ForegroundColorSpan s : old) e.removeSpan(s);
            StyleSpan[] oldB = e.getSpans(0, e.length(), StyleSpan.class);
            for (StyleSpan s : oldB) e.removeSpan(s);

            String text = e.toString();
            if (text.length() > 60000) return; // safety cap for huge files
            Matcher m = (xml ? XML_TOKENS : JAVA_TOKENS).matcher(text);
            while (m.find()) {
                if (xml) {
                    if (m.group(1) != null) color(e, m.start(1), m.end(1), COMMENT);
                    else if (m.group(2) != null) color(e, m.start(2), m.end(2), STRING);
                    else if (m.group(3) != null) color(e, m.start(3), m.end(3), TAG);
                    else if (m.group(4) != null) color(e, m.start(4), m.end(4), ATTR);
                } else {
                    if (m.group(1) != null) color(e, m.start(1), m.end(1), COMMENT);
                    else if (m.group(2) != null) color(e, m.start(2), m.end(2), COMMENT);
                    else if (m.group(3) != null) color(e, m.start(3), m.end(3), STRING);
                    else if (m.group(4) != null) color(e, m.start(4), m.end(4), STRING);
                    else if (m.group(5) != null) color(e, m.start(5), m.end(5), NUMBER);
                    else if (m.group(7) != null && JAVA_KEYWORDS.contains(m.group(7))) {
                        color(e, m.start(7), m.end(7), KEYWORD);
                        e.setSpan(new StyleSpan(Typeface.BOLD), m.start(7), m.end(7), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
        } finally {
            applying = false;
        }
    }

    private void color(Editable e, int start, int end, int c) {
        if (start < 0 || end > e.length() || start >= end) return;
        e.setSpan(new ForegroundColorSpan(c), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
