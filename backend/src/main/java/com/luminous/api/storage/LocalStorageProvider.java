package com.luminous.api.storage;

import com.luminous.api.common.SnowflakeIdGenerator;
import com.luminous.api.common.BusinessException;
import com.luminous.api.common.FileValidationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class LocalStorageProvider implements StorageProvider {
    private static final Logger log = LoggerFactory.getLogger(LocalStorageProvider.class);

    private final Path uploadDirectory;

    public LocalStorageProvider(String uploadDir) {
        this.uploadDirectory = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDirectory);
        } catch (IOException e) {
            log.error("Failed to create upload directory: {}", this.uploadDirectory, e);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_INIT_ERROR", "无法初始化文件存储目录");
        }
    }

    @Override
    public String store(MultipartFile file) {
        String mimeType = FileValidationUtil.detectMimeType(file);
        String extension = FileValidationUtil.getExtensionForMimeType(mimeType);
        String storedFileName = SnowflakeIdGenerator.getNextId() + extension;

        try {
            Path targetLocation = this.uploadDirectory.resolve(storedFileName).normalize();
            if (!targetLocation.startsWith(this.uploadDirectory)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PATH", "非法的文件路径");
            }
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            log.info("Stored file locally: {} as {}", file.getOriginalFilename(), storedFileName);
            return "/uploads/" + storedFileName;
        } catch (IOException ex) {
            log.error("Failed to store file: {}", file.getOriginalFilename(), ex);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_WRITE_ERROR", "文件保存失败");
        }
    }

    @Override
    public void delete(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith("/uploads/")) {
            return;
        }
        String fileName = fileUrl.substring("/uploads/".length());
        Path filePath = this.uploadDirectory.resolve(fileName).normalize();
        if (filePath.startsWith(this.uploadDirectory)) {
            try {
                Files.deleteIfExists(filePath);
                log.info("Deleted local file: {}", filePath);
            } catch (IOException e) {
                log.warn("Failed to delete local file: {}", filePath, e);
            }
        }
    }
}
