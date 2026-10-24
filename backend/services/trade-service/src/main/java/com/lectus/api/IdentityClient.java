package com.lectus.api;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;
@Component
public class IdentityClient implements SessionUserResolver {
 private final RestClient client;
 public IdentityClient(@Value("${lectus.identity-url}") String url) {
  var http=java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(2)).build();
  var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(http);
  factory.setReadTimeout(java.time.Duration.ofSeconds(3));
  client=RestClient.builder().baseUrl(url).requestFactory(factory).build();
 }
 public Contracts.User authenticate(String token) {
  if(token==null || token.length()!=43) return null;
  try {return client.get().uri("/api/auth/me").header("Authorization","Bearer "+token).retrieve().body(Contracts.User.class);}
  catch(HttpClientErrorException.Unauthorized e){return null;}
  catch(org.springframework.web.client.RestClientException e){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"IDENTITY_UNAVAILABLE","认证服务暂时不可用");}
 }
}