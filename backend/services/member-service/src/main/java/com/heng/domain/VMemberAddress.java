package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 会员收货地址
 * @TableName v_member_address
 */
@TableName(value ="v_member_address")
@Data
public class VMemberAddress {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long addressId;

    /**
     * 
     */
    private Long memberId;

    /**
     * 
     */
    private String contactName;

    /**
     * 
     */
    private String phone;

    /**
     * 
     */
    private String province;

    /**
     * 
     */
    private String city;

    /**
     * 
     */
    private String district;

    /**
     * 
     */
    private String detail;

    /**
     * 
     */
    private Integer isDefault;

    /**
     * 
     */
    private Date createTime;

    /**
     * 
     */
    private Date updateTime;
}