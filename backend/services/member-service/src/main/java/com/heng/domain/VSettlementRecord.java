package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 订单结算记录
 * @TableName v_settlement_record
 */
@TableName(value ="v_settlement_record")
@Data
public class VSettlementRecord {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long settlementId;

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
    private Long amountFen;

    /**
     * PENDING/SETTLED/FAILED
     */
    private String status;

    /**
     * 
     */
    private Date settledTime;

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