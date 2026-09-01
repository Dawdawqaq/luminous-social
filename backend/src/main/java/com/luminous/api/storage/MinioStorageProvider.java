package com.luminous.api.storage;

import com.luminous.api.common.SnowflakeIdGenerator;
import com.luminous.api.common.BusinessException;
import com.luminous.api.common.FileValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.net.URI;

public class MinioStorageProvider implements StorageProvider {
    private static final Logger log = LoggerFactory.getLogger(MinioStorageProvider.class);

    private final S3Client s3Client;
    private final String bucketName;
    private final String publicEndpoint;

    public MinioStorageProvider(String endpoint, String accessKey, String secretKey, String bucket) {
        this.bucketName = bucket;
        this.publicEndpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.US_EAST_1)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
        ensureBucketExists();
    }

    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (NoSuchBucketException e) {
            try {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
                log.info("Created MinIO bucket: {}", bucketName);
            } catch (Exception ex) {
                log.error("Failed to auto-create MinIO bucket: {}", bucketName, ex);
            }
        } catch (Exception e) {
            log.warn("MinIO bucket check failed, will try upload on demand: {}", e.getMessage());
        }
    }

    @Override
    public String store(MultipartFile file) {
        String mimeType = FileValidationUtil.detectMimeType(file);
        String extension = FileValidationUtil.getExtensionForMimeType(mimeType);
        String objectKey = SnowflakeIdGenerator.getNextId() + extension;

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(mimeType)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("Uploaded file to MinIO: {} as {}", file.getOriginalFilename(), objectKey);
            return publicEndpoint + "/" + bucketName + "/" + objectKey;
        } catch (IOException e) {
            log.error("Failed to read file for MinIO upload", e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_UPLOAD_ERROR", "文件上传失败");
        } catch (Exception e) {
            log.error("MinIO object upload failed: {}", e.getMessage(), e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_MINIO_ERROR", "对象存储上传失败");
        }
    }

    @Override
    public void delete(String fileUrl) {
        if (fileUrl == null || !fileUrl.contains(bucketName + "/")) {
            return;
        }
        try {
            String objectKey = fileUrl.substring(fileUrl.indexOf(bucketName + "/") + bucketName.length() + 1);
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(objectKey).build());
            log.info("Deleted object from MinIO: {}", objectKey);
        } catch (Exception e) {
            log.warn("Failed to delete MinIO object: {}", fileUrl, e);
        }
    }
}
