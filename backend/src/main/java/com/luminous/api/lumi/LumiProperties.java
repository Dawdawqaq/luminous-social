package com.luminous.api.lumi;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 费用单位为百万分之一美元，按配置的峰时单价保守预留；不表示供应商实时账单。 */
@Component
@ConfigurationProperties(prefix = "app.lumi")
public class LumiProperties {
    public static final long BOT_ID = 880000000000000001L;
    private boolean enabled;
    private String apiKey = "";
    private String model = "deepseek-flash";
    private int cooldownSeconds = 60, userDaily = 10, userPostDaily = 2, postDaily = 10;
    private int concurrency = 2, queueCapacity = 20, dailyCalls = 100, maxAttempts = 2;
    private int timeoutSeconds = 25, leaseSeconds = 120, maxInputBytes = 16000, maxOutputTokens = 384;
    private long dailyBudgetMicros = 100000, inputMicrosPerMillion = 300000, outputMicrosPerMillion = 1200000;
    public boolean ready() { return enabled && apiKey != null && !apiKey.isBlank(); }
    public void validate() {
        if (cooldownSeconds < 1 || userDaily < 1 || userPostDaily < 1 || postDaily < 1 || concurrency < 1 || concurrency > 8
                || queueCapacity < 1 || dailyCalls < 1 || maxAttempts < 1 || maxAttempts > 3 || timeoutSeconds < 1 || timeoutSeconds > 45
                || leaseSeconds < timeoutSeconds * 3 || maxInputBytes < 2048 || maxInputBytes > 32000 || maxOutputTokens < 64 || maxOutputTokens > 1024
                || dailyBudgetMicros < 1 || inputMicrosPerMillion < 1 || outputMicrosPerMillion < 1 || !"deepseek-flash".equals(model)) {
            throw new IllegalStateException("Lumi 配置超出安全范围，第一版使用已验证的 deepseek-flash");
        }
    }
    public long reservation() { return cost(maxInputBytes + 1024L, maxOutputTokens); }
    public long cost(long input, long output) { return Math.addExact(Math.multiplyExact(input, inputMicrosPerMillion), Math.multiplyExact(output, outputMicrosPerMillion)) / 1000000 + 1; }
    public boolean isEnabled() { return enabled; } public void setEnabled(boolean v) { enabled=v; }
    public String getApiKey() { return apiKey; } public void setApiKey(String v) { apiKey=v; }
    public String getModel() { return model; } public void setModel(String v) { model=v; }
    public int getCooldownSeconds() { return cooldownSeconds; } public void setCooldownSeconds(int v) { cooldownSeconds=v; }
    public int getUserDaily() { return userDaily; } public void setUserDaily(int v) { userDaily=v; }
    public int getUserPostDaily() { return userPostDaily; } public void setUserPostDaily(int v) { userPostDaily=v; }
    public int getPostDaily() { return postDaily; } public void setPostDaily(int v) { postDaily=v; }
    public int getConcurrency() { return concurrency; } public void setConcurrency(int v) { concurrency=v; }
    public int getQueueCapacity() { return queueCapacity; } public void setQueueCapacity(int v) { queueCapacity=v; }
    public int getDailyCalls() { return dailyCalls; } public void setDailyCalls(int v) { dailyCalls=v; }
    public int getMaxAttempts() { return maxAttempts; } public void setMaxAttempts(int v) { maxAttempts=v; }
    public int getTimeoutSeconds() { return timeoutSeconds; } public void setTimeoutSeconds(int v) { timeoutSeconds=v; }
    public int getLeaseSeconds() { return leaseSeconds; } public void setLeaseSeconds(int v) { leaseSeconds=v; }
    public int getMaxInputBytes() { return maxInputBytes; } public void setMaxInputBytes(int v) { maxInputBytes=v; }
    public int getMaxOutputTokens() { return maxOutputTokens; } public void setMaxOutputTokens(int v) { maxOutputTokens=v; }
    public long getDailyBudgetMicros() { return dailyBudgetMicros; } public void setDailyBudgetMicros(long v) { dailyBudgetMicros=v; }
    public long getInputMicrosPerMillion() { return inputMicrosPerMillion; } public void setInputMicrosPerMillion(long v) { inputMicrosPerMillion=v; }
    public long getOutputMicrosPerMillion() { return outputMicrosPerMillion; } public void setOutputMicrosPerMillion(long v) { outputMicrosPerMillion=v; }
}
