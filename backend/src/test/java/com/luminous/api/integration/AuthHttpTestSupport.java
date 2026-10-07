package com.luminous.api.integration;

import com.luminous.api.security.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import java.util.UUID;

/** 测试夹具读取其测试 Redis 中的答案；生产接口没有答案或跳过验证的分支。 */
final class AuthHttpTestSupport {
    private AuthHttpTestSupport() {}
    static HttpHeaders proofHeaders(CaptchaService captcha,StringRedisTemplate redis,String purpose) {
        String session=UUID.randomUUID().toString().replace("-","");
        var challenge=captcha.create(session,purpose);
        String key=CaptchaService.challengeKey(session,challenge.challengeId());
        int x=Integer.parseInt(redis.opsForValue().get(key).split("\\|")[0]);
        String proof=captcha.verify(session,purpose,challenge.challengeId(),x,400).proof();
        HttpHeaders headers=new HttpHeaders();
        headers.set("X-Idempotency-Key",UUID.randomUUID().toString());
        headers.set("X-Captcha-Proof",proof);
        headers.set(HttpHeaders.COOKIE,CaptchaSessionIdentity.COOKIE+"="+session);
        return headers;
    }
}
