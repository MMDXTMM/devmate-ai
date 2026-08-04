package com.devmate.knowledge.source;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import org.springframework.stereotype.Component;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;

@Component
public class JavaSourceParser {

    public ParsedSourceFile parse(ScannedSourceFile sourceFile) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new SourceImportException("Java源码解析需要使用JDK运行应用");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, null, StandardCharsets.UTF_8
        )) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjects(sourceFile.sourcePath());
            JavacTask task = (JavacTask) compiler.getTask(
                    null, fileManager, diagnostics, List.of("-proc:none"), null, units
            );
            CompilationUnitTree unit = firstUnit(task.parse(), sourceFile.relativePath());
            rejectSyntaxErrors(diagnostics, sourceFile.relativePath());

            String source = Files.readString(sourceFile.sourcePath(), StandardCharsets.UTF_8);
            String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            Trees trees = Trees.instance(task);
            ChunkScanner scanner = new ChunkScanner(
                    unit,
                    trees.getSourcePositions(),
                    source,
                    packageName
            );
            scanner.scan(unit, null);
            return new ParsedSourceFile(sourceFile, packageName, scanner.chunks());
        } catch (IOException exception) {
            throw new SourceImportException("读取Java源码失败：" + sourceFile.relativePath(), exception);
        } catch (SourceImportException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SourceImportException("解析Java源码失败：" + sourceFile.relativePath(), exception);
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

        private final CompilationUnitTree unit;
        private final SourcePositions positions;
        private final String source;
        private final String packageName;
        private final Deque<String> typeNames = new ArrayDeque<>();
        private final List<ParsedSourceChunk> chunks = new ArrayList<>();

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

        @Override
        public Void visitClass(ClassTree tree, Void unused) {
            String simpleName = tree.getSimpleName().toString();
            if (simpleName.isBlank()) {
                return super.visitClass(tree, unused);
            }
            String qualifiedName = qualifyType(simpleName);
            addChunk("CLASS", qualifiedName, tree.getModifiers().getAnnotations(), tree);
            typeNames.addLast(simpleName);
            try {
                return super.visitClass(tree, unused);
            } finally {
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
            return super.visitMethod(tree, unused);
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
                    content,
                    sha256(content),
                    startLine,
                    endLine
            ));
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
    }
}
