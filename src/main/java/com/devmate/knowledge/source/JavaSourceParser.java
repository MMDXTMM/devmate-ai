package com.devmate.knowledge.source;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import org.springframework.stereotype.Component;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JavaSourceParser {

    private static final int MAX_REFERENCE_QUALIFIER_CHARACTERS = 500;

    public ParsedSourceFile parse(ScannedSourceFile sourceFile) {
        try {
            String source = Files.readString(sourceFile.sourcePath(), StandardCharsets.UTF_8);
            ParsedSourceContent parsed = parseContent(sourceFile.relativePath(), source);
            return new ParsedSourceFile(
                    sourceFile,
                    parsed.packageName(),
                    parsed.chunks(),
                    parsed.references()
            );
        } catch (IOException exception) {
            throw new SourceImportException("读取Java源码失败：" + sourceFile.relativePath(), exception);
        }
    }

    public ParsedSourceContent parseContent(String relativePath, String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new SourceImportException("Java源码解析需要使用JDK运行应用");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, null, StandardCharsets.UTF_8
        )) {
            JavaFileObject sourceObject = new StringSourceFileObject(source);
            JavacTask task = (JavacTask) compiler.getTask(
                    null, fileManager, diagnostics, List.of("-proc:none"), null, List.of(sourceObject)
            );
            CompilationUnitTree unit = firstUnit(task.parse(), relativePath);
            rejectSyntaxErrors(diagnostics, relativePath);

            String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            Trees trees = Trees.instance(task);
            ChunkScanner scanner = new ChunkScanner(
                    unit,
                    trees.getSourcePositions(),
                    source,
                    packageName
            );
            scanner.addFileHeaderChunks();
            scanner.scan(unit, null);
            return new ParsedSourceContent(packageName, scanner.chunks(), scanner.references());
        } catch (IOException exception) {
            throw new SourceImportException("解析Java源码失败：" + relativePath, exception);
        } catch (SourceImportException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SourceImportException("解析Java源码失败：" + relativePath, exception);
        }
    }

    private static final class StringSourceFileObject extends SimpleJavaFileObject {

        private final String source;

        private StringSourceFileObject(String source) {
            super(URI.create("string:///DevMateSource.java"), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    private CompilationUnitTree firstUnit(
            Iterable<? extends CompilationUnitTree> units,
            String relativePath
    ) {
        var iterator = units.iterator();
        if (!iterator.hasNext()) {
            throw new SourceImportException("Java源码为空或无法解析：" + relativePath);
        }
        return iterator.next();
    }

    private void rejectSyntaxErrors(
            DiagnosticCollector<JavaFileObject> diagnostics,
            String relativePath
    ) {
        diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .findFirst()
                .ifPresent(diagnostic -> {
                    long line = diagnostic.getLineNumber();
                    throw new SourceImportException(
                            "Java语法解析失败：" + relativePath + "，第" + line + "行"
                    );
                });
    }

    private static final class ChunkScanner extends TreePathScanner<Void, Void> {

        private static final Pattern VALUE_KEY = Pattern.compile("\\$\\{([^}:]+)");
        private static final Pattern CONFIGURATION_PREFIX = Pattern.compile(
                "(?:prefix\\s*=\\s*)?[\\\"']([^\\\"']+)[\\\"']"
        );
        private static final Pattern MYBATIS_TABLE = Pattern.compile(
                "(?:value\\s*=\\s*)?[\\\"']([^\\\"']+)[\\\"']"
        );
        private static final Pattern JPA_TABLE = Pattern.compile(
                "name\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']"
        );

        private final CompilationUnitTree unit;
        private final SourcePositions positions;
        private final String source;
        private final String packageName;
        private final Deque<String> typeNames = new ArrayDeque<>();
        private final Deque<String> sourceSymbols = new ArrayDeque<>();
        private final List<ParsedSourceChunk> chunks = new ArrayList<>();
        private final List<ParsedCodeReference> references = new ArrayList<>();
        private int loopDepth;
        private int synchronizedDepth;

        private ChunkScanner(
                CompilationUnitTree unit,
                SourcePositions positions,
                String source,
                String packageName
        ) {
            this.unit = unit;
            this.positions = positions;
            this.source = source;
            this.packageName = packageName;
        }

        private void addFileHeaderChunks() {
            if (unit.getPackage() != null) {
                addChunk("FILE_HEADER", "package " + packageName, List.of(), unit.getPackage());
            }
            for (ImportTree importTree : unit.getImports()) {
                String prefix = importTree.isStatic() ? "import static " : "import ";
                addChunk(
                        "IMPORT",
                        prefix + importTree.getQualifiedIdentifier(),
                        List.of(),
                        importTree
                );
            }
        }

        @Override
        public Void visitClass(ClassTree tree, Void unused) {
            String simpleName = tree.getSimpleName().toString();
            if (simpleName.isBlank()) {
                return super.visitClass(tree, unused);
            }
            String qualifiedName = qualifyType(simpleName);
            addChunk("CLASS", qualifiedName, tree.getModifiers().getAnnotations(), tree);
            typeNames.addLast(simpleName);
            sourceSymbols.addLast(qualifiedName);
            try {
                return super.visitClass(tree, unused);
            } finally {
                sourceSymbols.removeLast();
                typeNames.removeLast();
            }
        }

        @Override
        public Void visitMethod(MethodTree tree, Void unused) {
            if (typeNames.isEmpty()) {
                return super.visitMethod(tree, unused);
            }
            boolean constructor = tree.getReturnType() == null;
            String methodName = constructor ? typeNames.getLast() : tree.getName().toString();
            String parameters = tree.getParameters().stream()
                    .map(parameter -> parameter.getType().toString())
                    .reduce((left, right) -> left + "," + right)
                    .orElse("");
            String symbolName = currentQualifiedType() + "#" + methodName + "(" + parameters + ")";
            addChunk(
                    constructor ? "CONSTRUCTOR" : "METHOD",
                    symbolName,
                    tree.getModifiers().getAnnotations(),
                    tree
            );
            sourceSymbols.addLast(symbolName);
            boolean synchronizedMethod = tree.getModifiers().getFlags().contains(Modifier.SYNCHRONIZED);
            if (synchronizedMethod) {
                synchronizedDepth++;
            }
            try {
                return super.visitMethod(tree, unused);
            } finally {
                if (synchronizedMethod) {
                    synchronizedDepth--;
                }
                sourceSymbols.removeLast();
            }
        }

        @Override
        public Void visitForLoop(ForLoopTree tree, Void unused) {
            return visitLoop(() -> super.visitForLoop(tree, unused));
        }

        @Override
        public Void visitEnhancedForLoop(EnhancedForLoopTree tree, Void unused) {
            return visitLoop(() -> super.visitEnhancedForLoop(tree, unused));
        }

        @Override
        public Void visitWhileLoop(WhileLoopTree tree, Void unused) {
            return visitLoop(() -> super.visitWhileLoop(tree, unused));
        }

        @Override
        public Void visitDoWhileLoop(DoWhileLoopTree tree, Void unused) {
            return visitLoop(() -> super.visitDoWhileLoop(tree, unused));
        }

        @Override
        public Void visitSynchronized(SynchronizedTree tree, Void unused) {
            synchronizedDepth++;
            try {
                return super.visitSynchronized(tree, unused);
            } finally {
                synchronizedDepth--;
            }
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree tree, Void unused) {
            if (!sourceSymbols.isEmpty()) {
                String methodName;
                String qualifier = null;
                if (tree.getMethodSelect() instanceof IdentifierTree identifier) {
                    methodName = identifier.getName().toString();
                } else if (tree.getMethodSelect() instanceof MemberSelectTree memberSelect) {
                    methodName = memberSelect.getIdentifier().toString();
                    qualifier = memberSelect.getExpression().toString();
                } else {
                    methodName = tree.getMethodSelect().toString();
                }
                addReference(
                        "METHOD_CALL",
                        methodName,
                        qualifier,
                        tree.getArguments().size(),
                        tree,
                        null
                );
                if (isDataAccessQualifier(qualifier)) {
                    addReference(
                            "DATA_ACCESS",
                            methodName,
                            qualifier,
                            tree.getArguments().size(),
                            tree,
                            "{\"classification\":\"NAMING_CONVENTION\","
                                    + "\"loopDepth\":" + loopDepth + ","
                                    + "\"synchronizedDepth\":" + synchronizedDepth + "}"
                    );
                }
            }
            return super.visitMethodInvocation(tree, unused);
        }

        private Void visitLoop(java.util.function.Supplier<Void> scanner) {
            loopDepth++;
            try {
                return scanner.get();
            } finally {
                loopDepth--;
            }
        }

        @Override
        public Void visitAnnotation(AnnotationTree tree, Void unused) {
            if (!sourceSymbols.isEmpty()) {
                String annotationName = tree.getAnnotationType().toString();
                if (simpleName(annotationName).equals("Value")) {
                    Matcher matcher = VALUE_KEY.matcher(tree.toString());
                    while (matcher.find()) {
                        addReference("CONFIG_KEY", matcher.group(1), null, null, tree,
                                "{\"source\":\"Value\"}");
                    }
                } else if (simpleName(annotationName).equals("ConfigurationProperties")) {
                    Matcher matcher = CONFIGURATION_PREFIX.matcher(tree.toString());
                    if (matcher.find()) {
                        addReference("CONFIG_PREFIX", matcher.group(1), null, null, tree,
                                "{\"source\":\"ConfigurationProperties\"}");
                    }
                } else if (simpleName(annotationName).equals("TableName")) {
                    addDatabaseTableReference(tree, MYBATIS_TABLE, "MyBatisPlusTableName");
                } else if (simpleName(annotationName).equals("Table")) {
                    addDatabaseTableReference(tree, JPA_TABLE, "JpaTable");
                }
            }
            return super.visitAnnotation(tree, unused);
        }

        private void addDatabaseTableReference(AnnotationTree tree, Pattern pattern, String source) {
            Matcher matcher = pattern.matcher(tree.toString());
            if (matcher.find()) {
                addReference(
                        "DATABASE_TABLE",
                        matcher.group(1).replace("`", "").toLowerCase(Locale.ROOT),
                        null,
                        null,
                        tree,
                        "{\"source\":\"" + source + "\"}"
                );
            }
        }

        private void addReference(
                String kind,
                String name,
                String qualifier,
                Integer argumentCount,
                com.sun.source.tree.Tree tree,
                String metadataJson
        ) {
            long start = positions.getStartPosition(unit, tree);
            long end = positions.getEndPosition(unit, tree);
            if (start < 0 || end <= start) {
                return;
            }
            references.add(new ParsedCodeReference(
                    sourceSymbols.getLast(),
                    kind,
                    name,
                    limitQualifier(qualifier),
                    argumentCount,
                    (int) unit.getLineMap().getLineNumber(start),
                    (int) unit.getLineMap().getLineNumber(Math.max(start, end - 1)),
                    metadataJson
            ));
        }

        private String limitQualifier(String qualifier) {
            if (qualifier == null || qualifier.length() <= MAX_REFERENCE_QUALIFIER_CHARACTERS) {
                return qualifier;
            }
            return qualifier.substring(0, MAX_REFERENCE_QUALIFIER_CHARACTERS - 3) + "...";
        }

        private boolean isDataAccessQualifier(String qualifier) {
            if (qualifier == null || qualifier.isBlank()) {
                return false;
            }
            String candidate = qualifier.substring(qualifier.lastIndexOf('.') + 1)
                    .toLowerCase(Locale.ROOT);
            return candidate.endsWith("mapper")
                    || candidate.endsWith("repository")
                    || candidate.endsWith("dao")
                    || candidate.equals("jdbctemplate")
                    || candidate.equals("entitymanager");
        }

        private String simpleName(String qualifiedName) {
            int separator = qualifiedName.lastIndexOf('.');
            return separator < 0 ? qualifiedName : qualifiedName.substring(separator + 1);
        }

        private void addChunk(
                String chunkType,
                String symbolName,
                List<? extends AnnotationTree> annotations,
                com.sun.source.tree.Tree tree
        ) {
            long start = positions.getStartPosition(unit, tree);
            long end = positions.getEndPosition(unit, tree);
            if (start < 0 || end <= start || end > source.length()) {
                return;
            }
            String content = source.substring((int) start, (int) end);
            int startLine = (int) unit.getLineMap().getLineNumber(start);
            int endLine = (int) unit.getLineMap().getLineNumber(Math.max(start, end - 1));
            chunks.add(new ParsedSourceChunk(
                    chunks.size(),
                    chunkType,
                    symbolName,
                    annotations.stream().map(annotation -> annotation.getAnnotationType().toString()).toList(),
                    tree instanceof MethodTree methodTree ? methodTree.getParameters().size() : null,
                    metadata(annotations, tree),
                    content,
                    sha256(content),
                    startLine,
                    endLine
            ));
        }

        private Map<String, Object> metadata(
                List<? extends AnnotationTree> annotations,
                com.sun.source.tree.Tree tree
        ) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("annotations", annotations.stream()
                    .map(annotation -> annotation.getAnnotationType().toString())
                    .toList());
            if (tree instanceof MethodTree methodTree) {
                metadata.put("parameterCount", methodTree.getParameters().size());
            }
            return metadata;
        }

        private String qualifyType(String simpleName) {
            String prefix = currentQualifiedType();
            if (!prefix.isBlank()) {
                return prefix + "." + simpleName;
            }
            return packageName.isBlank() ? simpleName : packageName + "." + simpleName;
        }

        private String currentQualifiedType() {
            String nested = String.join(".", typeNames);
            if (nested.isBlank()) {
                return packageName;
            }
            return packageName.isBlank() ? nested : packageName + "." + nested;
        }

        private String sha256(String value) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(digest);
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("当前JDK不支持SHA-256", exception);
            }
        }

        private List<ParsedSourceChunk> chunks() {
            return List.copyOf(chunks);
        }

        private List<ParsedCodeReference> references() {
            return List.copyOf(references);
        }
    }
}
