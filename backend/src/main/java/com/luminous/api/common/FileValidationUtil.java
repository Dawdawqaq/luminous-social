package com.luminous.api.common;

import org.apache.tika.Tika;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

public final class FileValidationUtil {
    private static final Tika TIKA = new Tika();

    private static final Map<String, String> MIME_TO_EXTENSION = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif"
    );

    private static final Set<String> ALLOWED_MIME_TYPES = MIME_TO_EXTENSION.keySet();

    private FileValidationUtil() {
    }

    /**
     * 根据真实文件头魔数探测 MIME 类型，杜绝扩展名伪造攻击
     */
    public static String detectMimeType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "上传文件不能为空");
        }
        try (InputStream is = file.getInputStream()) {
            String detectedType = TIKA.detect(is);
            if (detectedType == null || !ALLOWED_MIME_TYPES.contains(detectedType.toLowerCase())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE",
                        "仅支持上传 JPG、PNG、WEBP 或 GIF 格式的图片文件");
            }
            return detectedType.toLowerCase();
        } catch (IOException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_READ_ERROR", "读取上传文件失败");
        }
    }

    public static String getExtensionForMimeType(String mimeType) {
        String ext = MIME_TO_EXTENSION.get(mimeType);
        if (ext == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_TYPE", "不支持的文件格式");
        }
        return ext;
    }
}
