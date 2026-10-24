package com.heng.vo;

import java.util.List;

/** 商品卡片，列表和热榜共用。levelPriceFen 为当前会员等级折算价。 */
public record ProductCardVO(Long spuId, Long categoryId, String categoryName, String name, String subtitle,
                            String mainImageUrl, List<String> imageUrls, Long minPriceFen, Long levelPriceFen,
                            Long originalPriceFen, Integer salesCount, Integer availableStock,
                            Boolean hot, Boolean fresh, Double rating, Integer commentCount,
                            List<LabelVO> labels, Integer sort,
                            Long defaultSkuId, String defaultSpec) {
}
