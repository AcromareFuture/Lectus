package com.heng.controller;

import com.heng.biz.MemberCenterService;
import com.heng.domain.VMemberComment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/comments")
public class CommentController {
    private final MemberCenterService memberCenterService;

    public CommentController(MemberCenterService memberCenterService) {
        this.memberCenterService = memberCenterService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> orderReviews(
            @RequestAttribute("viridac.memberId") Long memberId, @RequestParam Long orderId) {
        return ApiResponse.ok(memberCenterService.orderReviews(memberId, orderId));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestAttribute("viridac.memberId") Long memberId,
                                                  @Valid @RequestBody CommentRequest request) {
        VMemberComment comment = memberCenterService.comment(memberId, request.orderId(), request.spuId(),
                request.rating(), request.content(), request.imageUrls());
        return ApiResponse.ok(Map.of("commentId", comment.getCommentId()));
    }

    public record CommentRequest(@NotNull Long orderId, @NotNull Long spuId,
                                @Min(1) @Max(5) Integer rating, String content, List<String> imageUrls) { }
}
