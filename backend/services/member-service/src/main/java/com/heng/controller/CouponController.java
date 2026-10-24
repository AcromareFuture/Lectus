package com.heng.controller;

import com.heng.biz.MarketingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {
    private final MarketingService marketingService;

    public CouponController(MarketingService marketingService) {
        this.marketingService = marketingService;
    }

    @GetMapping("/page") public ApiResponse<?> page(@RequestAttribute("viridac.memberId") Long memberId,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long size) {
        return ApiResponse.ok(marketingService.couponPage(memberId,status,page,size));
    }
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> mine(@RequestAttribute("viridac.memberId") Long memberId,
                                                      @RequestParam(required = false) String status) {
        return ApiResponse.ok(marketingService.myCoupons(memberId, status));
    }
}
