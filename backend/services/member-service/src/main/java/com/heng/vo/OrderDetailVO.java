package com.heng.vo;

import java.util.List;
import java.util.Map;

/** 订单详情，商品行来自下单时写入的快照，不依赖商品表现状。 */
public record OrderDetailVO(Long orderId, String orderNo, String status, String payType,
                            String contactName, String contactPhone, String shippingAddress,
                            Long goodsAmountFen, Long discountAmountFen, Long freightAmountFen,
                            Long payAmountFen, Integer pointsUsed, Long couponReceiveId, String remark,
                            String shipCompany, String shipNo,
                            String createTime, String payTime, String shipTime, String receiveTime,
                            String cancelTime,
                            List<Map<String, Object>> items,
                            Boolean canPay, Boolean canCancel, Boolean canReceive, Boolean canComment,
                            String paymentDeadline, Boolean demoMode) {
}
