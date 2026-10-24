package com.heng.vo;

/** 可下单的商品规格。availableStock 取自 v_stock，是唯一库存口径。 */
public record SkuVO(Long skuId, Long spuId, String specText, String specImageUrl, String skuCode,
                   Long priceFen, Long levelPriceFen, Long originalPriceFen, Integer availableStock,
                   Integer salesCount, Integer weightGram, Integer status) {
}
