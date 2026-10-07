package com.luminous.api.integration;

import com.luminous.api.auth.*;
import com.luminous.api.common.*;
import com.luminous.api.security.*;
import com.luminous.api.storage.*;
import com.luminous.api.upload.*;
import com.luminous.api.post.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.http.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.redis.core.ScanOptions;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 实际 HTTP、数据库事务及独立 Redis 验证凭证、限流、结果重放和文件故障恢复。 */
class SecurityStorageIntegrationTest extends AbstractVoteIntegrationTest {
    @Autowired CaptchaService captcha;
    @Autowired SlidingWindowRateLimiter limiter;
    @Autowired AdminBootstrapService bootstrap;
    @Autowired UploadService uploads;
    @Autowired UploadOperationService operations;
    @Autowired UploadRecoveryScheduler recovery;
    @Autowired TokenBlacklistService blacklist;
    @MockitoSpyBean PostMapper posts;
    @MockitoSpyBean UserMapper users;
    @MockitoSpyBean UploadedFileMapper uploadedFiles;
    @MockitoSpyBean StorageProvider storage;
    final List<String> sessions = new ArrayList<>(), operationKeys = new ArrayList<>(), fileKeys = new ArrayList<>();
    record Proof(String session, String proof) {}

    @BeforeEach void capturePlannedFiles() {
        doAnswer(call -> {
            StorageProvider.PlannedFile planned = (StorageProvider.PlannedFile) call.callRealMethod(); fileKeys.add(planned.key()); return planned;
        }).when(storage).plan(any());
    }
    HttpHeaders headers(String token, String operationKey) {
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) { headers.setBearerAuth(token); }
        if (operationKey != null) { headers.set("X-Idempotency-Key", operationKey); operationKeys.add(operationKey); }
        return headers;
    }
    Proof proof(String purpose) {
        String session = UUID.randomUUID().toString().replace("-", ""); sessions.add(session);
        var challenge = captcha.create(session, purpose);
        String stored = redisTemplate.opsForValue().get(CaptchaService.challengeKey(session, challenge.challengeId()));
        int offset = Integer.parseInt(stored.split("\\|")[0]);
        return new Proof(session, captcha.verify(session, purpose, challenge.challengeId(), offset, 400).proof());
    }
    HttpHeaders proofHeaders(Proof proof, String key) {
        HttpHeaders headers = headers(null, key);
        headers.set(HttpHeaders.COOKIE, CaptchaSessionIdentity.COOKIE + "=" + proof.session());
        headers.set("X-Captcha-Proof", proof.proof()); return headers;
    }
    PostDtos.CreatePostRequest postBody(String title) { return new PostDtos.CreatePostRequest(communitySlug, "[E2E_TEST] " + title, "[E2E_TEST] 内容", null, null, true); }
    ResponseEntity<String> create(HttpHeaders headers, Object body) { return restTemplate.exchange("/api/posts", HttpMethod.POST, new HttpEntity<>(body, headers), String.class); }
    long createdId(ResponseEntity<String> result) throws Exception {
        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getBody()).path("data").path("id").asLong();
        if (!postIds.contains(id)) { postIds.add(id); } return id;
    }

    @Test void loginRequiresUnexpiredUnusedPurposeBoundProofAndReplaysSuccessfulResult() {
        var body = new AuthDtos.LoginRequest(author.username(), "Password123!");
        assertThat(restTemplate.postForEntity("/api/auth/login", body, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Proof wrongPurpose = proof("register");
        assertThat(restTemplate.exchange("/api/auth/login", HttpMethod.POST, new HttpEntity<>(body, proofHeaders(wrongPurpose, UUID.randomUUID().toString())), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Proof expired = proof("login"); redisTemplate.delete(CaptchaService.proofKey(expired.session(), expired.proof()));
        assertThat(restTemplate.exchange("/api/auth/login", HttpMethod.POST, new HttpEntity<>(body, proofHeaders(expired, UUID.randomUUID().toString())), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Proof valid = proof("login"); HttpHeaders headers = proofHeaders(valid, UUID.randomUUID().toString());
        var first = restTemplate.exchange("/api/auth/login", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        var replay = restTemplate.exchange("/api/auth/login", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        assertThat(replay.getBody()).isEqualTo(first.getBody()); assertThat(replay.getHeaders().getFirst("Idempotency-Replayed")).isEqualTo("true");
        headers.set("X-Idempotency-Key", UUID.randomUUID().toString()); operationKeys.add(headers.getFirst("X-Idempotency-Key"));
        assertThat(restTemplate.exchange("/api/auth/login", HttpMethod.POST, new HttpEntity<>(body, headers), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    @Test void registrationNeedsProofAndConcurrentRetriesCreateOneOrdinaryUser() throws Exception {
        String username="E2E_TEST_new_"+UUID.randomUUID().toString().substring(0,8);
        var body=new AuthDtos.RegisterRequest(username,"[E2E_TEST] 并发注册","Password123!");
        assertThat(restTemplate.postForEntity("/api/auth/register",body,String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        HttpHeaders headers=proofHeaders(proof("register"),UUID.randomUUID().toString());
        ExecutorService pool=Executors.newFixedThreadPool(6);
        try {
            List<Future<ResponseEntity<String>>> results=new ArrayList<>();
            for(int i=0;i<6;i++) { results.add(pool.submit(() -> restTemplate.exchange("/api/auth/register",HttpMethod.POST,new HttpEntity<>(body,headers),String.class))); }
            String response=null;
            for(var result:results) {
                var actual=result.get(10,TimeUnit.SECONDS); assertThat(actual.getStatusCode()).isEqualTo(HttpStatus.OK);
                if(response==null) {
                    response=actual.getBody();
                    var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).path("data").path("user");
                    userIds.add(json.path("id").asLong()); assertThat(json.path("role").asText()).isEqualTo("USER");
                } else { assertThat(actual.getBody()).isEqualTo(response); }
            }
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE username=?",Long.class,username)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void missingProofWithValidSessionAndOperationKeyStillReturnsClientError() {
        Proof valid=proof("login"); HttpHeaders headers=proofHeaders(valid,UUID.randomUUID().toString()); headers.remove("X-Captcha-Proof");
        var body=new AuthDtos.LoginRequest(author.username(),"Password123!");
        assertThat(restTemplate.exchange("/api/auth/login",HttpMethod.POST,new HttpEntity<>(body,headers),String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    @Test void registrationRollbackRetainsProofOnlyForTheOriginalOperationAndParameters() throws Exception {
        String username = "E2E_TEST_retry_" + UUID.randomUUID().toString().substring(0, 8);
        var body = new AuthDtos.RegisterRequest(username, "[E2E_TEST] 注册恢复", "Password123!");
        Proof proof = proof("register");
        String key = UUID.randomUUID().toString();
        HttpHeaders original = proofHeaders(proof, key);
        doThrow(new IllegalStateException("[E2E_TEST] 注册写库暂失败")).when(users).insert(any(User.class));
        var failed = restTemplate.exchange("/api/auth/register", HttpMethod.POST, new HttpEntity<>(body, original), String.class);
        assertThat(failed.getStatusCode().is5xxServerError()).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM idempotent_requests WHERE operation_key=?", Long.class, key)).isZero();
        assertThat(redisTemplate.getExpire(CaptchaService.proofKey(proof.session(), proof.proof()))).isGreaterThan(120);
        reset(users);
        // 数据库回滚不能使凭证重新授权另一操作，也不能允许修改原请求参数。
        var changed = new AuthDtos.RegisterRequest(username, "[E2E_TEST] 修改参数", "Different123!");
        assertThat(restTemplate.exchange("/api/auth/register", HttpMethod.POST, new HttpEntity<>(changed, original), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(restTemplate.exchange("/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(body, proofHeaders(proof, UUID.randomUUID().toString())), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var retry = restTemplate.exchange("/api/auth/register", HttpMethod.POST, new HttpEntity<>(body, original), String.class);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        userIds.add(new com.fasterxml.jackson.databind.ObjectMapper().readTree(retry.getBody()).path("data").path("user").path("id").asLong());
        assertThat(restTemplate.exchange("/api/auth/register", HttpMethod.POST, new HttpEntity<>(body, original), String.class).getBody())
                .isEqualTo(retry.getBody());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE username=?", Long.class, username)).isEqualTo(1);
    }
    @Test void loginDatabaseFailureCanRetryTheOriginalProofWithoutChangingCredentials() {
        var body = new AuthDtos.LoginRequest(author.username(), "Password123!");
        Proof proof = proof("login"); HttpHeaders original = proofHeaders(proof, UUID.randomUUID().toString());
        doThrow(new IllegalStateException("[E2E_TEST] 登录读取暂失败")).when(users).selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        assertThat(restTemplate.exchange("/api/auth/login", HttpMethod.POST, new HttpEntity<>(body, original), String.class)
                .getStatusCode().is5xxServerError()).isTrue();
        reset(users);
        var changed = new AuthDtos.LoginRequest(author.username(), "Different123!");
        assertThat(restTemplate.exchange("/api/auth/login", HttpMethod.POST, new HttpEntity<>(changed, original), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(restTemplate.exchange("/api/auth/login", HttpMethod.POST, new HttpEntity<>(body, original), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test void proofCannotMoveToDifferentBrowserAndConcurrentConsumptionSucceedsOnce() throws Exception {
        Proof original = proof("login"); Proof other = proof("login");
        assertThatThrownBy(() -> captcha.consume(other.session(), "login", original.proof())).isInstanceOf(BusinessException.class);
        ExecutorService pool = Executors.newFixedThreadPool(8); List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) { results.add(pool.submit(() -> { try { captcha.consume(original.session(), "login", original.proof()); return true; } catch (BusinessException ex) { return false; } })); }
            int successes = 0; for (var result : results) { if (result.get(3, TimeUnit.SECONDS)) { successes++; } }
            assertThat(successes).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void challengeApiReturnsImagesAndConsumesChallengeOnce() throws Exception {
        var challenge = restTemplate.getForEntity("/api/auth/captcha?purpose=register", String.class);
        assertThat(challenge.getStatusCode()).isEqualTo(HttpStatus.OK);
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(challenge.getBody()).path("data");
        assertThat(json.has("x")).isFalse(); assertThat(json.path("image").asText()).startsWith("data:image/png;base64,");
        String cookie = challenge.getHeaders().getFirst("Set-Cookie").split(";")[0]; String session = cookie.split("=")[1]; sessions.add(session);
        String id = json.path("challengeId").asText(); int x = Integer.parseInt(redisTemplate.opsForValue().get(CaptchaService.challengeKey(session, id)).split("\\|")[0]);
        HttpHeaders headers = headers(null, null); headers.set(HttpHeaders.COOKIE, cookie);
        Map<String,Object> verification = Map.of("purpose", "register", "challengeId", id, "offset", x, "durationMillis", 400);
        assertThat(restTemplate.exchange("/api/auth/captcha/verify", HttpMethod.POST, new HttpEntity<>(verification, headers), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange("/api/auth/captcha/verify", HttpMethod.POST, new HttpEntity<>(verification, headers), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    @Test void atomicLimitAllowsExactlyThresholdAcrossConcurrentRequestsAndExpires() throws Exception {
        String key = "luminous:e2e:limit:" + UUID.randomUUID(); ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Future<Boolean>> requests = new ArrayList<>();
            for (int i = 0; i < 100; i++) { requests.add(pool.submit(() -> limiter.isAllowed(key, 20, Duration.ofSeconds(60)))); }
            int allowed = 0; for (var result : requests) { if (result.get(5, TimeUnit.SECONDS)) { allowed++; } }
            assertThat(allowed).isEqualTo(20); assertThat(redisTemplate.opsForZSet().zCard(key)).isEqualTo(20);
            redisTemplate.opsForZSet().add(key, "[E2E_TEST] 过期条目", System.currentTimeMillis() - 61000);
            assertThat(limiter.isAllowed(key, 20, Duration.ofSeconds(60))).isFalse();
            assertThat(redisTemplate.opsForZSet().zCard(key)).isEqualTo(20);
        } finally { pool.shutdownNow(); redisTemplate.delete(key); }
    }
    @Test void concurrentIdenticalPostRequestsReplayOneCommittedBusinessEffect() throws Exception {
        String key = UUID.randomUUID().toString(); HttpHeaders headers = headers(authorToken, key);
        var body = postBody("重复提交"); ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<ResponseEntity<String>>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) { results.add(pool.submit(() -> create(headers, body))); }
            String response = null;
            for (var result : results) {
                var actual = result.get(10, TimeUnit.SECONDS); assertThat(actual.getStatusCode()).isEqualTo(HttpStatus.OK);
                if (response == null) { response = actual.getBody(); createdId(actual); } else { assertThat(actual.getBody()).isEqualTo(response); }
            }
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM posts WHERE author_id=? AND title=?", Long.class, author.id(), body.title())).isEqualTo(1);
            assertThat(create(headers, body).getBody()).isEqualTo(response);
            assertThat(create(headers, postBody("不同参数")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            var differentUser = create(headers(voterToken, key), body); assertThat(differentUser.getStatusCode()).isEqualTo(HttpStatus.OK); createdId(differentUser);
        } finally { pool.shutdownNow(); }
    }
    @Test void failureRollsBackClaimAndRetryCompletesOnlyOnce() throws Exception {
        String key = UUID.randomUUID().toString(); var headers = headers(authorToken, key); var body = postBody("失败后重试");
        doThrow(new IllegalStateException("[E2E_TEST] 写库失败")).when(posts).insert(any(Post.class));
        assertThat(create(headers, body).getStatusCode().is5xxServerError()).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM idempotent_requests WHERE operation_key=?", Long.class, key)).isZero();
        reset(posts); var retry = create(headers, body); assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK); createdId(retry);
        assertThat(create(headers, body).getBody()).isEqualTo(retry.getBody());
    }
    @Test void sameKeyOnDifferentEndpointIsIndependentAndOldVoteReplayCannotReapplyState() throws Exception {
        String key = UUID.randomUUID().toString(); var headers = headers(authorToken, key);
        long post = createdId(create(headers, postBody("独立接口")));
        String path = "/api/posts/" + post + "/vote";
        var like = restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(Map.of("value", 1), headers), String.class);
        assertThat(like.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject("SELECT votes FROM posts WHERE id=?", Integer.class, post)).isEqualTo(1);
        restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(Map.of("value", 0), headers(authorToken, UUID.randomUUID().toString())), String.class);
        assertThat(restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(Map.of("value", 1), headers), String.class).getBody()).isEqualTo(like.getBody());
        assertThat(jdbcTemplate.queryForObject("SELECT votes FROM posts WHERE id=?", Integer.class, post)).isZero();
    }
    @Test void bannedStateSurvivesLostOrExpiredRedisAndDatabaseFailureIs503() {
        jdbcTemplate.update("UPDATE users SET is_banned=TRUE WHERE id=?", voter.id());
        redisTemplate.delete("luminous:ban:user:" + voter.id());
        assertStatus("/api/auth/me", HttpMethod.GET, null, voterToken, HttpStatus.UNAUTHORIZED);
        TokenBlacklistService restarted = new TokenBlacklistService(redisTemplate, users, 60 * 24 * 7);
        assertThat(restarted.isUserBanned(voter.id())).isTrue();
        jdbcTemplate.update("UPDATE users SET is_banned=FALSE WHERE id=?", voter.id());
        redisTemplate.opsForValue().set("luminous:ban:user:" + voter.id(), "banned");
        assertStatus("/api/auth/me", HttpMethod.GET, null, voterToken, HttpStatus.OK);
        doThrow(new IllegalStateException("[E2E_TEST] 数据库不可用")).when(users).selectById(voter.id());
        assertStatus("/api/auth/me", HttpMethod.GET, null, voterToken, HttpStatus.SERVICE_UNAVAILABLE);
        reset(users); redisTemplate.delete("luminous:ban:user:" + voter.id());
    }
    @Test void publicRegistrationNeverAllocatesAdminAndControlledInitializationIsSerialized() throws Exception {
        jdbcTemplate.update("UPDATE users SET role='USER' WHERE id=?", userIds.getFirst());
        jdbcTemplate.update("UPDATE system_bootstrap SET admin_user_id=NULL WHERE id=1");
        String name = "E2E_TEST_bootstrap_" + UUID.randomUUID().toString().substring(0,8);
        ExecutorService pool = Executors.newFixedThreadPool(8); List<Future<Long>> ids = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) { ids.add(pool.submit(() -> bootstrap.initialize(name, "Password123!Strong", "[E2E_TEST] 初始管理员"))); }
            Long first = ids.getFirst().get(15, TimeUnit.SECONDS); userIds.add(first);
            for (var id : ids) { assertThat(id.get(15, TimeUnit.SECONDS)).isEqualTo(first); }
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE role='ADMIN'", Long.class)).isEqualTo(1);
            AuthDtos.AuthResponse regular = register("after_bootstrap"); assertThat(regular.user().role()).isEqualTo("USER");
        } finally { pool.shutdownNow(); jdbcTemplate.update("UPDATE system_bootstrap SET admin_user_id=NULL WHERE id=1"); }
    }

    byte[] png() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB), "png", bytes); return bytes.toByteArray();
    }
    MockMultipartFile file() throws IOException { return new MockMultipartFile("file", "[E2E_TEST] 图片.png", "image/png", png()); }
    @Test void browserCanReadUploadedImageOnlyWithItsFileCredentialOrOwnerIdentity() throws Exception {
        byte[] image = png(); LinkedMultiValueMap<String,Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(image) { @Override public String getFilename() { return "[E2E_TEST] 图片.png"; } });
        HttpHeaders headers = headers(authorToken, UUID.randomUUID().toString()); headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        var response = restTemplate.exchange("/api/uploads/images", HttpMethod.POST, new HttpEntity<>(parts, headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getBody()).path("data");
        URI url = URI.create(json.path("url").asText()); String path = url.getRawPath() + "?" + url.getRawQuery();
        assertThat(url.getHost()).isEqualTo("localhost"); assertThat(url.getRawQuery()).startsWith("token=");
        assertThat(restTemplate.getForEntity(path, byte[].class).getBody()).containsExactly(image);
        assertThat(restTemplate.getForEntity(url.getRawPath(), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(restTemplate.getForEntity(url.getRawPath() + "?token=" + "0".repeat(64), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(restTemplate.exchange(url.getRawPath(), HttpMethod.GET, new HttpEntity<>(headers(authorToken,null)), byte[].class).getBody()).containsExactly(image);
        assertThat(restTemplate.exchange(url.getRawPath(), HttpMethod.GET, new HttpEntity<>(headers(voterToken,null)), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        String key = url.getPath().substring(url.getPath().lastIndexOf('/') + 1);
        assertThat(restTemplate.getForEntity("/uploads/private/" + key, String.class).getStatusCode().is4xxClientError()).isTrue();
    }
    @Test void metadataFailureCompensatesActualLocalFileAndFailedDeleteIsRetried() throws Exception {
        doThrow(new IllegalStateException("[E2E_TEST] 文件记录写入失败")).when(uploadedFiles).insert(any(UploadedFile.class));
        doThrow(new IllegalStateException("[E2E_TEST] 删除暂失败")).when(storage).delete(anyString());
        assertThatThrownBy(() -> uploads.uploadImage(file(), author)).isInstanceOf(BusinessException.class);
        String key = fileKeys.getLast();
        assertThat(Files.exists(Path.of("target/vote-validation-uploads/private", key))).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM storage_operations WHERE object_key=?", String.class,key)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT attempts FROM storage_operations WHERE object_key=?", Integer.class,key)).isEqualTo(1);
        reset(storage); jdbcTemplate.update("UPDATE storage_operations SET lease_until=? WHERE object_key=?", LocalDateTime.now().minusMinutes(1), key);
        recovery.recover();
        assertThat(Files.exists(Path.of("target/vote-validation-uploads/private", key))).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM storage_operations WHERE object_key=?", String.class,key)).isEqualTo("DELETED");
    }
    @Test void restartRecoveryCleansFileWrittenBeforeMetadataAndNeverDeletesCommittedFile() throws Exception {
        var planned = storage.plan(file()); long operation = operations.prepare(planned); storage.store(planned, file());
        jdbcTemplate.update("UPDATE storage_operations SET lease_until=? WHERE id=?", LocalDateTime.now().minusMinutes(1), operation);
        recovery.recover(); assertThat(Files.exists(Path.of("target/vote-validation-uploads/private",planned.key()))).isFalse();
        var committed = uploads.uploadImage(file(), author); String key = fileKeys.getLast();
        long committedOperation = jdbcTemplate.queryForObject("SELECT id FROM storage_operations WHERE object_key=?", Long.class,key);
        operations.cleanup(committedOperation);
        assertThat(Files.exists(Path.of("target/vote-validation-uploads/private",key))).isTrue();
        assertThat(committed.url()).contains("/api/files/");
    }
    @AfterEach void cleanSecurityFixtures() {
        reset(storage, users, posts, uploadedFiles);
        for (String key : fileKeys) { storage.delete(key); jdbcTemplate.update("DELETE FROM uploaded_files WHERE stored_name=?",key); jdbcTemplate.update("DELETE FROM storage_operations WHERE object_key=?",key); }
        for (String key : operationKeys) { jdbcTemplate.update("DELETE FROM idempotent_requests WHERE operation_key=?",key); }
        for (String session : sessions) {
            List<String> keys = new ArrayList<>();
            try (var cursor = redisTemplate.scan(ScanOptions.scanOptions().match("luminous:captcha:{"+session+"}:*").build())) { cursor.forEachRemaining(keys::add); }
            if (!keys.isEmpty()) { redisTemplate.delete(keys); }
        }
        for (Long user : userIds) { redisTemplate.delete("luminous:ban:user:"+user); }
    }
}
