package com.luminous.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.common.*;
import com.luminous.api.upload.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** 文件路由保留认证要求，匿名访问必须先验证该文件的独立读取凭证。 */
@Component
public class FileAccessAuthenticationFilter extends OncePerRequestFilter {
    public static final String FILE_ATTRIBUTE = "AUTHORIZED_UPLOAD_FILE";
    private final UploadService uploads;
    private final ObjectMapper mapper;
    public FileAccessAuthenticationFilter(UploadService uploads, ObjectMapper mapper) { this.uploads = uploads; this.mapper = mapper; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) { return !"GET".equals(request.getMethod()) || !request.getRequestURI().startsWith("/api/files/"); }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        CurrentUser user = authentication != null && authentication.getPrincipal() instanceof CurrentUser current ? current : null;
        try {
            UploadedFile file = uploads.authorizeRead(request.getRequestURI().substring("/api/files/".length()), request.getParameter("token"), user);
            request.setAttribute(FILE_ATTRIBUTE, file);
            if (user == null) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "file:" + file.getId(), null, AuthorityUtils.createAuthorityList("FILE_READER"))); }
            chain.doFilter(request, response);
        } catch (BusinessException ex) {
            response.setStatus(ex.getStatus().value()); response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(mapper.writeValueAsString(ApiResponse.fail(ex.getCode(), ex.getMessage())));
        }
    }
}
