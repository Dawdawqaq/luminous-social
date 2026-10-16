package com.luminous.api.upload;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luminous.api.common.*;
import com.luminous.api.security.CurrentUser;
import com.luminous.api.storage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadService {
    private static final Logger log = LoggerFactory.getLogger(UploadService.class);
    private final UploadedFileMapper files;
    private final StorageProvider storage;
    private final UploadOperationService operations;
    public UploadService(UploadedFileMapper files, StorageProvider storage, UploadOperationService operations) {
        this.files = files; this.storage = storage; this.operations = operations;
    }

    /** 评论仅允许引用当前用户通过上传接口提交的图片。 */
    public String requireOwnedImage(String url, CurrentUser user) {
        return requireOwnedImage(url, user, "INVALID_COMMENT_IMAGE", "请重新上传自己的图片后发表评论");
    }

    public String requireOwnedPostImage(String url, CurrentUser user) {
        return requireOwnedImage(url, user, "INVALID_POST_IMAGE", "请重新上传自己的图片后发布帖子");
    }

    private String requireOwnedImage(String url, CurrentUser user, String code, String message) {
        UploadedFile file = files.selectOne(new LambdaQueryWrapper<UploadedFile>().eq(UploadedFile::getUrl, url));
        if (file == null || !java.util.Objects.equals(file.getUserId(), user.id())
                || file.getContentType() == null
                || !java.util.Set.of("image/jpeg", "image/png", "image/gif", "image/webp").contains(file.getContentType())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, code, message);
        }
        return file.getUrl();
    }
    @Transactional(timeout = 60)
    public UploadDtos.UploadResponse uploadImage(MultipartFile file, CurrentUser user) {
        if (user == null) { throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录"); }
        StorageProvider.PlannedFile planned = storage.plan(file);
        long operation = operations.prepare(planned);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try { operations.cleanup(operation); }
                    catch (Exception ex) { log.warn("即时文件补偿失败，持久意图等待重试，id={}", operation, ex); }
                }
            }
        });
        try {
            storage.store(planned, file);
            UploadedFile record = new UploadedFile(); record.setId(SnowflakeIdGenerator.getNextId()); record.setUserId(user.id());
            record.setOriginalName(file.getOriginalFilename() == null ? "image" : file.getOriginalFilename());
            record.setStoredName(planned.key()); record.setUrl(planned.url()); record.setContentType(planned.contentType());
            record.setSizeBytes(planned.size()); record.setStorageType(planned.storageType()); record.setReadTokenHash(planned.readTokenHash());
            if (files.insert(record) != 1) { throw new IllegalStateException("文件记录未写入"); }
            operations.committed(operation);
            return new UploadDtos.UploadResponse(String.valueOf(record.getId()), planned.url(), record.getOriginalName(), planned.size());
        } catch (BusinessException ex) { throw ex; }
        catch (Exception ex) {
            log.warn("上传事务失败，补偿意图编号={}", operation, ex);
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD_FAILED", "图片上传失败，未提交的文件将被清理");
        }
    }
    public UploadedFile authorizeRead(String key, String token, CurrentUser user) {
        try { FileAccessTokens.requireKey(key); }
        catch (IllegalArgumentException ex) { throw new BusinessException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "文件不存在"); }
        UploadedFile file = files.selectOne(new LambdaQueryWrapper<UploadedFile>().eq(UploadedFile::getStoredName, key));
        if (file == null) { throw new BusinessException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "文件不存在"); }
        boolean owner = user != null && (file.getUserId().equals(user.id()) || "ADMIN".equals(user.role()));
        if (!owner && !FileAccessTokens.matches(token, file.getReadTokenHash())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FILE_FORBIDDEN", "文件访问凭证无效");
        }
        return file;
    }
}
