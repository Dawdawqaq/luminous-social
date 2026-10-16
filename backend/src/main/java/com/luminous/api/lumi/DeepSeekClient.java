package com.luminous.api.lumi;

import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.io.*;

/** 只请求固定 HTTPS 服务，不跟随重定向，也不把错误响应或认证头写入日志。 */
@Component
public class DeepSeekClient {
    public record Result(String content,String query,String toolId,Integer inputTokens,Integer outputTokens) {}
    public static class Failure extends RuntimeException {
        public Failure(String message) { super(message); }
    }
    private final LumiProperties p;
    private final ObjectMapper mapper;
    private static final ScheduledExecutorService DEADLINES=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"lumi-stream-deadline");t.setDaemon(true);return t;});
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public DeepSeekClient(LumiProperties p,ObjectMapper mapper) {this.p=p;this.mapper=mapper;}
    public byte[] requestBody(List<Map<String,Object>> messages,boolean tools) {
        return requestBody(messages,tools,p.getMaxOutputTokens());
    }
    public byte[] requestBody(List<Map<String,Object>> messages,boolean tools,int outputTokens) {
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("model",p.getModel());body.put("messages",messages);body.put("stream",false);
        body.put("thinking",Map.of("type","disabled"));body.put("temperature",0.3);body.put("max_tokens",outputTokens);
        if(tools) body.put("tools",List.of(Map.of("type","function","function",Map.of("name","search_posts",
                "description","仅当用户需要站内其他帖子时检索公开帖子；不是联网搜索。query 应为少量核心关键词，最多50字符。",
                "parameters",Map.of("type","object","properties",Map.of("query",Map.of("type","string","maxLength",50)),"required",List.of("query"),"additionalProperties",false)))));
        try {
            byte[] bytes=mapper.writeValueAsBytes(body);
            if(bytes.length>p.getMaxInputBytes()) throw new Failure("帖子上下文过长，请缩小问题范围");
            return bytes;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {throw new Failure("Lumi 请求准备失败");}
    }
    public Result complete(byte[] body) {
        try {
            var request=HttpRequest.newBuilder(URI.create("https://api.deepseek.com/chat/completions"))
                    .timeout(Duration.ofSeconds(p.getTimeoutSeconds())).header("Authorization","Bearer "+p.getApiKey())
                    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            var pending=http.sendAsync(request,HttpResponse.BodyHandlers.ofByteArray());
            HttpResponse<byte[]> response;
            try {response=pending.get(p.getTimeoutSeconds(),java.util.concurrent.TimeUnit.SECONDS);}
            catch(Exception ex){pending.cancel(true);throw ex;}
            byte[] bytes=response.body();
            return parse(response.statusCode(),bytes);
        } catch(Failure ex){throw ex;}
        catch(InterruptedException ex){Thread.currentThread().interrupt();throw new Failure("Lumi 请求已中断");}
        catch(Exception ex){throw new Failure("Lumi 模型连接暂不可用，请稍后重试");}
    }
    public byte[] streamBody(List<Map<String,Object>> messages,int outputTokens){
        try{
            var body=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(requestBody(messages,false,outputTokens));
            body.put("stream",true);body.putObject("stream_options").put("include_usage",true);
            byte[] bytes=mapper.writeValueAsBytes(body);
            if(bytes.length>p.getMaxInputBytes())throw new Failure("聊天上下文过长");
            return bytes;
        }catch(IOException ex){throw new Failure("Lumi 请求准备失败");}
    }
    public Result stream(byte[] body,LumiStreamCall call,Consumer<String> progress){
        var deadline=DEADLINES.schedule(call::cancel,p.getTimeoutSeconds(),TimeUnit.SECONDS);
        try{
            if(call.cancelled())throw new Failure("本次回答已停止");
            var request=HttpRequest.newBuilder(URI.create("https://api.deepseek.com/chat/completions"))
                    .timeout(Duration.ofSeconds(p.getTimeoutSeconds())).header("Authorization","Bearer "+p.getApiKey())
                    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            var pending=http.sendAsync(request,HttpResponse.BodyHandlers.ofInputStream());call.pending(pending);
            var response=pending.get(p.getTimeoutSeconds(),TimeUnit.SECONDS);call.input(response.body());
            try(var input=response.body()){
                if(response.statusCode()!=200)throw new Failure(response.statusCode()==429?"Lumi 服务繁忙，请稍后重试":"模型服务暂时无法完成请求");
                return new DeepSeekStreamParser(mapper).read(new InputStreamReader(input,StandardCharsets.UTF_8),progress);
            }
        }catch(Failure ex){throw ex;}
        catch(InterruptedException ex){Thread.currentThread().interrupt();throw new Failure("本次回答已中断，已保留生成内容");}
        catch(Exception ex){throw new Failure("连接已中断，已保留生成内容，可重试");}
        finally{deadline.cancel(false);call.cancel();}
    }
    Result parse(int status,byte[] bytes) {
        try {
            if(status!=200||bytes.length>65536) throw new Failure(status==429?"Lumi 服务繁忙，请稍后重试":"模型服务暂时无法完成请求");
            JsonNode root=mapper.readTree(bytes),choice=root.path("choices").path(0),message=choice.path("message");
            var usage=root.path("usage");
            Integer input=usage.path("prompt_tokens").canConvertToInt()?usage.path("prompt_tokens").intValue():null;
            Integer output=usage.path("completion_tokens").canConvertToInt()?usage.path("completion_tokens").intValue():null;
            if(input!=null&&input<0||output!=null&&output<0) {input=null;output=null;}
            if(!message.path("tool_calls").isMissingNode()&&message.path("tool_calls").size()>0) {
                if(message.path("tool_calls").size()!=1) throw new Failure("Lumi 请求了超出范围的工具操作");
                var tool=message.path("tool_calls").path(0);
                if(!"search_posts".equals(tool.path("function").path("name").asText())) throw new Failure("Lumi 请求了不支持的操作");
                String args=tool.path("function").path("arguments").asText();
                if(args.length()>512) throw new Failure("站内查询参数过长");
                String query=mapper.readTree(args).path("query").asText("");
                return new Result(null,query,tool.path("id").asText(),input,output);
            }
            String answer=message.path("content").asText("").trim();
            if(!"stop".equals(choice.path("finish_reason").asText())||answer.isBlank()) throw new Failure("Lumi 未能生成完整回复，请稍后重试");
            return new Result(answer,null,null,input,output);
        } catch(Failure ex){throw ex;}
        catch(Exception ex){throw new Failure("Lumi 模型连接暂不可用，请稍后重试");}
    }
}
