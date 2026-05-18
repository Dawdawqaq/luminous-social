package com.luminous.api.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.luminous.api.common.BusinessException;
import com.luminous.api.post.PostMapper;
import com.luminous.api.security.CurrentUser;
import com.luminous.api.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class AuthService {
    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserFollowMapper userFollowMapper;

    public AuthService(UserMapper userMapper, PostMapper postMapper, PasswordEncoder passwordEncoder, JwtService jwtService, UserFollowMapper userFollowMapper) {
        this.userMapper = userMapper;
        this.postMapper = postMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userFollowMapper = userFollowMapper;
    }

    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        User existing = findByUsername(request.username());
        if (existing != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "用户名已被占用");
        }
        User user = new User();
        user.setId(IdWorker.getId());
        user.setUsername(request.username());
        user.setDisplayName(request.displayName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setAvatarUrl(null);
        user.setBannerUrl(null);
        user.setBio("这个人正在让 Luminous 变得更热闹。");
        user.setJoinDate(LocalDate.now().getYear() + "年" + LocalDate.now().getMonthValue() + "月");
        userMapper.insert(user);
        return new AuthDtos.AuthResponse(jwtService.createToken(user.getId(), user.getUsername()), toDto(user, null));
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        User user = findByUsername(request.username());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "用户名或密码错误");
        }
        return new AuthDtos.AuthResponse(jwtService.createToken(user.getId(), user.getUsername()), toDto(user, null));
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

    public AuthDtos.UserDto getUserProfile(String username, CurrentUser currentUser) {
        User user = findByUsername(username);
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
        long posts = postMapper.selectCount(new LambdaQueryWrapper<com.luminous.api.post.Post>()
                .eq(com.luminous.api.post.Post::getAuthorId, user.getId()));
                
        long followers = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowingId, user.getId()));
                
        long following = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, user.getId()));
                
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
                new AuthDtos.Stats(posts, followers, following),
                user.getJoinDate(),
                isFollowing
        );
    }

    public AuthDtos.UserDto follow(String username, CurrentUser currentUser) {
        if (currentUser == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        User target = findByUsername(username);
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
        }
        return toDto(target, currentUser);
    }
    
    public AuthDtos.UserDto unfollow(String username, CurrentUser currentUser) {
        if (currentUser == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        User target = findByUsername(username);
        if (target == null) throw new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        
        userFollowMapper.delete(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, currentUser.id())
                .eq(UserFollow::getFollowingId, target.getId()));
                
        return toDto(target, currentUser);
    }

    public java.util.List<AuthDtos.UserDto> searchUsers(String search, CurrentUser currentUser) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (search != null && !search.isBlank()) {
            wrapper.and(w -> {
                w.like(User::getUsername, search)
                 .or().like(User::getDisplayName, search);
                try {
                    Long idVal = Long.parseLong(search.trim());
                    w.or().eq(User::getId, idVal);
                } catch (NumberFormatException e) {
                    // Ignore
                }
            });
        }
        return userMapper.selectList(wrapper)
                .stream()
                .map(user -> toDto(user, currentUser))
                .toList();
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }
}
