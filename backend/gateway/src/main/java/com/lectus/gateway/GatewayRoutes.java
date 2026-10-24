package com.lectus.gateway;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.*;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
@Configuration
class GatewayRoutes {
 @Bean RouteLocator routes(RouteLocatorBuilder b,
  @Value("${services.identity}") String identity,@Value("${services.catalog}") String catalog,
  @Value("${services.trade}") String trade,@Value("${services.member}") String member) {
  return b.routes()
   .route("legacy-auth",r->r.path("/api/legacy/auth/**").filters(f->f.rewritePath("/api/legacy/(?<rest>.*)","/api/${rest}")).uri(identity))
   .route("legacy-trade",r->r.path("/api/legacy/**").filters(f->f.rewritePath("/api/legacy/(?<rest>.*)","/api/${rest}")).uri(trade))
   .route("member-platform",r->r.path("/api/member-platform/**").filters(f->f.rewritePath("/api/member-platform/(?<rest>.*)","/api/${rest}")).uri(member))
   .route("mall",r->r.path("/api/mall/**").uri(member))
   .route("community",r->r.path("/api/community/**").uri(member))
   .route("identity",r->r.path("/api/auth/**").uri(identity))
   .route("catalog",r->r.path("/api/catalog/**").uri(catalog))
   .route("member",r->r.path("/api/member/**","/api/lectus-demo/**","/api/addresses","/api/addresses/**").uri(member))
   .route("trade",r->r.path("/api/products","/api/products/**","/api/cart","/api/cart/**","/api/orders","/api/orders/**").uri(trade))
   .build();
 }
}
