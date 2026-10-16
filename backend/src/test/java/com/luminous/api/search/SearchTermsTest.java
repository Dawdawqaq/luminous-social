package com.luminous.api.search;

import com.luminous.api.common.BusinessException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SearchTermsTest {
    @Test void normalizesWidthWhitespaceCaseAndDeduplicatesWords() {
        var terms = SearchTerms.parse("　Ｊａｖａ  学习 java　");
        assertThat(terms.text()).isEqualTo("java 学习 java");
        assertThat(terms.booleanQuery()).isEqualTo("+\"java\" +\"学习\"");
    }
    @Test void operatorsCannotBecomeBooleanInstructionsAndShortSymbolsRemainLiteral() {
        var terms = SearchTerms.parse("+Java -Redis*");
        assertThat(terms.booleanQuery()).isEqualTo("+\"java\" +\"redis\"");
        assertThat(SearchTerms.parse("C++").literals()).containsExactly("c++");
        assertThat(SearchTerms.parse("春").hint()).isNotBlank();
        assertThat(SearchTerms.parse("demo_1%").prefix()).isEqualTo("demo!_1!%%");
    }
    @Test void namesAndNumericUsernamesAreSeparateFromExplicitIds() {
        assertThat(SearchTerms.parse("123").userId()).isNull();
        assertThat(SearchTerms.parse("@123").forcedKind()).isEqualTo("users");
        assertThat(SearchTerms.parse("id:123").userId()).isEqualTo(123);
        assertThat(SearchTerms.parse("c/my_topic").forcedKind()).isEqualTo("communities");
    }
    @Test void rejectsEmptyOversizedPunctuationAndTooManyTokens() {
        for (String text : new String[]{"", "@", "!!!", "词".repeat(51), "one two three four five six seven", "id:-1"}) {
            assertThatThrownBy(() -> SearchTerms.parse(text)).isInstanceOf(BusinessException.class);
        }
    }
}
