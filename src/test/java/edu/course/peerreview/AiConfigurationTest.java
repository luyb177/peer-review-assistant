package edu.course.peerreview;

import com.sun.net.httpserver.HttpServer;
import edu.course.peerreview.ai.*;
import edu.course.peerreview.config.AppConfig;
import edu.course.peerreview.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class AiConfigurationTest {
    @TempDir Path dir;
    AppConfig config(int port){ AppConfig c=new AppConfig(dir.resolve("absent.properties"));c.set("ai.protocol","openai-compatible");c.set("ai.baseUrl","http://127.0.0.1:"+port+"/v1");c.set("ai.apiKey","test-only");c.set("ai.model","test-model");c.set("ai.enabled","true");c.set("ai.useEnvironmentKey","false");return c; }
    @Test void modelsProbeUsesGetAndNeverCallsGeneration() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); AtomicInteger calls=new AtomicInteger();
        server.createContext("/v1/models",x -> {assertEquals("GET",x.getRequestMethod());assertEquals("Bearer test-only",x.getRequestHeaders().getFirst("Authorization"));calls.incrementAndGet();byte[] b="{\"data\":[{\"id\":\"test-model\"}]}".getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);x.close();});server.start();
        try {assertTrue(AiServices.test(config(server.getAddress().getPort())).contains("未发起生成"));assertEquals(1,calls.get());} finally{server.stop(0);}
    }
    @Test void authenticationErrorDoesNotExposeProviderBodyOrKey() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/v1/models",x -> {byte[] b="private-provider-details".getBytes();x.sendResponseHeaders(401,b.length);x.getResponseBody().write(b);x.close();});server.start();
        try{var e=assertThrows(BusinessException.class,() -> AiServices.test(config(server.getAddress().getPort())));assertFalse(e.getMessage().contains("private-provider-details"));assertFalse(e.getMessage().contains("test-only"));}finally{server.stop(0);}
    }
    @Test void configurableProtocolAdapterReadsReferenceTextOnly() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicReference<String> body=new AtomicReference<>();
        server.createContext("/v1/chat/completions",x -> {body.set(new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));byte[] b="{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"参考意见：请独立核实边界条件。\"}}]}".getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(200,b.length);x.getResponseBody().write(b);x.close();});server.start();
        try{TestFixture f=new TestFixture();var a=f.service.assignment(f.teacher,f.readyId);String text=AiServices.provider(config(server.getAddress().getPort())).generate(new PeerReviewService.AdviceInput(a,"合成作业内容"));assertTrue(text.startsWith("参考意见"));assertTrue(body.get().contains("合成作业内容"));assertTrue(body.get().contains("评分细则"));}finally{server.stop(0);}
    }
    @Test void v1BasePathsAreNotDuplicated(){assertEquals("https://example.com/v1/models",OpenAiCompatibleAdviceProvider.endpoint("https://example.com/v1","models").toString());assertEquals("https://example.com/v1/models",OpenAiCompatibleAdviceProvider.endpoint("https://example.com","models").toString());assertEquals("https://example.com",AiServices.anthropicBase("https://example.com/v1/"));}
}
