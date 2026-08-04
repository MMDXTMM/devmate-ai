package com.devmate.knowledge.source;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.alter.AlterExpression;
import net.sf.jsqlparser.statement.alter.AlterOperation;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.table.Index;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DatabaseSchemaParser {

    private static final long PARSE_TIMEOUT_MILLIS = 1_000;
    private static final int MAX_SCHEMA_STATEMENTS_PER_FILE = 500;

    public ParsedSourceFile parse(ScannedSourceFile sourceFile) {
        if (sourceFile.fileType() != SourceFileType.SQL) {
            throw new IllegalArgumentException("数据库结构解析器只接受SQL迁移文件");
        }
        try {
            String source = Files.readString(sourceFile.sourcePath(), StandardCharsets.UTF_8);
            return new ParsedSourceFile(sourceFile, "", parseContent(sourceFile.relativePath(), source), List.of());
        } catch (IOException exception) {
            throw new SourceImportException("读取数据库迁移失败：" + sourceFile.relativePath(), exception);
        }
    }

    List<ParsedSourceChunk> parseContent(String relativePath, String source) {
        List<ParsedSourceChunk> chunks = new ArrayList<>();
        int schemaStatementCount = 0;
        for (SqlSegment segment : splitStatements(relativePath, source)) {
            if (!isSchemaStatement(segment.sql())) {
                continue;
            }
            if (++schemaStatementCount > MAX_SCHEMA_STATEMENTS_PER_FILE) {
                throw new SourceImportException("单个数据库迁移中的结构语句超过限制：" + relativePath);
            }
            Statement statement = parseStatement(relativePath, segment);
            if (statement instanceof CreateTable createTable) {
                addCreateTable(chunks, createTable, segment);
            } else if (statement instanceof CreateIndex createIndex) {
                addCreateIndex(chunks, createIndex, segment);
            } else if (statement instanceof Alter alter) {
                addAlterTable(chunks, alter, segment);
            }
        }
        return List.copyOf(chunks);
    }

    private Statement parseStatement(String relativePath, SqlSegment segment) {
        try {
            return CCJSqlParserUtil.parse(
                    segment.sql(),
                    parser -> parser.withTimeOut(PARSE_TIMEOUT_MILLIS)
                            .withBackslashEscapeCharacter(true)
            );
        } catch (JSQLParserException exception) {
            throw new SourceImportException(
                    "数据库迁移解析失败：" + relativePath + "，第" + segment.startLine() + "行",
                    exception
            );
        }
    }

    private void addCreateTable(
            List<ParsedSourceChunk> chunks,
            CreateTable statement,
            SqlSegment segment
    ) {
        String table = normalizeName(statement.getTable().getName());
        List<ColumnDefinition> columns = statement.getColumnDefinitions() == null
                ? List.of()
                : statement.getColumnDefinitions();
        List<String> columnNames = columns.stream()
                .map(column -> normalizeName(column.getColumnName()))
                .toList();
        addChunk(chunks, "DATABASE_TABLE", table,
                "table " + table + " (columns: " + String.join(", ", columnNames) + ")",
                segment, Map.of("table", table, "columns", columnNames));

        for (ColumnDefinition column : columns) {
            addColumn(chunks, table, column, segment, "CREATE");
        }
        if (statement.getIndexes() != null) {
            for (Index index : statement.getIndexes()) {
                addIndex(chunks, table, index, segment, "CREATE_TABLE");
            }
        }
    }

    private void addCreateIndex(
            List<ParsedSourceChunk> chunks,
            CreateIndex statement,
            SqlSegment segment
    ) {
        addIndex(chunks, normalizeName(statement.getTable().getName()), statement.getIndex(), segment,
                "CREATE_INDEX");
    }

    private void addAlterTable(
            List<ParsedSourceChunk> chunks,
            Alter statement,
            SqlSegment segment
    ) {
        String table = normalizeName(statement.getTable().getName());
        List<AlterExpression> expressions = statement.getAlterExpressions() == null
                ? List.of()
                : statement.getAlterExpressions();
        boolean extracted = false;
        for (AlterExpression expression : expressions) {
            if (expression.getOperation() == AlterOperation.ADD && expression.getColDataTypeList() != null) {
                for (AlterExpression.ColumnDataType column : expression.getColDataTypeList()) {
                    addColumn(chunks, table, column, segment, "ALTER_ADD");
                    extracted = true;
                }
            }
            if (expression.getIndex() != null) {
                addIndex(chunks, table, expression.getIndex(), segment, "ALTER_TABLE");
                extracted = true;
            }
        }
        if (!extracted) {
            addChunk(chunks, "DATABASE_CHANGE", table + "#change@" + segment.startLine(),
                    "schema change on table " + table,
                    segment, Map.of("table", table, "operation", "ALTER"));
        }
    }

    private void addColumn(
            List<ParsedSourceChunk> chunks,
            String table,
            ColumnDefinition column,
            SqlSegment segment,
            String source
    ) {
        String columnName = normalizeName(column.getColumnName());
        String dataType = column.getColDataType() == null
                ? "UNKNOWN"
                : column.getColDataType().toString().toUpperCase(Locale.ROOT);
        List<String> specs = column.getColumnSpecs() == null ? List.of() : column.getColumnSpecs();
        boolean nullable = !containsSequence(specs, "NOT", "NULL") && !contains(specs, "PRIMARY");
        String summary = table + "." + columnName + " " + dataType + " nullable=" + nullable;
        addChunk(chunks, "DATABASE_COLUMN", table + "." + columnName, summary, segment,
                Map.of(
                        "table", table,
                        "column", columnName,
                        "dataType", dataType,
                        "nullable", nullable,
                        "source", source
                ));
    }

    private void addIndex(
            List<ParsedSourceChunk> chunks,
            String table,
            Index index,
            SqlSegment segment,
            String source
    ) {
        if (index == null) {
            return;
        }
        String indexName = index.getName() == null || index.getName().isBlank()
                ? normalizeName(index.getType() == null ? "unnamed" : index.getType())
                : normalizeName(index.getName());
        List<String> columns = index.getColumnsNames() == null
                ? List.of()
                : index.getColumnsNames().stream().map(this::normalizeName).toList();
        String type = index.getType() == null ? "INDEX" : index.getType().toUpperCase(Locale.ROOT);
        String summary = indexName + " ON " + table + " (" + String.join(", ", columns) + ")";
        String chunkType = type.contains("PRIMARY") || type.contains("UNIQUE") || type.contains("FOREIGN")
                ? "DATABASE_CONSTRAINT"
                : "DATABASE_INDEX";
        addChunk(chunks, chunkType, table + "#" + indexName, summary, segment,
                Map.of("table", table, "index", indexName, "indexType", type,
                        "columns", columns, "source", source));
    }

    private void addChunk(
            List<ParsedSourceChunk> chunks,
            String type,
            String symbol,
            String content,
            SqlSegment segment,
            Map<String, Object> metadata
    ) {
        chunks.add(new ParsedSourceChunk(
                chunks.size(), type, symbol, List.of(), null, metadata, content,
                sha256(content), segment.startLine(), segment.endLine()
        ));
    }

    private List<SqlSegment> splitStatements(String relativePath, String source) {
        List<SqlSegment> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int line = 1;
        int startLine = 1;
        boolean singleQuote = false;
        boolean doubleQuote = false;
        boolean backtick = false;
        boolean lineComment = false;
        boolean blockComment = false;
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if (lineComment) {
                if (character == '\n') {
                    lineComment = false;
                    line++;
                    current.append('\n');
                    if (current.toString().isBlank()) {
                        startLine = line;
                    }
                }
                continue;
            }
            if (blockComment) {
                if (character == '*' && next == '/') {
                    blockComment = false;
                    index++;
                } else if (character == '\n') {
                    line++;
                    current.append('\n');
                    if (current.toString().isBlank()) {
                        startLine = line;
                    }
                }
                continue;
            }
            if (!singleQuote && !doubleQuote && !backtick) {
                if ((character == '-' && next == '-') || character == '#') {
                    lineComment = true;
                    if (character == '-') {
                        index++;
                    }
                    continue;
                }
                if (character == '/' && next == '*') {
                    blockComment = true;
                    index++;
                    continue;
                }
            }
            if (character == '\'' && !doubleQuote && !backtick && !escaped(source, index)) {
                singleQuote = !singleQuote;
            } else if (character == '"' && !singleQuote && !backtick && !escaped(source, index)) {
                doubleQuote = !doubleQuote;
            } else if (character == '`' && !singleQuote && !doubleQuote) {
                backtick = !backtick;
            }
            if (character == ';' && !singleQuote && !doubleQuote && !backtick) {
                appendSegment(segments, current, startLine, line);
                current.setLength(0);
                startLine = line;
                continue;
            }
            current.append(character);
            if (character == '\n') {
                line++;
                if (current.toString().isBlank()) {
                    startLine = line;
                }
            }
        }
        if (singleQuote || doubleQuote || backtick || blockComment) {
            throw new SourceImportException("数据库迁移包含未闭合内容：" + relativePath);
        }
        appendSegment(segments, current, startLine, line);
        return segments;
    }

    private void appendSegment(List<SqlSegment> segments, StringBuilder sql, int startLine, int endLine) {
        String statement = sql.toString().trim();
        if (!statement.isBlank()) {
            segments.add(new SqlSegment(statement, startLine, endLine));
        }
    }

    private boolean isSchemaStatement(String sql) {
        String normalized = sql.stripLeading().toUpperCase(Locale.ROOT);
        return normalized.startsWith("CREATE TABLE")
                || normalized.startsWith("CREATE INDEX")
                || normalized.startsWith("CREATE UNIQUE INDEX")
                || normalized.startsWith("ALTER TABLE");
    }

    private boolean contains(List<String> values, String expected) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(expected));
    }

    private boolean containsSequence(List<String> values, String first, String second) {
        for (int index = 0; index + 1 < values.size(); index++) {
            if (values.get(index).equalsIgnoreCase(first) && values.get(index + 1).equalsIgnoreCase(second)) {
                return true;
            }
        }
        return false;
    }

    private boolean escaped(String source, int index) {
        int slashes = 0;
        for (int cursor = index - 1; cursor >= 0 && source.charAt(cursor) == '\\'; cursor--) {
            slashes++;
        }
        return slashes % 2 == 1;
    }

    private String normalizeName(String value) {
        if (value == null) {
            return "unknown";
        }
        return value.replace("`", "").replace("\"", "").trim().toLowerCase(Locale.ROOT);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }

    private record SqlSegment(String sql, int startLine, int endLine) {
    }
}
