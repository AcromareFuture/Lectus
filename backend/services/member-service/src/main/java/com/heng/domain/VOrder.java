package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 订单主表
 * @TableName v_order
 */
@TableName(value ="v_order")
@Data
public class VOrder {
    private Boolean demoMode;
    private String fundingSource;
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long orderId;

    /**
     * 
     */
    private String orderNo;

    /**
     * 
     */
    private Long memberId;

    /**
     * 
     */
    private Long addressId;

    /**
     * 
     */
    private String contactName;

    /**
     * 
     */
    private String contactPhone;

    /**
     * 
     */
    private String shippingAddress;

    /**
     * 
     */
    private Long goodsAmountFen;

    /**
     * 
     */
    private Long discountAmountFen;

    /**
     * 
     */
    private Long freightAmountFen;

    /**
     * 
     */
    private Long payAmountFen;

    /**
     * 
     */
    private Integer pointsUsed;

    /**
     * 
     */
    private Long couponReceiveId;

    /**
     * WECHAT/ALIPAY/BALANCE
     */
    private String payType;

    /**
     * PENDING_PAYMENT/PAID/SHIPPED/COMPLETED/CANCELLED
     */
    private String status;

    /**
     * 
     */
    private Date payTime;

    /**
     * 
     */
    private Date shipTime;

    /**
     * 
     */
    private Date receiveTime;

    /**
     * 
     */
    private Date cancelTime;

    /**
     * 
     */
    private Date closeTime;

    /**
     * 
     */
    private String shipCompany;

    /**
     * 
     */
    private String shipNo;

    /**
     * 
     */
    private String remark;

    /**
     * 
     */
    private Date createTime;

    /**
     * 
     */
    private Date updateTime;
}