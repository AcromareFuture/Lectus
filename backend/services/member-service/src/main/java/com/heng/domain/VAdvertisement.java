package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 广告和首页轮播
 * @TableName v_advertisement
 */
@TableName(value ="v_advertisement")
@Data
public class VAdvertisement {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long advertisementId;

    /**
     * 
     */
    private String title;

    /**
     * 
     */
    private String imageUrl;

    /**
     * PRODUCT/CATEGORY/URL/MINI_PROGRAM
     */
    private String linkType;

    /**
     * 
     */
    private String linkValue;

    /**
     * 
     */
    private String position;

    /**
     * 
     */
    private Integer sort;

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
    private Integer status;

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