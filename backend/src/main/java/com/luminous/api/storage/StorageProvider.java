package com.luminous.api.storage;

import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;

/** 文件先规划再写入，使上传前就能持久化准确的补偿对象；删除失败必须向上报告。 */
public interface StorageProvider extends AutoCloseable {
    record PlannedFile(String key, String url, String contentType, long size, String storageType, String readTokenHash) {}
    PlannedFile plan(MultipartFile file);
    void store(PlannedFile planned, MultipartFile file);
    void delete(String objectKey);
    InputStream read(String objectKey);
    String type();
    default String store(MultipartFile file) { PlannedFile planned = plan(file); store(planned, file); return planned.url(); }
    @Override default void close() {}
}
