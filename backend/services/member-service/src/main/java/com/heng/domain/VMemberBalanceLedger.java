package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 余额流水
 * @TableName v_member_balance_ledger
 */
@TableName(value ="v_member_balance_ledger")
@Data
public class VMemberBalanceLedger {
    private String fundingSource;
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
     * 
     */
    private Long changeAmountFen;

    /**
     * 
     */
    private Long balanceAfterFen;

    /**
     * DEPOSIT/PAY/REFUND/ADJUST
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
    private Date createTime;
}