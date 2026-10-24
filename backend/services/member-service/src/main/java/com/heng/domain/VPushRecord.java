package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 会员推送消息
 * @TableName v_push_record
 */
@TableName(value ="v_push_record")
@Data
public class VPushRecord {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long pushId;

    /**
     * 
     */
    private Long memberId;

    /**
     * ORDER/COUPON/PROMOTION/SYSTEM
     */
    private String pushType;

    /**
     * 
     */
    private String title;

    /**
     * 
     */
    private String content;

    /**
     * 
     */
    private Long bizId;

    /**
     * 
     */
    private Date readTime;

    /**
     * 
     */
    private Date createTime;
}