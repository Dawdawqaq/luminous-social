package com.luminous.api.common;

import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 通过真实 MVC 异常分派区分已关闭响应和实际业务故障，不把断线伪装成模型失败。 */
class ClosedAsyncResponseTest {
    @RestController static class Fixture {
        @GetMapping("/e2e/closed") String closed() throws Exception {throw new AsyncRequestNotUsableException("[E2E_TEST] 客户端已断开");}
        @GetMapping("/e2e/fault") String fault(){throw new IllegalStateException("[E2E_TEST] 真实故障");}
    }
    private final MockMvc mvc=MockMvcBuilders.standaloneSetup(new Fixture()).setControllerAdvice(new GlobalExceptionHandler()).build();
    @Test void disconnectedStreamDoesNotWriteAnErrorBody() throws Exception {mvc.perform(get("/e2e/closed")).andExpect(status().isOk()).andExpect(content().string(""));}
    @Test void unrelatedServerFailureStillReturnsInternalError() throws Exception {mvc.perform(get("/e2e/fault")).andExpect(status().isInternalServerError()).andExpect(jsonPath("error.code").value("INTERNAL_ERROR"));}
}
