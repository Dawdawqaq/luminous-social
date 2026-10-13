package com.luminous.api.post;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostRequestTest {
    @Test
    void acceptsShortPostBoundaryAndRejectsOversizeRequests() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(request("题".repeat(15), "文".repeat(1000)))).isEmpty();
            assertThat(validator.validate(request("题".repeat(16), "正文")))
                    .anySatisfy(violation -> {
                        assertThat(violation.getPropertyPath().toString()).isEqualTo("title");
                        assertThat(violation.getMessage()).isEqualTo("标题最多 15 个字符");
                    });
            assertThat(validator.validate(request("标题", "文".repeat(1001))))
                    .anySatisfy(violation -> {
                        assertThat(violation.getPropertyPath().toString()).isEqualTo("content");
                        assertThat(violation.getMessage()).isEqualTo("正文最多 1000 个字符");
                    });
            assertThat(validator.validate(request("   ", "正文"))).isNotEmpty();
        }
    }

    private PostDtos.CreatePostRequest request(String title, String content) {
        return new PostDtos.CreatePostRequest("test", title, content, null, "DISCUSSION", true);
    }
}
