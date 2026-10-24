package com.heng.controller;

import com.heng.auth.MemberContext;
import com.heng.biz.CatalogService;
import com.heng.config.TradeProperties;
import com.heng.vo.CategoryNodeVO;
import com.heng.vo.CommentVO;
import com.heng.vo.PageResult;
import com.heng.vo.ProductCardVO;
import com.heng.vo.ProductDetailVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api")
public class ProductController {
    private final CatalogService catalogService;
    private final MemberContext memberContext;
    private final TradeProperties tradeProperties;

    public ProductController(CatalogService catalogService, MemberContext memberContext, TradeProperties tradeProperties) {
        this.catalogService = catalogService;
        this.memberContext = memberContext;
        this.tradeProperties = tradeProperties;
    }

    @GetMapping("/products")
    public ApiResponse<PageResult<ProductCardVO>> products(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue="false") boolean offers,
            @RequestParam(defaultValue="false") boolean favorites,
            @RequestParam(required = false, defaultValue = "default") String sort,
            @RequestParam(required = false, defaultValue = "1") long page,
            @RequestParam(required = false, defaultValue = "20") long size,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(catalogService.page(categoryId, keyword, sort, page,
                Math.min(Math.max(size,1), 50), memberContext.from(authorization), offers, favorites));
    }

    @GetMapping("/products/{id}")
    public ApiResponse<ProductDetailVO> product(@PathVariable Long id,
                                                @RequestHeader(value = "Authorization", required = false)
                                                String authorization) {
        return ApiResponse.ok(catalogService.detail(id, memberContext.from(authorization)));
    }

    @GetMapping("/products/{id}/comments")
    public ApiResponse<PageResult<CommentVO>> comments(@PathVariable Long id,
                                                       @RequestParam(defaultValue = "1") long page,
                                                       @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.ok(catalogService.commentPage(id, page, Math.min(size, 50)));
    }

    @GetMapping("/categories")
    public ApiResponse<List<CategoryNodeVO>> categories() {
        return ApiResponse.ok(catalogService.categoryTree());
    }

    @GetMapping("/home")
    public ApiResponse<Map<String, Object>> home(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> home = new LinkedHashMap<>(catalogService.home(memberContext.from(authorization)));
        home.put("demoMode", tradeProperties.isDemoMode());
        return ApiResponse.ok(home);
    }
}
