package com.luminous.api.lumi;

import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.util.function.Consumer;

/** 仅解析有界的内容增量和用量；缺少结束标记、工具调用及截断均不冒充完整回答。 */
final class DeepSeekStreamParser {
    private final ObjectMapper mapper;
    DeepSeekStreamParser(ObjectMapper mapper){this.mapper=mapper;}
    DeepSeekClient.Result read(Reader reader,Consumer<String> progress) throws IOException {
        var answer=new StringBuilder();var event=new StringBuilder();var line=new StringBuilder();
        String finish=null;Integer input=null,output=null;int total=0;boolean done=false;
        while(true){
            int value=reader.read();
            if(value<0)break;
            if(++total>262144)throw new DeepSeekClient.Failure("模型返回内容过大，已停止接收");
            if(value=='\r')continue;
            if(value!='\n'){if(line.length()>=32768)throw new DeepSeekClient.Failure("模型返回格式异常");line.append((char)value);continue;}
            String current=line.toString();line.setLength(0);
            if(!current.isEmpty()){
                if(current.startsWith("data:")){if(!event.isEmpty())event.append('\n');event.append(current.substring(5).stripLeading());}
                continue;
            }
            if(event.isEmpty())continue;
            String data=event.toString();event.setLength(0);
            if("[DONE]".equals(data)){done=true;break;}
            JsonNode root=mapper.readTree(data);
            if(root.has("error"))throw new DeepSeekClient.Failure("模型服务暂时无法完成请求");
            var usage=root.path("usage");
            if(usage.path("prompt_tokens").canConvertToInt()&&usage.path("completion_tokens").canConvertToInt()){
                input=usage.path("prompt_tokens").intValue();output=usage.path("completion_tokens").intValue();
                if(input<0||output<0){input=null;output=null;}
            }
            for(var choice:root.path("choices")){
                if(choice.path("index").asInt()!=0)continue;
                var delta=choice.path("delta");
                if(delta.path("tool_calls").size()>0)throw new DeepSeekClient.Failure("聊天暂不支持工具操作");
                String content=delta.path("content").asText("");
                if(answer.length()+content.length()>4000)throw new DeepSeekClient.Failure("回答已达到长度上限，已保留生成内容");
                if(!content.isEmpty()){answer.append(content);progress.accept(answer.toString());}
                if(!choice.path("finish_reason").isNull()&&!choice.path("finish_reason").isMissingNode())finish=choice.path("finish_reason").asText();
            }
        }
        if(!done||!"stop".equals(finish)||answer.toString().isBlank())throw new DeepSeekClient.Failure("回答未完成，已保留生成内容，可重试");
        return new DeepSeekClient.Result(answer.toString(),null,null,input,output);
    }
}
