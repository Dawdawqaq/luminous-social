package com.luminous.api.storage;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.*;

/** 使用独立本地 S3 协议测试桩验证 SDK 调用；不连接共享对象存储。 */
class MinioStorageProviderTest {
    @Test void failedDeleteIsReportedAndUnavailableReadReturnsServiceUnavailable() {
        var client=org.mockito.Mockito.mock(software.amazon.awssdk.services.s3.S3Client.class);
        var failure=software.amazon.awssdk.core.exception.SdkClientException.create("[E2E_TEST] 连接失败");
        org.mockito.Mockito.when(client.getObject(org.mockito.ArgumentMatchers.any(software.amazon.awssdk.services.s3.model.GetObjectRequest.class))).thenThrow(failure);
        org.mockito.Mockito.when(client.deleteObject(org.mockito.ArgumentMatchers.any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class))).thenThrow(failure);
        try(var storage=new MinioStorageProvider(client,"e2e-test-files","https://files.example.com")) {
            assertThatThrownBy(() -> storage.read("123.png")).isInstanceOfSatisfying(com.luminous.api.common.BusinessException.class,
                    error -> assertThat(error.getStatus()).isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
            assertThatThrownBy(() -> storage.delete("123.png")).isSameAs(failure);
        }
    }
    @Test void invalidPublicOriginCannotBecomeInternalOrMalformedResourcePath() {
        for(String value:List.of("http://example.com/path","http://user@example.com","ftp://example.com","http://example.com?token=test")) {
            assertThatThrownBy(() -> FileAccessTokens.normalizeOrigin(value)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(FileAccessTokens.normalizeOrigin("https://files.example.com/")).isEqualTo("https://files.example.com");
    }
    @Test void internalS3EndpointUsesSignedRequestsWhileBrowserReceivesControlledPublicUrl() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",bytes);
        byte[] image = bytes.toByteArray(); List<String> requests = new CopyOnWriteArrayList<>();
        List<String> signatures = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestMethod()+" "+exchange.getRequestURI());
            signatures.add(Objects.toString(exchange.getRequestHeaders().getFirst("Authorization"),""));
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("x-amz-bucket-region","us-east-1");
            if ("GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Content-Type","image/png"); exchange.sendResponseHeaders(200,image.length);
                exchange.getResponseBody().write(image);
            } else { exchange.sendResponseHeaders(200,-1); }
            exchange.close();
        });
        server.start();
        try (var storage = new MinioStorageProvider("http://127.0.0.1:"+server.getAddress().getPort(),"E2E_TEST_access","[E2E_TEST] secret","e2e-test-files","https://files.example.com")) {
            var file = new MockMultipartFile("file","[E2E_TEST].png","image/png",image);
            var planned = storage.plan(file);
            assertThat(planned.url()).startsWith("https://files.example.com/api/files/").contains("?token=");
            assertThat(FileAccessTokens.matches(URI.create(planned.url()).getQuery().substring(6),planned.readTokenHash())).isTrue();
            storage.store(planned,file);
            try (InputStream input=storage.read(planned.key())) { assertThat(input.readAllBytes()).containsExactly(image); }
            storage.delete(planned.key());
            assertThat(requests).hasSize(4);
            assertThat(requests).anyMatch(value -> value.startsWith("PUT /e2e-test-files/"+planned.key()));
            assertThat(requests).noneMatch(value -> value.contains("policy") || value.contains("acl"));
            assertThat(signatures).allMatch(value -> value.startsWith("AWS4-HMAC-SHA256 "));
        } finally { server.stop(0); }
    }
}
