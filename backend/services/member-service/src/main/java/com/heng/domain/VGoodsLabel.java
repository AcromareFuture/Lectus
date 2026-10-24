package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 商品标签关联
 * @TableName v_goods_label
 */
@TableName(value ="v_goods_label")
@Data
public class VGoodsLabel {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 
     */
    private Long spuId;

    /**
     * 
     */
    private Long labelId;

    /**
     * 
     */
    private Date createTime;
}