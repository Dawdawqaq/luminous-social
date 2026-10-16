package com.luminous.api.community;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luminous.api.common.SnowflakeIdGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.auth.User;
import com.luminous.api.auth.UserMapper;
import com.luminous.api.common.BusinessException;
import com.luminous.api.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

import com.luminous.api.cache.CacheConstants;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

@Service
public class CommunityService {
    private static final Logger log = LoggerFactory.getLogger(CommunityService.class);

    private final CommunityMapper communityMapper;
    private final CommunityMemberMapper communityMemberMapper;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;
    private final RedissonClient redissonClient;
    private final org.springframework.context.ApplicationEventPublisher events;

    public CommunityService(CommunityMapper communityMapper, CommunityMemberMapper communityMemberMapper, ObjectMapper objectMapper, UserMapper userMapper, RedissonClient redissonClient, org.springframework.context.ApplicationEventPublisher events) {
        this.communityMapper = communityMapper;
        this.communityMemberMapper = communityMemberMapper;
        this.objectMapper = objectMapper;
        this.userMapper = userMapper;
        this.redissonClient = redissonClient;
        this.events = events;
    }

    public List<CommunityDtos.CommunityDto> list(String search, CurrentUser currentUser) {
        LambdaQueryWrapper<Community> wrapper = new LambdaQueryWrapper<Community>();
        if (search != null && !search.isBlank()) {
            String sanitized = escapeLike(search.trim());
            wrapper.and(w -> w.like(Community::getSlug, sanitized)
                    .or().like(Community::getName, sanitized)
                    .or().like(Community::getDescription, sanitized));
        }
        wrapper.orderByDesc(Community::getMembersCount);
        wrapper.last("LIMIT 50");
        List<Community> list = communityMapper.selectList(wrapper);
        if (list.isEmpty()) {
            return List.of();
        }

        return assembleMany(list, currentUser);
    }

    public List<CommunityDtos.CommunityDto> assembleMany(List<Community> list, CurrentUser currentUser) {
        if (list.isEmpty()) return List.of();
        Set<Long> ownerIds = list.stream().map(Community::getOwnerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, User> ownerMap = ownerIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(ownerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        List<Long> communityIds = list.stream().map(Community::getId).toList();
        Set<Long> joinedIds = (currentUser == null) ? Set.of() :
                communityMemberMapper.selectList(new LambdaQueryWrapper<CommunityMember>()
                        .eq(CommunityMember::getUserId, currentUser.id())
                        .in(CommunityMember::getCommunityId, communityIds))
                        .stream().map(CommunityMember::getCommunityId).collect(Collectors.toSet());

        return list.stream()
                .map(community -> assembleDto(community, ownerMap.get(community.getOwnerId()), joinedIds.contains(community.getId())))
                .toList();
    }

    public CommunityDtos.CommunityDto detail(String slug, CurrentUser currentUser) {
        return toDto(requireBySlug(slug), currentUser);
    }

    public List<CommunityDtos.RecommendedCommunityDto> recommended(CurrentUser currentUser) {
        List<Community> list = communityMapper.selectList(new LambdaQueryWrapper<Community>().orderByDesc(Community::getMembersCount).last("LIMIT 5"));
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> communityIds = list.stream().map(Community::getId).toList();
        Set<Long> joinedIds = (currentUser == null) ? Set.of() :
                communityMemberMapper.selectList(new LambdaQueryWrapper<CommunityMember>()
                        .eq(CommunityMember::getUserId, currentUser.id())
                        .in(CommunityMember::getCommunityId, communityIds))
                        .stream().map(CommunityMember::getCommunityId).collect(Collectors.toSet());

        return list.stream()
                .map(item -> new CommunityDtos.RecommendedCommunityDto(
                        String.valueOf(item.getId()),
                        "c/" + item.getSlug(),
                        compactMembers(item.getMembersCount()) + " members",
                        item.getSlug().contains("pixel") ? "phone" : "brain",
                        item.getIconUrl(),
                        joinedIds.contains(item.getId())
                ))
                .toList();
    }

    @Transactional
    public CommunityDtos.CommunityDto join(String slug, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "您需要先登录才能关注社区");
        }
        Community community = requireBySlug(slug);

        String lockKey = CacheConstants.LOCK_JOIN_COMMUNITY + community.getId() + ":" + currentUser.id();
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(3, 5, TimeUnit.SECONDS);
            if (!acquired) {
                throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "JOIN_BUSY", "操作过于频繁，请稍后再试");
            }
            if (!isJoined(community.getId(), currentUser.id())) {
                CommunityMember member = new CommunityMember();
                member.setId(SnowflakeIdGenerator.getNextId());
                member.setCommunityId(community.getId());
                member.setUserId(currentUser.id());
                communityMemberMapper.insert(member);
                // 先完成社区行更新，再锁成长账户，与发帖的社区外键锁顺序保持一致。
                communityMapper.updateMembersCountAtomic(community.getId(), 1);
                community.setMembersCount(community.getMembersCount() + 1);
                events.publishEvent(new com.luminous.api.growth.GrowthEvent.Joined(currentUser.id(), community.getId()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "LOCK_INTERRUPTED", "操作被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return toDto(community, currentUser);
    }

    @Transactional
    public CommunityDtos.CommunityDto leave(String slug, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "您需要先登录才能取消关注社区");
        }
        Community community = requireBySlug(slug);
        int deleted = communityMemberMapper.delete(new LambdaQueryWrapper<CommunityMember>()
                .eq(CommunityMember::getCommunityId, community.getId())
                .eq(CommunityMember::getUserId, currentUser.id()));
        if (deleted > 0) {
            communityMapper.updateMembersCountAtomic(community.getId(), -1);
            community.setMembersCount(Math.max(0, community.getMembersCount() - 1));
        }
        return toDto(community, currentUser);
    }

