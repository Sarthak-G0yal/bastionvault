package tech.sarthakgoyal.bastionvault.service;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Represent;
import org.yaml.snakeyaml.representer.Representer;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads and writes secrets as .env, JSON or YAML.
 *
 * <p>Two shapes:
 * <ul>
 * <li><b>group</b>: a flat map {@code {"KEY": "value"}} (all three formats)</li>
 * <li><b>vault</b>: {@code {"group": {"KEY": "value"}}} (JSON and YAML)</li>
 * </ul>
 *
 * <p>Values are always strings. YAML is read at the node level, so a value comes back exactly as
 * written: {@code yes}, {@code 0123} or {@code 5432} stay those strings instead of becoming a boolean,
 * octal or integer. JSON numbers and booleans are accepted and converted to their text with a warning.
 */
@Component
public class SecretCodec {

    public static final int MAX_GROUP_BYTES = EnvParser.MAX_BYTES;
    public static final int MAX_VAULT_BYTES = 4 * 1024 * 1024;

    public record Parsed(Map<String, String> entries, List<String> warnings) {
    }

    public record ParsedVault(Map<String, Map<String, String>> groups, List<String> warnings) {
        public int keyCount() {
            return groups.values().stream().mapToInt(Map::size).sum();
        }
    }

    private final ObjectMapper json;

    public SecretCodec(ObjectMapper json) {
        this.json = json;
    }

    // ---------------------------------------------------------------- write

    public String writeGroup(Map<String, String> values, SecretFormat format) {
        return switch (format) {
            case ENV -> EnvFormatter.format(values);
            case JSON -> json.writerWithDefaultPrettyPrinter().writeValueAsString(values) + "\n";
            case YAML -> yamlWriter().dump(values);
        };
    }

    public String writeVault(Map<String, Map<String, String>> groups, SecretFormat format) {
        return switch (format) {
            case ENV -> throw new IllegalArgumentException(".env holds one group; export the vault as JSON or YAML");
            case JSON -> json.writerWithDefaultPrettyPrinter().writeValueAsString(groups) + "\n";
            case YAML -> yamlWriter().dump(groups);
        };
    }

    // ---------------------------------------------------------------- read

    public Parsed readGroup(String text, SecretFormat format) {
        checkSize(text, MAX_GROUP_BYTES);
        if (format == SecretFormat.ENV) {
            EnvParser.Result r = EnvParser.parse(text);
            return new Parsed(r.entries(), r.warnings());
        }
        List<String> warnings = new ArrayList<>();
        Map<String, String> entries = format == SecretFormat.JSON
                ? jsonFlat(jsonRoot(text), "", warnings)
                : yamlFlat(yamlRoot(text), "", warnings);
        return new Parsed(entries, warnings);
    }

    public ParsedVault readVault(String text, SecretFormat format) {
        checkSize(text, MAX_VAULT_BYTES);
        List<String> warnings = new ArrayList<>();
        Map<String, Map<String, String>> groups = new LinkedHashMap<>();
        switch (format) {
            case ENV -> throw new IllegalArgumentException("A vault file must be JSON or YAML");
            case JSON -> {
                JsonNode root = jsonRoot(text);
                for (Map.Entry<String, JsonNode> group : root.properties()) {
                    if (!group.getValue().isObject()) {
                        warnings.add(group.getKey() + ": expected an object of KEY: value pairs, skipped");
                        continue;
                    }
                    groups.put(group.getKey(), jsonFlat(group.getValue(), group.getKey() + "/", warnings));
                }
            }
            case YAML -> {
                MappingNode root = yamlRoot(text);
                for (NodeTuple tuple : root.getValue()) {
                    String group = scalarKey(tuple.getKeyNode(), "", warnings);
                    if (group == null) {
                        continue;
                    }
                    if (!(tuple.getValueNode() instanceof MappingNode mapping)) {
                        warnings.add(group + ": expected a mapping of KEY: value pairs, skipped");
                        continue;
                    }
                    groups.put(group, yamlFlat(mapping, group + "/", warnings));
                }
            }
        }
        return new ParsedVault(groups, warnings);
    }

    // ---------------------------------------------------------------- JSON

