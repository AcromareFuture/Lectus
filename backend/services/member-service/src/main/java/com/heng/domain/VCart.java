package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 购物车
 * @TableName v_cart
 */
@TableName(value ="v_cart")
@Data
public class VCart {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long cartId;

    /**
     * 
     */
    private Long memberId;

    /**
     * 
     */
    private Long skuId;

    /**
     * 
     */
    private Integer quantity;

    /**
     * 
     */
    private Integer checked;

    /**
     * 
     */
    private Date createTime;

    /**
     * 
     */
    private Date updateTime;
}