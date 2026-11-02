package org.ruoyi.service.embed;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.core.exception.ServiceException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class TeiHttpClientTest {
    @Test void actualTransportClassifiesStatusAndMalformedJsonWithoutRetainingResponseBodies() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        for(String route:new String[]{"failure","invalid"}) {
            server.createContext("/"+route+"/embed",exchange->{
                exchange.getRequestBody().readAllBytes();
                byte[] body="private contract and credential".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(route.equals("failure")?429:200,body.length);
                exchange.getResponseBody().write(body);exchange.close();
            });
        }
        server.start();
        try {
            var config=new ChatModelVo();
            config.setApiHost("http://127.0.0.1:"+server.getAddress().getPort()+"/failure");
            var status=assertThrows(ServiceException.class,()->TeiHttpClient.post(config,"/embed",Map.of("inputs",new String[]{"question"})));
            assertEquals("TEI_HTTP_429",status.getDetailMessage());
            assertFalse(status.getMessage().contains("private"));assertNull(status.getCause());
            config.setApiHost("http://127.0.0.1:"+server.getAddress().getPort()+"/invalid");
            var malformed=assertThrows(ServiceException.class,()->TeiHttpClient.post(config,"/embed",Map.of("inputs",new String[]{"question"})));
            assertEquals("TEI_INVALID_RESPONSE",malformed.getDetailMessage());
            assertFalse(malformed.getMessage().contains("private"));assertNull(malformed.getCause());
        } finally {server.stop(0);}
    }
}
