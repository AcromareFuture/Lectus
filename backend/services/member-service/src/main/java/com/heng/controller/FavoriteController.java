package com.heng.controller;

import com.heng.biz.MemberCenterService;
import com.heng.vo.ProductCardVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {
    private final MemberCenterService memberCenterService;

    public FavoriteController(MemberCenterService memberCenterService) {
        this.memberCenterService = memberCenterService;
    }

    @GetMapping
    public ApiResponse<List<ProductCardVO>> list(@RequestAttribute("viridac.memberId") Long memberId) {
        return ApiResponse.ok(memberCenterService.favorites(memberId));
    }

    @GetMapping("/page") public ApiResponse<?> page(@RequestAttribute("viridac.memberId") Long memberId,@RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long size) {
        return ApiResponse.ok(memberCenterService.favoritePage(memberId,page,size));
    }
    /** 切换收藏状态，前端只需把返回的 favorited 写回按钮即可。 */
    @PostMapping("/{spuId}")
    public ApiResponse<Map<String, Object>> toggle(@RequestAttribute("viridac.memberId") Long memberId,
                                                  @PathVariable Long spuId) {
        boolean favorited = memberCenterService.toggleFavorite(memberId, spuId);
        return ApiResponse.ok(Map.of("spuId", spuId, "favorited", favorited));
    }

    @DeleteMapping("/{spuId}")
    public ApiResponse<Void> remove(@RequestAttribute("viridac.memberId") Long memberId, @PathVariable Long spuId) {
        memberCenterService.removeFavorite(memberId, spuId);
        return ApiResponse.ok(null);
    }
}
