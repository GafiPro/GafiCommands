package com.gafipro.gaficommands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GafiConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("gaficommands.json");
    private static final List<String> DEFAULT_BLACKLIST = List.of("Siebe_pro2707", "GafiPro");
    private static final Map<String, String> BLACKLIST = new LinkedHashMap<>();

    private GafiConfig() {}

    public static synchronized void load() {
        BLACKLIST.clear();
        addDefaults();
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(CONFIG_PATH, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new IllegalStateException("Config root is not an object");
            JsonObject object = parsed.getAsJsonObject();
            if (!object.has("blacklist") || !object.get("blacklist").isJsonArray())
                throw new IllegalStateException("Config has no blacklist array");

            BLACKLIST.clear();
            for (JsonElement element : object.getAsJsonArray("blacklist")) {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    String fragment = normalizeFragment(element.getAsString());
                    if (!fragment.isEmpty()) BLACKLIST.putIfAbsent(key(fragment), fragment);
                }
            }
        } catch (Exception exception) {
            BLACKLIST.clear();
            addDefaults();
            System.err.println("[GafiCommands] Could not read config; using defaults: " + exception.getMessage());
            save();
        }
    }

    public static synchronized boolean addBlacklist(String input) {
        String fragment = normalizeFragment(input);
        if (fragment.isEmpty() || BLACKLIST.putIfAbsent(key(fragment), fragment) != null) return false;
        save();
        return true;
    }

    public static synchronized boolean removeBlacklist(String input) {
        String fragment = normalizeFragment(input);
        if (fragment.isEmpty() || BLACKLIST.remove(key(fragment)) == null) return false;
        save();
        return true;
    }

    public static synchronized boolean isBlacklisted(String nickname) {
        if (nickname == null || nickname.isBlank()) return false;
        String candidate = nickname.toLowerCase(Locale.ROOT);
        return BLACKLIST.values().stream().map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(candidate::contains);
    }

    public static synchronized List<String> blacklist() {
        return BLACKLIST.values().stream()
                .sorted(Comparator.comparing(value -> value.toLowerCase(Locale.ROOT))).toList();
    }

    private static void addDefaults() {
        for (String item : DEFAULT_BLACKLIST) BLACKLIST.putIfAbsent(key(item), item);
    }

    private static String normalizeFragment(String input) {
        if (input == null) return "";
        String clean = input.replaceAll("(?i)§[0-9A-FK-ORX]", "").trim();
        return clean.length() > 32 ? clean.substring(0, 32) : clean;
    }

    private static String key(String input) { return input.toLowerCase(Locale.ROOT); }

    private static synchronized void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("formatVersion", 1);
            JsonArray array = new JsonArray();
            for (String item : BLACKLIST.values()) array.add(item);
            root.add("blacklist", array);
            Path temporary = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            System.err.println("[GafiCommands] Could not save config: " + exception.getMessage());
        }
    }
}
