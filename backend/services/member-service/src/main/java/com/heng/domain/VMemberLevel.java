package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 会员等级
 * @TableName v_member_level
 */
@TableName(value ="v_member_level")
@Data
public class VMemberLevel {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long levelId;

    /**
     * 
     */
    private String name;

    /**
     * 
     */
    private Integer minGrowth;

    /**
     * 
     */
    private Integer maxGrowth;

    /**
     * 折扣基点，10000=原价
     */
    private Integer discountBps;

    /**
     * 
     */
    private String iconUrl;

    /**
     * 
     */
    private String description;

    /**
     * 
     */
    private Integer status;

    /**
     * 
     */
    private Date createTime;

    /**
     * 
     */
    private Date updateTime;
}