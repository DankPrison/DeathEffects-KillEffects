package com.dank420girl.core.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Edits YAML files as text, so that comments, blank lines and formatting survive.
 * <p>
 * Bukkit's YamlConfiguration drops comments when it saves (before 1.18), which would destroy the
 * documentation in our configuration files. This class understands just enough YAML (block maps,
 * block lists, flow lists, quoted and plain scalars, comments) to find a value by its path and
 * replace it in place. Reading the actual configuration is still left to YamlConfiguration.
 */
public final class YamlText {

    /** Called for every scalar value; returns a replacement value or {@code null} to keep it. */
    public interface ValueVisitor {
        /**
         * @param path   full path such as {@code effects.flame.layers[0].particle}
         * @param key    the key the value belongs to; for list items the key of the list
         * @param value  the unquoted value
         */
        String visit(String path, String key, String value);
    }

    /**
     * A visitor that can also replace a whole one-line list such as {@code sound: [A, B]} by a single
     * value. Lists it keeps are then visited item by item.
     */
    public interface ListVisitor extends ValueVisitor {
        /**
         * @param values the unquoted items
         * @return a single value replacing the whole list, or {@code null} to keep the list
         */
        String visitList(String path, String key, List<String> values);
    }

    /** Starts a value returned by a visitor that replaces a scalar by a one-line list, see {@link #listValue}. */
    private static final String LIST_VALUE = "\u0000[";

    private final List<String> lines = new ArrayList<String>();
    private final String newline;
    private final boolean trailingNewline;

