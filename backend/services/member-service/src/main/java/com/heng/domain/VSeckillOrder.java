package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 秒杀资格和补偿记录
 * @TableName v_seckill_order
 */
@TableName(value ="v_seckill_order")
@Data
public class VSeckillOrder {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 
     */
    private Long seckillId;

    /**
     * 
     */
    private Long memberId;

    /**
     * 
     */
    private Long orderId;

    /**
     * 
     */
    private Integer quantity;

    /**
     * RESERVED/ORDERED/RELEASED
     */
    private String status;

    /**
     * 
     */
    private Date createTime;

    /**
     * 
     */
    private Date updateTime;
}