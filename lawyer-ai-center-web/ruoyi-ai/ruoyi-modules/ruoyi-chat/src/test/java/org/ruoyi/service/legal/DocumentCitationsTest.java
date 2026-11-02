package org.ruoyi.service.legal;

import dev.langchain4j.model.chat.response.*;
import dev.langchain4j.data.message.AiMessage;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DocumentCitationsTest {
    private static final String CITATION="{\"type\":\"law\",\"lawName\":\"中华人民共和国民法典\",\"articleNo\":\"第577条\",\"status\":\"effective\",\"caseNo\":null,\"title\":null,\"court\":null,\"sourceUrl\":null,\"fragmentId\":\"test\"}";
    @Test void removesSingleObjectsAndArraysWithoutRemovingDocumentJson(){
        for(String metadata:List.of(CITATION,"["+CITATION+"]","```json\n"+CITATION+"\n```","```legal-citations\n["+CITATION+"]\n```"))
            assertEquals("律师函\n正文",DocumentCitations.clean("律师函\n正文\n\n"+metadata));
        String business="{\"合同编号\":\"A-1\",\"备注\":\"含有 { 和 }\"}";
        assertEquals(business,DocumentCitations.clean(business));
        String protocol="{\"documentTemplate\":\"version\",\"blocks\":[{\"id\":\"p1\",\"text\":\"正文\"}]}";
        assertEquals(protocol,DocumentCitations.clean(protocol));
    }
    @Test void publishesCleanedTextOnceForStreamingAndCompletionOnlyModels(){
        for(boolean streaming:List.of(true,false)){
            var events=new ArrayList<String>();var sink=new StreamingChatResponseHandler(){
                public void onPartialResponse(String value){events.add(value);}
                public void onCompleteResponse(ChatResponse value){events.add("done");}
                public void onError(Throwable error){fail(error);}
            };
            var wrapped=DocumentCitations.wrap(sink);String raw="律师函\n正文\n"+CITATION;
            if(streaming)for(int i=0;i<raw.length();i+=7)wrapped.onPartialResponse(raw.substring(i,Math.min(i+7,raw.length())));
            var response=ChatResponse.builder().aiMessage(AiMessage.from(raw)).build();
            wrapped.onCompleteResponse(response);wrapped.onCompleteResponse(response);wrapped.onPartialResponse("late");
            assertEquals(List.of("律师函\n正文","done"),events);
        }
    }
    @Test void errorsNeverPublishAnUncleanedPartialDocument(){
        var events=new ArrayList<String>();var sink=new StreamingChatResponseHandler(){
            public void onPartialResponse(String value){events.add(value);}
            public void onCompleteResponse(ChatResponse value){events.add("done");}
            public void onError(Throwable error){events.add("error");}
        };
        var wrapped=DocumentCitations.wrap(sink);wrapped.onPartialResponse(CITATION);wrapped.onError(new IllegalStateException());wrapped.onCompleteResponse(null);
        assertEquals(List.of("error"),events);
    }
    @Test void templateEditableTextIsCleanedAndFixedTextIsPreserved()throws Exception{
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var expected=new TemplateDrafting.Document("version",List.of(new TemplateLayout.Block("p0","律师函",false),new TemplateLayout.Block("p1","{{正文}}",true)));
        var answer=new TemplateDrafting.Document("version",List.of(new TemplateLayout.Block("p1","当前正文\n"+CITATION,true)));
        String request="---版式填写JSON开始---\n"+json.writeValueAsString(expected)+"\n---版式填写JSON结束---";
        var result=TemplateDrafting.read(TemplateDrafting.finish(json.writeValueAsString(answer),request));
        assertEquals("律师函",result.blocks().get(0).text());assertEquals("当前正文",result.blocks().get(1).text());
    }
}
