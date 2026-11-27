package tech.sarthakgoyal.bastionvault.service;

import java.util.Locale;

/** File formats for import and export. ENV only exists per group; JSON and YAML also cover a whole vault. */
public enum SecretFormat {
    ENV("env", ".env", "text/plain"),
    JSON("json", ".json", "application/json"),
    YAML("yaml", ".yaml", "application/yaml");

    private final String id;
    private final String extension;
    private final String contentType;

    SecretFormat(String id, String extension, String contentType) {
        this.id = id;
        this.extension = extension;
        this.contentType = contentType;
    }

    public String id() { return id; }
    public String extension() { return extension; }
    public String contentType() { return contentType; }

    public String label() {
        return this == ENV ? ".env" : name();
    }

    public static SecretFormat of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Format is required: env, json or yaml");
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "env", "dotenv" -> ENV;
            case "json" -> JSON;
            case "yaml", "yml" -> YAML;
            default -> throw new IllegalArgumentException("Unknown format '" + value + "': use env, json or yaml");
        };
    }

    /** From a file name: .json, .yaml/.yml, otherwise .env. */
    public static SecretFormat fromFileName(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".json")) {
            return JSON;
        }
        if (name.endsWith(".yaml") || name.endsWith(".yml")) {
            return YAML;
        }
        return ENV;
    }

    /** From an HTTP Content-Type, falling back to ENV for text/plain or anything unknown. */
    public static SecretFormat fromContentType(String contentType) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (type.contains("json")) {
            return JSON;
        }
        if (type.contains("yaml") || type.contains("yml")) {
            return YAML;
        }
        return ENV;
    }
}
