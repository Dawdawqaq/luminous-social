package com.luminous.api.upload;

import com.luminous.api.common.SnowflakeIdGenerator;
import com.luminous.api.common.BusinessException;
import com.luminous.api.security.CurrentUser;
import com.luminous.api.storage.StorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadService {
    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    private final UploadedFileMapper uploadedFileMapper;
    private final StorageProvider storageProvider;
    private final String publicBaseUrl;

    public UploadService(
            UploadedFileMapper uploadedFileMapper,
            StorageProvider storageProvider,
            @Value("${app.public-base-url}") String publicBaseUrl
    ) {
        this.uploadedFileMapper = uploadedFileMapper;
        this.storageProvider = storageProvider;
        this.publicBaseUrl = publicBaseUrl;
    }

    public UploadDtos.UploadResponse uploadImage(MultipartFile file, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "您需要先登录才能上传文件");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "上传文件不能为空");
        }

        try {
            String originalName = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
            String storedPathOrUrl = storageProvider.store(file);
            String fullUrl = storedPathOrUrl.startsWith("http://") || storedPathOrUrl.startsWith("https://")
                    ? storedPathOrUrl
                    : publicBaseUrl + storedPathOrUrl;

            String storedName = storedPathOrUrl.contains("/") 
                    ? storedPathOrUrl.substring(storedPathOrUrl.lastIndexOf('/') + 1)
                    : storedPathOrUrl;

            UploadedFile uploadedFile = new UploadedFile();
            uploadedFile.setId(SnowflakeIdGenerator.getNextId());
            uploadedFile.setUserId(currentUser.id());
            uploadedFile.setOriginalName(originalName);
            uploadedFile.setStoredName(storedName);
            uploadedFile.setUrl(fullUrl);
            uploadedFile.setContentType(file.getContentType());
            uploadedFile.setSizeBytes(file.getSize());
            uploadedFileMapper.insert(uploadedFile);

            log.info("User {} uploaded file: {} -> {}", currentUser.username(), originalName, fullUrl);
            return new UploadDtos.UploadResponse(String.valueOf(uploadedFile.getId()), fullUrl, originalName, file.getSize());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to process file upload for user: {}", currentUser.username(), ex);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD_FAILED", "图片上传失败");
        }
    }
}
