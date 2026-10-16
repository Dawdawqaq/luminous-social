package com.luminous.api.lumi;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LumiIntentTest {
    @Test void onlyCompleteExplicitMentionTriggers() {
        assertThat(LumiIntent.question("@Lumi 请解释这篇帖子")).isEqualTo("请解释这篇帖子");
        assertThat(LumiIntent.question("@Lumi你好")).isEqualTo("你好");
        assertThat(LumiIntent.question("你好 @lUMi 总结这层")).isEqualTo("你好  总结这层");
        for(String text:new String[]{"回复 Lumi","lumi","a@lumi","@Lumina","@Lumi_abc","\\@Lumi","`@Lumi`","```\n@Lumi 解释\n```","> @Lumi 引用"})
            assertThat(LumiIntent.question(text)).as(text).isNull();
    }
    @Test void externalSearchRequiresExplicitPositiveRequest() {
        for(String text:new String[]{"联网查一下","请联网搜索这个说法","帮我上网查证","站外搜索一下"})assertThat(LumiIntent.requestsWeb(text)).isTrue();
        for(String text:new String[]{"不要联网，总结帖子","不必联网","搜一下站内其他帖子","解释一下内容"})assertThat(LumiIntent.requestsWeb(text)).isFalse();
    }
    @Test void propertiesRejectUnlimitedOrUnpricedSettings() {
        var p=new LumiProperties();p.validate();assertThat(p.ready()).isFalse();assertThat(p.reservation()).isPositive();
        p.setDailyBudgetMicros(0);assertThatThrownBy(p::validate).isInstanceOf(IllegalStateException.class);
    }
}
