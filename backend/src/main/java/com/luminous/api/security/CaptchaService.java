package com.luminous.api.security;

import com.luminous.api.common.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 服务端生成随机拼图，凭证绑定会话及用途；认证凭证只授权一个操作，允许原操作失败后重试。 */
@Service
public class CaptchaService {
    public record Challenge(String challengeId, String image, String piece, int width, int height,
                            int pieceSize, int pieceY, int expiresIn) {}
    public record Verification(String proof, int expiresIn) {}
    private static final int WIDTH = 280, HEIGHT = 120, SIZE = 38, TTL = 120;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> VERIFY = new DefaultRedisScript<>(
            "local v=redis.call('GET',KEYS[1]); if not v then return 0 end; redis.call('DEL',KEYS[1]); "
            + "local x,p=string.match(v,'^(%d+)|([a-z]+)$'); "
            + "if p~=ARGV[2] or math.abs(tonumber(x)-tonumber(ARGV[1]))>4 then return 0 end; "
            + "redis.call('SET',KEYS[2],ARGV[2],'EX',ARGV[3]); return 1", Long.class);
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>(
            "if redis.call('GET',KEYS[1])~=ARGV[1] then return 0 end; redis.call('DEL',KEYS[1]); return 1", Long.class);
    private static final DefaultRedisScript<Long> BIND_OPERATION = new DefaultRedisScript<>(
            "local v=redis.call('GET',KEYS[1]); "
            + "if v==ARGV[2] then return 1 end; "
            + "if v~=ARGV[1] then return 0 end; "
            + "redis.call('SET',KEYS[1],ARGV[2],'EX',ARGV[3]); return 1", Long.class);
    private final StringRedisTemplate redis;
    public CaptchaService(StringRedisTemplate redis) { this.redis = redis; }

    public Challenge create(String session, String purpose) {
        requireId(session); requirePurpose(purpose);
        int x = 65 + RANDOM.nextInt(WIDTH - SIZE - 70), y = 25 + RANDOM.nextInt(HEIGHT - SIZE - 30);
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(225, 217, 202)); graphics.fillRect(0, 0, WIDTH, HEIGHT);
            for (int i = 0; i < 100; i++) {
                graphics.setColor(new Color(50 + RANDOM.nextInt(160), 50 + RANDOM.nextInt(160), 50 + RANDOM.nextInt(160)));
                graphics.fillOval(RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT), 15 + RANDOM.nextInt(55), 15 + RANDOM.nextInt(40));
            }
            BufferedImage piece = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
            Graphics2D tile = piece.createGraphics();
            try { tile.drawImage(image, 0, 0, SIZE, SIZE, x, y, x + SIZE, y + SIZE, null); }
            finally { tile.dispose(); }
            graphics.setColor(new Color(20, 20, 20, 180)); graphics.fillRect(x, y, SIZE, SIZE);
            String id = id(); redis.opsForValue().set(challengeKey(session, id), x + "|" + purpose, TTL, TimeUnit.SECONDS);
            return new Challenge(id, png(image), png(piece), WIDTH, HEIGHT, SIZE, y, TTL);
        } catch (Exception ex) { throw unavailable(); }
        finally { graphics.dispose(); }
    }
    public Verification verify(String session, String purpose, String challengeId, int offset, long durationMillis) {
        requireId(session); requirePurpose(purpose); requireId(challengeId);
        if (offset < 0 || offset > WIDTH - SIZE || durationMillis < 250 || durationMillis > 60000) { throw invalid(); }
        String proof = id();
        try {
            if (!Long.valueOf(1).equals(redis.execute(VERIFY, List.of(challengeKey(session, challengeId), proofKey(session, proof)),
                    String.valueOf(offset), purpose, String.valueOf(TTL)))) { throw invalid(); }
            return new Verification(proof, TTL);
        } catch (BusinessException ex) { throw ex; }
        catch (Exception ex) { throw unavailable(); }
    }
    public void consume(String session, String purpose, String proof) {
        requireId(session); requirePurpose(purpose); requireId(proof);
        try {
            if (!Long.valueOf(1).equals(redis.execute(CONSUME, List.of(proofKey(session, proof)), purpose))) { throw invalid(); }
        } catch (BusinessException ex) { throw ex; }
        catch (Exception ex) { throw unavailable(); }
    }
    public void consume(String session, String purpose, String proof, IdempotencyContext operation) {
        requireId(session); requirePurpose(purpose); requireId(proof);
        if (operation == null || operation.binding() == null || !operation.binding().matches("[a-f0-9]{64}")
                || operation.retentionSeconds() < 60) { throw invalid(); }
        try {
            // 首次消费保留操作绑定，数据库回滚后仅完全相同的请求可以重试；重试不延长有效期。
            String bound = purpose + "|" + operation.binding();
            if (!Long.valueOf(1).equals(redis.execute(BIND_OPERATION, List.of(proofKey(session, proof)),
                    purpose, bound, String.valueOf(operation.retentionSeconds())))) { throw invalid(); }
        } catch (BusinessException ex) { throw ex; }
        catch (Exception ex) { throw unavailable(); }
    }
    public static String challengeKey(String session, String id) { return "luminous:captcha:{" + session + "}:challenge:" + id; }
    public static String proofKey(String session, String id) { return "luminous:captcha:{" + session + "}:proof:" + id; }
    private static String id() { return UUID.randomUUID().toString().replace("-", ""); }
    private static void requireId(String value) { if (value == null || !value.matches("[a-f0-9]{32}")) { throw invalid(); } }
    private static void requirePurpose(String value) { if (!"login".equals(value) && !"register".equals(value)) { throw invalid(); } }
    private static BusinessException invalid() { return new BusinessException(HttpStatus.BAD_REQUEST, "CAPTCHA_INVALID", "请重新完成安全验证"); }
    private static BusinessException unavailable() { return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "CAPTCHA_UNAVAILABLE", "安全验证服务暂不可用"); }
    private static String png(BufferedImage image) throws java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); ImageIO.write(image, "png", output);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
    }
}
