package com.heng.controller;

import com.heng.auth.PhoneAuthService;
import com.heng.auth.PhoneAuthService.Purpose;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/member")
public class PhoneAuthController {
    private final PhoneAuthService auth;
    public PhoneAuthController(PhoneAuthService auth) { this.auth = auth; }

    @GetMapping("/sms/status") public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(Map.of("available", auth.available()));
    }
    @PostMapping("/sms/send") public ApiResponse<Map<String, Object>> send(@Valid @RequestBody SendRequest form, HttpServletRequest request) {
        if (form.purpose() == Purpose.BIND) throw new com.heng.config.BizException("请在账户安全页面发送绑定验证码");
        // 只使用实际远端地址；不相信客户端伪造的 X-Forwarded-For。
        return ApiResponse.ok(auth.send(form.phone(), form.purpose(), request.getRemoteAddr()));
    }
    @PostMapping("/phone/login") public ApiResponse<Map<String, Object>> login(@Valid @RequestBody VerifyRequest form) {
        return ApiResponse.ok(auth.authenticate(form.phone(), Purpose.LOGIN, form.challengeId(), form.code(), null));
    }
    @PostMapping("/phone/register") public ApiResponse<Map<String, Object>> register(@Valid @RequestBody VerifyRequest form) {
        return ApiResponse.ok(auth.authenticate(form.phone(), Purpose.REGISTER, form.challengeId(), form.code(), form.nickname()));
    }
    @PostMapping("/phone/bind/send") public ApiResponse<Map<String,Object>> sendBinding(@RequestAttribute("viridac.memberId") Long memberId, @Valid @RequestBody BindingSend form, HttpServletRequest request) {
        return ApiResponse.ok(auth.sendBinding(memberId,form.phone(),form.password(),request.getRemoteAddr()));
    }
    @PostMapping("/phone/bind") public ApiResponse<Map<String,Object>> bind(@RequestAttribute("viridac.memberId") Long memberId, @Valid @RequestBody VerifyRequest form) {
        return ApiResponse.ok(auth.bindPhone(memberId,form.phone(),form.challengeId(),form.code()));
    }
    @PostMapping("/phone/password/reset") public ApiResponse<?> reset(@Valid @RequestBody ResetRequest form) {
        auth.resetPassword(form.phone(),form.challengeId(),form.code(),form.newPassword());return ApiResponse.ok(null);
    }
    public record ResetRequest(@NotNull @Pattern(regexp="1[3-9][0-9]{9}") String phone,@NotNull @Pattern(regexp="[a-f0-9]{32}") String challengeId,@NotNull @Pattern(regexp="[0-9]{6}") String code,@NotNull @Size(min=8,max=64) String newPassword) { }
    public record BindingSend(@NotNull @Pattern(regexp="1[3-9][0-9]{9}") String phone, @NotNull @Size(min=6,max=64) String password) { }
    public record SendRequest(@NotNull @Pattern(regexp="1[3-9][0-9]{9}") String phone, @NotNull Purpose purpose) { }
    public record VerifyRequest(@NotNull @Pattern(regexp="1[3-9][0-9]{9}") String phone,
                               @NotNull @Pattern(regexp="[a-f0-9]{32}") String challengeId,
                               @NotNull @Pattern(regexp="[0-9]{6}") String code, @Size(max=30) String nickname) { }
}
