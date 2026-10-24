package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * Viridac 商品标签
 * @TableName v_label
 */
@TableName(value ="v_label")
@Data
public class VLabel {
    /**
     * 标签 ID
     */
    @TableId(type = IdType.AUTO)
    private Long labelId;

    /**
     * 标签名称
     */
    private String name;

    /**
     * 标签颜色
     */
    private String color;

    /**
     * 标签图标地址
     */
    private String iconUrl;

    /**
     * 1启用 0停用
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