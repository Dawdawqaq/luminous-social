package com.luminous.api.lumi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luminous.api.search.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 外部模型使用桩，验证发布权限、工具回合与文本边界；不代表真实模型效果。 */
class LumiWorkerTest {
    private final LumiTaskService tasks=mock(LumiTaskService.class);
    private final LumiRepository store=mock(LumiRepository.class);
    private final DeepSeekClient model=mock(DeepSeekClient.class);
    private final SearchRepository search=mock(SearchRepository.class);
    private final LumiProperties p=new LumiProperties();
    private LumiWorker worker;
    private LumiTaskService.Work work;
    @BeforeEach void setup() {
        var task=new LumiRepository.Task(1,2,3,4,LocalDate.now(),"RUNNING",LocalDateTime.now(),"[E2E_TEST]",1,null,null,null);
        work=new LumiTaskService.Work(task,new LumiRepository.Attempt(5,1,LocalDate.now(),1000),"[E2E_TEST]");
        worker=new LumiWorker(tasks,store,p,model,search,new ObjectMapper());
        when(store.context(2)).thenReturn(new LumiRepository.Context(2,"[E2E_TEST] 标题","[E2E_TEST] 正文","@Lumi 总结这篇帖子",null));
        when(store.discussion(3,null)).thenReturn(List.of("[E2E_TEST] 评论"));
        when(model.requestBody(anyList(),anyBoolean())).thenReturn(new byte[]{1});
        when(tasks.sending(eq(work),any(),anyList())).thenReturn(true);
    }
    @AfterEach void close(){worker.destroy();}
    @Test void shortAnswerPublishesAfterUsageSettlement() {
        when(model.complete(any())).thenReturn(new DeepSeekClient.Result("[E2E_TEST] 简短总结",null,null,100,20));
        worker.execute(work);
        var order=inOrder(tasks);order.verify(tasks).sending(eq(work),any(),anyList());order.verify(tasks).usage(work.attempt(),100,20);
        order.verify(tasks).publish(work,"[E2E_TEST] 简短总结（仅参考部分文字讨论）",List.of());
    }
    @Test void longOrInventedExternalOrPostReferencesAreNotPublished() {
        for(String text:List.of("字".repeat(151),"https://example.com", "/post/999")){
            when(model.complete(any())).thenReturn(new DeepSeekClient.Result(text,null,null,100,50));worker.execute(work);
        }
        verify(tasks,never()).publish(any(),anyString(),anyList());verify(tasks,times(3)).fail(eq(work),anyString(),anyString(),eq(false));
    }
    @Test void removedContextCancelsBeforeModelAccess() {
        when(store.context(2)).thenReturn(null);worker.execute(work);verifyNoInteractions(model);
        verify(tasks).fail(eq(work),anyString(),anyString(),eq(true));
    }
    @Test void explicitWebRequestDoesNotFallBackToModelKnowledge() {
        when(store.context(2)).thenReturn(new LumiRepository.Context(2,"[E2E_TEST]","[E2E_TEST]","@Lumi 联网查一下",null));
        worker.execute(work);verifyNoInteractions(model);verify(tasks).fail(eq(work),anyString(),anyString(),eq(true));
    }
    @Test void oneReadOnlySearchUsesSecondBudgetReservationAndOnlyVerifiedSource() {
        when(model.complete(any())).thenReturn(new DeepSeekClient.Result(null,"量子","tool-e2e",100,20),new DeepSeekClient.Result("相关内容见 /post/9",null,null,200,30));
        when(search.candidates(any(),eq("posts"),eq(4))).thenReturn(List.of(9L));
        when(search.hydrate("posts",List.of(9L))).thenReturn(List.of(new SearchDtos.Hit("9","posts","[E2E_TEST]",null,"[E2E_TEST]",null,"/post/9")));
        var second=new LumiRepository.Attempt(6,1,LocalDate.now(),1000);when(tasks.next(work)).thenReturn(second);
        worker.execute(work);verify(model,times(2)).complete(any());verify(tasks).publish(work,"相关内容见 /post/9（仅参考部分文字讨论）",List.of(9L));
        verify(tasks).usage(second,200,30);
    }
    @Test void toolCallCannotContinueWithoutGlobalBudget() {
        when(model.complete(any())).thenReturn(new DeepSeekClient.Result(null,"量子","tool-e2e",100,20));
        when(search.candidates(any(),eq("posts"),eq(4))).thenReturn(List.of());when(search.hydrate("posts",List.of())).thenReturn(List.of());
        worker.execute(work);verify(model,times(1)).complete(any());verify(tasks,never()).publish(any(),anyString(),anyList());
    }
    @Test void finalPreflightFailureNeverCallsExternalModel() {
        when(tasks.sending(eq(work),any(),anyList())).thenReturn(false);worker.execute(work);verify(model,never()).complete(any());
    }
}
