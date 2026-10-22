package com.gafipro.gaficommands;

import java.util.Locale;
import java.util.Optional;

public final class PublicChatParser {
    private PublicChatParser() {}

    public static Optional<String> extractPublicBody(String rawMessage, String senderName) {
        if (rawMessage == null || rawMessage.isBlank() || senderName == null || senderName.isBlank()) return Optional.empty();
        String clean = stripFormatting(rawMessage).trim();
        if (clean.isEmpty() || looksPrivate(clean)) return Optional.empty();
        String lower = clean.toLowerCase(Locale.ROOT);
        String sender = senderName.toLowerCase(Locale.ROOT);
        int index = findNicknameToken(lower, sender);
        if (index < 0) return Optional.of(clean);

        String prefix = lower.substring(0, index).trim();
        if (looksPrivate(prefix)) return Optional.empty();
        int end = index + senderName.length();
        if (end > clean.length()) return Optional.of(clean);
        String suffix = clean.substring(end).stripLeading();
        if (suffix.isEmpty()) return Optional.empty();
        char first = suffix.charAt(0);
        if (first == ':' || first == '>' || first == '»' || first == '›' || first == '|' || first == ']') {
            suffix = suffix.substring(1).stripLeading();
        } else if (first == '-' && suffix.length() > 1 && suffix.charAt(1) == '>') {
            suffix = suffix.substring(2).stripLeading();
        } else if (!Character.isWhitespace(clean.charAt(end))) {
            return Optional.of(clean);
        }
        if (suffix.isBlank() || looksPrivate(suffix)) return Optional.empty();
        return Optional.of(suffix);
    }

    public static String stripFormatting(String message) {
        return message == null ? "" : message.replaceAll("(?i)§[0-9A-FK-ORX]", "");
    }

    private static int findNicknameToken(String line, String nickname) {
        int from = 0;
        while (from < line.length()) {
            int found = line.indexOf(nickname, from);
            if (found < 0) return -1;
            int end = found + nickname.length();
            boolean leftBoundary = found == 0 || !isNicknameChar(line.charAt(found - 1));
            boolean rightBoundary = end >= line.length() || !isNicknameChar(line.charAt(end));
            if (leftBoundary && rightBoundary) return found;
            from = found + 1;
        }
        return -1;
    }

    private static boolean isNicknameChar(char c) { return Character.isLetterOrDigit(c) || c == '_'; }

    private static boolean looksPrivate(String text) {
        String value = text.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("msg ") || value.startsWith("msg:") || value.startsWith("msg|")
                || value.startsWith("[msg]") || value.startsWith("[pm]") || value.startsWith("[dm]")
                || value.startsWith("from ") || value.startsWith("whisper ")
                || value.startsWith("you whisper") || value.startsWith("to ")
                || value.contains(" whispers to you:") || value.contains(" whispers to you ")
                || value.contains(" whispers:") || value.contains(" tells you:")
                || value.contains(" sent you a private message")
                || value.matches(".*\\s->\\s*(?:you|me)(?:\\s*:.*)?$");
    }
}
