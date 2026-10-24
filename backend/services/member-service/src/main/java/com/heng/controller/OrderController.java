package com.heng.controller;

import com.heng.biz.CartService;
import com.heng.biz.OrderService;
import com.heng.biz.PricingService;
import com.heng.domain.VOrder;
import com.heng.vo.OrderDetailVO;
import com.heng.vo.OrderVO;
import com.heng.vo.PageResult;
import com.heng.vo.QuoteVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final com.heng.community.StoreCouponController stores;
    private final OrderService orderService;
    private final PricingService pricingService;
    private final CartService cartService;
    private final com.heng.biz.RequestDeduplicator dedup;

    public OrderController(OrderService orderService, PricingService pricingService, CartService cartService, com.heng.biz.RequestDeduplicator dedup, com.heng.community.StoreCouponController stores) {
        this.stores=stores;
        this.dedup=dedup;
        this.orderService = orderService;
        this.pricingService = pricingService;
        this.cartService = cartService;
    }

    /** 结算预览：不传 items 时使用购物车勾选项，传 items 时为立即购买。 */
    @PostMapping("/preview")
    public ApiResponse<QuoteVO> preview(@RequestAttribute("viridac.memberId") Long memberId,
                                        @Valid @RequestBody PreviewRequest request) {
        List<PricingService.Line> lines = lines(memberId, request.items());
        var resolved = pricingService.resolveLines(lines);
        return ApiResponse.ok(pricingService.quote(memberId, resolved, request.couponReceiveId(),
                request.pointsUsed()));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestAttribute("viridac.memberId") Long memberId,
                                                   @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody CreateRequest request) {
        Long id=dedup.execute(memberId,"ORDER",key,request.toString(),()-> {if(request.storeId()!=null)stores.requireStore(request.storeId());Long created=orderService.create(memberId,
                lines(memberId,request.items()),request.addressId(),request.couponReceiveId(),request.pointsUsed(),
                request.remark(),request.expectedPayAmountFen()).getOrderId();stores.attachOrder(memberId,created,request.storeId());return created;});
        VOrder order=orderService.getCreated(memberId,id);
        return ApiResponse.ok(Map.of("orderId", order.getOrderId(), "orderNo", order.getOrderNo(),
                "payAmountFen", order.getPayAmountFen(), "status", order.getStatus()));
    }

    @GetMapping
    public ApiResponse<PageResult<OrderVO>> list(@RequestAttribute("viridac.memberId") Long memberId,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(orderService.page(memberId, status, page, Math.min(size, 50)));
    }

    @GetMapping("/counts")
    public ApiResponse<Map<String, Long>> counts(@RequestAttribute("viridac.memberId") Long memberId) {
        return ApiResponse.ok(orderService.counts(memberId));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderDetailVO> detail(@RequestAttribute("viridac.memberId") Long memberId,
                                             @PathVariable Long id) {
        return ApiResponse.ok(orderService.detail(memberId, id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@RequestAttribute("viridac.memberId") Long memberId, @PathVariable Long id) {
        orderService.cancel(memberId, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/receive")
    public ApiResponse<Void> receive(@RequestAttribute("viridac.memberId") Long memberId, @PathVariable Long id) {
        orderService.confirmReceive(memberId, id);
        return ApiResponse.ok(null);
    }

    private List<PricingService.Line> lines(Long memberId, List<ItemRequest> items) {
        if (items == null || items.isEmpty()) return cartService.checkedLines(memberId);
        List<PricingService.Line> lines = items.stream()
                .map(item -> new PricingService.Line(item.skuId(), item.quantity(), item.seckillId()))
                .toList();
        if (lines.stream().map(PricingService.Line::skuId).count() !=
                lines.stream().map(PricingService.Line::skuId).distinct().count()) {
            throw new com.heng.config.BizException("同一商品不能重复提交");
        }
        return lines;
    }

    public record ItemRequest(@NotNull @Min(1) Long skuId, @NotNull @Min(1) @Max(999) Integer quantity,
                              @Min(1) Long seckillId) {
    }

    public record PreviewRequest(@Size(max = 100) List<@NotNull @Valid ItemRequest> items,
                                 @Min(1) Long addressId, @Min(1) Long couponReceiveId,
                                 @Min(0) Integer pointsUsed) {
    }

    public record CreateRequest(@Size(max = 100) List<@NotNull @Valid ItemRequest> items,
                                @Min(1) Long addressId, @Min(1) Long couponReceiveId,
                                @Min(0) Integer pointsUsed, @Size(max = 500) String remark, @NotNull @Min(0) Long expectedPayAmountFen, @Min(1) Long storeId) {
        public CreateRequest(List<ItemRequest> items,Long addressId,Long couponReceiveId,Integer pointsUsed,String remark,Long expectedPayAmountFen){this(items,addressId,couponReceiveId,pointsUsed,remark,expectedPayAmountFen,null);}
        public CreateRequest(List<ItemRequest> items,Long addressId,Long couponReceiveId,Integer pointsUsed,String remark) {this(items,addressId,couponReceiveId,pointsUsed,remark,null,null);}
    }
}
