package com.lectus.api;

import static com.lectus.api.Contracts.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
class AuthController {
    private final AuthService auth;
    private final Map<String, Attempt> attempts = new HashMap<>();
    record Attempt(int count, long until) {}
    AuthController(AuthService auth) {this.auth=auth;}
    // Single-instance rate limit. A shared limit belongs at the gateway for multiple replicas.
    private synchronized void limit(String key) {
        long now=Instant.now().getEpochSecond();
        attempts.entrySet().removeIf(entry->entry.getValue().until()<=now);
        if(attempts.size()>10000) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED","请求过于频繁，请稍后再试");
        Attempt current=attempts.get(key);
        if(current!=null && current.count()>=20)
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED","请求过于频繁，请稍后再试");
        attempts.put(key,new Attempt(current==null?1:current.count()+1,current==null?now+900:current.until()));
    }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    Session register(@Valid @RequestBody Register body, HttpServletRequest request) {
        limit(request.getRemoteAddr());return auth.register(body);
    }
    @PostMapping("/login")
    Session login(@Valid @RequestBody Login body, HttpServletRequest request) {
        limit(request.getRemoteAddr());return auth.login(body);
    }
    @GetMapping("/me") User me(@AuthenticationPrincipal User user) {return user;}
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@RequestHeader("Authorization") String header) {auth.logout(header.substring(7));}
}
