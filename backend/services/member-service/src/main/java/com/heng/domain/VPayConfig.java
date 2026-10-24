package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 支付方式配置（敏感密钥不落库）
 * @TableName v_pay_config
 */
@TableName(value ="v_pay_config")
@Data
public class VPayConfig {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long configId;

    /**
     * WECHAT/ALIPAY/BALANCE
     */
    private String payType;

    /**
     * 
     */
    private String appId;

    /**
     * 
     */
    private String merchantId;

    /**
     * 
     */
    private String certificatePath;

    /**
     * 
     */
    private Integer enabled;

    /**
     * 
     */
    private String createBy;

    /**
     * 
     */
    private Date createTime;

    /**
     * 
     */
    private String updateBy;

    /**
     * 
     */
    private Date updateTime;
}