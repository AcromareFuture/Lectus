package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 库存变动日志
 * @TableName v_stock_log
 */
@TableName(value ="v_stock_log")
@Data
public class VStockLog {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long stockLogId;

    /**
     * 
     */
    private Long skuId;

    /**
     * IN/LOCK/UNLOCK/OUT/ADJUST
     */
    private String changeType;

    /**
     * 
     */
    private Integer quantity;

    /**
     * 
     */
    private Integer beforeAvailable;

    /**
     * 
     */
    private Integer afterAvailable;

    /**
     * 
     */
    private String bizType;

    /**
     * 
     */
    private Long bizId;

    /**
     * 
     */
    private Long operatorId;

    /**
     * 
     */
    private Date createTime;
}