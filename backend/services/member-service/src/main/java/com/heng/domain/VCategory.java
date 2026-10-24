package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * Viridac 商品分类
 * @TableName v_category
 */
@TableName(value ="v_category")
@Data
public class VCategory {
    /**
     * 分类 ID
     */
    @TableId(type = IdType.AUTO)
    private Long categoryId;

    /**
     * 父分类 ID，0 为顶级
     */
    private Long parentId;

    /**
     * 分类名称
     */
    private String name;

    /**
     * 图标地址
     */
    private String iconUrl;

    /**
     * 分类图片地址
     */
    private String imageUrl;

    /**
     * 排序，越小越靠前
     */
    private Integer sort;

    /**
     * 1启用 0停用
     */
    private Integer status;

    /**
     * 创建者
     */
    private String createBy;

    /**
     * 
     */
    private Date createTime;

    /**
     * 更新者
     */
    private String updateBy;

    /**
     * 
     */
    private Date updateTime;
}