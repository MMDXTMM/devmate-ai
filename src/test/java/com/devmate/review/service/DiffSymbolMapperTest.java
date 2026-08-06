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
        KnowledgeChunk value = new KnowledgeChunk();
        value.setId(id);
        value.setDocumentId(documentId);
        value.setChunkIndex(0);
        value.setChunkType("METHOD");
        value.setSymbolName(symbolName);
        value.setStartLine(8);
        value.setEndLine(12);
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
