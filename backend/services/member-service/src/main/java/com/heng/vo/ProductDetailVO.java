package com.heng.vo;

import java.util.List;

/** 商品详情，包含可下单规格、评价摘要和当前会员的收藏状态。 */
public record ProductDetailVO(ProductCardVO card, String description, String videoUrl,
                              List<SkuVO> skus, Boolean favorited, List<CommentVO> topComments, Boolean onSale) {
}
