package com.devmate.review.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.KnowledgeDocument;
import com.devmate.knowledge.mapper.KnowledgeChunkMapper;
import com.devmate.knowledge.mapper.KnowledgeDocumentMapper;
import com.devmate.knowledge.source.JavaSourceParser;
import com.devmate.review.model.GitChangedFile;
import com.devmate.review.model.LineRange;
import com.devmate.review.source.GitRevisionSourceReader;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DiffSymbolMapperTest {

    private static final String TARGET_PATH = "src/main/java/com/example/foo.java";
    private static final String TARGET_REVISION = "a".repeat(40);

    @Mock
    private KnowledgeDocumentMapper documentMapper;
    @Mock
    private KnowledgeChunkMapper chunkMapper;
    @Mock
    private GitRevisionSourceReader revisionSourceReader;
    @Mock
    private JavaSourceParser sourceParser;

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                KnowledgeDocument.class
        );
    }

    @Test
    void resolvesTargetDocumentByCaseSensitivePathHash() {
        KnowledgeDocument document = document(11L, TARGET_PATH);
        KnowledgeChunk chunk = chunk(21L, document.getId(), "com.example.foo#run()");
        given(documentMapper.selectOne(any())).willAnswer(invocation -> {
            LambdaQueryWrapper<KnowledgeDocument> query = invocation.getArgument(0);
            assertThat(query.getSqlSegment())
                    .contains("path_hash")
                    .doesNotContain("file_path");
            assertThat(query.getParamNameValuePairs().values())
                    .contains(sha256(TARGET_PATH))
                    .doesNotContain(TARGET_PATH);
            return document;
        });
        given(chunkMapper.selectList(any())).willReturn(List.of(chunk));

        MappedReviewFile result = mapper().map(
                1L,
                Path.of("/tmp/unused-review-repository"),
                "b".repeat(40),
                TARGET_REVISION,
                changedFile()
        );

        assertThat(result.coverageStatus()).isEqualTo("FULL");
        assertThat(result.mappedSymbols()).singleElement().satisfies(symbol -> {
            assertThat(symbol.chunkId()).isEqualTo(chunk.getId());
            assertThat(symbol.revisionSide()).isEqualTo("TARGET");
            assertThat(symbol.symbolName()).isEqualTo("com.example.foo#run()");
        });
    }

    @Test
    void rejectsDocumentWhenHashLookupReturnsDifferentPath() {
        given(documentMapper.selectOne(any()))
                .willReturn(document(12L, "src/main/java/com/example/Foo.java"));

        MappedReviewFile result = mapper().map(
                1L,
                Path.of("/tmp/unused-review-repository"),
                "b".repeat(40),
                TARGET_REVISION,
                changedFile()
        );

        assertThat(result.coverageStatus()).isEqualTo("PARTIAL");
        assertThat(result.mappedSymbols()).isEmpty();
        verifyNoInteractions(chunkMapper);
    }

    @Test
    void mapsPersistedTargetImportAsFullCoverage() {
        KnowledgeDocument document = document(13L, TARGET_PATH);
        KnowledgeChunk importChunk = chunk(
                22L,
                document.getId(),
                "IMPORT",
                "import java.util.concurrent.ConcurrentHashMap",
                3,
                3
        );
        given(documentMapper.selectOne(any())).willReturn(document);
        given(chunkMapper.selectList(any())).willReturn(List.of(importChunk));

        MappedReviewFile result = mapper().map(
                1L,
                Path.of("/tmp/unused-review-repository"),
                "b".repeat(40),
                TARGET_REVISION,
                new GitChangedFile(
                        TARGET_PATH,
                        TARGET_PATH,
                        "MODIFY",
                        1,
                        0,
                        List.of(),
                        List.of(new LineRange(3, 3))
                )
        );

        assertThat(result.coverageStatus()).isEqualTo("FULL");
        assertThat(result.mappedSymbols()).singleElement().satisfies(symbol -> {
            assertThat(symbol.chunkId()).isEqualTo(importChunk.getId());
            assertThat(symbol.revisionSide()).isEqualTo("TARGET");
            assertThat(symbol.chunkType()).isEqualTo("IMPORT");
            assertThat(symbol.startLine()).isEqualTo(3);
            assertThat(symbol.endLine()).isEqualTo(3);
        });
    }

    @Test
    void mapsTransientBaseImportAsFullCoverage() {
        String baseSource = """
                package com.example;

                import java.util.concurrent.ConcurrentHashMap;

                class Foo {}
                """;
        given(revisionSourceReader.read(any(), any(), any()))
                .willReturn(Optional.of(baseSource));
        given(sourceParser.parseContent(TARGET_PATH, baseSource))
                .willReturn(new JavaSourceParser().parseContent(TARGET_PATH, baseSource));

        MappedReviewFile result = mapper().map(
                1L,
                Path.of("/tmp/unused-review-repository"),
                "b".repeat(40),
                TARGET_REVISION,
                new GitChangedFile(
                        TARGET_PATH,
                        TARGET_PATH,
                        "MODIFY",
                        0,
                        1,
                        List.of(new LineRange(3, 3)),
                        List.of()
                )
        );

        assertThat(result.coverageStatus()).isEqualTo("FULL");
        assertThat(result.mappedSymbols()).singleElement().satisfies(symbol -> {
            assertThat(symbol.chunkId()).isNull();
            assertThat(symbol.revisionSide()).isEqualTo("BASE");
            assertThat(symbol.chunkType()).isEqualTo("IMPORT");
            assertThat(symbol.startLine()).isEqualTo(3);
            assertThat(symbol.endLine()).isEqualTo(3);
        });
    }

    private DiffSymbolMapper mapper() {
        return new DiffSymbolMapper(documentMapper, chunkMapper, revisionSourceReader, sourceParser);
    }

    private GitChangedFile changedFile() {
        return new GitChangedFile(
                TARGET_PATH,
                TARGET_PATH,
                "MODIFY",
                1,
                0,
                List.of(),
                List.of(new LineRange(10, 10))
        );
    }

    private KnowledgeDocument document(Long id, String filePath) {
        KnowledgeDocument value = new KnowledgeDocument();
        value.setId(id);
        value.setFilePath(filePath);
        return value;
    }

    private KnowledgeChunk chunk(Long id, Long documentId, String symbolName) {
        return chunk(id, documentId, "METHOD", symbolName, 8, 12);
    }

    private KnowledgeChunk chunk(
            Long id,
            Long documentId,
            String chunkType,
            String symbolName,
            int startLine,
            int endLine
    ) {
        KnowledgeChunk value = new KnowledgeChunk();
        value.setId(id);
        value.setDocumentId(documentId);
        value.setChunkIndex(0);
        value.setChunkType(chunkType);
        value.setSymbolName(symbolName);
        value.setStartLine(startLine);
        value.setEndLine(endLine);
        return value;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }
}
