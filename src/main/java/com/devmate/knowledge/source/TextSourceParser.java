package com.devmate.knowledge.source;

import org.springframework.stereotype.Component;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Bounded, line-preserving text excerpts, not a language AST or a verified call graph. */
@Component
public class TextSourceParser {
    public ParsedSourceFile parse(ScannedSourceFile file) {
        try {
            String[] lines = Files.readString(file.sourcePath(), StandardCharsets.UTF_8).split("\\R", -1);
            List<ParsedSourceChunk> chunks = new ArrayList<>();
            for (int start = 0; start < lines.length;) {
                int end = start;
                StringBuilder text = new StringBuilder();
                while (end < lines.length && end - start < 60
                        && (text.isEmpty() || text.length() + lines[end].length() < 6000)) {
                    // Redact common literal credentials while retaining original line numbers.
                    String line = lines[end].replaceAll("(?i)((?:password|passwd|api[_-]?key|access[_-]?token|secret)\\s*[\"']?\\s*[:=]\\s*)[^,;]+", "$1[REDACTED]")
                            .replaceAll("(?i)sk-[a-z0-9_-]{16,}", "[REDACTED]");
                    if (line.length() > 6000) line = line.substring(0, 6000) + " [行已截断]";
                    text.append(line).append('\n'); end++;
                }
                String content = text.toString();
                if (!content.isBlank()) chunks.add(new ParsedSourceChunk(chunks.size(), "TEXT_EXCERPT",
                        file.relativePath() + ":" + (start + 1), List.of(), null,
                        Map.of("parserVersion", "text-excerpts-v1", "ast", false), content,
                        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8))),
                        start + 1, end));
                start = end;
            }
            return new ParsedSourceFile(file, "", chunks, List.of());
        } catch (Exception exception) {
            throw new SourceImportException("读取项目文本失败：" + file.relativePath(), exception);
        }
    }
}
