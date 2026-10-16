package com.luminous.api.post;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PostCreationValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    @Test void titleAndAtLeastOneBodyElementAreRequired() {
        assertThat(validator.validate(new PostDtos.CreatePostRequest("tech", "[E2E_TEST]", " ", null, null, true))).isNotEmpty();
        assertThat(validator.validate(new PostDtos.CreatePostRequest("tech", "[E2E_TEST]", "", "/api/files/test.png", null, true))).isEmpty();
        assertThat(validator.validate(new PostDtos.CreatePostRequest("tech", " ", "内容", null, null, true))).isNotEmpty();
    }
    @Test void exactCharacterBoundariesAndNormalization() {
        var request = new PostDtos.CreatePostRequest("tech", " 标" + "题".repeat(14) + " ", "内".repeat(1000), null, null, true);
        assertThat(validator.validate(request)).isEmpty();
        assertThat(validator.validate(new PostDtos.CreatePostRequest("tech", "题".repeat(16), "内容", null, null, true))).isNotEmpty();
        assertThat(validator.validate(new PostDtos.CreatePostRequest("tech", "标题", "内".repeat(1001), null, null, true))).isNotEmpty();
    }
}
