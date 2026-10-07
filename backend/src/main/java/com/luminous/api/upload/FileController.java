package com.luminous.api.upload;

import com.luminous.api.security.FileAccessAuthenticationFilter;
import com.luminous.api.storage.StorageProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 元数据和访问凭证已经由文件认证过滤器核对，浏览器不需要接触内部 S3 地址。 */
@RestController
public class FileController {
    private final StorageProvider storage;
    public FileController(StorageProvider storage) { this.storage = storage; }
    @GetMapping("/api/files/{key}")
    public ResponseEntity<InputStreamResource> read(@PathVariable String key, HttpServletRequest request) {
        UploadedFile file = (UploadedFile) request.getAttribute(FileAccessAuthenticationFilter.FILE_ATTRIBUTE);
        if (file == null) { throw new IllegalStateException("文件访问校验未完成"); }
        if (file.getStorageType() != null && !storage.type().equals(file.getStorageType())) {
            throw new com.luminous.api.common.BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_TYPE_CHANGED", "文件存储配置暂不匹配");
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.getContentType())).contentLength(file.getSizeBytes())
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").header("Referrer-Policy", "no-referrer")
                .body(new InputStreamResource(storage.read(key)));
    }
}
