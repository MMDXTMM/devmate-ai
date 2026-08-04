package com.devmate.knowledge.retrieval;

import org.junit.jupiter.api.Test;

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
}
