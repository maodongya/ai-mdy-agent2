package com.anvil.ui;

/** Strips ANSI escape sequences from terminal output (v1 plain-text display). */
final class AnsiStrip {

    private static final java.util.regex.Pattern ANSI =
            java.util.regex.Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]|\u001B\\][^\u0007]*\u0007|\u001B[()][AB012]");

    private AnsiStrip() {}

    static String plain(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        return ANSI.matcher(text).replaceAll("");
    }
}
