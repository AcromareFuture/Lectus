package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * SKU 库存权威表
 * @TableName v_stock
 */
@TableName(value ="v_stock")
@Data
public class VStock {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long stockId;

    /**
     * 
     */
    private Long skuId;

    /**
     * 
     */
    private Integer availableStock;

    /**
     * 
     */
    private Integer lockedStock;

    /**
     * 
     */
    private Integer warningStock;

    /**
     * 
     */
    private Integer version;

    /**
     * 
     */
    private Date createTime;

    /**
     * 
     */
    private Date updateTime;
}