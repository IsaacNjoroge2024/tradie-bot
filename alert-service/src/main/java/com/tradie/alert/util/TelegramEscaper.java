package com.tradie.alert.util;

/**
 * Escapes special characters for Telegram MarkdownV2 parse mode.
 * Characters that must be escaped: \ _ * [ ] ( ) ~ ` > # + - = | { } . !
 */
public final class TelegramEscaper {

    private static final String SPECIAL_CHARS = "\\_*[]()~`>#+-=|{}.!";

    private TelegramEscaper() {}

    /**
     * Escapes all MarkdownV2 special characters in the given text so it renders as plain text.
     * Use this on every dynamic value inserted into a MarkdownV2 message template.
     */
    public static String escape(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() * 2);
        for (char c : text.toCharArray()) {
            if (SPECIAL_CHARS.indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
