package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 文章、公告和 FAQ
 * @TableName v_article
 */
@TableName(value ="v_article")
@Data
public class VArticle {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long articleId;

    /**
     * 
     */
    private String title;

    /**
     * NOTICE/HELP/NEWS/FAQ
     */
    private String category;

    /**
     * 
     */
    private String summary;

    /**
     * 
     */
    private String coverUrl;

    /**
     * 
     */
    private String content;

    /**
     * 
     */
    private String author;

    /**
     * 
     */
    private Integer viewCount;

    /**
     * 
     */
    private Integer sort;

    /**
     * 
     */
    private Integer isTop;

    /**
     * DRAFT/PUBLISHED/OFFLINE
     */
    private String status;

    /**
     * 
     */
    private Date publishTime;

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