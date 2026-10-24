package com.heng.vo;

import java.util.List;

public record CommentVO(Long commentId, String nickname, String avatarUrl, Integer rating, String content,
                        List<String> imageUrls, Integer likeCount, String merchantReply, String createTime) {
}
