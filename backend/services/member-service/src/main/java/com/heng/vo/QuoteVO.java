package com.heng.vo;

import java.util.List;

/**
 * 结算快照。金额单位全部为分，全部由服务端计算，客户端传入的金额一律忽略。
 */
public record QuoteVO(
        List<QuoteLineVO> lines,
        Long goodsAmountFen,
        Long levelDiscountFen,
        Long couponDiscountFen,
        Long discountAmountFen,
        Integer pointsUsed,
        Long pointsDiscountFen,
        Integer pointsAvailable,
        Integer pointsMaxUse,
        Long freightAmountFen,
        Long freeShippingThresholdFen,
        Long payAmountFen,
        Long couponReceiveId,
        List<CouponOptionVO> couponOptions) {
}
