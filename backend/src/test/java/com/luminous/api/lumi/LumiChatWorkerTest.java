package com.luminous.api.lumi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 模型请求使用桩，验证私有上下文裁剪、无工具调用和失败边界，不代表真实模型质量。 */
class LumiChatWorkerTest {
    final LumiChatService tasks=mock(LumiChatService.class);
    final LumiChatRepository store=mock(LumiChatRepository.class);
    final LumiProperties p=new LumiProperties();
    final LumiChatProperties cp=new LumiChatProperties();
    final DeepSeekClient model=spy(new DeepSeekClient(p,new ObjectMapper()));
    LumiChatWorker worker;
    LumiChatService.Work work;
    LumiChatRepository.Turn turn(long id,String question,String answer){return new LumiChatRepository.Turn(id,1,2,question,answer,"RUNNING","[E2E_TEST]",LocalDateTime.now(),LocalDateTime.now(),"[E2E_TEST]",1,1);}
    @BeforeEach void setup(){worker=new LumiChatWorker(tasks,store,model,p,cp);work=new LumiChatService.Work(turn(100,"[E2E_TEST] 继续说",""),new LumiRepository.Attempt(3,100,LocalDate.now(),1000),"[E2E_TEST]");when(tasks.sending(work)).thenReturn(true);}
    @AfterEach void close(){worker.destroy();}
    @Test void sendsOnlyOwnCompletedHistoryWithServerRolesAndNoTools() throws Exception {
        when(store.history(work.turn(),6)).thenReturn(List.of(turn(90,"[E2E_TEST] 我的暗号是星河","[E2E_TEST] 记住了")));
        doAnswer(call->{var body=new ObjectMapper().readTree((byte[])call.getArgument(0));assertThat(body.has("tools")).isFalse();assertThat(body.path("stream").asBoolean()).isTrue();assertThat(body.path("stream_options").path("include_usage").asBoolean()).isTrue();assertThat(body.path("max_tokens").asInt()).isEqualTo(768);assertThat(body.path("messages").size()).isEqualTo(4);assertThat(body.path("messages").path(1).path("role").asText()).isEqualTo("user");assertThat(body.path("messages").path(2).path("role").asText()).isEqualTo("assistant");return new DeepSeekClient.Result("[E2E_TEST] 星河",null,null,100,20);}).when(model).stream(any(),any(),any());
        worker.execute(work);verify(tasks).finish(eq(work),any());verify(model,times(1)).stream(any(),any(),any());
    }
    @Test void dropsOldestWholeTurnsToFitBudgetWithoutBreakingRoles() {
        when(store.history(work.turn(),6)).thenReturn(List.of(turn(80,"[E2E_TEST] 老问题","字".repeat(4000)),turn(90,"[E2E_TEST] 近期问题","[E2E_TEST] 近期回答")));
        doAnswer(call->{String body=new String((byte[])call.getArgument(0),java.nio.charset.StandardCharsets.UTF_8);assertThat(body).doesNotContain("老问题").contains("近期问题");return new DeepSeekClient.Result("[E2E_TEST] 回答",null,null,100,20);}).when(model).stream(any(),any(),any());
        p.setMaxInputBytes(8000);worker.execute(work);verify(tasks).finish(eq(work),any());
    }
    @Test void failedPreflightNeverCallsProvider(){when(store.history(work.turn(),6)).thenReturn(List.of());when(tasks.sending(work)).thenReturn(false);worker.execute(work);verify(model,never()).stream(any(),any(),any());verify(tasks).fail(eq(work),anyString());}
    @Test void sanitizedProviderFailureBecomesRetryableTaskWithoutAutomaticSecondCall(){when(store.history(work.turn(),6)).thenReturn(List.of());doThrow(new DeepSeekClient.Failure("模型服务暂时无法完成请求")).when(model).stream(any(),any(),any());worker.execute(work);verify(tasks).fail(work,"模型服务暂时无法完成请求");verify(tasks,never()).finish(any(),any());verify(model,times(1)).stream(any(),any(),any());}
    @Test void partialTextSurvivesFailureWithoutASecondModelCall(){
        when(store.history(work.turn(),6)).thenReturn(List.of());when(tasks.progress(eq(work),anyString())).thenReturn(true);
        doAnswer(call->{java.util.function.Consumer<String> progress=call.getArgument(2);progress.accept("[E2E_TEST] 部分内容");throw new DeepSeekClient.Failure("[E2E_TEST] 连接中断");}).when(model).stream(any(),any(),any());
        worker.execute(work);verify(tasks).checkpoint(work,"[E2E_TEST] 部分内容");verify(tasks).fail(work,"[E2E_TEST] 连接中断");verify(model,times(1)).stream(any(),any(),any());
    }
    @Test void partialHistoryIsExplicitlyMarkedIncompleteInProviderContext(){
        var previous=new LumiChatRepository.Turn(90,1,2,"[E2E_TEST] 写故事","[E2E_TEST] 一只月亮猫","STOPPED","已停止",LocalDateTime.now(),LocalDateTime.now(),null,1,2);
        when(store.history(work.turn(),6)).thenReturn(List.of(previous));
        doAnswer(call->{var json=new ObjectMapper().readTree((byte[])call.getArgument(0));assertThat(json.path("messages").path(2).path("content").asText()).contains("未完成部分","月亮猫");return new DeepSeekClient.Result("[E2E_TEST] 继续故事",null,null,100,20);}).when(model).stream(any(),any(),any());
        worker.execute(work);verify(tasks).finish(eq(work),any());
    }
}
