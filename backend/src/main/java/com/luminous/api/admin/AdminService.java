package com.luminous.api.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luminous.api.auth.AuthDtos;
import com.luminous.api.auth.AuthService;
import com.luminous.api.auth.User;
import com.luminous.api.auth.UserMapper;
import com.luminous.api.common.BusinessException;
import com.luminous.api.security.TokenBlacklistService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminService {

    private final UserMapper userMapper;
    private final AuthService authService;
    private final TokenBlacklistService tokenBlacklistService;

    public AdminService(UserMapper userMapper, AuthService authService, TokenBlacklistService tokenBlacklistService) {
        this.userMapper = userMapper;
        this.authService = authService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    /**
     * 封禁指定用户
     */
    @Transactional
    public AuthDtos.UserDto banUser(String username) {
        User user = requireByUsername(username);
        if ("ADMIN".equals(user.getRole())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "CANNOT_BAN_ADMIN", "不能封禁管理员账号");
        }
        user.setIsBanned(true);
        userMapper.updateById(user);
        tokenBlacklistService.banUser(user.getId());
        return authService.toDto(user, null);
    }

    /**
     * 解封指定用户
     */
    @Transactional
    public AuthDtos.UserDto unbanUser(String username) {
        User user = requireByUsername(username);
        user.setIsBanned(false);
        userMapper.updateById(user);
        tokenBlacklistService.unbanUser(user.getId());
        return authService.toDto(user, null);
    }

    /**
     * 查询全部用户列表（管理员专用）
     */
    public List<AdminDtos.AdminUserDto> listUsers() {
        return userMapper.selectList(new LambdaQueryWrapper<User>().orderByDesc(User::getCreatedAt))
                .stream()
                .map(u -> new AdminDtos.AdminUserDto(
                        String.valueOf(u.getId()),
                        u.getUsername(),
                        u.getDisplayName(),
                        u.getAvatarUrl(),
                        u.getRole() == null ? "USER" : u.getRole(),
                        Boolean.TRUE.equals(u.getIsBanned()),
                        u.getJoinDate()
                ))
                .toList();
    }

    private User requireByUsername(String username) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
        }
        return user;
    }
}
