package com.heng.config;

import com.heng.auth.MemberAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.beans.factory.annotation.Value;
import java.util.Arrays;
import java.util.List;

/** C 端接口默认需要会员 Token，公开路径显式白名单，避免新增接口默认裸奔。 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private static final String[] PUBLIC_PATHS = {
            "/api/member/login", "/api/member/register", "/api/lectus-demo/scan", "/api/mall/public/**", "/api/mall/workspace/**", "/api/community/public/**",
            "/api/member/sms/status", "/api/member/sms/send",
            "/api/member/phone/login", "/api/member/phone/register", "/api/member/phone/password/reset",
            "/api/products/**", "/api/categories", "/api/home", "/api/stores",
            "/api/articles/**", "/api/seckills", "/api/pay-methods", "/api/pay/callback/**", "/api/pay/provider/**",
            "/api/logistics/demo",
            "/api/logistics/tracking-demo",
            "/api/points-mall", "/api/member/levels", "/api/member/avatar/*"
    };
    private final MemberAuthInterceptor authInterceptor;
    private final List<String> allowedOrigins;

    public WebConfig(MemberAuthInterceptor authInterceptor,
                     @Value("${viridac.web.allowed-origins:http://localhost:5174,http://127.0.0.1:5174,http://localhost:5175,http://127.0.0.1:5175}") String origins) {
        this.authInterceptor = authInterceptor;
        this.allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim).filter(value -> !value.isBlank()).toList();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(PUBLIC_PATHS);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
