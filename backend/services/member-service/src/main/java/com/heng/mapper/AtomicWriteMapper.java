package com.heng.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;
import com.heng.domain.VMember;
import com.heng.domain.VOrder;
import com.heng.domain.VStock;

/**
 * 并发写入语句集中在此，全部依赖数据库行级条件更新保证正确性：
 * 影响行数为 0 即代表条件不满足（库存不足、状态已变、券被占用等），调用方必须据此回滚。
 */
@Mapper
public interface AtomicWriteMapper {

    @Select("SELECT * FROM v_member WHERE member_id = #{memberId} FOR UPDATE")
    VMember lockMember(@Param("memberId") Long memberId);

    @Select("SELECT * FROM v_order WHERE order_id = #{orderId} FOR UPDATE")
    VOrder lockOrder(@Param("orderId") Long orderId);

    @Select("SELECT * FROM v_stock WHERE sku_id = #{skuId} FOR UPDATE")
    VStock readStockForUpdate(@Param("skuId") Long skuId);

    @Update("UPDATE v_stock SET available_stock = available_stock - #{quantity}, "
            + "locked_stock = locked_stock + #{quantity}, version = version + 1 "
            + "WHERE sku_id = #{skuId} AND available_stock >= #{quantity}")
    int lockStock(@Param("skuId") Long skuId, @Param("quantity") int quantity);

    @Update("UPDATE v_stock SET available_stock = available_stock + #{quantity}, "
            + "locked_stock = locked_stock - #{quantity}, version = version + 1 "
            + "WHERE sku_id = #{skuId} AND locked_stock >= #{quantity}")
    int unlockStock(@Param("skuId") Long skuId, @Param("quantity") int quantity);

    @Update("UPDATE v_stock SET locked_stock = locked_stock - #{quantity}, version = version + 1 "
            + "WHERE sku_id = #{skuId} AND locked_stock >= #{quantity}")
    int consumeLockedStock(@Param("skuId") Long skuId, @Param("quantity") int quantity);

    @Update("UPDATE v_goods_sku SET sales_count = sales_count + #{quantity}, stock = stock - #{quantity} "
            + "WHERE sku_id = #{skuId}")
    int increaseSkuSales(@Param("skuId") Long skuId, @Param("quantity") int quantity);

    @Update("UPDATE v_goods_spu SET sales_count = sales_count + #{quantity}, "
            + "total_stock = GREATEST(total_stock - #{quantity}, 0) WHERE spu_id = #{spuId}")
    int increaseSpuSales(@Param("spuId") Long spuId, @Param("quantity") int quantity);

    @Update("UPDATE v_member SET points = points - #{points} WHERE member_id = #{memberId} AND points >= #{points}")
    int deductPoints(@Param("memberId") Long memberId, @Param("points") int points);

    @Update("UPDATE v_member SET points = points + #{points} WHERE member_id = #{memberId}")
    int addPoints(@Param("memberId") Long memberId, @Param("points") int points);

    @Update("UPDATE v_member SET growth = growth + #{growth} WHERE member_id = #{memberId}")
    int addGrowth(@Param("memberId") Long memberId, @Param("growth") int growth);

    @Update("UPDATE v_member SET balance_fen = balance_fen - #{amountFen} "
            + "WHERE member_id = #{memberId} AND balance_fen >= #{amountFen}")
    int deductBalance(@Param("memberId") Long memberId, @Param("amountFen") long amountFen);

    @Update("UPDATE v_member SET balance_fen = balance_fen + #{amountFen} WHERE member_id = #{memberId}")
    int addBalance(@Param("memberId") Long memberId, @Param("amountFen") long amountFen);

    @Update("UPDATE v_member_coupon SET status = 'LOCKED', locked_order_id = #{orderId} "
            + "WHERE receive_id = #{receiveId} AND member_id = #{memberId} "
            + "AND status = 'AVAILABLE' AND expire_time > NOW()")
    int lockCoupon(@Param("receiveId") Long receiveId, @Param("memberId") Long memberId,
                   @Param("orderId") Long orderId);

    @Update("UPDATE v_member_coupon SET status = 'AVAILABLE', locked_order_id = NULL "
            + "WHERE receive_id = #{receiveId} AND locked_order_id = #{orderId} AND status = 'LOCKED'")
    int releaseCoupon(@Param("receiveId") Long receiveId, @Param("orderId") Long orderId);

    @Update("UPDATE v_member_coupon SET status = 'USED', used_order_id = #{orderId}, used_time = NOW() "
            + "WHERE receive_id = #{receiveId} AND locked_order_id = #{orderId} AND status = 'LOCKED'")
    int useCoupon(@Param("receiveId") Long receiveId, @Param("orderId") Long orderId);

    @Update("UPDATE v_order SET status = 'PAID', pay_type = #{payType}, pay_time = NOW() "
            + "WHERE order_id = #{orderId} AND status = 'PENDING_PAYMENT'")
    int markOrderPaid(@Param("orderId") Long orderId, @Param("payType") String payType);

    @Update("UPDATE v_order SET status = 'CANCELLED', cancel_time = NOW(), close_time = NOW() "
            + "WHERE order_id = #{orderId} AND status = 'PENDING_PAYMENT'")
    int cancelOrder(@Param("orderId") Long orderId);

    @Update("UPDATE v_order SET status = 'COMPLETED', receive_time = NOW() "
            + "WHERE order_id = #{orderId} AND status = 'SHIPPED'")
    int completeOrder(@Param("orderId") Long orderId);

    @Update("UPDATE v_seckill SET stock = stock - #{quantity}, sold_count = sold_count + #{quantity} "
            + "WHERE seckill_id = #{seckillId} AND status = 'RUNNING' AND NOW() BETWEEN start_time AND end_time "
            + "AND stock >= #{quantity}")
    int reserveSeckill(@Param("seckillId") Long seckillId, @Param("quantity") int quantity);

    @Update("UPDATE v_seckill SET stock = stock + #{quantity}, sold_count = GREATEST(sold_count - #{quantity}, 0) "
            + "WHERE seckill_id = #{seckillId}")
    int releaseSeckill(@Param("seckillId") Long seckillId, @Param("quantity") int quantity);

    @Update("UPDATE v_points_mall SET stock = stock - #{quantity}, exchanged_count = exchanged_count + #{quantity} "
            + "WHERE item_id = #{itemId} AND status = 1 AND stock >= #{quantity}")
    int deductPointsMallStock(@Param("itemId") Long itemId, @Param("quantity") int quantity);

    @Update("UPDATE v_article SET view_count = view_count + 1 WHERE article_id = #{articleId}")
    int increaseArticleView(@Param("articleId") Long articleId);
    @Update("UPDATE v_coupon SET remain_count=remain_count-#{quantity} WHERE coupon_id=#{id} AND status=1 AND (total_count=0 OR remain_count>=#{quantity})")
    int reserveRewardCoupon(@Param("id") Long id,@Param("quantity") int quantity);
}
