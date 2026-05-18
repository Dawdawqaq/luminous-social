package com.luminous.api.community;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.auth.User;
import com.luminous.api.auth.UserMapper;
import com.luminous.api.common.BusinessException;
import com.luminous.api.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommunityService {
    private final CommunityMapper communityMapper;
    private final CommunityMemberMapper communityMemberMapper;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;

    public CommunityService(CommunityMapper communityMapper, CommunityMemberMapper communityMemberMapper, ObjectMapper objectMapper, UserMapper userMapper) {
        this.communityMapper = communityMapper;
        this.communityMemberMapper = communityMemberMapper;
        this.objectMapper = objectMapper;
        this.userMapper = userMapper;
    }

    public List<CommunityDtos.CommunityDto> list(String search, CurrentUser currentUser) {
        LambdaQueryWrapper<Community> wrapper = new LambdaQueryWrapper<Community>();
        if (search != null && !search.isBlank()) {
            wrapper.and(w -> w.like(Community::getSlug, search)
                    .or().like(Community::getName, search)
                    .or().like(Community::getDescription, search));
        }
        wrapper.orderByDesc(Community::getMembersCount);
        return communityMapper.selectList(wrapper)
                .stream()
                .map(community -> toDto(community, currentUser))
                .toList();
    }

    public CommunityDtos.CommunityDto detail(String slug, CurrentUser currentUser) {
        return toDto(requireBySlug(slug), currentUser);
    }

    public List<CommunityDtos.RecommendedCommunityDto> recommended(CurrentUser currentUser) {
        return communityMapper.selectList(new LambdaQueryWrapper<Community>().orderByDesc(Community::getMembersCount).last("LIMIT 5"))
                .stream()
                .map(item -> new CommunityDtos.RecommendedCommunityDto(
                        String.valueOf(item.getId()),
                        "c/" + item.getSlug(),
                        compactMembers(item.getMembersCount()) + " members",
                        item.getSlug().contains("pixel") ? "phone" : "brain",
                        item.getIconUrl(),
                        currentUser != null && isJoined(item.getId(), currentUser.id())
                ))
                .toList();
    }

    public CommunityDtos.CommunityDto join(String slug, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "您需要先登录才能关注社区");
        }
        Community community = requireBySlug(slug);
        if (!isJoined(community.getId(), currentUser.id())) {
            CommunityMember member = new CommunityMember();
            member.setId(IdWorker.getId());
            member.setCommunityId(community.getId());
            member.setUserId(currentUser.id());
            communityMemberMapper.insert(member);
            community.setMembersCount(community.getMembersCount() + 1);
            communityMapper.updateById(community);
        }
        return toDto(community, currentUser);
    }

    public CommunityDtos.CommunityDto leave(String slug, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "您需要先登录才能取消关注社区");
        }
        Community community = requireBySlug(slug);
        communityMemberMapper.delete(new LambdaQueryWrapper<CommunityMember>()
                .eq(CommunityMember::getCommunityId, community.getId())
                .eq(CommunityMember::getUserId, currentUser.id()));
        community.setMembersCount(Math.max(0, community.getMembersCount() - 1));
        communityMapper.updateById(community);
        return toDto(community, currentUser);
    }

    public CommunityDtos.CommunityDto create(CommunityDtos.CreateCommunityRequest request, CurrentUser currentUser) {
        if (communityMapper.selectCount(new LambdaQueryWrapper<Community>().eq(Community::getSlug, request.slug())) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SLUG_EXISTS", "该社区 ID 已被占用");
        }
        Community community = new Community();
        community.setId(IdWorker.getId());
        community.setOwnerId(currentUser.id());
        community.setSlug(request.slug());
        community.setName(request.name());
        community.setDescription(request.description());
        community.setMembersCount(1);
        community.setMembersUnit("members");
        community.setRulesJson("[]");
        community.setModeratorsJson("[]");
        community.setCreatedAt(java.time.LocalDateTime.now());
        community.setUpdatedAt(java.time.LocalDateTime.now());
        communityMapper.insert(community);

        CommunityMember member = new CommunityMember();
        member.setId(IdWorker.getId());
        member.setCommunityId(community.getId());
        member.setUserId(currentUser.id());
        communityMemberMapper.insert(member);

        return toDto(community, currentUser);
    }

    public CommunityDtos.CommunityDto update(String slug, CommunityDtos.UpdateCommunityRequest request, CurrentUser currentUser) {
        Community community = requireBySlug(slug);
        if (community.getOwnerId() == null || !community.getOwnerId().equals(currentUser.id())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "只有吧主可以修改社区信息");
        }
        community.setName(request.name());
        community.setIconUrl(request.iconUrl());
        community.setBannerUrl(request.bannerUrl());
        community.setDescription(request.description());
        try {
            community.setRulesJson(objectMapper.writeValueAsString(request.rules() == null ? java.util.List.of() : request.rules()));
        } catch (Exception e) {
            community.setRulesJson("[]");
        }
        community.setUpdatedAt(java.time.LocalDateTime.now());
        communityMapper.updateById(community);
        return toDto(community, currentUser);
    }

    public Community requireBySlug(String slug) {
        Community community = communityMapper.selectOne(new LambdaQueryWrapper<Community>().eq(Community::getSlug, slug));
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

    public CommunityDtos.CommunityDto toDto(Community community, CurrentUser currentUser) {
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
                readModerators(community.getModeratorsJson(), community.getOwnerId()),
                currentUser != null && isJoined(community.getId(), currentUser.id()),
                community.getOwnerId() == null ? null : String.valueOf(community.getOwnerId())
        );
    }

    private boolean isJoined(Long communityId, Long userId) {
        return communityMemberMapper.selectCount(new LambdaQueryWrapper<CommunityMember>()
                .eq(CommunityMember::getCommunityId, communityId)
                .eq(CommunityMember::getUserId, userId)) > 0;
    }

    private List<String> readRules(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "COMMUNITY_RULES_INVALID", "社区规则配置异常");
        }
    }

    private List<CommunityDtos.ModeratorDto> readModerators(String json, Long ownerId) {
        List<CommunityDtos.ModeratorDto> mods = new java.util.ArrayList<>();
        try {
            List<CommunityDtos.ModeratorDto> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            if (parsed != null) mods.addAll(parsed);
        } catch (Exception ex) {
            // ignore
        }
        
        if (ownerId != null) {
            User owner = userMapper.selectById(ownerId);
            if (owner != null) {
                String initials = owner.getUsername().length() >= 2 ? owner.getUsername().substring(0, 2).toUpperCase() : owner.getUsername().toUpperCase();
                boolean exists = mods.stream().anyMatch(m -> m.id().equals(String.valueOf(owner.getId())));
                if (!exists) {
                    mods.add(0, new CommunityDtos.ModeratorDto(String.valueOf(owner.getId()), owner.getUsername(), owner.getDisplayName(), initials));
                }
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
}
