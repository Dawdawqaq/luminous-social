package com.luminous.api.storage;

import com.luminous.api.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.*;

/** 新文件保存在静态资源不能访问的 private 子目录，仅通过带访问控制的文件接口读取。 */
public class LocalStorageProvider implements StorageProvider {
    private final Path directory;
    private final String publicOrigin;
    public LocalStorageProvider(String uploadDir, String publicOrigin) {
        directory = Path.of(uploadDir).toAbsolutePath().normalize().resolve("private");
        this.publicOrigin = FileAccessTokens.normalizeOrigin(publicOrigin);
        try { Files.createDirectories(directory); }
        catch (IOException ex) { throw new IllegalStateException("无法初始化私有文件目录", ex); }
    }
    public LocalStorageProvider(String uploadDir) { this(uploadDir, "http://localhost:8080"); }
    @Override public PlannedFile plan(MultipartFile file) { return FileAccessTokens.plan(file, publicOrigin, type()); }
    @Override public void store(PlannedFile planned, MultipartFile file) {
        try (InputStream input = file.getInputStream()) { Files.copy(input, path(planned.key()), StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException ex) { throw new IllegalStateException("本地文件写入失败", ex); }
    }
    @Override public void delete(String key) {
        try { Files.deleteIfExists(path(key)); }
        catch (IOException ex) { throw new IllegalStateException("本地文件补偿删除失败", ex); }
    }
    @Override public InputStream read(String key) {
        try { return Files.newInputStream(path(key)); }
        catch (NoSuchFileException ex) { throw new BusinessException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "文件不存在"); }
        catch (IOException ex) { throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "FILE_READ_FAILED", "文件暂不可读"); }
    }
    private Path path(String key) { return directory.resolve(FileAccessTokens.requireKey(key)); }
    @Override public String type() { return "local"; }
}
