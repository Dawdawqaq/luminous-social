package com.luminous.api.storage;

import com.luminous.api.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.model.*;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

/** S3 仅使用内部地址和服务端凭据；浏览器地址指向受控文件接口，桶保持私有。 */
public class MinioStorageProvider implements StorageProvider {
    private final S3Client client;
    private final String bucket;
    private final String publicOrigin;
    public MinioStorageProvider(String endpoint, String accessKey, String secretKey, String bucket, String publicOrigin) {
        this(S3Client.builder().endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.US_EAST_1).serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .overrideConfiguration(ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(30))
                        .apiCallAttemptTimeout(Duration.ofSeconds(20)).build()).build(), bucket, publicOrigin);
        try { client.headBucket(HeadBucketRequest.builder().bucket(bucket).build()); }
        catch (S3Exception ex) {
            if (ex.statusCode() != 404) { throw ex; }
            try { client.createBucket(CreateBucketRequest.builder().bucket(bucket).build()); }
            catch (S3Exception race) { if (race.statusCode() != 409) { throw race; } }
        }
    }
    public MinioStorageProvider(S3Client client, String bucket, String publicOrigin) {
        this.client = client; this.bucket = bucket; this.publicOrigin = FileAccessTokens.normalizeOrigin(publicOrigin);
    }
    @Override public PlannedFile plan(MultipartFile file) { return FileAccessTokens.plan(file, publicOrigin, type()); }
    @Override public void store(PlannedFile planned, MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(FileAccessTokens.requireKey(planned.key()))
                    .contentType(planned.contentType()).build(), RequestBody.fromInputStream(input, planned.size()));
        } catch (Exception ex) { throw new IllegalStateException("对象存储上传失败，保留补偿记录", ex); }
    }
    @Override public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(FileAccessTokens.requireKey(key)).build());
    }
    @Override public InputStream read(String key) {
        try { return client.getObject(GetObjectRequest.builder().bucket(bucket).key(FileAccessTokens.requireKey(key)).build()); }
        catch (NoSuchKeyException ex) { throw new BusinessException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "文件不存在"); }
        catch (S3Exception | software.amazon.awssdk.core.exception.SdkClientException ex) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "FILE_READ_FAILED", "文件暂不可读");
        }
    }
    @Override public String type() { return "minio"; }
    @Override public void close() { client.close(); }
}
