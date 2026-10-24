package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Date;
import lombok.Data;

/**
 * Viridac C端会员
 * @TableName v_member
 */
@TableName(value ="v_member")
@Data
public class VMember {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long memberId;

    /**
     * 
     */
    private String username;

    /**
     * 
     */
    @JsonIgnore
    private String password;

    /**
     * 
     */
    private String nickname;

    /**
     * 
     */
    private String avatarUrl;

    /**
     * 
     */
    private String sex;

    /**
     * 
     */
    @com.fasterxml.jackson.annotation.JsonFormat(pattern="yyyy-MM-dd", timezone="Asia/Shanghai")
    private Date birthday;

    /**
     * 
     */
    private String phone;

    /**
     * 
     */
    private String email;

    /**
     * 
     */
    private String openid;

    /**
     * 
     */
    private String unionid;

    /**
     * 
     */
    private Long levelId;

    /**
     * 成长值
     */
    private Integer growth;

    /**
     * 账户余额，分
     */
    private Long balanceFen;

    /**
     * 积分余额
     */
    private Integer points;

    /**
     * 1正常 0停用
     */
    private Integer status;

    /**
     * 
     */
    private Date lastLoginTime;

    /**
     * 
     */
    private Date createTime;

    /**
     * 
     */
    private Date updateTime;
}