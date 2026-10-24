package com.lectus.api;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
class IdentityClientTest {
 HttpServer upstream; IdentityClient client; int status=200; String body="{\"id\":\"id-1\",\"email\":\"a@example.test\",\"displayName\":\"User\"}";
 String authorization;
 @BeforeEach void setup()throws Exception{
  upstream=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  upstream.createContext("/api/auth/me",e->{authorization=e.getRequestHeaders().getFirst("Authorization");byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(status,bytes.length);e.getResponseBody().write(bytes);e.close();});upstream.start();
  client=new IdentityClient("http://127.0.0.1:"+upstream.getAddress().getPort());
 }
 @AfterEach void close(){upstream.stop(0);}
 @Test void verifiesOpaqueTokenWithIdentityService(){String token="a".repeat(43);assertEquals("id-1",client.authenticate(token).id());assertEquals("Bearer "+token,authorization);}
 @Test void rejectsExpiredAndInvalidTokens(){status=401;assertNull(client.authenticate("a".repeat(43)));assertNull(client.authenticate("invalid"));}
 @Test void failsClosedWhenIdentityIsUnavailable(){status=503;var error=assertThrows(ApiException.class,()->client.authenticate("a".repeat(43)));assertEquals("IDENTITY_UNAVAILABLE",error.code);}
}
