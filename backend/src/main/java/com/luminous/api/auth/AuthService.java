package com.luminous.api.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luminous.api.common.SnowflakeIdGenerator;
import com.luminous.api.common.BusinessException;
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

    public AuthService(
            UserMapper userMapper,
            PostMapper postMapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            UserFollowMapper userFollowMapper,
            org.springframework.context.ApplicationEventPublisher eventPublisher,
            UserStatsCacheService userStatsCacheService,
            @org.springframework.context.annotation.Lazy com.luminous.api.feed.FeedService feedService,
            GrowthQueryService growthQueries
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
        User user = findByUsernameOrId(identifier);
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        }
        return toDto(user, currentUser);
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
        if (currentUser == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        User target = findByUsernameOrId(identifier);
        if (target == null) throw new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        if (target.getId().equals(currentUser.id())) throw new BusinessException(HttpStatus.BAD_REQUEST, "CANNOT_FOLLOW_SELF", "不能关注自己");
        
        boolean exists = userFollowMapper.exists(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, currentUser.id())
                .eq(UserFollow::getFollowingId, target.getId()));
                
        if (!exists) {
            UserFollow uf = new UserFollow();
            uf.setFollowerId(currentUser.id());
            uf.setFollowingId(target.getId());
            userFollowMapper.insert(uf);
            
            userStatsCacheService.incrementFollowerCount(target.getId());
            userStatsCacheService.incrementFollowingCount(currentUser.id());
            com.luminous.api.common.AfterCommit.run(() -> feedService.invalidateInbox(currentUser.id()));

            // 异步发布关注通知事件给被关注者
            eventPublisher.publishEvent(new com.luminous.api.notification.NotificationEvent(
                    currentUser.id(),
                    target.getId(),
                    "FOLLOW_USER",
                    "USER",
                    currentUser.id(),
                    currentUser.username() + " 关注了你"
            ));
        }
        return toDto(target, currentUser);
    }
    
    @Transactional
    public AuthDtos.UserDto unfollow(String identifier, CurrentUser currentUser) {
        if (currentUser == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        User target = findByUsernameOrId(identifier);
        if (target == null) throw new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        
        int removed = userFollowMapper.delete(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, currentUser.id())
                .eq(UserFollow::getFollowingId, target.getId()));
                
        if (removed > 0) {
            userStatsCacheService.decrementFollowerCount(target.getId());
            userStatsCacheService.decrementFollowingCount(currentUser.id());
            com.luminous.api.common.AfterCommit.run(() -> feedService.invalidateInbox(currentUser.id()));
        }
                
        return toDto(target, currentUser);
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

    private User findByUsernameOrId(String identifier) {
        if (identifier == null || identifier.isBlank()) return null;
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>().eq(User::getUsername, identifier);
        try {
            Long idVal = Long.parseLong(identifier.trim());
            wrapper.or().eq(User::getId, idVal);
        } catch (NumberFormatException ignored) {}
        return userMapper.selectOne(wrapper);
    }
}
