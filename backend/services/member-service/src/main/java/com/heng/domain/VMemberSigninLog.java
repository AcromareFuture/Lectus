package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 会员签到记录
 * @TableName v_member_signin_log
 */
@TableName(value ="v_member_signin_log")
@Data
public class VMemberSigninLog {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long signinId;

    /**
     * 
     */
    private Long memberId;

    /**
     * 
     */
    private Date signinDate;

    /**
     * 
     */
    private Integer pointsEarned;

    /**
     * 
     */
    private Integer consecutiveDays;

    /**
     * 
     */
    private Date createTime;
}