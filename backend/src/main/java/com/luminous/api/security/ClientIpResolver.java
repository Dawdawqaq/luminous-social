package com.luminous.api.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Arrays;

/** 只有受控代理可以提供转发链，从右向左剥离受信任代理，避免客户端伪造来源。 */
@Component
public class ClientIpResolver {
    private final List<IpAddressMatcher> trusted;
    public ClientIpResolver(@Value("${app.security.trusted-proxies:}") String proxies) {
        trusted = Arrays.stream(proxies.split(",")).map(String::trim).filter(value -> !value.isEmpty())
                .map(IpAddressMatcher::new).toList();
    }
    public boolean isTrusted(String address) { return isLiteral(address) && trusted.stream().anyMatch(matcher -> matcher.matches(address)); }
    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!isTrusted(remote)) { return remote; }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.length() > 1024) { return remote; }
        String[] chain = forwarded.split(",", -1);
        if (chain.length > 10) { return remote; }
        String result = remote;
        for (int i = chain.length - 1; i >= 0 && isTrusted(result); i--) {
            String address = chain[i].trim();
            if (!isLiteral(address)) { return remote; }
            result = address;
        }
        return result;
    }
    private static boolean isLiteral(String value) {
        if (value == null || !value.matches("[0-9a-fA-F:.]+")) { return false; }
        try {
            // 限制为数字地址后解析，不允许代理头触发域名查询。
            java.net.InetAddress.getByName(value); return true;
        } catch (Exception ex) { return false; }
    }
}
