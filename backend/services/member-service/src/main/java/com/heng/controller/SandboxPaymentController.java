package com.heng.controller;
import com.heng.biz.SandboxPaymentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@ConditionalOnProperty(name="viridac.sandbox.confirm-enabled",havingValue="true")
@RequestMapping("/api/pay/sandbox")
public class SandboxPaymentController {
 private final SandboxPaymentService service;
 public SandboxPaymentController(SandboxPaymentService service){this.service=service;}
 @PostMapping("/confirm") public ApiResponse<?> confirm(@RequestAttribute("viridac.memberId") long member,@Valid @RequestBody PayController.PayRequest form){return ApiResponse.ok(service.confirm(member,form.orderNo(),form.payType()));}
}
