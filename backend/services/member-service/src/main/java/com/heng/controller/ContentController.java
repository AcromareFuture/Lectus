package com.heng.controller;

import com.heng.biz.CatalogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/articles")
public class ContentController {
    private final CatalogService catalogService;

    public ContentController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ApiResponse<?> list(@RequestParam(required=false) String category,@RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long size) {
        return ApiResponse.ok(catalogService.articlePage(category,page,size));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(catalogService.article(id));
    }
}
