package com.luminous.api.integration;

import com.luminous.api.LuminousApiApplication;
import com.luminous.api.vote.VoteCommandService;
import com.luminous.api.vote.VoteProjectionService;
import com.luminous.api.mq.ReliableOutboxDispatcher;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 仅供故障验收子进程使用，禁止作为生产启动入口。 */
public final class VoteCrashProbe {
    public static final long POST_ID = 900000001L;
    public static final long USER_ID = 900000002L;
    public static void main(String[] args) {
        if (args.length != 2 || !args[1].startsWith("jdbc:h2:file:")) {
            throw new IllegalArgumentException("故障验收仅允许独立 H2 文件数据库");
        }
        String mode = args[0];
        SpringApplication application = new SpringApplication(LuminousApiApplication.class);
        if ("consume-crash".equals(mode)) {
            application.addInitializers(context -> context.getBeanFactory().addBeanPostProcessor(
                    new org.springframework.beans.factory.config.BeanPostProcessor() {
                        @Override public Object postProcessAfterInitialization(Object bean, String name) {
                            if (!(bean instanceof com.luminous.api.post.PostMapper)) { return bean; }
                            return java.lang.reflect.Proxy.newProxyInstance(bean.getClass().getClassLoader(),
                                    new Class<?>[]{com.luminous.api.post.PostMapper.class}, (proxy, method, parameters) -> {
                                        if ("updateVotesAtomic".equals(method.getName())) {
                                            System.out.println("[E2E_TEST] 投票记录已写入事务，第二次业务更新前立即终止进程");
                                            System.out.flush();
                                            Runtime.getRuntime().halt(24);
                                        }
                                        try { return method.invoke(bean, parameters); }
                                        catch (java.lang.reflect.InvocationTargetException ex) { throw ex.getCause(); }
                                    });
                        }
                    }));
        }
        try (var context = application.run(
                "--spring.profiles.active=vote-validation", "--spring.datasource.url=" + args[1],
                "--spring.sql.init.mode=never", "--server.port=0", "--app.reliable-events.dispatch-enabled=false")) {
            if ("crash".equals(mode)) {
                context.getBean(VoteCommandService.class).accept(POST_ID, USER_ID, 1);
                context.getBean(VoteProjectionService.class).refresh(POST_ID);
                String cached = context.getBean(StringRedisTemplate.class).opsForValue().get("luminous:stat:post:votes:" + POST_ID);
                if (!"1".equals(cached)) { throw new IllegalStateException("验收 Redis 更新未完成"); }
                System.out.println("[E2E_TEST] Redis 已更新，消息尚未发送，立即终止进程");
                System.out.flush();
                // 故意跳过关闭钩子，模拟进程在 Redis 成功与 MQ 发送之间突然退出。
                Runtime.getRuntime().halt(23);
            }
            context.getBean(ReliableOutboxDispatcher.class).retryPending();
            int score = context.getBean(JdbcTemplate.class).queryForObject("SELECT votes FROM posts WHERE id=?", Integer.class, POST_ID);
            if (score != 1) { throw new IllegalStateException("重启后的待投递事件未恢复"); }
            System.out.println("[E2E_TEST] 重启恢复成功，总分为 1");
        }
    }
}
