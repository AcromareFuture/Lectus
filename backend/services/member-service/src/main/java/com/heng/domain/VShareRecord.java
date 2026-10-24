package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 商品分享记录
 * @TableName v_share_record
 */
@TableName(value ="v_share_record")
@Data
public class VShareRecord {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long shareId;

    /**
     * 分享会员 ID，匿名可为空
     */
    private Long memberId;

    /**
     * PRODUCT/CATEGORY/SHOP
     */
    private String targetType;

    /**
     * 
     */
    private Long targetId;

    /**
     * 
     */
    private String shareChannel;

    /**
     * 
     */
    private String shareUrl;

    /**
     * 
     */
    private Date createTime;
}