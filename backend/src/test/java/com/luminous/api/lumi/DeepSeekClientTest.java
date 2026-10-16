package com.luminous.api.lumi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class DeepSeekClientTest {
    private final LumiProperties p=new LumiProperties();
    private final DeepSeekClient client=new DeepSeekClient(p,new ObjectMapper());
    private byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
    @Test void requestDisablesThinkingAndHasHardOutputLimitAndSingleReadOnlyTool() throws Exception {
        p.setApiKey("[E2E_TEST] secret");
        byte[] body=client.requestBody(List.of(Map.of("role","user","content","[E2E_TEST] 解释帖子")),true);
        var json=new ObjectMapper().readTree(body);
        assertThat(json.path("thinking").path("type").asText()).isEqualTo("disabled");
        assertThat(json.path("max_tokens").intValue()).isEqualTo(384);
        assertThat(json.path("tools").size()).isEqualTo(1);
        assertThat(new String(body,StandardCharsets.UTF_8)).doesNotContain("[E2E_TEST] secret","web_search");
    }
    @Test void boundsPayloadByBytesBeforeExternalCall() {
        assertThatThrownBy(()->client.requestBody(List.of(Map.of("role","user","content","字".repeat(20000))),false)).isInstanceOf(DeepSeekClient.Failure.class);
    }
    @Test void parsesAnswerAndUsageWithoutReturningReasoning() {
        var result=client.parse(200,bytes("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"短回答\",\"reasoning_content\":\"隐藏思考\"}}],\"usage\":{\"prompt_tokens\":25,\"completion_tokens\":8}}"));
        assertThat(result.content()).isEqualTo("短回答");assertThat(result.inputTokens()).isEqualTo(25);assertThat(result.outputTokens()).isEqualTo(8);
    }
    @Test void unsupportedOrMultipleToolsAndTruncatedOutputAreRejected() {
        for(String response:new String[]{"{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"不完整\"}}]}",
                "{\"choices\":[{\"message\":{\"tool_calls\":[{\"function\":{\"name\":\"delete_post\",\"arguments\":\"{}\"}}]}}]}"})
            assertThatThrownBy(()->client.parse(200,bytes(response))).isInstanceOf(DeepSeekClient.Failure.class);
        assertThatThrownBy(()->client.parse(401,bytes("[E2E_TEST] sensitive provider error"))).hasMessage("模型服务暂时无法完成请求");
    }
}
