package com.heng.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 会员商品收藏
 * @TableName v_member_favorite
 */
@TableName(value ="v_member_favorite")
@Data
public class VMemberFavorite {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long favoriteId;

    /**
     * 
     */
    private Long memberId;

    /**
     * 
     */
    private Long spuId;

    /**
     * 
     */
    private Date createTime;
}