package com.luminous.api.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.common.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.*;
import java.time.*;
import java.util.*;
import java.util.stream.LongStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SearchServiceTest {
    private SearchRepository repository;
    private SearchService service;
    private Map<String, String> store;
    private Set<Long> hidden;
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        repository = mock(SearchRepository.class);
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        store = new HashMap<>(); hidden = new HashSet<>();
        when(values.get(anyString())).thenAnswer(call -> store.get(call.getArgument(0)));
        doAnswer(call -> { store.put(call.getArgument(0), call.getArgument(1)); return null; }).when(values).set(anyString(), anyString(), any(Duration.class));
        service = new SearchService(repository, redis, new ObjectMapper(), Clock.fixed(Instant.parse("2026-10-15T08:00:00Z"), ZoneOffset.UTC));
        when(repository.hydrate(anyString(), anyList())).thenAnswer(call -> {
            List<Long> ids = call.getArgument(1);
            return ids.reversed().stream().filter(id -> !hidden.contains(id)).map(id -> new SearchDtos.Hit(id.toString(), call.getArgument(0), "[E2E_TEST]", "", "", "", "/post/" + id)).toList();
        });
    }
    @Test void suggestionsCacheOnlyIdsButRehydrateOnEveryRead() {
        var terms = SearchTerms.parse("@java");
        when(repository.candidates(terms, "users", 5)).thenReturn(List.of(1L, 2L, 3L, 4L, 5L));
        assertThat(service.suggest(terms).groups().getFirst().items()).hasSize(4);
        hidden.add(1L);
        assertThat(service.suggest(terms).groups().getFirst().items()).extracting(SearchDtos.Hit::id).containsExactly("2", "3", "4", "5");
        verify(repository, times(1)).candidates(terms, "users", 5);
        verify(repository, times(2)).hydrate("users", List.of(1L, 2L, 3L, 4L, 5L));
    }
    @Test void snapshotPagingSkipsHiddenIdsAndKeepsOriginalOrder() {
        var terms = SearchTerms.parse("学习");
        when(repository.candidates(terms, "posts", 201)).thenReturn(LongStream.rangeClosed(1, 45).boxed().toList());
        var first = service.list(terms, "posts", null, 20);
        hidden.addAll(LongStream.rangeClosed(21, 29).boxed().toList());
        var second = service.list(terms, "posts", first.nextCursor(), 20);
        assertThat(first.items()).extracting(SearchDtos.Hit::id).containsExactlyElementsOf(LongStream.rangeClosed(1, 20).mapToObj(String::valueOf).toList());
        assertThat(second.items()).extracting(SearchDtos.Hit::id).containsExactlyElementsOf(LongStream.rangeClosed(30, 45).mapToObj(String::valueOf).toList());
        assertThat(second.hasMore()).isFalse();
    }
    @Test void expiredCursorCanRefreshAndCannotCrossQueryOrKind() {
        var terms = SearchTerms.parse("学习");
        when(repository.candidates(terms, "posts", 201)).thenReturn(List.of(1L, 2L, 3L));
        var first = service.list(terms, "posts", null, 1);
        assertThatThrownBy(() -> service.list(SearchTerms.parse("别的词"), "posts", first.nextCursor(), 1)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.list(terms, "users", first.nextCursor(), 1)).isInstanceOf(BusinessException.class);
        store.clear();
        assertThatThrownBy(() -> service.list(terms, "posts", first.nextCursor(), 1)).isInstanceOf(BusinessException.class).satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("SEARCH_EXPIRED"));
        assertThat(service.list(terms, "posts", null, 1).items()).hasSize(1);
        verify(repository, times(2)).candidates(terms, "posts", 201);
    }
    @Test void resultWindowIsExplicitlyBoundedAndBadSizeDoesNotQuery() {
        var terms = SearchTerms.parse("学习");
        when(repository.candidates(terms, "posts", 201)).thenReturn(LongStream.rangeClosed(1, 201).boxed().toList());
        assertThat(service.list(terms, "posts", null, 20).windowLimited()).isTrue();
        assertThatThrownBy(() -> service.list(terms, "posts", null, 21)).isInstanceOf(BusinessException.class);
    }
    @Test void partiallyUnavailableSuggestionsStayDistinctFromEmptyResults() {
        var terms = SearchTerms.parse("学习");
        when(repository.candidates(terms, "posts", 5)).thenThrow(new IllegalStateException("[E2E_TEST] 超时"));
        var result = service.suggest(terms);
        assertThat(result.groups().getFirst().unavailable()).isTrue();
        assertThat(result.groups().get(1).unavailable()).isFalse();
    }
}
