package com.luminous.api.lumi;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 聊天独立设置用户限额，但平台并发、尝试和费用仍与帖子助手共享。 */
@Component
@ConfigurationProperties(prefix="app.lumi-chat")
public class LumiChatProperties {
    private int userDaily=20,cooldownSeconds=5,outputTokens=768,contextTurns=6,maxConversations=50;
    public void validate(){if(userDaily<1||cooldownSeconds<1||outputTokens<64||outputTokens>1024||contextTurns<1||contextTurns>10||maxConversations<1||maxConversations>200)throw new IllegalStateException("Lumi 聊天配置超出范围");}
    public int getMaxConversations(){return maxConversations;} public void setMaxConversations(int v){maxConversations=v;}
    public int getUserDaily(){return userDaily;} public void setUserDaily(int v){userDaily=v;}
    public int getCooldownSeconds(){return cooldownSeconds;} public void setCooldownSeconds(int v){cooldownSeconds=v;}
    public int getOutputTokens(){return outputTokens;} public void setOutputTokens(int v){outputTokens=v;}
    public int getContextTurns(){return contextTurns;} public void setContextTurns(int v){contextTurns=v;}
}
