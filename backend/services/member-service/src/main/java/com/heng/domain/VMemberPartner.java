package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 社区合伙人
 * @TableName v_member_partner
 */
@TableName(value ="v_member_partner")
@Data
public class VMemberPartner {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long partnerId;

    /**
     * 
     */
    private Long memberId;

    /**
     * PENDING/APPROVED/REJECTED/QUIT
     */
    private String status;

    /**
     * 
     */
    private Long depositAmountFen;

    /**
     * 
     */
    private Long totalIncomeFen;

    /**
     * 
     */
    private String applyReason;

    /**
     * 
     */
    private String auditRemark;

    /**
     * 
     */
    private Long auditedBy;

    /**
     * 
     */
    private Date auditedTime;

    /**
     * 
     */
    private Date createTime;

    /**
     * 
     */
    private Date updateTime;
}