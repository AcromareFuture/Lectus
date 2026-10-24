package com.heng.demo;

import com.heng.controller.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lectus-demo")
@ConditionalOnProperty(name="lectus.demo.enabled",havingValue="true")
public class LectusDemoController {
    private final LectusDemoService service;
    public LectusDemoController(LectusDemoService service) { this.service=service; }
    @GetMapping("/orders") public ApiResponse<?> list(@RequestAttribute("viridac.memberId") Long member) { return ApiResponse.ok(service.list(member)); }
    @PostMapping("/orders") public ApiResponse<?> create(@RequestAttribute("viridac.memberId") Long member,@Valid @RequestBody Create form) { return ApiResponse.ok(service.create(member,form.productId(),form.payMethod(),form.requestKey())); }
    @GetMapping("/orders/{id}") public ApiResponse<?> get(@RequestAttribute("viridac.memberId") Long member,@PathVariable String id) { return ApiResponse.ok(service.own(member,id)); }
    @PutMapping("/orders/{id}/method") public ApiResponse<?> method(@RequestAttribute("viridac.memberId") Long member,@PathVariable String id,@Valid @RequestBody Method form) { return ApiResponse.ok(service.change(member,id,form.payMethod())); }
    @PostMapping("/orders/{id}/cancel") public ApiResponse<?> cancel(@RequestAttribute("viridac.memberId") Long member,@PathVariable String id) { return ApiResponse.ok(service.cancel(member,id)); }
    @PostMapping("/scan") public ApiResponse<?> scan(@Valid @RequestBody Scan form) { return ApiResponse.ok(service.scan(form.token())); }
    public record Create(@NotBlank String productId,@NotBlank String payMethod,@Pattern(regexp="[a-f0-9]{32}") @NotBlank String requestKey) {}
    public record Method(@NotBlank String payMethod) {}
    public record Scan(@Pattern(regexp="[a-f0-9]{64}") @NotBlank String token) {}
}
