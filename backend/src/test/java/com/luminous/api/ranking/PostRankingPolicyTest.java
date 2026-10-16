package com.luminous.api.ranking;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class PostRankingPolicyTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 12, 0);
    private PostRankingPolicy.Candidate post(long id, long author, long community, int hours, long votes, int participants) {
        return new PostRankingPolicy.Candidate(id, author, community, NOW.minusHours(hours), votes, participants);
    }

    @Test void freshnessAndJoinedCommunityAffectRecommendationButViewsDoNotExistInItsInput() {
        var fresh = post(1, 1, 1, 1, 4, 0);
        var older = post(2, 2, 2, 48, 4, 0);
        assertThat(PostRankingPolicy.recommendationScore(fresh, NOW, Set.of()))
                .isGreaterThan(PostRankingPolicy.recommendationScore(older, NOW, Set.of()));
        assertThat(PostRankingPolicy.recommendationScore(fresh, NOW, Set.of(1L)))
                .isGreaterThan(PostRankingPolicy.recommendationScore(fresh, NOW, Set.of()));
    }

    @Test void lowInteractionFreshPostsGetThreeExplorationSlotsWithoutDuplicates() {
        var input = new ArrayList<PostRankingPolicy.Candidate>();
        for (int i = 0; i < 20; i++) input.add(post(i + 1, i + 1, i % 4, 12, 100, 5));
        for (int i = 0; i < 10; i++) input.add(post(100 + i, 100 + i, i % 4, 1, 0, 0));
        var result = PostRankingPolicy.recommend(input, NOW, Set.of());
        assertThat(result).doesNotHaveDuplicates();
        assertThat(result.subList(0, 10)).filteredOn(id -> id >= 100).hasSize(3);
        assertThat(result.get(2)).isGreaterThanOrEqualTo(100);
        assertThat(result.get(5)).isGreaterThanOrEqualTo(100);
        assertThat(result.get(8)).isGreaterThanOrEqualTo(100);
    }

    @Test void recommendationSeparatesAuthorsAndCanStillServeOneAuthorOrOneCommunity() {
        var input = List.of(post(1, 1, 1, 0, 100, 0), post(2, 1, 1, 0, 99, 0), post(3, 2, 2, 0, 98, 0));
        assertThat(PostRankingPolicy.recommend(input, NOW, Set.of())).containsExactly(1L, 3L, 2L);
        assertThat(PostRankingPolicy.recommend(input.subList(0, 2), NOW, Set.of())).containsExactly(1L, 2L);
    }

    @Test void discussionContributionStopsGrowingAtTenAndNegativeVotesAreDemoted() {
        var ten = post(1, 1, 1, 1, 5, 10);
        var many = post(2, 2, 2, 1, 5, 1000);
        assertThat(PostRankingPolicy.recommendationScore(ten, NOW, Set.of()))
                .isEqualTo(PostRankingPolicy.recommendationScore(many, NOW, Set.of()));
        assertThat(PostRankingPolicy.recommendationScore(post(3, 3, 3, 1, -1, 0), NOW, Set.of()))
                .isLessThan(PostRankingPolicy.recommendationScore(post(4, 4, 4, 1, 0, 0), NOW, Set.of()));
    }

    @Test void singleCommunityRelaxationStillSeparatesAvailableAuthors() {
        var input = List.of(post(1, 1, 1, 0, 100, 0), post(2, 1, 1, 0, 99, 0),
                post(3, 2, 1, 0, 98, 0), post(4, 3, 1, 0, 97, 0));
        assertThat(PostRankingPolicy.recommend(input, NOW, Set.of())).containsExactly(1L, 3L, 2L, 4L);
    }

    @Test void hotUsesExactPublicationWindowsAndRequiresNonNegativeExternalInteraction() {
        var input = List.of(post(1, 1, 1, 24, 1, 0), post(2, 2, 2, 25, 100, 0),
                post(3, 3, 3, 168, 90, 0), post(4, 4, 4, 169, 999, 0),
                post(5, 5, 5, 0, 0, 0), post(6, 6, 6, 0, -1, 5), post(7, 7, 7, -1, 999, 0));
        assertThat(PostRankingPolicy.hot(input, NOW, "day")).containsExactly(1L);
        assertThat(PostRankingPolicy.hot(input, NOW, "week")).containsExactly(2L, 3L, 1L);
    }

    @Test void hotPrioritizesNetVotesThenCappedParticipantsThenTimeAndId() {
        var input = List.of(post(1, 1, 1, 2, 3, 0), post(2, 2, 2, 1, 2, 9),
                post(3, 3, 3, 3, 2, 10), post(4, 4, 4, 1, 2, 100));
        assertThat(PostRankingPolicy.hot(input, NOW, "day")).containsExactly(1L, 4L, 3L, 2L);
    }

    @Test void bothRankingsAreBoundedAndEmptyPoolsStayEmpty() {
        var input = new ArrayList<PostRankingPolicy.Candidate>();
        for (int i = 0; i < 600; i++) input.add(post(i + 1, i + 1, i % 3, 1, 10, 0));
        assertThat(PostRankingPolicy.recommend(input, NOW, Set.of())).hasSize(500);
        assertThat(PostRankingPolicy.hot(input, NOW, "day")).hasSize(500);
        assertThat(PostRankingPolicy.hot(List.of(), NOW, "week")).isEmpty();
    }
}