    @Transactional
    public CommunityDtos.CommunityDto create(CommunityDtos.CreateCommunityRequest request, CurrentUser currentUser) {
        if (communityMapper.selectCount(new LambdaQueryWrapper<Community>().eq(Community::getSlug, request.slug())) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SLUG_EXISTS", "该社区 ID 已被占用");
        }
        Community community = new Community();
        community.setId(SnowflakeIdGenerator.getNextId());
        community.setOwnerId(currentUser.id());
        community.setSlug(request.slug());
        community.setName(request.name().trim());
        community.setDescription(request.description() == null ? "" : request.description().trim());
        community.setCategory(request.category() == null ? "Living" : request.category());
        community.setMembersCount(1);
        community.setMembersUnit("members");
        community.setRulesJson("[]");
        community.setModeratorsJson("[]");
        community.setCreatedAt(java.time.LocalDateTime.now());
        community.setUpdatedAt(java.time.LocalDateTime.now());
        communityMapper.insert(community);

        CommunityMember member = new CommunityMember();
        member.setId(SnowflakeIdGenerator.getNextId());
        member.setCommunityId(community.getId());
        member.setUserId(currentUser.id());
        communityMemberMapper.insert(member);
        events.publishEvent(new com.luminous.api.growth.GrowthEvent.Joined(currentUser.id(), community.getId()));

        return toDto(community, currentUser);
    }

