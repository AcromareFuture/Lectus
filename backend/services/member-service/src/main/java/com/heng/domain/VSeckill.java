package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 限时秒杀活动
 * @TableName v_seckill
 */
@TableName(value ="v_seckill")
@Data
public class VSeckill {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long seckillId;

    /**
     * 
     */
    private Long spuId;

    /**
     * 
     */
    private Long skuId;

    /**
     * 
     */
    private String name;

    /**
     * 
     */
    private Long priceFen;

    /**
     * 
     */
    private Integer stock;

    /**
     * 
     */
    private Integer soldCount;

    /**
     * 
     */
    private Integer limitNum;

    /**
     * 
     */
    private Date startTime;

    /**
     * 
     */
    private Date endTime;

    /**
     * DRAFT/READY/RUNNING/ENDED/OFFLINE
     */
    private String status;

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