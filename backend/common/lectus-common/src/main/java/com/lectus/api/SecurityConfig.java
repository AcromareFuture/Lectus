package com.lectus.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
class SecurityConfig {
    @Bean
    SecurityFilterChain security(HttpSecurity http, SessionUserResolver auth, ObjectMapper json,
                                 @Value("${lectus.cors-origins}") String origins) throws Exception {
        return http.csrf(csrf->csrf.disable())
                .cors(cors->cors.configurationSource(request->{
                    var config = new CorsConfiguration();
                    config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::strip).toList());
                    config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
                    config.setAllowedHeaders(List.of("Content-Type","Authorization","Idempotency-Key"));
                    config.setMaxAge(3600L);
                    return config;
                }))
                .sessionManagement(session->session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers->headers.cacheControl(cache->{}))
                .authorizeHttpRequests(r->r
                        .requestMatchers(HttpMethod.OPTIONS,"/**").permitAll()
                        .requestMatchers(HttpMethod.GET,"/actuator/health","/actuator/health/**","/api/health","/api/products","/api/products/*","/api/catalog/**").permitAll()
                        .requestMatchers(HttpMethod.POST,"/api/auth/register","/api/auth/login").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e->e.authenticationEntryPoint((req,res,exception)->{
                    res.setStatus(401);res.setContentType("application/json;charset=UTF-8");
                    json.writeValue(res.getOutputStream(),Map.of("code","UNAUTHORIZED","message","请先登录"));
                }).accessDeniedHandler((req,res,exception)->{
                    res.setStatus(403);res.setContentType("application/json;charset=UTF-8");
                    json.writeValue(res.getOutputStream(),Map.of("code","FORBIDDEN","message","无权执行此操作"));
                }))
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                              FilterChain chain) throws ServletException, IOException {
                        String header=request.getHeader("Authorization");
                        if(header!=null && header.startsWith("Bearer ")) {
                            Contracts.User user;
                            try { user=auth.authenticate(header.substring(7)); } catch(ApiException e) {
                                response.setStatus(e.status.value()); response.setContentType("application/json;charset=UTF-8");
                                json.writeValue(response.getOutputStream(),Map.of("code",e.code,"message",e.getMessage())); return;
                            }
                            if(user!=null) SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(user,null,List.of()));
                        }
                        chain.doFilter(request,response);
                    }
                }, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
