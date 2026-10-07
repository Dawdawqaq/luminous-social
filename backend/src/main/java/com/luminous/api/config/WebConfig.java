package com.luminous.api.config;

import com.luminous.api.security.IdempotentInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String uploadDir;
    private final IdempotentInterceptor idempotentInterceptor;

    public WebConfig(@Value("${app.upload.dir}") String uploadDir,
                     IdempotentInterceptor idempotentInterceptor) {
        this.uploadDir = uploadDir;
        this.idempotentInterceptor = idempotentInterceptor;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(location + "/").resourceChain(true)
                .addResolver(new org.springframework.web.servlet.resource.PathResourceResolver() {
                    @Override protected org.springframework.core.io.Resource getResource(String path, org.springframework.core.io.Resource root) throws java.io.IOException {
                        // 仅保留历史根目录图片，禁止静态入口读取新的 private 子目录。
                        if (!path.matches("[0-9]{1,19}\\.(jpg|png|webp|gif)")) { return null; }
                        return super.getResource(path, root);
                    }
                });
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(idempotentInterceptor)
                .addPathPatterns("/api/**");
    }
}
