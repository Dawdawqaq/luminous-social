package com.luminous.api.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.*;
import com.luminous.api.common.BusinessException;
import org.slf4j.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 短缓存仅保存候选编号；每次输出重新检查可见性，不组装完整用户或帖子。 */
@Service
public class SearchService {
    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    private static final List<String> KINDS = List.of("posts", "communities", "users");
    private static final String PREFIX = "luminous:search:v1:snapshot:";
    public record Snapshot(String id, String query, String kind, List<Long> ids, boolean limited, long expiresAt) {}
    private final Cache<String, List<Long>> suggestions = Caffeine.newBuilder().maximumSize(2000).expireAfterWrite(Duration.ofSeconds(20)).build();
    private final Cache<String, Snapshot> firstPages = Caffeine.newBuilder().maximumSize(256).expireAfterWrite(Duration.ofSeconds(20)).build();
    private final SearchRepository repository;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Clock clock;
    public SearchService(SearchRepository repository, StringRedisTemplate redis, ObjectMapper mapper, Clock clock) {
        this.repository = repository; this.redis = redis; this.mapper = mapper; this.clock = clock;
    }
    public SearchDtos.Suggestions suggest(SearchTerms terms) {
        var groups = new ArrayList<SearchDtos.Group>();
        for (String kind : KINDS) {
            if (terms.forcedKind() != null && !terms.forcedKind().equals(kind)) continue;
            try {
                var ids = suggestions.get(kind + ":" + terms.key(), ignored -> repository.candidates(terms, kind, 5));
                var current = ordered(repository.hydrate(kind, ids), ids);
                groups.add(new SearchDtos.Group(kind, current.stream().limit(4).toList(), ids.size() > 4, false));
            } catch (RuntimeException ex) {
                log.warn("搜索提示分类暂不可用，分类={}", kind, ex);
                groups.add(new SearchDtos.Group(kind, List.of(), false, true));
            }
        }
        if (groups.stream().allMatch(SearchDtos.Group::unavailable)) throw unavailable();
        return new SearchDtos.Suggestions(groups, terms.hint());
    }
    public SearchDtos.Slice list(SearchTerms terms, String requestedKind, String cursor, int size) {
        if (!KINDS.contains(requestedKind) || size < 1 || size > 20) throw SearchTerms.invalid("分类无效，单页数量为1至20");
        String kind = terms.forcedKind() == null ? requestedKind : terms.forcedKind();
        Snapshot snapshot;
        int offset = 0;
        if (cursor == null || cursor.isBlank()) {
            snapshot = firstPages.get(kind + ":" + terms.key(), ignored -> createSnapshot(terms, kind));
        } else {
            if (!cursor.matches("[a-f0-9]{32}\\.[0-9]{1,3}")) throw SearchTerms.invalid("搜索游标无效");
            String[] parts = cursor.split("\\."); offset = Integer.parseInt(parts[1]);
            try {
                String value = redis.opsForValue().get(PREFIX + parts[0]);
                if (value == null) { firstPages.invalidate(kind + ":" + terms.key()); throw expired(); }
                snapshot = mapper.readValue(value, Snapshot.class);
            } catch (BusinessException ex) { throw ex; }
            catch (Exception ex) { throw unavailable(); }
            if (!snapshot.query().equals(terms.key()) || !snapshot.kind().equals(kind) || offset > snapshot.ids().size()) throw SearchTerms.invalid("游标不属于当前搜索");
        }
        if (snapshot.expiresAt() <= clock.millis()) throw expired();
        try { return page(terms, snapshot, offset, size); }
        catch (BusinessException ex) { throw ex; }
        catch (RuntimeException ex) { throw unavailable(); }
    }
    private Snapshot createSnapshot(SearchTerms terms, String kind) {
        try {
            var ids = repository.candidates(terms, kind, 201);
            var snapshot = new Snapshot(UUID.randomUUID().toString().replace("-", ""), terms.key(), kind,
                    ids.stream().limit(200).toList(), ids.size() > 200, clock.millis() + 300000);
            redis.opsForValue().set(PREFIX + snapshot.id(), mapper.writeValueAsString(snapshot), Duration.ofMinutes(5));
            return snapshot;
        } catch (RuntimeException ex) { throw unavailable(); }
        catch (Exception ex) { throw unavailable(); }
    }
    private SearchDtos.Slice page(SearchTerms terms, Snapshot snapshot, int offset, int size) {
        var hits = new ArrayList<SearchDtos.Hit>();
        int next = offset, scan = offset;
        boolean more = false;
        while (scan < snapshot.ids().size() && !more) {
            int end = Math.min(snapshot.ids().size(), scan + Math.max(20, size + 1));
            var chunk = snapshot.ids().subList(scan, end);
            var byId = repository.hydrate(snapshot.kind(), chunk).stream().collect(Collectors.toMap(SearchDtos.Hit::id, Function.identity()));
            for (int i = scan; i < end; i++) {
                var hit = byId.get(snapshot.ids().get(i).toString());
                if (hit == null) continue;
                if (hits.size() == size) { more = true; break; }
                hits.add(hit); next = i + 1;
            }
            scan = end;
        }
        return new SearchDtos.Slice(snapshot.kind(), hits, more ? snapshot.id() + "." + next : null, more, snapshot.limited(), terms.hint());
    }
    private List<SearchDtos.Hit> ordered(List<SearchDtos.Hit> hits, List<Long> ids) {
        Map<String, SearchDtos.Hit> map = hits.stream().collect(Collectors.toMap(SearchDtos.Hit::id, Function.identity()));
        return ids.stream().map(id -> map.get(id.toString())).filter(Objects::nonNull).toList();
    }
    private static BusinessException expired() { return new BusinessException(HttpStatus.GONE, "SEARCH_EXPIRED", "搜索结果已过期，请刷新结果"); }
    private static BusinessException unavailable() { return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "SEARCH_UNAVAILABLE", "搜索暂不可用，请稍后重试"); }
}
