package com.luminous.api.config;

import com.luminous.api.security.JwtAuthenticationFilter;
import com.luminous.api.security.RateLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final com.luminous.api.security.IdempotencyFilter idempotencyFilter;
    private final com.luminous.api.security.FileAccessAuthenticationFilter fileAccessFilter;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RateLimitFilter rateLimitFilter,
            com.luminous.api.security.IdempotencyFilter idempotencyFilter,
            com.luminous.api.security.FileAccessAuthenticationFilter fileAccessFilter,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.idempotencyFilter = idempotencyFilter;
        this.fileAccessFilter = fileAccessFilter;
        this.objectMapper = objectMapper;
    }

    // 这些过滤器仅由 Spring Security 按认证顺序执行，禁止容器再次自动注册。
    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration() {
        return disabled(jwtAuthenticationFilter);
    }
    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<RateLimitFilter> rateFilterRegistration() {
        return disabled(rateLimitFilter);
    }
    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<com.luminous.api.security.IdempotencyFilter> idempotencyFilterRegistration() {
        return disabled(idempotencyFilter);
    }
    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<com.luminous.api.security.FileAccessAuthenticationFilter> fileFilterRegistration() {
        return disabled(fileAccessFilter);
    }
    private <T extends jakarta.servlet.Filter> org.springframework.boot.web.servlet.FilterRegistrationBean<T> disabled(T filter) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
                            response.getWriter().write(objectMapper.writeValueAsString(
                                    com.luminous.api.common.ApiResponse.fail("UNAUTHORIZED", "未登录或 Token 无效，请重新登录")
                            ));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
                            response.getWriter().write(objectMapper.writeValueAsString(
                                    com.luminous.api.common.ApiResponse.fail("FORBIDDEN", "无权限访问此资源")
                            ));
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(new com.luminous.api.security.LumiChatAsyncMatcher()).permitAll()
                        .requestMatchers("/error", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/uploads/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/captcha/verify").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/captcha").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/search", "/api/search/**", "/api/posts/**", "/api/communities/**", "/api/users/**", "/api/checkin/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/files/**").hasAnyAuthority("FILE_READER", "USER", "ADMIN")
                        .requestMatchers("/api/admin/**").hasAuthority("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(fileAccessFilter, JwtAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(idempotencyFilter, org.springframework.security.web.access.intercept.AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
