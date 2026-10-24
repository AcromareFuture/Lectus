package com.heng.controller;

import com.heng.biz.PaymentService;
import com.heng.domain.VPayConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PayController {
    private final PaymentService paymentService;

    public PayController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/pay-methods")
    public ApiResponse<List<Map<String, Object>>> methods() {
        return ApiResponse.ok(paymentService.enabledPayMethods().stream()
                .map(this::toView).toList());
    }

    @PostMapping("/pay")
    public ApiResponse<Map<String, Object>> pay(@RequestAttribute("viridac.memberId") Long memberId,
                                                @Valid @RequestBody PayRequest request) {
        return ApiResponse.ok(paymentService.pay(memberId, request.orderNo(), request.payType()));
    }

    @PostMapping("/pay/refresh")
    public ApiResponse<Map<String,Object>> refresh(@RequestAttribute("viridac.memberId") Long memberId,@Valid @RequestBody PayRequest request){return ApiResponse.ok(paymentService.refresh(memberId,request.orderNo()));}

    /** 第三方渠道回调：按 out_trade_no 幂等，重复回调不会二次结算。 */
    @PostMapping("/pay/callback/{payType}")
    public ApiResponse<Void> callback(@PathVariable String payType,
                                      @RequestHeader(value = "X-Viridac-Pay-Signature", required = false) String signature,
                                      @RequestBody Map<String, Object> payload) {
        Object outTradeNoValue = payload.get("outTradeNo");
        if (outTradeNoValue == null || String.valueOf(outTradeNoValue).isBlank()) {
            throw new com.heng.config.BizException("支付回调缺少交易号");
        }
        String outTradeNo = String.valueOf(outTradeNoValue);
        String transactionId = String.valueOf(payload.getOrDefault("transactionId", outTradeNo));
        boolean success = Boolean.TRUE.equals(payload.get("success"));
        paymentService.callback(payType, outTradeNo, transactionId, success, signature, payload);
        return ApiResponse.ok(null);
    }

    private Map<String, Object> toView(VPayConfig config) {
        String environment = paymentService.paymentEnvironment(config.getPayType());
        return Map.of("payType", config.getPayType(), "enabled", config.getEnabled() == 1, "environment", environment, "sandbox", !"PRODUCTION".equals(environment));
    }

    public record PayRequest(@NotBlank String orderNo, @NotBlank String payType) {
    }
}
