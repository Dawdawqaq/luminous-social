package com.luminous.api.storage;

import com.luminous.api.common.FileValidationUtil;
import com.luminous.api.common.SnowflakeIdGenerator;
import org.springframework.web.multipart.MultipartFile;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/** 每个文件使用独立随机读取凭证，校验时比较摘要；知道对象名不等于获得读取权限。 */
public final class FileAccessTokens {
    private static final SecureRandom RANDOM = new SecureRandom();
    private FileAccessTokens() {}
    public static StorageProvider.PlannedFile plan(MultipartFile file, String origin, String type) {
        String mime = FileValidationUtil.detectMimeType(file);
        String key = SnowflakeIdGenerator.getNextId() + FileValidationUtil.getExtensionForMimeType(mime);
        byte[] secret = new byte[32]; RANDOM.nextBytes(secret); String token = HexFormat.of().formatHex(secret);
        return new StorageProvider.PlannedFile(key, normalizeOrigin(origin) + "/api/files/" + key + "?token=" + token,
                mime, file.getSize(), type, hash(token));
    }
    public static boolean matches(String token, String expectedHash) {
        if (token == null || expectedHash == null || !token.matches("[a-f0-9]{64}")) { return false; }
        return MessageDigest.isEqual(hash(token).getBytes(StandardCharsets.US_ASCII), expectedHash.getBytes(StandardCharsets.US_ASCII));
    }
    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    public static String requireKey(String key) {
        if (key == null || !key.matches("[0-9]{1,19}\\.(jpg|png|webp|gif)")) { throw new IllegalArgumentException("对象标识无效"); }
        return key;
    }
    public static String normalizeOrigin(String value) {
        URI uri = URI.create(value);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || (!uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("文件公开地址必须是有效的 HTTP 或 HTTPS 服务地址");
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
