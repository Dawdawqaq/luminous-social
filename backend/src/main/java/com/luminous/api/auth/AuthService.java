package com.luminous.api.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luminous.api.common.SnowflakeIdGenerator;
import com.luminous.api.common.BusinessException;
import com.luminous.api.common.PageResponse;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.luminous.api.post.PostMapper;
import com.luminous.api.security.CurrentUser;
import com.luminous.api.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.luminous.api.cache.UserStatsCacheService;
import com.luminous.api.growth.GrowthEvent;
import com.luminous.api.growth.GrowthQueryService;

@Service
public class AuthService {
    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserFollowMapper userFollowMapper;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final UserStatsCacheService userStatsCacheService;
    private final com.luminous.api.feed.FeedService feedService;
    private final GrowthQueryService growthQueries;
    private final FollowActionPolicy followPolicy;

    public AuthService(
            UserMapper userMapper,
            PostMapper postMapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            UserFollowMapper userFollowMapper,
            org.springframework.context.ApplicationEventPublisher eventPublisher,
            UserStatsCacheService userStatsCacheService,
            @org.springframework.context.annotation.Lazy com.luminous.api.feed.FeedService feedService,
            GrowthQueryService growthQueries, FollowActionPolicy followPolicy
    ) {
        this.userMapper = userMapper;
        this.postMapper = postMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userFollowMapper = userFollowMapper;
        this.eventPublisher = eventPublisher;
        this.userStatsCacheService = userStatsCacheService;
        this.feedService = feedService;
        this.growthQueries = growthQueries;
        this.followPolicy = followPolicy;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        User existing = findByUsername(request.username());
        if (existing != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "用户名已被占用");
        }
        User user = new User();
        user.setId(SnowflakeIdGenerator.getNextId());
        user.setUsername(request.username());
        user.setDisplayName(request.displayName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setAvatarUrl(null);
        user.setBannerUrl(null);
        user.setBio(null);
        user.setJoinDate(LocalDate.now().getYear() + "年" + LocalDate.now().getMonthValue() + "月");

        // 注册接口始终创建普通用户，管理员只能由受控初始化流程创建。
        user.setRole("USER");

        user.setIsBanned(false);
        userMapper.insert(user);
        eventPublisher.publishEvent(new GrowthEvent.Registered(user.getId()));
        return new AuthDtos.AuthResponse(jwtService.createToken(user.getId(), user.getUsername(), user.getRole()), toDto(user, null));
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        User user = findByUsername(request.username());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "用户名或密码错误");
        }
        if (Boolean.TRUE.equals(user.getIsBanned())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "USER_BANNED", "该账号已被封禁，如有异议请联系平台客服");
        }
        
        return new AuthDtos.AuthResponse(jwtService.createToken(user.getId(), user.getUsername(), user.getRole()), toDto(user, null));
    }

    public AuthDtos.UserDto currentUser(CurrentUser currentUser) {
        return toDto(requireUser(currentUser.id()), currentUser);
    }

    public User requireUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "当前用户不存在");
        }
        return user;
    }

    public AuthDtos.UserDto getUserProfile(String identifier, CurrentUser currentUser) {
        return getUserProfile(identifier, currentUser, UserIdentifierType.AUTO);
    }

    public AuthDtos.UserDto getUserProfile(String identifier, CurrentUser currentUser, UserIdentifierType identifierType) {
        User user = findUser(identifier, identifierType);
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        }
        return toDto(user, currentUser);
    }

    public PageResponse<AuthDtos.FollowUserDto> listFollowers(String identifier, long page, long size) {
        return listFollowers(identifier,page,size,null);
    }

    public PageResponse<AuthDtos.FollowUserDto> listFollowing(String identifier, long page, long size) {
        return listFollowing(identifier,page,size,null);
    }

    public PageResponse<AuthDtos.FollowUserDto> listFollowers(String identifier,long page,long size,CurrentUser viewer) {
        return listFollowers(identifier, page, size, viewer, UserIdentifierType.AUTO);
    }
    public PageResponse<AuthDtos.FollowUserDto> listFollowing(String identifier,long page,long size,CurrentUser viewer) {
        return listFollowing(identifier, page, size, viewer, UserIdentifierType.AUTO);
    }

    public PageResponse<AuthDtos.FollowUserDto> listFollowers(String identifier, long page, long size, CurrentUser viewer,
                                                            UserIdentifierType identifierType) {
        return listFollowUsers(identifier, page, size, true, viewer, identifierType);
    }

    public PageResponse<AuthDtos.FollowUserDto> listFollowing(String identifier, long page, long size, CurrentUser viewer,
                                                            UserIdentifierType identifierType) {
        return listFollowUsers(identifier, page, size, false, viewer, identifierType);
    }

    private PageResponse<AuthDtos.FollowUserDto> listFollowUsers(String identifier, long page, long size, boolean followers,
                                                               CurrentUser viewer, UserIdentifierType identifierType) {
        if (page < 1 || size < 1 || size > 50 || page > Long.MAX_VALUE / size) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "页码必须大于零，每页条数必须为 1 到 50");
        }
        User target = findUser(identifier, identifierType);
        if (target == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        }
        // 联表分页只读取公开资料，避免逐个加载用户统计和关注关系。
        Page<User> request = new Page<>(page, size);
        IPage<User> result = followers
                ? userFollowMapper.selectFollowers(request, target.getId())
                : userFollowMapper.selectFollowing(request, target.getId());
        List<Long> ids = result.getRecords().stream().map(User::getId).toList();
        Set<Long> followed = viewer == null || ids.isEmpty() ? Set.of() : userFollowMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId,viewer.id()).in(UserFollow::getFollowingId,ids)).stream()
                .map(UserFollow::getFollowingId).collect(Collectors.toSet());
        List<AuthDtos.FollowUserDto> items = result.getRecords().stream()
                .map(user -> new AuthDtos.FollowUserDto(String.valueOf(user.getId()), user.getUsername(),
                        user.getDisplayName(), user.getAvatarUrl(), user.getBio(),followed.contains(user.getId())))
                .toList();
        return new PageResponse<>(items, result.getCurrent(), result.getSize(), result.getTotal(),
                result.getCurrent() < result.getPages());
    }

    public AuthDtos.UserDto updateProfile(AuthDtos.UpdateProfileRequest request, CurrentUser currentUser) {
        User user = requireUser(currentUser.id());
        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName());
        }
        if (request.bio() != null) user.setBio(request.bio());
        if (request.avatar() != null) user.setAvatarUrl(request.avatar());
        if (request.banner() != null) user.setBannerUrl(request.banner());
        userMapper.updateById(user);
        return toDto(user, currentUser);
    }

    public AuthDtos.UserDto toDto(User user, CurrentUser currentUser) {
        UserStatsCacheService.Stats stats = userStatsCacheService.getUserStats(user.getId());
                
        boolean isFollowing = false;
        if (currentUser != null && !currentUser.id().equals(user.getId())) {
            isFollowing = userFollowMapper.exists(new LambdaQueryWrapper<UserFollow>()
                    .eq(UserFollow::getFollowerId, currentUser.id())
                    .eq(UserFollow::getFollowingId, user.getId()));
        }

        return new AuthDtos.UserDto(
                String.valueOf(user.getId()),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getBannerUrl(),
                user.getBio(),
                new AuthDtos.Stats(stats.posts(), stats.followers(), stats.following()),
                user.getJoinDate(),
                isFollowing,
                user.getRole(),
                Boolean.TRUE.equals(user.getIsBanned()),
                growthQueries.progress(user.getId(), 0)
        );
    }

    @Transactional
    public AuthDtos.UserDto follow(String identifier, CurrentUser currentUser) {
        return follow(identifier, currentUser, UserIdentifierType.AUTO);
    }

    @Transactional
    public AuthDtos.UserDto follow(String identifier, CurrentUser currentUser, UserIdentifierType identifierType) {
        if (currentUser == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        User target = findUser(identifier, identifierType);
        if (target == null) throw new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        if (target.getId().equals(currentUser.id())) throw new BusinessException(HttpStatus.BAD_REQUEST, "CANNOT_FOLLOW_SELF", "不能关注自己");
        
        if(followPolicy.change(currentUser.id(),target.getId(),true)) {
            userStatsCacheService.incrementFollowerCount(target.getId());
            userStatsCacheService.incrementFollowingCount(currentUser.id());
            com.luminous.api.common.AfterCommit.run(() -> feedService.invalidateInbox(currentUser.id()));
            if(followPolicy.shouldNotify(currentUser.id(),target.getId())) {
                eventPublisher.publishEvent(new com.luminous.api.notification.NotificationEvent(currentUser.id(),target.getId(),
                        "FOLLOW_USER","USER",currentUser.id(),currentUser.username()+" 关注了你"));
            }
        }
        return toDtoWithFollowing(target,true);
    }

    @Transactional
    public AuthDtos.UserDto unfollow(String identifier, CurrentUser currentUser) {
        return unfollow(identifier, currentUser, UserIdentifierType.AUTO);
    }

    @Transactional
    public AuthDtos.UserDto unfollow(String identifier, CurrentUser currentUser, UserIdentifierType identifierType) {
        if(currentUser == null) throw new BusinessException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","请先登录");
        User target = findUser(identifier, identifierType);
        if(target == null) throw new BusinessException(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","用户不存在");
        if(followPolicy.change(currentUser.id(),target.getId(),false)) {
            userStatsCacheService.decrementFollowerCount(target.getId());
            userStatsCacheService.decrementFollowingCount(currentUser.id());
            com.luminous.api.common.AfterCommit.run(() -> feedService.invalidateInbox(currentUser.id()));
        }
        return toDtoWithFollowing(target,false);
    }

    public List<AuthDtos.UserDto> searchUsers(String search, CurrentUser currentUser) {
        if (search == null || search.isBlank()) {
            return List.of();
        }
        String trimmed = search.trim();
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        try {
            Long idVal = Long.parseLong(trimmed);
            wrapper.eq(User::getId, idVal);
        } catch (NumberFormatException e) {
            // 搜索词长度保护：小于 2 字符不触发全量扫描
            if (trimmed.length() < 2) {
                return List.of();
            }
            // 激活 V7 迁移中建立的 MATCH(username, display_name) AGAINST(? IN BOOLEAN MODE) ngram 全文倒排索引
            wrapper.apply("MATCH(username, display_name) AGAINST({0} IN BOOLEAN MODE)", trimmed);
        }

        // 限制最大返回条数，消除无界拉取导致的 JVM 内存放大
        wrapper.last("LIMIT 20");
        List<User> users = userMapper.selectList(wrapper);
        if (users.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = users.stream().map(User::getId).toList();
        Set<Long> followingIds = (currentUser == null || userIds.isEmpty()) ? Set.of() :
                userFollowMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, currentUser.id())
                        .in(UserFollow::getFollowingId, userIds))
                        .stream().map(UserFollow::getFollowingId).collect(Collectors.toSet());

        return users.stream()
                .map(user -> toDtoWithFollowing(user, followingIds.contains(user.getId())))
                .toList();
    }

    private AuthDtos.UserDto toDtoWithFollowing(User user, boolean isFollowing) {
        UserStatsCacheService.Stats stats = userStatsCacheService.getUserStats(user.getId());

        return new AuthDtos.UserDto(
                String.valueOf(user.getId()),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getBannerUrl(),
                user.getBio(),
                new AuthDtos.Stats(stats.posts(), stats.followers(), stats.following()),
                user.getJoinDate(),
                isFollowing,
                user.getRole(),
                Boolean.TRUE.equals(user.getIsBanned()),
                growthQueries.progress(user.getId(), 0)
        );
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    /** 显式模式只查一个唯一键；旧兼容模式同一查询检测歧义，不依赖查询顺序选人。 */
    private User findUser(String identifier, UserIdentifierType identifierType) {
        if (identifierType == UserIdentifierType.ID) {
            try {
                if (identifier == null || !identifier.matches("[0-9]+")) throw new NumberFormatException();
                long id = Long.parseLong(identifier);
                if (id <= 0) throw new NumberFormatException();
                return userMapper.selectById(id);
            } catch (NumberFormatException error) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_USER_ID", "用户 ID 必须为有效的正整数");
            }
        }
        if (identifier == null || identifier.isBlank()) return null;
        if (identifierType == UserIdentifierType.USERNAME) return findByUsername(identifier);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>().eq(User::getUsername, identifier);
        try {
            Long idVal = Long.parseLong(identifier.trim());
            wrapper.or().eq(User::getId, idVal);
        } catch (NumberFormatException ignored) {
            return findByUsername(identifier);
        }
        List<User> matches = userMapper.selectList(wrapper);
        if (matches.size() > 1) {
            throw new BusinessException(HttpStatus.CONFLICT, "USER_IDENTIFIER_AMBIGUOUS",
                    "用户标识同时匹配不同用户名和 ID，请通过 identifierType 指定 username 或 id");
        }
        return matches.isEmpty() ? null : matches.getFirst();
    }
}
