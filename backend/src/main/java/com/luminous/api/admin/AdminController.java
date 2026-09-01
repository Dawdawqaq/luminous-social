package com.luminous.api.admin;

import com.luminous.api.auth.AuthDtos;
import com.luminous.api.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin", description = "系统管理员专用接口，需要 ADMIN 角色")
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @Operation(summary = "获取全部用户列表（管理员）")
    @GetMapping("/users")
    public ApiResponse<List<AdminDtos.AdminUserDto>> listUsers() {
        return ApiResponse.ok(adminService.listUsers());
    }

    @Operation(summary = "封禁指定用户")
    @PostMapping("/users/{username}/ban")
    public ApiResponse<AuthDtos.UserDto> banUser(@PathVariable String username) {
        return ApiResponse.ok(adminService.banUser(username));
    }

    @Operation(summary = "解封指定用户")
    @DeleteMapping("/users/{username}/ban")
    public ApiResponse<AuthDtos.UserDto> unbanUser(@PathVariable String username) {
        return ApiResponse.ok(adminService.unbanUser(username));
    }
}
