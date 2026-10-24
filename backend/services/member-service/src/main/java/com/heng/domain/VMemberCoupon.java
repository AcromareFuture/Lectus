package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 会员优惠券领取记录
 * @TableName v_member_coupon
 */
@TableName(value ="v_member_coupon")
@Data
public class VMemberCoupon {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long receiveId;

    /**
     * 
     */
    private Long memberId;

    /**
     * 
     */
    private Long couponId;

    /**
     * AVAILABLE/LOCKED/USED/EXPIRED/RELEASED
     */
    private String status;

    /**
     * 
     */
    private Date receivedTime;

    /**
     * 
     */
    private Date expireTime;

    /**
     * 
     */
    private Long lockedOrderId;

    /**
     * 
     */
    private Long usedOrderId;

    /**
     * 
     */
    private Date usedTime;

    /**
     * 
     */
    private Date createTime;

    /**
     * 
     */
    private Date updateTime;
}