    @Transactional
    public CommunityDtos.CommunityDto update(String slug, CommunityDtos.UpdateCommunityRequest request, CurrentUser currentUser) {
        Community community = requireBySlug(slug);
        if (community.getOwnerId() == null || !community.getOwnerId().equals(currentUser.id())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "只有吧主可以修改社区信息");
        }
        community.setName(request.name().trim());
        community.setIconUrl(request.iconUrl());
        community.setBannerUrl(request.bannerUrl());
        community.setDescription(request.description() == null ? "" : request.description().trim());
        try {
            community.setRulesJson(objectMapper.writeValueAsString(request.rules() == null ? java.util.List.of() : request.rules()));
        } catch (Exception e) {
            log.warn("Failed to serialize community rules: {}", e.getMessage(), e);
            community.setRulesJson("[]");
        }
        community.setUpdatedAt(java.time.LocalDateTime.now());
        communityMapper.updateById(community);
        return toDto(community, currentUser);
    }

    public Community requireBySlug(String slug) {
        String cleanSlug = slug != null && slug.startsWith("c/") ? slug.substring(2) : slug;
        Community community = communityMapper.selectOne(new LambdaQueryWrapper<Community>().eq(Community::getSlug, cleanSlug));
        if (community == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "COMMUNITY_NOT_FOUND", "社区不存在");
        }
        return community;
    }

    public Community detailById(Long id) {
        Community community = communityMapper.selectById(id);
        if (community == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "COMMUNITY_NOT_FOUND", "社区不存在");
        }
        return community;
    }

    public Map<Long, Community> mapByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        return communityMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Community::getId, Function.identity(), (a, b) -> a));
    }

    public CommunityDtos.CommunityDto toDto(Community community, CurrentUser currentUser) {
        User owner = community.getOwnerId() == null ? null : userMapper.selectById(community.getOwnerId());
        boolean joined = currentUser != null && isJoined(community.getId(), currentUser.id());
        return assembleDto(community, owner, joined);
    }

    private CommunityDtos.CommunityDto assembleDto(Community community, User owner, boolean joined) {
        return new CommunityDtos.CommunityDto(
                String.valueOf(community.getId()),
                community.getSlug(),
                community.getName(),
                community.getIconUrl(),
                community.getBannerUrl(),
                community.getDescription(),
                community.getMembersCount(),
                community.getMembersUnit(),
                readRules(community.getRulesJson()),
                assembleModerators(community.getModeratorsJson(), owner),
                joined,
                community.getOwnerId() == null ? null : String.valueOf(community.getOwnerId()),
                community.getCategory() == null ? "Living" : community.getCategory()
        );
    }

    private boolean isJoined(Long communityId, Long userId) {
        return communityMemberMapper.selectCount(new LambdaQueryWrapper<CommunityMember>()
                .eq(CommunityMember::getCommunityId, communityId)
                .eq(CommunityMember::getUserId, userId)) > 0;
    }

    /** 发布事务锁定成员关系，避免退出社区与发布之间出现检查后状态变化。 */
    public void requireMembership(long communityId, long userId) {
        var member = communityMemberMapper.selectOne(new LambdaQueryWrapper<CommunityMember>()
                .eq(CommunityMember::getCommunityId, communityId).eq(CommunityMember::getUserId, userId).last("FOR SHARE"));
        if (member == null) throw new BusinessException(HttpStatus.FORBIDDEN, "COMMUNITY_JOIN_REQUIRED", "请先加入社区再发布帖子");
    }

    private List<String> readRules(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            log.warn("Failed to parse community rules JSON: {}", json, ex);
            return List.of();
        }
    }

    private List<CommunityDtos.ModeratorDto> assembleModerators(String json, User owner) {
        List<CommunityDtos.ModeratorDto> mods = new java.util.ArrayList<>();
        try {
            List<CommunityDtos.ModeratorDto> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            if (parsed != null) mods.addAll(parsed);
        } catch (Exception ex) {
            log.warn("Failed to parse moderators JSON: {}", json, ex);
        }
        
        if (owner != null && owner.getUsername() != null) {
            String username = owner.getUsername();
            String displayName = owner.getDisplayName() != null ? owner.getDisplayName() : username;
            String initials = username.length() >= 2 ? username.substring(0, 2).toUpperCase() : username.toUpperCase();
            boolean exists = mods.stream().anyMatch(m -> m.id().equals(String.valueOf(owner.getId())));
            if (!exists) {
                mods.add(0, new CommunityDtos.ModeratorDto(String.valueOf(owner.getId()), username, displayName, initials));
            }
        }
        return mods;
    }

    private String compactMembers(int count) {
        if (count >= 1_000_000) {
            return String.format("%.1fM", count / 1_000_000.0);
        }
        if (count >= 1000) {
            return String.format("%.1fk", count / 1000.0);
        }
        return String.valueOf(count);
    }

    private String escapeLike(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
    }
}
