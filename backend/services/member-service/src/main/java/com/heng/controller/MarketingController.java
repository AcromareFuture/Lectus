package com.heng.controller;

import com.heng.auth.MemberContext;
import com.heng.biz.MarketingService;
import com.heng.biz.OrderService;
import com.heng.domain.VOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MarketingController {
    private final MarketingService marketingService;
    private final OrderService orderService;
    private final MemberContext memberContext;
    private final com.heng.biz.RequestDeduplicator dedup;

    public MarketingController(MarketingService marketingService, OrderService orderService,
                               MemberContext memberContext, com.heng.biz.RequestDeduplicator dedup) {
        this.dedup=dedup;
        this.marketingService = marketingService;
        this.orderService = orderService;
        this.memberContext = memberContext;
    }

    @GetMapping("/seckills")
    public ApiResponse<List<Map<String, Object>>> seckills() {
        return ApiResponse.ok(marketingService.seckills());
    }

    /** 秒杀下单：活动价、限购和活动库存都由服务端判定。 */
    @PostMapping("/seckills/{id}/orders")
    public ApiResponse<Map<String, Object>> seckillOrder(@RequestAttribute("viridac.memberId") Long memberId,
                                                        @PathVariable Long id,
                                                        @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody SeckillOrderRequest request) {
        Long orderId=dedup.execute(memberId,"SECKILL",key,id+":"+request,()->orderService.create(memberId,List.of(marketingService.seckillLine(id,request.quantity())),request.addressId(),null,null,"秒杀订单",request.expectedPayAmountFen()).getOrderId());
        VOrder order=orderService.getCreated(memberId,orderId);
        return ApiResponse.ok(Map.of("orderId", order.getOrderId(), "orderNo", order.getOrderNo(),
                "payAmountFen", order.getPayAmountFen(), "status", order.getStatus()));
    }

    @GetMapping("/points-mall")
    public ApiResponse<List<Map<String, Object>>> pointsMall() {
        return ApiResponse.ok(marketingService.pointsMall());
    }

    @GetMapping("/points-mall/exchanges")
    public ApiResponse<?> exchanges(@RequestAttribute("viridac.memberId") Long memberId,@RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long size) {
        return ApiResponse.ok(marketingService.exchangePage(memberId,page,size));
    }

    @PostMapping("/points-mall/{itemId}/exchanges")
    public ApiResponse<Map<String, Object>> exchange(@RequestAttribute("viridac.memberId") Long memberId,
                                                    @PathVariable Long itemId,
                                                    @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ExchangeRequest request) {
        Long id=dedup.execute(memberId,"EXCHANGE",key,itemId+":"+request,()->((Number)marketingService.exchange(memberId,itemId,request.quantity(),request.addressId()).get("exchangeId")).longValue());
        return ApiResponse.ok(marketingService.exchangeResult(memberId,id));
    }

    @GetMapping("/products/hot")
    public ApiResponse<?> hot(@RequestHeader(value = "Authorization", required = false) String authorization,
                              @RequestParam(defaultValue = "8") int limit) {
        return ApiResponse.ok(marketingService.hotProducts(memberContext.from(authorization),
                Math.min(Math.max(limit, 1), 20)));
    }

    @PostMapping("/points-mall/exchanges/{id}/cancel")
    public ApiResponse<?> cancelExchange(@RequestAttribute("viridac.memberId") Long memberId,@PathVariable Long id) {marketingService.cancelExchange(memberId,id);return ApiResponse.ok(null);}
    public record ExchangeRequest(@Min(1) @Max(20) int quantity, @Min(1) Long addressId) { }

    public record SeckillOrderRequest(@Min(1) Long addressId, @Min(1) @Max(20) int quantity, @jakarta.validation.constraints.NotNull @Min(0) Long expectedPayAmountFen) { }
}