    public YamlText(String text) {
        this.newline = text.contains("\r\n") ? "\r\n" : "\n";
        String normalized = text.replace("\r\n", "\n");
        this.trailingNewline = normalized.endsWith("\n");
        String[] split = normalized.split("\n", -1);
        int count = trailingNewline ? split.length - 1 : split.length;
        for (int i = 0; i < count; i++) {
            lines.add(split[i]);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append(newline);
            }
            sb.append(lines.get(i));
        }
        if (trailingNewline) {
            sb.append(newline);
        }
        return sb.toString();
    }

    /** Returns the value at the path (unquoted), or {@code null}. */
    public String get(final String path) {
        final String[] found = new String[1];
        walk(new ValueVisitor() {
            @Override
            public String visit(String valuePath, String key, String value) {
                if (found[0] == null && valuePath.equals(path)) {
                    found[0] = value;
                }
                return null;
            }
        });
        return found[0];
    }

    /**
     * Replaces the value at the path. Strings are quoted when needed.
     *
     * @return {@code false} if there is no such key with a value in the file
     */
    public boolean set(final String path, final Object value) {
        return set(path, value, value instanceof String ? QuoteMode.FORCE : QuoteMode.NEVER);
    }

    /**
     * Replaces the value at the path like {@link #set}, but quotes it only if it needs quotes or the
     * old value was quoted. Meant for names such as {@code DIAMOND_BLOCK}.
     */
    public boolean setName(String path, String value) {
        return set(path, value, QuoteMode.KEEP);
    }

    private boolean set(final String path, final Object value, QuoteMode mode) {
        final boolean[] changed = new boolean[1];
        final String text = value == null ? "" : String.valueOf(value);
        walk(new ListVisitor() {
            @Override
            public String visit(String valuePath, String key, String old) {
                if (!changed[0] && valuePath.equals(path)) {
                    changed[0] = true;
                    return text;
                }
                return null;
            }

            @Override
            public String visitList(String valuePath, String key, List<String> values) {
                // A one-line list (for example a list of alternatives) is replaced as a whole.
                return visit(valuePath, key, null);
            }
        }, mode);
        return changed[0];
    }

    /** Lets the visitor replace whole one-line lists and any scalar value. Returns the number of changes. */
    public int transform(ListVisitor visitor) {
        return walk(visitor, QuoteMode.KEEP);
    }

    /**
     * A value a visitor can return to replace a scalar (not a list item) by a one-line list such as
     * {@code [A, B]}.
     */
    public static String listValue(List<String> items) {
        StringBuilder sb = new StringBuilder(LIST_VALUE);
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append('\u0000');
            }
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    private static String renderList(String listValue) {
        String[] items = listValue.substring(LIST_VALUE.length()).split("\u0000", -1);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(Scalar.needsQuotes(items[i]) ? quote(items[i]) : items[i]);
        }
        return sb.append(']').toString();
    }

    /** Lets the visitor replace any scalar value. Returns the number of changed values. */
    public int transform(ValueVisitor visitor) {
        return walk(visitor, QuoteMode.KEEP);
    }

    /**
     * Sets a top-level key, adding it (with an optional comment) after the header comment of the file
     * if it does not exist yet.
     */
    public void setTopLevel(String key, Object value, String comment) {
        if (set(key, value)) {
            return;
        }
        // Keys go after directives and a "---" document start marker, if the file has them.
        int start = 0;
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("%")) {
                continue;
            }
            if (trimmed.equals("---") || trimmed.startsWith("--- ")) {
                start = i + 1;
            }
            break;
        }
        int insertAt = start;
        while (insertAt < lines.size()) {
            String trimmed = lines.get(insertAt).trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                break;
            }
            insertAt++;
        }
        // Insert before a comment block that directly precedes the first key, not inside it.
        while (insertAt > start && lines.get(insertAt - 1).trim().startsWith("#")) {
            insertAt--;
        }
        String rendered = value instanceof String ? quote((String) value) : String.valueOf(value);
        List<String> added = new ArrayList<String>();
        if (comment != null) {
            added.add("# " + comment);
        }
        added.add(key + ": " + rendered);
        added.add("");
        lines.addAll(insertAt, added);
    }

    private int walk(ValueVisitor visitor) {
        return walk(visitor, QuoteMode.KEEP);
    }

    private enum QuoteMode {
        /** Keep the original quotes, adding them only when the new value requires it. */
        KEEP,
        /** Always quote strings (used when setting text values). */
        FORCE,
        /** Write the value as it is (numbers and booleans). */
        NEVER
    }

    private static final class Frame {
        final int indent;
        final String name;
        final boolean item;
        int items;

        Frame(int indent, String name, boolean item) {
            this.indent = indent;
            this.name = name;
            this.item = item;
        }
    }

    private int walk(ValueVisitor visitor, QuoteMode mode) {
        List<Frame> stack = new ArrayList<Frame>();
        int changes = 0;
        int blockScalarIndent = -1;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int indent = indentOf(line);
            String content = line.substring(indent);
            if (blockScalarIndent >= 0) {
                if (content.isEmpty() || indent > blockScalarIndent) {
                    continue;
                }
                blockScalarIndent = -1;
            }
            if (content.isEmpty() || content.startsWith("#") || content.startsWith("---")) {
                continue;
            }

            boolean dash = content.equals("-") || content.startsWith("- ");
            while (!stack.isEmpty()) {
                Frame top = stack.get(stack.size() - 1);
                boolean listOfSameIndent = dash && !top.item && top.indent == indent;
                if (top.indent >= indent && !listOfSameIndent) {
                    stack.remove(stack.size() - 1);
                } else {
                    break;
                }
            }

            int contentIndent = indent;
            if (dash) {
                Frame parent = stack.isEmpty() ? null : stack.get(stack.size() - 1);
                int index = parent == null ? 0 : parent.items++;
                String parentKey = parent == null ? "" : parent.name;
                String rest = content.length() > 1 ? content.substring(1) : "";
                int skip = indentOf(rest);
                rest = rest.substring(skip);
                contentIndent = indent + 1 + skip;
                stack.add(new Frame(indent, "[" + index + "]", true));
                if (rest.isEmpty() || rest.startsWith("#")) {
                    continue;
                }
                int colon = keyColon(rest);
                if (colon < 0) {
                    // "- value": a scalar list item.
                    String replaced = visitScalar(visitor, path(stack, null), parentKey, rest, mode);
                    if (replaced != null && replaced.startsWith("[")) {
                        replaced = null; // a block list item cannot become a list here
                    }
                    if (replaced != null) {
                        lines.set(i, line.substring(0, contentIndent) + replaced);
                        changes++;
                    }
                    continue;
                }
                content = rest;
            }

            int colon = keyColon(content);
            if (colon < 0) {
                continue; // not something we understand, e.g. the continuation of a multi-line value
            }
            String key = unquoteKey(content.substring(0, colon).trim());
            String after = content.substring(colon + 1);
            int valueStart = indentOf(after);
            String valueText = after.substring(valueStart);
            if (valueText.isEmpty() || valueText.startsWith("#")) {
                stack.add(new Frame(contentIndent, key, false));
                continue;
            }
            if (valueText.startsWith("|") || valueText.startsWith(">")) {
                blockScalarIndent = contentIndent;
                continue;
            }
            String path = path(stack, key);
            String prefix = line.substring(0, line.length() - content.length()) + content.substring(0, colon + 1)
                    + after.substring(0, valueStart);
            if (valueText.startsWith("[")) {
                String replaced = null;
                if (visitor instanceof ListVisitor) {
                    replaced = replaceFlowList((ListVisitor) visitor, path, key, valueText, mode);
                }
                if (replaced == null) {
                    replaced = visitFlowList(visitor, path, key, valueText, mode);
                }
                if (replaced != null) {
                    lines.set(i, prefix + replaced);
                    changes++;
                }
                continue;
            }
            String replaced = visitScalar(visitor, path, key, valueText, mode);
            if (replaced != null) {
                lines.set(i, prefix + replaced);
                changes++;
            }
        }
        return changes;
    }

    private static String path(List<Frame> stack, String key) {
        StringBuilder sb = new StringBuilder();
        for (Frame frame : stack) {
            if (frame.item) {
                sb.append(frame.name);
            } else {
                if (sb.length() > 0) {
                    sb.append('.');
                }
                sb.append(frame.name);
            }
        }
        if (key != null) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(key);
        }
        return sb.toString();
    }

    /** Visits a scalar written as {@code text} (with quotes and comment); returns the new text or null. */
    private static String visitScalar(ValueVisitor visitor, String path, String key, String text, QuoteMode mode) {
        Scalar scalar = Scalar.parse(text);
        String replacement = visitor.visit(path, key, scalar.value);
        if (replacement == null || (replacement.equals(scalar.value) && mode == QuoteMode.KEEP)) {
            return null;
        }
        if (replacement.startsWith(LIST_VALUE)) {
            return renderList(replacement) + scalar.trailing;
        }
        return scalar.render(replacement, mode) + scalar.trailing;
    }

    /** Offers a whole one-line list to the visitor; returns the new text or null to keep the list. */
    private static String replaceFlowList(ListVisitor visitor, String path, String key, String text, QuoteMode mode) {
        int end = findFlowEnd(text);
        if (end < 0) {
            return null;
        }
        List<String> values = new ArrayList<String>();
        for (String part : splitFlow(text.substring(1, end))) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                values.add(Scalar.parse(trimmed).value);
            }
        }
        String replacement = visitor.visitList(path, key, values);
        if (replacement == null) {
            return null;
        }
        String trailing = text.substring(end + 1);
        Scalar plain = new Scalar((char) 0, "", "");
        return plain.render(replacement, mode) + trailing;
    }

    private static String visitFlowList(ValueVisitor visitor, String path, String key, String text, QuoteMode mode) {
        int end = findFlowEnd(text);
        if (end < 0) {
            return null; // spans several lines; leave it alone
        }
        String inner = text.substring(1, end);
        String trailing = text.substring(end + 1);
        List<String> parts = splitFlow(inner);
        boolean changed = false;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            int lead = indentOf(part);
            String trimmed = part.trim();
            String rendered = part;
            if (!trimmed.isEmpty()) {
                Scalar scalar = Scalar.parse(trimmed);
                String replacement = visitor.visit(path + "[" + i + "]", key, scalar.value);
                // Lists cannot be put inside lists here.
                if (replacement != null && !replacement.equals(scalar.value) && !replacement.startsWith(LIST_VALUE)) {
                    String tail = part.substring(lead + trimmed.length());
                    rendered = part.substring(0, lead) + scalar.render(replacement, mode) + tail;
                    changed = true;
                }
            }
            if (i > 0) {
                sb.append(',');
            }
            sb.append(rendered);
        }
        sb.append(']').append(trailing);
        return changed ? sb.toString() : null;
    }

    private static int findFlowEnd(String text) {
        char quote = 0;
        for (int i = 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    if (quote == '\'' && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                        i++;
                    } else {
                        quote = 0;
                    }
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == ']') {
                return i;
            }
        }
        return -1;
    }

    private static List<String> splitFlow(String inner) {
        List<String> parts = new ArrayList<String>();
        if (inner.trim().isEmpty()) {
            return parts;
        }
        char quote = 0;
        int start = 0;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    if (quote == '\'' && i + 1 < inner.length() && inner.charAt(i + 1) == '\'') {
                        i++;
                    } else {
                        quote = 0;
                    }
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == ',') {
                parts.add(inner.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(inner.substring(start));
        return parts;
    }

    /** Position of the colon that ends a key, or -1. Colons inside quotes or values like WOOL:14 do not count. */
    static int keyColon(String content) {
        if (content.startsWith("'") || content.startsWith("\"")) {
            char quote = content.charAt(0);
            int close = content.indexOf(quote, 1);
            while (close > 0 && quote == '\'' && close + 1 < content.length() && content.charAt(close + 1) == '\'') {
                close = content.indexOf(quote, close + 2);
            }
            if (close < 0) {
                return -1;
            }
            int colon = close + 1;
            return colon < content.length() && content.charAt(colon) == ':'
                    && (colon + 1 == content.length() || content.charAt(colon + 1) == ' ') ? colon : -1;
        }
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '#' && i > 0 && content.charAt(i - 1) == ' ') {
                return -1;
            }
            if (c == ':' && (i + 1 == content.length() || content.charAt(i + 1) == ' ')) {
                return i;
            }
            if (c == '\'' || c == '"' || c == '[' || c == '{') {
                return -1;
            }
        }
        return -1;
    }

    private static String unquoteKey(String key) {
        if (key.length() >= 2 && (key.startsWith("'") && key.endsWith("'") || key.startsWith("\"") && key.endsWith("\""))) {
            return key.substring(1, key.length() - 1);
        }
        return key;
    }

    private static int indentOf(String text) {
        int i = 0;
        while (i < text.length() && text.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    /** Quotes a string for YAML with single quotes. */
    public static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    /** A scalar value as written in the file: its quotes, its value and anything after it (a comment). */
    private static final class Scalar {
        final char quote;
        final String value;
        final String trailing;

        private Scalar(char quote, String value, String trailing) {
            this.quote = quote;
            this.value = value;
            this.trailing = trailing;
        }

        static Scalar parse(String text) {
            if (text.startsWith("'")) {
                StringBuilder sb = new StringBuilder();
                int i = 1;
                while (i < text.length()) {
                    char c = text.charAt(i);
                    if (c == '\'') {
                        if (i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                            sb.append('\'');
                            i += 2;
                            continue;
                        }
                        return new Scalar('\'', sb.toString(), text.substring(i + 1));
                    }
                    sb.append(c);
                    i++;
                }
                return new Scalar('\'', sb.toString(), "");
            }
            if (text.startsWith("\"")) {
                StringBuilder sb = new StringBuilder();
                int i = 1;
                while (i < text.length()) {
                    char c = text.charAt(i);
                    if (c == '\\' && i + 1 < text.length()) {
                        sb.append(text.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    if (c == '"') {
                        return new Scalar('"', sb.toString(), text.substring(i + 1));
                    }
                    sb.append(c);
                    i++;
                }
                return new Scalar('"', sb.toString(), "");
            }
            int comment = -1;
            for (int i = 1; i < text.length(); i++) {
                if (text.charAt(i) == '#' && text.charAt(i - 1) == ' ') {
                    comment = i;
                    break;
                }
            }
            String value = comment < 0 ? text : text.substring(0, comment);
            String trimmed = value.replaceAll("\\s+$", "");
            return new Scalar((char) 0, trimmed, text.substring(trimmed.length()));
        }

        String render(String newValue, QuoteMode mode) {
            if (mode == QuoteMode.NEVER) {
                return newValue;
            }
            // Quotes that were only there because of the old value (like 'WOOL:14') are dropped.
            boolean keepQuotes = quote != 0 && !(mode == QuoteMode.KEEP && needsQuotes(value) && !needsQuotes(newValue));
            if (quote == '"' && keepQuotes) {
                return "\"" + newValue.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            }
            if (keepQuotes || mode == QuoteMode.FORCE || needsQuotes(newValue)) {
                return quote(newValue);
            }
            return newValue;
        }

        static boolean needsQuotes(String value) {
            if (value.isEmpty()) {
                return true;
            }
            char first = value.charAt(0);
            if ("&*!|>%@`'\"#[]{},?-:".indexOf(first) >= 0 || Character.isWhitespace(first)) {
                return true;
            }
            return value.contains(": ") || value.contains(" #") || value.contains(":")
                    || Character.isWhitespace(value.charAt(value.length() - 1));
        }
    }
}
