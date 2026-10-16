package com.luminous.api.ranking;

import com.luminous.api.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import java.time.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PostRankingCandidatesTest {
    private final PostRankingCache cache = mock(PostRankingCache.class);
    private final PostRankingRepository repository = mock(PostRankingRepository.class);
    private final RedissonClient redisson = mock(RedissonClient.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private final PostRankingCandidates provider = new PostRankingCandidates(cache, repository, redisson, clock);
    private final PostRankingCache.Batch batch = new PostRankingCache.Batch(LocalDateTime.now(clock), List.of());

    @Test void warmedCacheNeverAggregatesOrAcquiresRefreshLock() {
        when(cache.batch(null)).thenReturn(batch);
        assertThat(provider.current(null)).isSameAs(batch);
        verifyNoInteractions(repository, redisson);
    }

    @Test void doubleCheckReusesTheOtherInstancesCompletedBuild() throws Exception {
        var lock = mock(RLock.class);
        when(cache.batch(null)).thenReturn(null, batch);
        when(cache.candidateKey(null)).thenReturn("luminous:test-ranking");
        when(redisson.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(1, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        assertThat(provider.current(null)).isSameAs(batch);
        verifyNoInteractions(repository); verify(lock).unlock();
    }

    @Test void coldCacheContentionFailsClearlyInsteadOfRunningAnotherAggregation() throws Exception {
        var lock = mock(RLock.class);
        when(redisson.getLock(anyString())).thenReturn(lock);
        when(cache.candidateKey(null)).thenReturn("luminous:test-ranking");
        assertThatThrownBy(() -> provider.current(null)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo("RANKING_UNAVAILABLE"));
        verifyNoInteractions(repository);
    }

    @Test void queryFailureReleasesLockAndReturnsRecoverableError() throws Exception {
        var lock = mock(RLock.class);
        when(cache.candidateKey(null)).thenReturn("luminous:test-ranking");
        when(redisson.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(1, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(repository.candidates(LocalDateTime.now(clock), null)).thenThrow(new IllegalStateException("[E2E_TEST] 查询失败"));
        assertThatThrownBy(() -> provider.current(null)).isInstanceOf(BusinessException.class);
        verify(lock).unlock();
    }
}
