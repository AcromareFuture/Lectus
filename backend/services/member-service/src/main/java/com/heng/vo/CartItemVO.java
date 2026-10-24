package com.heng.vo;

/** 购物车行，价格已按会员等级折扣折算。 */
public record CartItemVO(Long cartId, Long skuId, Long spuId, String name, String subtitle,
                         String imageUrl, String specText, Long priceFen, Long levelPriceFen,
                         Long subtotalFen, Integer quantity, Integer checked,
                         Integer availableStock, Boolean onSale, Boolean stockEnough) {
}
