package org.ruoyi.service.embed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.core.exception.ServiceException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.nio.charset.StandardCharsets;

/** TEI transport: no redirects or payload logs; bounded responses and generic failures. */
public final class TeiHttpClient {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final HttpClient CLIENT=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private TeiHttpClient(){}
    public static JsonNode post(ChatModelVo config,String path,Object body){
        try{
            URI base=URI.create(config.getApiHost());
            if(!java.util.Set.of("http","https").contains(base.getScheme())||base.getHost()==null||base.getUserInfo()!=null||base.getQuery()!=null||base.getFragment()!=null)throw new IllegalArgumentException();
            String prefix=base.toString().replaceAll("/+$","");
            if(prefix.endsWith("/v1"))prefix=prefix.substring(0,prefix.length()-3);
            if(prefix.endsWith("/embed")||prefix.endsWith("/rerank"))prefix=prefix.substring(0,prefix.lastIndexOf('/'));
            var request=HttpRequest.newBuilder(URI.create(prefix+path)).timeout(Duration.ofSeconds(60)).header("Content-Type","application/json");
            if(config.getApiKey()!=null&&!config.getApiKey().isBlank())request.header("Authorization","Bearer "+config.getApiKey());
            var response=CLIENT.send(request.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body),StandardCharsets.UTF_8)).build(),HttpResponse.BodyHandlers.ofInputStream());
            try(var stream=response.body()){
                if(response.statusCode()!=200)throw new ServiceException("TEI接口调用失败 HTTP "+response.statusCode()).setDetailMessage("TEI_HTTP_"+response.statusCode());
                byte[] bytes=stream.readNBytes(32*1024*1024+1);
                if(bytes.length>32*1024*1024)throw new ServiceException("TEI响应过大");
                return JSON.readTree(bytes);
            }
        }catch(ServiceException e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new ServiceException("TEI请求已中断").setDetailMessage("TEI_INTERRUPTED");}
        catch(Exception e){
            String category=e instanceof HttpTimeoutException?"TIMEOUT":e instanceof java.net.ConnectException?"CONNECTION":
                e instanceof com.fasterxml.jackson.core.JsonProcessingException?"INVALID_RESPONSE":e instanceof IllegalArgumentException?"CONFIGURATION":"TRANSPORT";
            throw new ServiceException("TEI接口不可用，请检查模型服务及网络连接").setDetailMessage("TEI_"+category);
        }
    }
}
