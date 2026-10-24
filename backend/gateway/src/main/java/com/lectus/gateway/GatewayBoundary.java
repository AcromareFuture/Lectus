package com.lectus.gateway;
import org.springframework.cloud.gateway.filter.*;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
@Component
class GatewayBoundary implements GlobalFilter, Ordered {
 public int getOrder(){return -100;}
 public Mono<Void> filter(ServerWebExchange exchange,GatewayFilterChain chain){
  var request=exchange.getRequest().mutate().headers(headers->{
   headers.keySet().removeIf(k->{var n=k.toLowerCase(java.util.Locale.ROOT);return n.startsWith("x-user")||n.startsWith("x-tenant")||n.startsWith("x-merchant")||n.startsWith("x-forwarded")||n.equals("forwarded")||n.equals("x-real-ip");});
  }).build();
  return chain.filter(exchange.mutate().request(request).build());
 }
}
