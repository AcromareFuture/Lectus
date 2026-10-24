package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 积分流水
 * @TableName v_member_point_ledger
 */
@TableName(value ="v_member_point_ledger")
@Data
public class VMemberPointLedger {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long ledgerId;

    /**
     * 
     */
    private Long memberId;

    /**
     * 正数增加，负数消耗
     */
    private Integer changePoints;

    /**
     * 
     */
    private Integer balanceAfter;

    /**
     * ORDER_REWARD/ORDER_USE/SIGNIN/EXCHANGE/ADJUST
     */
    private String bizType;

    /**
     * 
     */
    private Long bizId;

    /**
     * 
     */
    private String idempotencyKey;

    /**
     * 
     */
    private String remark;

    /**
     * 
     */
    private Date createTime;
}