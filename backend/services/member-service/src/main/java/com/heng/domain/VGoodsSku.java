package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * Viridac 商品 SKU
 * @TableName v_goods_sku
 */
@TableName(value ="v_goods_sku")
@Data
public class VGoodsSku {
    /**
     * SKU ID
     */
    @TableId(type = IdType.AUTO)
    private Long skuId;

    /**
     * SPU ID，由商品域校验
     */
    private Long spuId;

    /**
     * 规格 JSON 或规范化文本
     */
    private String specValue;

    /**
     * 
     */
    private String specImageUrl;

    /**
     * 销售价，分
     */
    private Long priceFen;

    /**
     * 原价，分
     */
    private Long originalPriceFen;

    /**
     * 可售库存
     */
    private Integer stock;

    /**
     * 锁定库存
     */
    private Integer lockedStock;

    /**
     * 
     */
    private Integer salesCount;

    /**
     * 
     */
    private String skuCode;

    /**
     * 
     */
    private String barCode;

    /**
     * 重量，克
     */
    private Integer weightGram;

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