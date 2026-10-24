package com.heng.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.heng.domain.VMember;
import com.heng.service.VMemberService;

@Component
public class MemberAuthInterceptor implements HandlerInterceptor {
    public static final String MEMBER_ID = "viridac.memberId";
    private final MemberTokenService tokenService;
    private final VMemberService memberService;

    public MemberAuthInterceptor(MemberTokenService tokenService, VMemberService memberService) {
        this.tokenService = tokenService;
        this.memberService = memberService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        Long memberId = tokenService.resolve(token);
        if (memberId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        VMember member = memberService.getById(memberId);
        if (member == null || !Integer.valueOf(1).equals(member.getStatus())) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        request.setAttribute(MEMBER_ID, memberId);
        return true;
    }
}
