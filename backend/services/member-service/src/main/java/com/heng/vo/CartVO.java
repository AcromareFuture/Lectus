package com.heng.vo;

import java.util.List;

/**
 * 购物车视图。checkedCount/amountFen 只统计勾选且在架、有货的行，供前端底部结算栏直接使用。
 */
public record CartVO(List<CartItemVO> items, Integer totalQuantity, Integer checkedQuantity,
                     Long checkedAmountFen, Long freeShippingThresholdFen, Long amountToFreeShipping) {
}
