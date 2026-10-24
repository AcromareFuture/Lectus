package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 优惠券模板
 * @TableName v_coupon
 */
@TableName(value ="v_coupon")
@Data
public class VCoupon {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long couponId;

    /**
     * 
     */
    private String name;

    /**
     * FULL_REDUCTION/DISCOUNT/FREE
     */
    private String couponType;

    /**
     * 满减或无门槛金额，分
     */
    private Long valueFen;

    /**
     * 折扣基点
     */
    private Integer discountBps;

    /**
     * 
     */
    private Long minAmountFen;

    /**
     * 0不限量
     */
    private Integer totalCount;

    /**
     * 
     */
    private Integer remainCount;

    /**
     * 
     */
    private Integer perMemberLimit;

    /**
     * 
     */
    private Date validFrom;

    /**
     * 
     */
    private Date validTo;

    /**
     * ALL/CATEGORY/PRODUCT
     */
    private String useScope;

    /**
     * CATEGORY/PRODUCT 适用范围 ID 列表，ALL 时为空
     */
    private String scopeIds;

    /**
     * 
     */
    private String description;

    /**
     * 
     */
    private Integer status;

    /**
     * 
     */
    private String createBy;

    /**
     * 
     */
    private Date createTime;

    /**
     * 
     */
    private String updateBy;

    /**
     * 
     */
    private Date updateTime;
}