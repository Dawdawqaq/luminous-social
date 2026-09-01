package com.luminous.api.config;

import com.luminous.api.storage.LocalStorageProvider;
import com.luminous.api.storage.MinioStorageProvider;
import com.luminous.api.storage.StorageProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "minio")
    @Primary
    public StorageProvider minioStorageProvider(
            @Value("${app.storage.minio.endpoint:http://localhost:9000}") String endpoint,
            @Value("${app.storage.minio.access-key:minioadmin}") String accessKey,
            @Value("${app.storage.minio.secret-key:minioadmin}") String secretKey,
            @Value("${app.storage.minio.bucket:luminous-uploads}") String bucket
    ) {
        return new MinioStorageProvider(endpoint, accessKey, secretKey, bucket);
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
    public StorageProvider localStorageProvider(
            @Value("${app.upload.dir:./uploads}") String uploadDir
    ) {
        return new LocalStorageProvider(uploadDir);
    }
}
