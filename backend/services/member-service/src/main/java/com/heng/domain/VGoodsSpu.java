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
 * Viridac 商品 SPU
 * @TableName v_goods_spu
 */
@TableName(value ="v_goods_spu", autoResultMap = true)
@Data
public class VGoodsSpu {
    /**
     * SPU ID
     */
    @TableId(type = IdType.AUTO)
    private Long spuId;

    /**
     * 分类 ID，由商品域校验
     */
    private Long categoryId;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 副标题
     */
    private String subtitle;

    /**
     * 主图地址
     */
    private String mainImageUrl;

    /**
     * 轮播图地址数组
     */
    @TableField(typeHandler = JsonTypeHandler.class)
    private List<String> imageUrls;

    /**
     * 商品详情富文本
     */
    private String description;

    /**
     * 视频地址
     */
    private String videoUrl;

    /**
     * 最低 SKU 价，分
     */
    private Long minPriceFen;

    /**
     * 原价，分
     */
    private Long originalPriceFen;

    /**
     * 销量
     */
    private Integer salesCount;

    /**
     * 展示总库存
     */
    private Integer totalStock;

    /**
     * 1上架 0下架
     */
    private Integer onSale;

    /**
     * 是否热榜商品
     */
    private Integer isHot;

    /**
     * 是否新品
     */
    private Integer isNew;

    /**
     * 
     */
    private Integer sort;

    /**
     * 0存在 2逻辑删除
     */
    private String delFlag;

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
