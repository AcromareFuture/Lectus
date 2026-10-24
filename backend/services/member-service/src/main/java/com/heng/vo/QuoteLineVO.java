package com.heng.vo;

import java.util.List;

/** 订单行可展示信息，价格与库存均为服务端实时计算结果。 */
public record QuoteLineVO(Long spuId, Long skuId, String name, String specText, String imageUrl,
                          Integer quantity, Long unitPriceFen, Long originalPriceFen,
                          Long subtotalFen, Integer availableStock, Long seckillId) {
}
