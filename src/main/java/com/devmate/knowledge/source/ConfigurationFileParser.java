package com.devmate.knowledge.source;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

@Component
public class ConfigurationFileParser {

    private static final Set<String> SENSITIVE_SEGMENTS = Set.of(
            "password", "passwd", "pwd", "secret", "token", "credential",
            "credentials", "apikey", "api-key", "api_key", "access-key",
            "access_key", "private-key", "private_key"
    );

    public ParsedSourceFile parse(ScannedSourceFile sourceFile) {
        try {
            String content = Files.readString(sourceFile.sourcePath(), StandardCharsets.UTF_8);
            List<ParsedSourceChunk> chunks = switch (sourceFile.fileType()) {
                case YAML -> parseYaml(sourceFile.relativePath(), content);
                case PROPERTIES -> parseProperties(sourceFile.relativePath(), content);
                case JAVA -> throw new IllegalArgumentException("Java源码应由JavaSourceParser解析");
                case SQL -> throw new IllegalArgumentException("SQL迁移应由DatabaseSchemaParser解析");
            };
            return new ParsedSourceFile(sourceFile, "", chunks, List.of());
        } catch (IOException exception) {
            throw new SourceImportException("读取配置文件失败：" + sourceFile.relativePath(), exception);
        }
    }

    List<ParsedSourceChunk> parseYaml(String relativePath, String source) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setAllowRecursiveKeys(false);
        options.setMaxAliasesForCollections(20);
        options.setNestingDepthLimit(50);
        options.setCodePointLimit(2_000_000);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        List<ParsedSourceChunk> chunks = new ArrayList<>();
        try {
            for (Node document : yaml.composeAll(new StringReader(source))) {
                if (document != null) {
                    flattenYaml(document, "", chunks);
                }
            }
            return List.copyOf(chunks);
        } catch (RuntimeException exception) {
            throw new SourceImportException("YAML配置解析失败：" + relativePath, exception);
        }
    }

    List<ParsedSourceChunk> parseProperties(String relativePath, String source) {
        List<ParsedSourceChunk> chunks = new ArrayList<>();
        Set<String> parsedKeys = new HashSet<>();
        String[] lines = source.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            int startLine = index + 1;
            StringBuilder logicalLine = new StringBuilder(lines[index]);
            while (continues(logicalLine) && index + 1 < lines.length) {
                logicalLine.setLength(logicalLine.length() - 1);
                logicalLine.append(lines[++index].stripLeading());
            }
            String trimmed = logicalLine.toString().stripLeading();
            if (trimmed.isBlank() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                continue;
            }
            String key = decodePropertyKey(trimmed, relativePath);
            if (!key.isBlank()) {
                if (!parsedKeys.add(key)) {
                    throw new SourceImportException("Properties配置包含重复键：" + key);
                }
                chunks.add(configurationChunk(
                        chunks.size(), key, "STRING", isSensitive(key), startLine, index + 1
                ));
            }
        }
        return List.copyOf(chunks);
    }

    private void flattenYaml(Node node, String prefix, List<ParsedSourceChunk> chunks) {
        if (node instanceof MappingNode mapping) {
            Set<String> siblingKeys = new HashSet<>();
            for (NodeTuple tuple : mapping.getValue()) {
                if (!(tuple.getKeyNode() instanceof ScalarNode keyNode)) {
                    throw new SourceImportException("YAML配置键必须是字符串");
                }
                if (!siblingKeys.add(keyNode.getValue())) {
                    throw new SourceImportException("YAML配置包含重复键：" + join(prefix, keyNode.getValue()));
                }
                String key = join(prefix, keyNode.getValue());
                flattenYaml(tuple.getValueNode(), key, chunks);
            }
            return;
        }
        if (node instanceof SequenceNode sequence) {
            for (int index = 0; index < sequence.getValue().size(); index++) {
                flattenYaml(sequence.getValue().get(index), prefix + "[" + index + "]", chunks);
            }
            if (sequence.getValue().isEmpty() && !prefix.isBlank()) {
                chunks.add(configurationChunk(chunks.size(), prefix, "SEQUENCE", isSensitive(prefix),
                        line(node), endLine(node)));
            }
            return;
        }
        if (node instanceof ScalarNode scalar && !prefix.isBlank()) {
            chunks.add(configurationChunk(chunks.size(), prefix, valueType(scalar), isSensitive(prefix),
                    line(node), endLine(node)));
        }
    }

    private ParsedSourceChunk configurationChunk(
            int index,
            String key,
            String valueType,
            boolean sensitive,
            int startLine,
            int endLine
    ) {
        String summary = key + " = " + (sensitive ? "<redacted>" : "<" + valueType.toLowerCase(Locale.ROOT) + ">");
        return new ParsedSourceChunk(
                index,
                "CONFIG_PROPERTY",
                key,
                List.of(),
                null,
                Map.of("valueType", valueType, "sensitive", sensitive),
                summary,
                sha256(summary),
                startLine,
                endLine
        );
    }

    private String decodePropertyKey(String line, String relativePath) {
        try {
            Properties properties = new Properties();
            properties.load(new StringReader(line));
            if (properties.isEmpty()) {
                return "";
            }
            return properties.stringPropertyNames().iterator().next().trim();
        } catch (IllegalArgumentException | IOException exception) {
            throw new SourceImportException("Properties配置解析失败：" + relativePath, exception);
        }
    }

    private boolean continues(CharSequence line) {
        int slashes = 0;
        for (int index = line.length() - 1; index >= 0 && line.charAt(index) == '\\'; index--) {
            slashes++;
        }
        return slashes % 2 == 1;
    }

    private String join(String prefix, String key) {
        String normalized = key == null ? "" : key.trim();
        if (normalized.isBlank()) {
            throw new SourceImportException("YAML配置键不能为空");
        }
        return prefix.isBlank() ? normalized : prefix + "." + normalized;
    }

    private boolean isSensitive(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        Set<String> tokens = new HashSet<>(Arrays.asList(normalized.split("[._-]+")));
        return tokens.stream().anyMatch(SENSITIVE_SEGMENTS::contains)
                || SENSITIVE_SEGMENTS.stream().anyMatch(segment ->
                normalized.equals(segment)
                        || normalized.endsWith("." + segment)
                        || normalized.contains("." + segment + ".")
        );
    }

    private String valueType(ScalarNode scalar) {
        String tag = scalar.getTag().getValue();
        int separator = tag.lastIndexOf(':');
        return (separator < 0 ? tag : tag.substring(separator + 1)).toUpperCase(Locale.ROOT);
    }

    private int line(Node node) {
        return node.getStartMark() == null ? 1 : node.getStartMark().getLine() + 1;
    }

    private int endLine(Node node) {
        return node.getEndMark() == null
                ? line(node)
                : Math.max(line(node), node.getEndMark().getLine() + 1);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }
}
