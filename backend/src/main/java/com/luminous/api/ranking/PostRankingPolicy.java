package com.luminous.api.ranking;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/** 纯排序规则：推荐混合新鲜度与新帖探索，热榜保持可解释的认可顺序。 */
public final class PostRankingPolicy {
    public static final int WINDOW_LIMIT = 500;
    public static final int DISCUSSION_CAP = 10;
    public record Candidate(long id, long authorId, long communityId, LocalDateTime createdAt,
                            long netVotes, int participants) {}

    private PostRankingPolicy() {}

    public static double recommendationScore(Candidate post, LocalDateTime at, Set<Long> joined) {
        double ageHours = Math.max(0, Duration.between(post.createdAt(), at).toSeconds() / 3600.0);
        double contribution = 1 + Math.max(0, post.netVotes()) + 0.25 * Math.min(DISCUSSION_CAP, post.participants());
        double penalty = post.netVotes() < 0 ? 0.05 / (1.0 - post.netVotes()) : 1;
        return contribution * penalty * (joined.contains(post.communityId()) ? 1.15 : 1)
                / Math.pow(ageHours + 2, 1.1);
    }

    public static List<Long> recommend(List<Candidate> candidates, LocalDateTime at, Set<Long> joined) {
        List<Candidate> ranked = candidates.stream().sorted(Comparator
                .comparingDouble((Candidate post) -> recommendationScore(post, at, joined)).reversed()
                .thenComparing(Candidate::createdAt, Comparator.reverseOrder())
                .thenComparing(Candidate::id, Comparator.reverseOrder())).toList();
        List<Candidate> fresh = candidates.stream()
                .filter(post -> !post.createdAt().isBefore(at.minusHours(48)) && post.netVotes() >= 0
                        && post.netVotes() <= 2 && post.participants() <= 2)
                .sorted(Comparator.comparing(Candidate::createdAt).thenComparingLong(Candidate::id).reversed()).toList();
        List<Candidate> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        while (result.size() < Math.min(WINDOW_LIMIT, candidates.size())) {
            int slot = result.size() % 10;
            boolean explore = slot == 2 || slot == 5 || slot == 8;
            Candidate next = take(explore ? fresh : ranked, seen, result, 2);
            if (next == null) next = take(ranked, seen, result, 2);
            // 单社区池先放宽社区连续限制，继续优先选择不同作者。
            if (next == null && explore) next = take(fresh, seen, result, 1);
            if (next == null) next = take(ranked, seen, result, 1);
            if (next == null) next = take(ranked, seen, result, 0);
            if (next == null) break;
            result.add(next);
            seen.add(next.id());
        }
        return result.stream().map(Candidate::id).toList();
    }

    private static Candidate take(List<Candidate> source, Set<Long> seen, List<Candidate> result, int diversity) {
        Candidate previous = result.isEmpty() ? null : result.getLast();
        for (Candidate candidate : source) {
            if (seen.contains(candidate.id())) continue;
            if (diversity > 0 && previous != null) {
                if (candidate.authorId() == previous.authorId()) continue;
                if (diversity > 1 && result.size() >= 2 && candidate.communityId() == previous.communityId()
                        && candidate.communityId() == result.get(result.size() - 2).communityId()) continue;
            }
            return candidate;
        }
        return null;
    }

    public static List<Long> hot(List<Candidate> candidates, LocalDateTime at, String period) {
        LocalDateTime since = "week".equals(period) ? at.minusDays(7) : at.minusHours(24);
        return candidates.stream().filter(post -> !post.createdAt().isBefore(since)
                        && !post.createdAt().isAfter(at) && post.netVotes() >= 0
                        && (post.netVotes() > 0 || post.participants() > 0))
                .sorted(Comparator.comparingLong(Candidate::netVotes).reversed()
                        .thenComparing(Comparator.comparingInt((Candidate post) -> Math.min(DISCUSSION_CAP, post.participants())).reversed())
                        .thenComparing(Candidate::createdAt, Comparator.reverseOrder())
                        .thenComparing(Candidate::id, Comparator.reverseOrder()))
                .limit(WINDOW_LIMIT).map(Candidate::id).toList();
    }
}
