package com.heng.auth;

import org.springframework.stereotype.Component;

/** 公开接口按需解析身份：带 Token 就按会员等级折算价格，不带就按原价。 */
@Component
public class MemberContext {
    private final MemberTokenService tokenService;

    public MemberContext(MemberTokenService tokenService) {
        this.tokenService = tokenService;
    }

    public Long from(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        return tokenService.resolve(authorization.substring(7).trim());
    }
}
