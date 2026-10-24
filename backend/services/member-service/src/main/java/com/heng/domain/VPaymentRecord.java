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
 * 支付流水和回调幂等记录
 * @TableName v_payment_record
 */
@TableName(value ="v_payment_record", autoResultMap = true)
@Data
public class VPaymentRecord {
    private String environment;
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long paymentId;

    /**
     * 
     */
    private Long orderId;

    /**
     * 
     */
    private String orderNo;

    /**
     * 
     */
    private String payType;

    /**
     * 
     */
    private String outTradeNo;

    /**
     * 
     */
    private String transactionId;

    /**
     * 
     */
    private Long amountFen;

    /**
     * PENDING/SUCCESS/FAILED/CLOSED
     */
    private String status;

    /**
     * 
     */
    @TableField(typeHandler = JsonTypeHandler.class)
    private Map<String, Object> callbackPayload;

    /**
     * 
     */
    private Date paidTime;

    /**
     * 
     */
    private Date createTime;

    /**
     * 
     */
    private Date updateTime;
}