package com.luminous.api.lumi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;

/** 协议用例不访问外网，刻意覆盖中文分片、用量尾帧、截断、异常与取消竞态。 */
class DeepSeekStreamParserTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private String chunk(String content,String finish) throws Exception {
        var choice=new LinkedHashMap<String,Object>();choice.put("index",0);choice.put("delta",Map.of("content",content));choice.put("finish_reason",finish);
        return "data: "+mapper.writeValueAsString(Map.of("choices",List.of(choice)))+"\n\n";
    }
    @Test void accumulatesChineseAndIgnoresHeartbeatAndReasoningAndReadsUsage() throws Exception {
        var snapshots=new ArrayList<String>();
        String body=": keepalive\r\n\r\n"+chunk("[E2E_TEST] 中文",null)+"data: {\"choices\":[{\"index\":0,\"delta\":{\"reasoning_content\":\"不展示\"}}]}\n\n"+chunk("😀",null)+chunk("","stop")+"data: {\"choices\":[],\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":12}}\n\ndata: [DONE]\n\n";
        var result=new DeepSeekStreamParser(mapper).read(new StringReader(body),snapshots::add);
        assertThat(snapshots).containsExactly("[E2E_TEST] 中文","[E2E_TEST] 中文😀");assertThat(result.content()).isEqualTo("[E2E_TEST] 中文😀");assertThat(result.inputTokens()).isEqualTo(120);assertThat(result.outputTokens()).isEqualTo(12);
    }
    @Test void cutConnectionPreservesPublishedPartialButDoesNotSucceed() throws Exception {
        var snapshots=new ArrayList<String>();String body=chunk("[E2E_TEST] 部分",null);
        assertThatThrownBy(()->new DeepSeekStreamParser(mapper).read(new StringReader(body),snapshots::add)).hasMessageContaining("未完成");assertThat(snapshots).containsExactly("[E2E_TEST] 部分");
    }
    @Test void lengthFinishDoesNotPretendToBeComplete() throws Exception {
        String body=chunk("[E2E_TEST] 部分","length")+"data: [DONE]\n\n";
        assertThatThrownBy(()->new DeepSeekStreamParser(mapper).read(new StringReader(body),s->{})).hasMessageContaining("未完成");
    }
    @Test void toolCallsAndProviderErrorsNeverLeakRawPayload(){
        for(String data:List.of("{\"choices\":[{\"delta\":{\"tool_calls\":[{}]}}]}","{\"error\":{\"message\":\"[E2E_TEST] secret\"}}"))
            assertThatThrownBy(()->new DeepSeekStreamParser(mapper).read(new StringReader("data: "+data+"\n\n"),s->{})).isInstanceOf(DeepSeekClient.Failure.class).hasMessageNotContaining("secret");
    }
    @Test void hardAnswerBoundDoesNotPublishOversizedText() throws Exception {
        var snapshots=new ArrayList<String>();String body=chunk("字".repeat(4000),null)+chunk("再",null);
        assertThatThrownBy(()->new DeepSeekStreamParser(mapper).read(new StringReader(body),snapshots::add)).hasMessageContaining("长度上限");assertThat(snapshots).hasSize(1);assertThat(snapshots.getFirst()).hasSize(4000);
    }
    @Test void cancelBeforeRegistrationClosesResourcesAndCancelsFuture(){
        var call=new LumiStreamCall();var closed=new java.util.concurrent.atomic.AtomicBoolean();call.cancel();
        var future=new CompletableFuture<Void>();call.pending(future);call.input(new ByteArrayInputStream(new byte[0]){@Override public void close(){closed.set(true);}});
        assertThat(call.cancelled()).isTrue();assertThat(future.isCancelled()).isTrue();assertThat(closed.get()).isTrue();call.cancel();
    }
}
