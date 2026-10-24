package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 积分兑换记录
 * @TableName v_points_exchange
 */
@TableName(value ="v_points_exchange")
@Data
public class VPointsExchange {
    private String nameSnapshot;
    private String imageSnapshot;
    private String addressSnapshot;
    private String rewardType;
    private String shipCompany;
    private String shipNo;
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long exchangeId;

    /**
     * 
     */
    private String exchangeNo;

    /**
     * 
     */
    private Long memberId;

    /**
     * 
     */
    private Long itemId;

    /**
     * 
     */
    private Integer pointsUsed;

    /**
     * 
     */
    private Integer quantity;

    /**
     * SUCCESS/CANCELLED
     */
    private String status;

    /**
     * 
     */
    private Date createTime;
}