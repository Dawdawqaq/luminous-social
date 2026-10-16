package com.luminous.api.ranking;

import com.luminous.api.common.BusinessException;
import com.luminous.api.community.CommunityService;
import com.luminous.api.post.*;
import com.luminous.api.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PostRankingServiceTest {
    private PostRankingCandidates candidates;
    private PostRankingCache cache;
    private PostRankingRepository repository;
    private PostRankingService service;
    private Clock clock;
    private final Map<String, PostRankingCache.Snapshot> snapshots = new HashMap<>();
    private final Map<Long, Post> visible = new HashMap<>();

    @BeforeEach void setup() {
        clock = Clock.fixed(Instant.parse("2026-10-15T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        candidates = mock(PostRankingCandidates.class); cache = mock(PostRankingCache.class);
        repository = mock(PostRankingRepository.class); var posts = mock(PostService.class);
        service = new PostRankingService(candidates, cache, repository, posts, mock(CommunityService.class), clock);
        var input = new ArrayList<PostRankingPolicy.Candidate>();
        for (long id = 1; id <= 5; id++) {
            Post post = new Post(); post.setId(id); post.setIsPublic(true); post.setStatus("NORMAL"); visible.put(id, post);
            input.add(new PostRankingPolicy.Candidate(id, id, 1, LocalDateTime.now(clock), 10 - id, 0));
        }
        when(candidates.current(null)).thenReturn(new PostRankingCache.Batch(LocalDateTime.now(clock), input));
        doAnswer(call -> { snapshots.put(call.getArgument(0), call.getArgument(1)); return null; }).when(cache).saveSnapshot(anyString(), any());
        when(cache.snapshot(anyString())).thenAnswer(call -> snapshots.get(call.getArgument(0)));
        when(repository.visiblePosts(anyList())).thenAnswer(call -> ((List<Long>) call.getArgument(0)).stream().map(visible::get).filter(Objects::nonNull).toList());
        when(posts.assemblePostDtos(anyList(), any())).thenAnswer(call -> ((List<Post>) call.getArgument(0)).stream()
                .map(post -> new PostDtos.PostDto(post.getId().toString(), null, null, "[E2E_TEST]", "", null, "", 0, 0, "TEXT", false, 0, 0)).toList());
    }

    @Test void stableCursorDoesNotRecomputeOrderWhenVotesChange() {
        var first = service.list("hot", "day", null, null, 2, null);
        when(candidates.current(null)).thenReturn(new PostRankingCache.Batch(LocalDateTime.now(clock), List.of()));
        var second = service.list("hot", "day", null, first.nextCursor(), 2, null);
        assertThat(first.items()).extracting(PostDtos.PostDto::id).containsExactly("1", "2");
        assertThat(second.items()).extracting(PostDtos.PostDto::id).containsExactly("3", "4");
        verify(candidates, times(1)).current(null);
    }

    @Test void invisibleEntriesAreSkippedWithoutRepeatingOrSkippingVisibleEntries() {
        var first = service.list("hot", "day", null, null, 1, null);
        visible.remove(2L); visible.remove(4L);
        var second = service.list("hot", "day", null, first.nextCursor(), 1, null);
        var last = service.list("hot", "day", null, second.nextCursor(), 1, null);
        assertThat(second.items()).extracting(PostDtos.PostDto::id).containsExactly("3");
        assertThat(last.items()).extracting(PostDtos.PostDto::id).containsExactly("5");
        assertThat(last.hasMore()).isFalse(); assertThat(last.nextCursor()).isNull();
    }

    @Test void allRemainingRemovedEntriesReturnAnEmptyTerminalPage() {
        var first = service.list("hot", "day", null, null, 1, null);
        visible.clear();
        var second = service.list("hot", "day", null, first.nextCursor(), 10, null);
        assertThat(second.items()).isEmpty(); assertThat(second.hasMore()).isFalse();
    }

    @Test void cursorCannotBeReusedForAnotherAccountOrPeriod() {
        var first = service.list("hot", "day", null, null, 1, null);
        assertThatThrownBy(() -> service.list("hot", "week", null, first.nextCursor(), 1, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.list("hot", "day", null, first.nextCursor(), 1, new CurrentUser(7L, "user", "USER")))
                .isInstanceOf(BusinessException.class);
    }

    @Test void expiredOrMissingSnapshotRequiresExplicitRefresh() {
        var first = service.list("hot", "day", null, null, 1, null);
        snapshots.clear();
        assertThatThrownBy(() -> service.list("hot", "day", null, first.nextCursor(), 1, null))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.GONE);
                    assertThat(error.getCode()).isEqualTo("RANKING_SNAPSHOT_EXPIRED");
                });
    }

    @Test void invalidParametersNeverQueryCandidatePools() {
        for (String cursor : List.of("../other", "a".repeat(32) + ".501", "a".repeat(32) + ".-1")) {
            assertThatThrownBy(() -> service.list("hot", "day", null, cursor, 10, null)).isInstanceOf(BusinessException.class);
        }
        assertThatThrownBy(() -> service.list("recommend", "week", null, null, 10, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.list("hot", "day", null, null, 0, null)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(candidates);
    }

    @Test void repeatedFirstPageRequestsReuseTheSameSnapshotInsteadOfAllocatingMoreRedisEntries() {
        var first = service.list("hot", "day", null, null, 1, null);
        var repeated = service.list("hot", "day", null, null, 1, null);
        assertThat(repeated.nextCursor()).isEqualTo(first.nextCursor());
        verify(cache, times(1)).saveSnapshot(anyString(), any());
    }
}
