package com.heng.controller;

import com.heng.auth.MemberTokenService;
import com.heng.biz.MemberCenterService;
import com.heng.config.BizException;
import com.heng.domain.VMember;
import com.heng.service.VMemberService;
import com.heng.vo.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping("/api/member")
public class MemberController {
    private final VMemberService memberService;
    private final MemberTokenService tokenService;
    private final MemberCenterService memberCenterService;
    private final com.heng.auth.PasswordLoginGuard loginGuard;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public MemberController(VMemberService memberService, MemberTokenService tokenService,
                            MemberCenterService memberCenterService, com.heng.auth.PasswordLoginGuard loginGuard) {
        this.loginGuard = loginGuard;
        this.memberService = memberService;
        this.tokenService = tokenService;
        this.memberCenterService = memberCenterService;
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        if (memberService.lambdaQuery().eq(VMember::getUsername, request.username()).exists()) {
            throw new BizException("用户名已存在");
        }
        com.heng.auth.PasswordLoginGuard.validatePassword(request.password());
        VMember member = new VMember();
        member.setUsername(request.username());
        member.setPassword(passwordEncoder.encode(request.password()));
        member.setNickname(request.nickname() == null ? request.username() : request.nickname());
        member.setStatus(1);
        member.setLevelId(1L);
        member.setGrowth(0);
        member.setPoints(0);
        member.setBalanceFen(0L);
        memberService.save(member);
        return ApiResponse.ok(loginView(member));
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request, jakarta.servlet.http.HttpServletRequest http) {
        com.heng.auth.PasswordLoginGuard.validatePassword(request.password());
        loginGuard.attempt(request.username(), http.getRemoteAddr());
        VMember member = memberService.lambdaQuery().eq(VMember::getUsername, request.username()).one();
        boolean valid = member != null && Integer.valueOf(1).equals(member.getStatus())
                && passwordEncoder.matches(request.password(), member.getPassword());
        if (!valid) throw new BizException("用户名或密码错误");
        memberService.lambdaUpdate().eq(VMember::getMemberId, member.getMemberId())
                .set(VMember::getLastLoginTime, new Date()).update();
        return ApiResponse.ok(loginView(member));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@RequestAttribute("viridac.memberId") Long memberId) {
        return ApiResponse.ok(memberCenterService.profile(memberId));
    }

    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(@RequestAttribute("viridac.memberId") Long memberId,
                                                         @RequestBody VMember form) {
        memberCenterService.updateProfile(memberId, form);
        return ApiResponse.ok(memberCenterService.profile(memberId));
    }

    @PutMapping("/password")
    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public ApiResponse<Void> changePassword(@RequestAttribute("viridac.memberId") Long memberId, @Valid @RequestBody PasswordRequest form) {
        VMember member=memberCenterService.member(memberId);
        if (!passwordEncoder.matches(form.currentPassword(),member.getPassword())) throw new BizException("当前密码不正确");
        if(form.currentPassword().equals(form.newPassword())) throw new BizException("新密码不能与当前密码相同");
        com.heng.auth.PasswordLoginGuard.validatePassword(form.newPassword());
        boolean changed = memberService.lambdaUpdate().eq(VMember::getMemberId, memberId)
                .eq(VMember::getPassword, member.getPassword())
                .set(VMember::getPassword, passwordEncoder.encode(form.newPassword())).update();
        if (!changed) throw new BizException("密码已变更，请重新登录后再试");
        tokenService.revokeAll(memberId);
        return ApiResponse.ok(null);
    }
    public record PasswordRequest(@NotBlank @jakarta.validation.constraints.Size(max=64) String currentPassword,
                                  @NotBlank @jakarta.validation.constraints.Size(min=8,max=64) String newPassword) { }

    @GetMapping("/levels")
    public ApiResponse<?> levels() { return ApiResponse.ok(memberCenterService.levels()); }

    @PostMapping("/signin")
    public ApiResponse<Map<String, Object>> signIn(@RequestAttribute("viridac.memberId") Long memberId) {
        return ApiResponse.ok(memberCenterService.signIn(memberId));
    }

    @GetMapping("/points")
    public ApiResponse<PageResult<Map<String, Object>>> points(@RequestAttribute("viridac.memberId") Long memberId,
                                                              @RequestParam(defaultValue = "1") long page,
                                                              @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(memberCenterService.pointLedgers(memberId, page, Math.min(size, 50)));
    }

    @GetMapping("/balance")
    public ApiResponse<PageResult<Map<String, Object>>> balance(@RequestAttribute("viridac.memberId") Long memberId,
                                                               @RequestParam(defaultValue = "1") long page,
                                                               @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.ok(memberCenterService.balanceLedgers(memberId, page, Math.min(size, 50)));
    }

    @PostMapping("/deposit")
    public ApiResponse<Map<String, Object>> deposit(@RequestAttribute("viridac.memberId") Long memberId,
                                                   @Valid @RequestBody DepositRequest request) {
        if (!memberCenterService.isDevDepositAllowed()) {
            throw new BizException("余额充值暂未开放");
        }
        memberCenterService.deposit(memberId, request.amountFen());
        return ApiResponse.ok(memberCenterService.profile(memberId));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false)
                                    String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            tokenService.revoke(authorization.substring(7));
        }
        return ApiResponse.ok(null);
    }

    private Map<String, Object> loginView(VMember member) {
        return Map.of("memberId", member.getMemberId(),
                "token", tokenService.issue(member.getMemberId()),
                "nickname", member.getNickname() == null ? "" : member.getNickname());
    }

    public record RegisterRequest(@NotBlank @jakarta.validation.constraints.Pattern(regexp="[A-Za-z0-9_]{3,50}") String username, @NotBlank @jakarta.validation.constraints.Size(min=6,max=64) String password, @jakarta.validation.constraints.Size(max=30) String nickname) { }
    public record LoginRequest(@NotBlank @jakarta.validation.constraints.Size(max=50) String username, @NotBlank @jakarta.validation.constraints.Size(max=64) String password) { }
    public record DepositRequest(@Min(value = 100, message = "充值金额最低 1 元") long amountFen) { }
}
