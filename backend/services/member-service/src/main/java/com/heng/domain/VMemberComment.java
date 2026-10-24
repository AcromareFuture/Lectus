package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.heng.support.JsonTypeHandler;
import java.util.Date;
import java.util.List;
import lombok.Data;

/**
 * 商品评论
 * @TableName v_member_comment
 */
@TableName(value ="v_member_comment", autoResultMap = true)
@Data
public class VMemberComment {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long commentId;

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
    private Long spuId;

    /**
     * 
     */
    private Long skuId;

    /**
     * 
     */
    private Integer rating;

    /**
     * 
     */
    private String content;

    /**
     * 
     */
    @TableField(typeHandler = JsonTypeHandler.class)
    private List<String> imageUrls;

    /**
     * 
     */
    private String merchantReply;

    /**
     * 
     */
    private Date replyTime;

    /**
     * 
     */
    private Integer likeCount;

    /**
     * 1显示 0隐藏
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