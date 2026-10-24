package com.heng.vo;

/** 会员优惠券在当次结算中的可用性判断结果。 */
public record CouponOptionVO(Long receiveId, Long couponId, String name, String couponType,
                             Long minAmountFen, Long valueFen, Integer discountBps, String useScope,
                             Boolean usable, Long discountFen, String reason, String expireTime) {
}
