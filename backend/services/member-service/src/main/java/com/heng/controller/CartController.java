package com.heng.controller;

import com.heng.biz.CartService;
import com.heng.vo.CartVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ApiResponse<CartVO> cart(@RequestAttribute("viridac.memberId") Long memberId) {
        return ApiResponse.ok(cartService.view(memberId));
    }

    @PostMapping("/items")
    public ApiResponse<CartVO> add(@RequestAttribute("viridac.memberId") Long memberId,
                                   @Valid @RequestBody AddRequest request) {
        cartService.add(memberId, request.skuId(), request.quantity());
        return ApiResponse.ok(cartService.view(memberId));
    }

    @PutMapping("/items/{skuId}")
    public ApiResponse<CartVO> update(@RequestAttribute("viridac.memberId") Long memberId,
                                      @PathVariable Long skuId,
                                      @Valid @RequestBody UpdateRequest request) {
        cartService.updateQuantity(memberId, skuId, request.quantity(), request.checked());
        return ApiResponse.ok(cartService.view(memberId));
    }

    @PutMapping("/checked")
    public ApiResponse<CartVO> check(@RequestAttribute("viridac.memberId") Long memberId,
                                     @Valid @RequestBody CheckRequest request) {
        cartService.updateChecked(memberId, request.skuId(), request.checked());
        return ApiResponse.ok(cartService.view(memberId));
    }

    @PutMapping("/checked-all")
    public ApiResponse<CartVO> checkAll(@RequestAttribute("viridac.memberId") Long memberId,
                                        @Valid @RequestBody CheckAllRequest request) {
        cartService.checkAll(memberId, request.checked());
        return ApiResponse.ok(cartService.view(memberId));
    }

    @DeleteMapping("/items")
    public ApiResponse<CartVO> remove(@RequestAttribute("viridac.memberId") Long memberId,
                                      @Valid @RequestBody RemoveRequest request) {
        cartService.remove(memberId, request.skuIds());
        return ApiResponse.ok(cartService.view(memberId));
    }

    @DeleteMapping
    public ApiResponse<CartVO> clear(@RequestAttribute("viridac.memberId") Long memberId) {
        cartService.clear(memberId);
        return ApiResponse.ok(cartService.view(memberId));
    }

    public record AddRequest(@NotNull Long skuId, @Min(1) @Max(999) int quantity) { }
    public record UpdateRequest(@Min(1) @Max(999) int quantity, Integer checked) { }
    public record CheckRequest(@NotNull Long skuId, @Min(0) @Max(1) int checked) { }
    public record CheckAllRequest(@Min(0) @Max(1) int checked) { }
    public record RemoveRequest(@NotEmpty List<Long> skuIds) { }
}
