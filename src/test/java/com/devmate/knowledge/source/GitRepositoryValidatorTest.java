package com.devmate.knowledge.source;

import com.devmate.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitRepositoryValidatorTest {

    private final GitRepositoryValidator validator = new GitRepositoryValidator();

    @Test
    void acceptsPublicHttpsRepository() {
        assertThatCode(() -> validator.validate("https://github.com/MMDXTMM/devmate-ai.git"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsafeRepositoryAddresses() {
        assertThatThrownBy(() -> validator.validate("http://github.com/example/demo.git"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validator.validate("https://user:secret@github.com/example/demo.git"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validator.validate("https://127.0.0.1/demo.git"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validator.validate("file:///tmp/demo"))
                .isInstanceOf(BusinessException.class);
    }
}
