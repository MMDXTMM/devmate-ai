package com.devmate.knowledge.retrieval;

import org.junit.jupiter.api.Test;
import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.KnowledgeDocument;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LexicalGraphRankerTest {

    private final LexicalGraphRanker ranker = new LexicalGraphRanker();

    @Test
    void tokenizesCamelCaseSnakeCaseAndUnicodeTerms() {
        assertThat(ranker.tokenize("reserveStock order_service 库存扣减"))
                .contains("reserve", "stock", "order", "service", "库存扣减");
    }

    @Test
    void estimatesTokensWithoutClaimingModelTokenizerAccuracy() {
        assertThat(ranker.estimateTokens("12345678")).isEqualTo(2);
        assertThat(ranker.estimateTokens("")).isEqualTo(1);
    }
    @Test void prioritizesImplementationForExplicitSymbolsAndIgnoresImports() {
        var doc = new KnowledgeDocument(); doc.setId(1L); doc.setFilePath("src/VoucherOrderServiceImpl.java");
        var method = chunk(1L, "METHOD", "VoucherOrderServiceImpl#seckillVoucher(Long)", "public Result seckillVoucher(Long id) { return save(id); }");
        var header = chunk(2L, "IMPORT", "import SeckillVoucher", "import sample.SeckillVoucher;");
        var empty = chunk(3L, "CLASS", "SeckillVoucherMapper", "public interface SeckillVoucherMapper {}");
        assertThat(ranker.rank(List.of(empty, header, method), Map.of(1L, doc), "seckillVoucher", Set.of(), Map.of()))
                .extracting(item -> item.chunk().getId()).startsWith(1L).doesNotContain(2L);
    }

    @Test void findsQualifiedPythonMembersInsideTextChunks() {
        var doc = new KnowledgeDocument(); doc.setId(1L); doc.setFilePath("src/itsdangerous/signer.py");
        var intro = chunk(1L, "TEXT_EXCERPT", "signer.py lines 1-80", "class Signer:\n    def __init__(self): pass");
        var implementation = chunk(2L, "TEXT_EXCERPT", "signer.py lines 200-280", "    def sign(self, value):\n        return value + self.get_signature(value)\n    def unsign(self, value):\n        return self.verify_signature(value)");
        assertThat(ranker.rank(List.of(intro, implementation), Map.of(1L, doc), "请继续解释 Signer.sign 和 Signer.unsign 的实现", Set.of(), Map.of()))
                .first().satisfies(item -> assertThat(item.chunk().getId()).isEqualTo(2L));
    }

    @Test void matchesChineseBusinessPhrasesAndReturnsNoUnrelatedFiller() {
        var doc = new KnowledgeDocument(); doc.setId(1L); doc.setFilePath("src/OrderService.java");
        var method = chunk(1L, "METHOD", "OrderService#submit()", "// 检查库存是否充足，防止重复下单\npublic Result submit() { return reserve(); }");
        var unrelated = chunk(2L, "METHOD", "BlogService#list()", "public Result list() { return all(); }");
        assertThat(ranker.rank(List.of(unrelated, method), Map.of(1L, doc), "抢购时怎样检查库存和重复下单？", Set.of(), Map.of()))
                .extracting(item -> item.chunk().getId()).containsExactly(1L);
    }

    @Test void definitionsBeatCallsAndSimilarNamesInLexicalAndHybridResults() {
        var source = new KnowledgeDocument(); source.setId(1L); source.setFilePath("src/signer.py");
        var test = new KnowledgeDocument(); test.setId(2L); test.setFilePath("tests/test_signer.py");
        var definition = chunk(1L, "TEXT_EXCERPT", "src/signer.py:200", "    def sign(self, value):\n        return signature(value)");
        var usage = chunk(2L, "TEXT_EXCERPT", "tests/test_signer.py:1", "def test_sign(signer):\n    assert signer.sign('a') == signer.sign('a')"); usage.setDocumentId(2L);
        var similar = chunk(3L, "METHOD", "VoucherService#addSeckillVoucher(Long)", "public void addSeckillVoucher(Long id) { seckillVoucher(id); }");
        var exact = chunk(4L, "METHOD", "VoucherOrderService#seckillVoucher(Long)", "public void seckillVoucher(Long id) { save(id); }");
        var docs = Map.of(1L, source, 2L, test);
        var ranked = ranker.rank(List.of(usage, definition), docs, "Signer.sign", Set.of(), Map.of());
        assertThat(ranked).first().satisfies(item -> assertThat(item.chunk().getId()).isEqualTo(1L));
        var hybrid = new HybridRetrievalRanker().fuse(RetrievalMode.HYBRID, ranked,
                List.of(new com.devmate.knowledge.vector.VectorMatch(2L, 0.99), new com.devmate.knowledge.vector.VectorMatch(1L, 0.1)),
                Map.of(1L, definition, 2L, usage), docs);
        assertThat(hybrid).first().satisfies(item -> assertThat(item.chunk().getId()).isEqualTo(1L));
        assertThat(ranker.rank(List.of(similar, exact), docs, "seckillVoucher", Set.of(), Map.of()))
                .first().satisfies(item -> assertThat(item.chunk().getId()).isEqualTo(4L));
    }

    private KnowledgeChunk chunk(Long id, String type, String symbol, String content) {
        var chunk = new KnowledgeChunk(); chunk.setId(id); chunk.setDocumentId(1L); chunk.setChunkType(type);
        chunk.setSymbolName(symbol); chunk.setContent(content); chunk.setStartLine(id.intValue()); return chunk;
    }

}
