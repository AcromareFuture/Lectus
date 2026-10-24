package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.heng.support.JsonTypeHandler;
import java.util.Date;
import java.util.Map;
import lombok.Data;

/**
 * 订单商品快照
 * @TableName v_order_item
 */
@TableName(value ="v_order_item", autoResultMap = true)
@Data
public class VOrderItem {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long itemId;

    /**
     * 
     */
    private Long orderId;

    /**
     * 
     */
    private Integer quantity;

    /**
     * 
     */
    private Long unitPriceFen;

    /**
     * 
     */
    private Long subtotalFen;

    /**
     * 商品名称、SKU、规格、图片、价格等完整快照
     */
    @TableField(typeHandler = JsonTypeHandler.class)
    private Map<String, Object> goodsSnapshot;

    /**
     * 
     */
    private Date createTime;
}