package com.lectus.gateway;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIntegrationTest {
 static final Map<String,HttpServer> upstreams=new HashMap<>();
 @LocalServerPort int port;
 @DynamicPropertySource static void upstreams(DynamicPropertyRegistry registry)throws Exception{
  for(String name:List.of("identity","catalog","trade","member")){
   var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
   server.createContext("/",ex->{
    var headers=ex.getRequestHeaders();
    String result=name+":"+ex.getRequestURI().toString()+":"+headers.getFirst("Authorization")+":"+headers.getFirst("X-User-Id")+":"+headers.getFirst("X-Tenant-Id")+":"+headers.getFirst("X-Forwarded-For");
    byte[] data=result.getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(200,data.length);ex.getResponseBody().write(data);ex.close();
   });server.start();upstreams.put(name,server);
   registry.add("services."+name,()->"http://127.0.0.1:"+server.getAddress().getPort());
  }
 }
 @AfterAll static void close(){upstreams.values().forEach(s->s.stop(0));}
 HttpResponse<String> get(String path)throws Exception{
  return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Authorization","Bearer preserved-token").header("X-User-Id","forged").header("X-Tenant-Id","forged").header("X-Forwarded-For","203.0.113.90").GET().build(),HttpResponse.BodyHandlers.ofString());
 }
 @Test void routesDomainsAndKeepsQueryString()throws Exception{
  for(var pair:Map.of("/api/auth/me","identity","/api/catalog/products?q=shoe","catalog","/api/products","trade","/api/member/me","member","/api/addresses","member","/api/community/public/feed?kind=VIDEO","member").entrySet()){
   var response=get(pair.getKey());assertEquals(200,response.statusCode());assertTrue(response.body().startsWith(pair.getValue()+":"+pair.getKey()+":"),response.body());
  }
 }
 @Test void legacyNamespaceResolvesAddressCollision()throws Exception{
  assertTrue(get("/api/legacy/auth/me").body().startsWith("identity:/api/auth/me:"));
  assertTrue(get("/api/legacy/addresses").body().startsWith("trade:/api/addresses:"));
 }
 @Test void dropsSpoofedIdentityAndForwardingHeaders()throws Exception{
  var result=get("/api/cart");assertTrue(result.body().contains(":Bearer preserved-token:null:null:"));assertFalse(result.body().contains("203.0.113.90"));
 }
 @Test void doesNotRouteUnknownApis()throws Exception{assertEquals(404,get("/api/unknown").statusCode());}
}
