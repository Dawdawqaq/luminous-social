package com.luminous.api.post;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CommentRequestTest {
    @Test
    void acceptsTextOrOneImageButRejectsEmptyAndOversizeFields() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new PostDtos.CreateCommentRequest(null, "/api/files/image.png"))).isEmpty();
            assertThat(validator.validate(new PostDtos.CreateCommentRequest("文字"))).isEmpty();
            assertThat(validator.validate(new PostDtos.CreateCommentRequest("文字", "/api/files/image.png"))).isEmpty();
            assertThat(validator.validate(new PostDtos.CreateCommentRequest("  ", "  "))).isNotEmpty();
            assertThat(validator.validate(new PostDtos.CreateCommentRequest("字".repeat(300)))).isEmpty();
            assertThat(validator.validate(new PostDtos.CreateCommentRequest("字".repeat(301))))
                    .anySatisfy(violation -> assertThat(violation.getMessage()).isEqualTo("评论最多 300 个字符"));
            assertThat(validator.validate(new PostDtos.CreateCommentRequest("字".repeat(150), null, 1L))).isEmpty();
            assertThat(validator.validate(new PostDtos.CreateCommentRequest("字".repeat(151), null, 1L)))
                    .anySatisfy(violation -> assertThat(violation.getMessage()).isEqualTo("楼中楼回复最多 150 个字符"));
            assertThat(validator.validate(new PostDtos.CreateCommentRequest("文字", "x".repeat(513)))).isNotEmpty();
            assertThat(validator.validate(new PostDtos.CreateCommentRequest("回复", null, 1L))).isEmpty();
            assertThat(validator.validate(new PostDtos.CreateCommentRequest("回复", "/api/files/image.png", 1L))).isNotEmpty();
            assertThat(validator.validate(new PostDtos.CreateCommentRequest("回复", null, 0L))).isNotEmpty();
        }
    }
}
