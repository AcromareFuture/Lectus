package com.heng.vo;

/** 订单列表行，附带当前状态下可执行的操作。 */
public record OrderVO(Long orderId, String orderNo, String status, String payType,
                      Long goodsAmountFen, Long discountAmountFen, Long freightAmountFen,
                      Long payAmountFen, Integer pointsUsed, Integer itemCount,
                      String firstName, String firstImage, String createTime, String payTime,
                      Boolean canPay, Boolean canCancel, Boolean canReceive, Boolean canComment) {
}
