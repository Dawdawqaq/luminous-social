package com.luminous.api.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** 固定初始化行串行协调多节点，公开注册接口永远不参与管理员分配。 */
@Service
public class AdminBootstrapService {
    private final JdbcTemplate jdbc;
    private final AuthService auth;
    public AdminBootstrapService(JdbcTemplate jdbc, AuthService auth) { this.jdbc = jdbc; this.auth = auth; }
    @Transactional
    public Long initialize(String username, String password, String displayName) {
        jdbc.queryForList("SELECT id FROM system_bootstrap WHERE id=1 FOR UPDATE", Long.class);
        Long initialized = jdbc.queryForObject("SELECT admin_user_id FROM system_bootstrap WHERE id=1", Long.class);
        if (initialized != null) { return initialized; }
        List<Long> administrators = jdbc.queryForList("SELECT id FROM users WHERE role='ADMIN'", Long.class);
        if (administrators.size() > 1) { throw new IllegalStateException("已有多个管理员，请先明确初始管理员账号"); }
        Long id;
        if (!administrators.isEmpty()) { id = administrators.getFirst(); }
        else {
            if (username == null || !username.matches("[a-zA-Z0-9_]{3,32}") || password == null || password.length() < 12
                    || password.length() > 72 || displayName == null || displayName.length() < 2 || displayName.length() > 96) {
                throw new IllegalArgumentException("初始化账号需合法用户名、至少十二位密码及有效昵称");
            }
            if (!jdbc.queryForList("SELECT id FROM users WHERE username=?", Long.class, username).isEmpty()) {
                throw new IllegalStateException("初始化用户名已被普通账号占用，请使用独立账号");
            }
            id = Long.valueOf(auth.register(new AuthDtos.RegisterRequest(username, displayName, password)).user().id());
            jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", id);
        }
        jdbc.update("UPDATE system_bootstrap SET admin_user_id=? WHERE id=1", id);
        return id;
    }
}
