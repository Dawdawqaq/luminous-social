package com.luminous.api.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.*;
import org.yaml.snakeyaml.nodes.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** 静态检查当前文件的合并结果；不启动 Docker，不读取生产密钥。 */
class DeploymentConfigurationTest {
    private record OverrideValue(Object value) {}
    private static class ComposeConstructor extends SafeConstructor {
        ComposeConstructor() {
            super(new LoaderOptions());
            yamlConstructors.put(new Tag("!override"), new AbstractConstruct() {
                @Override public Object construct(Node node) {
                    if (node instanceof MappingNode map) { flattenMapping(map); return new OverrideValue(constructMapping(map)); }
                    if (node instanceof SequenceNode sequence) { return new OverrideValue(constructSequence(sequence)); }
                    return new OverrideValue(constructScalar((ScalarNode) node));
                }
            });
        }
    }
    private Map<?,?> load(String name) throws Exception {
        return new Yaml(new ComposeConstructor()).load(Files.readString(Path.of("..", name)));
    }
    private Object unwrap(Object value) {
        if (value instanceof OverrideValue override) { return unwrap(override.value()); }
        if (value instanceof Map<?,?> map) {
            Map<Object,Object> result = new LinkedHashMap<>(); map.forEach((key,item) -> result.put(key,unwrap(item))); return result;
        }
        if (value instanceof List<?> list) { return list.stream().map(this::unwrap).toList(); }
        return value;
    }
    private Object merge(Object base, Object overlay) {
        if (overlay instanceof OverrideValue) { return unwrap(overlay); }
        if (base instanceof Map<?,?> original && overlay instanceof Map<?,?> changes) {
            Map<Object,Object> result = new LinkedHashMap<>(); original.forEach((key,value) -> result.put(key,unwrap(value)));
            changes.forEach((key,value) -> result.put(key,merge(result.get(key),value))); return result;
        }
        if (base instanceof List<?> original && overlay instanceof List<?> additions) {
            List<Object> result = new ArrayList<>(original); additions.forEach(value -> { if (!result.contains(value)) result.add(unwrap(value)); }); return result;
        }
        return unwrap(overlay);
    }
    private Map<?,?> services(Object configuration) { return (Map<?,?>)((Map<?,?>)configuration).get("services"); }
    private Map<?,?> service(Map<?,?> services, String name) { return (Map<?,?>)services.get(name); }
    @Test void productionMergeHasOneExternalEntryAndIdenticalAuthenticationAndStorageConfiguration() throws Exception {
        var services = services(merge(load("docker-compose.yml"),load("docker-compose.prod.yml")));
        for (String name : List.of("backend","frontend","namesrv","broker")) { assertThat((List<?>)service(services,name).get("ports")).isEmpty(); }
        assertThat(service(services,"backend-2").get("ports")).isNull();
        for (String name : List.of("mysql","redis","minio")) {
            for (Object port : (List<?>)service(services,name).get("ports")) { assertThat(port.toString()).startsWith("127.0.0.1:"); }
        }
        assertThat((List<?>)service(services,"nginx-lb").get("ports")).hasSize(1);
        var first = (Map<?,?>)service(services,"backend").get("environment");
        var second = (Map<?,?>)service(services,"backend-2").get("environment");
        Map<Object,Object> comparable = new LinkedHashMap<>(first); comparable.remove("SNOWFLAKE_WORKER_ID");
        Map<Object,Object> other = new LinkedHashMap<>(second); other.remove("SNOWFLAKE_WORKER_ID");
        assertThat(comparable).isEqualTo(other);
        assertThat(first.get("JWT_SECRET").toString()).startsWith("${JWT_SECRET:?");
        assertThat(first.get("MINIO_ENDPOINT")).isEqualTo("http://minio:9000");
        assertThat(first.get("MINIO_PUBLIC_ENDPOINT").toString()).contains("APP_PUBLIC_BASE_URL");
        assertThat(first.get("TRUSTED_PROXIES").toString()).endsWith("/32");
        assertThat((Map<?,?>)service(services,"frontend").get("networks")).hasSize(1);
        for (String name : List.of("nginx/nginx.prod.conf","nginx/nginx.prod.tls.conf")) {
            String nginx = Files.readString(Path.of("..",name));
            assertThat(nginx).contains("proxy_set_header X-Forwarded-For $remote_addr;", "client_max_body_size 12M;");
            assertThat(nginx).doesNotContain("$proxy_add_x_forwarded_for", "\"$request\"");
        }
    }
    @Test void tlsIsExplicitlyConfiguredWithCertificateMountsAndSecureCookies() throws Exception {
        Object merged = merge(merge(load("docker-compose.yml"),load("docker-compose.prod.yml")),load("docker-compose.tls.yml"));
        var services = services(merged); var gateway = service(services,"nginx-lb");
        assertThat((List<?>)gateway.get("ports")).hasSize(2);
        assertThat(gateway.get("volumes").toString()).contains("fullchain.pem:ro", "privkey.pem:ro", "nginx.prod.tls.conf");
        for (String name : List.of("backend","backend-2")) {
            assertThat(((Map<?,?>)service(services,name).get("environment")).get("SECURE_COOKIES")).isEqualTo("true");
        }
        assertThat(Files.readString(Path.of("..","nginx/nginx.prod.tls.conf")))
                .contains("listen 443 ssl;", "ssl_certificate ", "ssl_certificate_key ", "ssl_protocols TLSv1.2 TLSv1.3;", "return 308 https://");
    }
    @Test void bothNodesAcceptTheSameSignedTokenAndRejectDifferentSecrets() {
        String secret="[E2E_TEST]_012345678901234567890123456789";
        var first=new com.luminous.api.security.JwtService(secret,4320);
        var second=new com.luminous.api.security.JwtService(secret,4320);
        String token=first.createToken(123L,"E2E_TEST_node","USER");
        assertThat(second.parse(token).id()).isEqualTo(123L);
        assertThatThrownBy(() -> new com.luminous.api.security.JwtService(secret+"other",4320).parse(token)).isInstanceOf(io.jsonwebtoken.JwtException.class);
    }
    @Test void unsafeProductionSecretsAndPublicOriginsAreRejected() {
        String secret="[E2E_TEST]_012345678901234567890123456789";
        assertThatCode(() -> new ProductionConfigurationGuard(secret,"https://example.com","https://example.com",true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> new ProductionConfigurationGuard("short","https://example.com","https://example.com",true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductionConfigurationGuard(secret,"https://example.com","http://minio:9000",false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductionConfigurationGuard(secret,"http://example.com","http://example.com",true)).isInstanceOf(IllegalArgumentException.class);
    }
}
