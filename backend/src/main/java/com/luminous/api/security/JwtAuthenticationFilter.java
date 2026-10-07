package com.luminous.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.common.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, TokenBlacklistService tokenBlacklistService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                CurrentUser currentUser = jwtService.parse(header.substring(7));
                
                // 数据库封禁状态是认证依据，Redis 缓存故障不能放行封禁账号
                if (tokenBlacklistService.isUserBanned(currentUser.id())) {
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
                    response.getWriter().write(objectMapper.writeValueAsString(
                            ApiResponse.fail("USER_BANNED", "该账号已被封禁，无法继续操作")
                    ));
                    return;
                }

                String authority = "ADMIN".equals(currentUser.role()) ? "ADMIN" : "USER";
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        currentUser,
                        null,
                        AuthorityUtils.createAuthorityList(authority)
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (com.luminous.api.common.BusinessException ex) {
                SecurityContextHolder.clearContext(); response.setStatus(ex.getStatus().value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
                response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(ex.getCode(), ex.getMessage())));
                return;
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
