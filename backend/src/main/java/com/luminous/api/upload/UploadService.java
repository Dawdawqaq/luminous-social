package com.luminous.api.upload;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.luminous.api.common.BusinessException;
import com.luminous.api.security.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

@Service
public class UploadService {
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final UploadedFileMapper uploadedFileMapper;
    private final Path uploadDir;
    private final String publicBaseUrl;

    public UploadService(
            UploadedFileMapper uploadedFileMapper,
            @Value("${app.upload.dir}") String uploadDir,
            @Value("${app.public-base-url}") String publicBaseUrl
    ) {
        this.uploadedFileMapper = uploadedFileMapper;
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl;
    }

    public UploadDtos.UploadResponse uploadImage(MultipartFile file, CurrentUser currentUser) {
        if (file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "上传文件不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE_TYPE", "仅支持 jpg、png、webp、gif 图片");
        }
        try {
            Files.createDirectories(uploadDir);
            String originalName = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
            String extension = extensionOf(originalName, contentType);
            String storedName = IdWorker.getIdStr() + extension;
            Path target = uploadDir.resolve(storedName).normalize();
            if (!target.startsWith(uploadDir)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "BAD_FILE_NAME", "文件名不合法");
            }
            file.transferTo(target);
            String url = publicBaseUrl + "/uploads/" + storedName;
            UploadedFile uploadedFile = new UploadedFile();
            uploadedFile.setId(IdWorker.getId());
            uploadedFile.setUserId(currentUser.id());
            uploadedFile.setOriginalName(originalName);
            uploadedFile.setStoredName(storedName);
            uploadedFile.setUrl(url);
            uploadedFile.setContentType(contentType);
            uploadedFile.setSizeBytes(file.getSize());
            uploadedFileMapper.insert(uploadedFile);
            return new UploadDtos.UploadResponse(String.valueOf(uploadedFile.getId()), url, originalName, file.getSize());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD_FAILED", "图片上传失败");
        }
    }

    private String extensionOf(String filename, String contentType) {
        int dot = filename.lastIndexOf('.');
        if (dot >= 0 && dot < filename.length() - 1) {
            return filename.substring(dot).toLowerCase(Locale.ROOT);
        }
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
    }
}
