package com.luminous.api.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageProvider {

    /**
     * 上传并持久化文件
     *
     * @param file 上传的多部分文件
     * @return 文件的可访问 URL 或路径
     */
    String store(MultipartFile file);

    /**
     * 删除指定文件
     *
     * @param fileUrl 文件的访问 URL 或相对路径
     */
    void delete(String fileUrl);
}
