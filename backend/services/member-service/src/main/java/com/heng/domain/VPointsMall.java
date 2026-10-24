package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 积分商城商品
 * @TableName v_points_mall
 */
@TableName(value ="v_points_mall")
@Data
public class VPointsMall {
    private String rewardType;
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long itemId;
    private Long couponId;

    /**
     * 
     */
    private String name;

    /**
     * 
     */
    private String imageUrl;

    /**
     * 
     */
    private Integer pointsPrice;

    /**
     * 
     */
    private Integer stock;

    /**
     * 
     */
    private Integer exchangedCount;

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
    private Date startTime;

    /**
     * 
     */
    private Date endTime;

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