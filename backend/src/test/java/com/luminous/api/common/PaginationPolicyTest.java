package com.luminous.api.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class PaginationPolicyTest {
    @ParameterizedTest @CsvSource({"0,10","-1,10","1,0","1,-1","1,101","9223372036854775807,100","1,9223372036854775807"})
    void invalidOrOverflowingParametersAreRejected(long page,long size) { assertThatThrownBy(()->PaginationPolicy.validate(page,size)).isInstanceOf(BusinessException.class); }
    @Test void postWindowAllowsLastPartialPageAndRejectsNextPage() {
        assertThat(PaginationPolicy.posts(7,80)).isEqualTo(480);
        assertThatThrownBy(()->PaginationPolicy.posts(8,80)).isInstanceOfSatisfying(BusinessException.class,ex->assertThat(ex.getCode()).isEqualTo("POST_WINDOW_LIMIT"));
    }
}
