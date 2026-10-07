package com.luminous.api.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 仅显式配置初始化凭据时启动；凭据不写日志，不通过公开 HTTP 接口接收。 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {
    private final AdminBootstrapService bootstrap;
    @Value("${app.bootstrap.username:}") private String username;
    @Value("${app.bootstrap.password:}") private String password;
    @Value("${app.bootstrap.display-name:管理员}") private String displayName;
    public AdminBootstrapRunner(AdminBootstrapService bootstrap) { this.bootstrap = bootstrap; }
    @Override public void run(ApplicationArguments arguments) {
        if (username.isBlank() && password.isBlank()) { return; }
        bootstrap.initialize(username, password, displayName);
    }
}