    private JsonNode jsonRoot(String text) {
        JsonNode root;
        try {
            root = json.readTree(text);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Invalid JSON: " + e.getOriginalMessage());
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Expected a JSON object at the top level");
        }
        return root;
    }

    private static Map<String, String> jsonFlat(JsonNode object, String prefix, List<String> warnings) {
        Map<String, String> entries = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> field : object.properties()) {
            String key = field.getKey();
            JsonNode value = field.getValue();
            if (value.isString()) {
                entries.put(key, value.asString());
            } else if (value.isNumber() || value.isBoolean()) {
                entries.put(key, value.asString());
                warnings.add(prefix + key + ": " + (value.isNumber() ? "number" : "boolean")
                        + " stored as the string \"" + value.asString() + "\"");
            } else if (value.isNull()) {
                warnings.add(prefix + key + ": null value, skipped");
            } else {
                warnings.add(prefix + key + ": nested objects and arrays are not supported, skipped");
            }
        }
        return entries;
    }

    // ---------------------------------------------------------------- YAML

    private static Yaml yamlReader() {
        LoaderOptions options = new LoaderOptions();
        options.setMaxAliasesForCollections(20);
        options.setAllowRecursiveKeys(false);
        options.setCodePointLimit(MAX_VAULT_BYTES);
        DumperOptions dumper = new DumperOptions();
        return new Yaml(new SafeConstructor(options), new Representer(dumper), dumper, options);
    }

    private static MappingNode yamlRoot(String text) {
        Node root;
        try {
            root = yamlReader().compose(new StringReader(text));
        } catch (YAMLException e) {
            throw new IllegalArgumentException("Invalid YAML: " + e.getMessage().lines().findFirst().orElse(""));
        }
        if (!(root instanceof MappingNode mapping)) {
            throw new IllegalArgumentException("Expected a YAML mapping (KEY: value) at the top level");
        }
        return mapping;
    }

    private static Map<String, String> yamlFlat(MappingNode mapping, String prefix, List<String> warnings) {
        Map<String, String> entries = new LinkedHashMap<>();
        for (NodeTuple tuple : mapping.getValue()) {
            String key = scalarKey(tuple.getKeyNode(), prefix, warnings);
            if (key == null) {
                continue;
            }
            Node value = tuple.getValueNode();
            if (!(value instanceof ScalarNode scalar)) {
                warnings.add(prefix + key + ": nested mappings and lists are not supported, skipped");
                continue;
            }
            if (Tag.NULL.equals(scalar.getTag())) {
                warnings.add(prefix + key + ": empty or null value, skipped");
                continue;
            }
            if (entries.containsKey(key)) {
                warnings.add(prefix + key + ": defined again, last value wins");
            }
            entries.put(key, scalar.getValue());
        }
        return entries;
    }

    private static String scalarKey(Node node, String prefix, List<String> warnings) {
        if (node instanceof ScalarNode scalar) {
            return scalar.getValue();
        }
        warnings.add(prefix + "a non-text key was skipped");
        return null;
    }

    /** Multi-line values become literal blocks (|) when YAML can represent them exactly. */
    private static Yaml yamlWriter() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setSplitLines(false);
        options.setWidth(Integer.MAX_VALUE);
        options.setIndent(2);
        Representer representer = new LiteralBlockRepresenter(options);
        return new Yaml(representer, options);
    }

    private static final class LiteralBlockRepresenter extends Representer {
        LiteralBlockRepresenter(DumperOptions options) {
            super(options);
            Represent plain = this.representers.get(String.class);
            this.representers.put(String.class, data -> {
                String s = data.toString();
                if (s.indexOf('\n') >= 0 && s.indexOf('\r') < 0) {
                    // The emitter falls back to a quoted scalar if a literal block can't hold it exactly.
                    return representScalar(Tag.STR, s, DumperOptions.ScalarStyle.LITERAL);
                }
                return plain.representData(data);
            });
        }
    }

    private static void checkSize(String text, int max) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("The file is empty");
        }
        if (text.length() > max) {
            throw new IllegalArgumentException("The file is larger than " + (max / 1024) + " KiB");
        }
    }
}
