package com.heng;

import com.heng.auth.MemberTokenService;
import com.heng.biz.OrderService;
import com.heng.biz.PaymentService;
import com.heng.biz.PendingOrderTimeoutTask;
import com.heng.config.BizException;
import com.heng.config.TradeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class LaunchReadinessTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderService orders;
    @Autowired PaymentService payments;
    @Autowired PendingOrderTimeoutTask timeoutTask;
    @Autowired TradeProperties properties;

    @BeforeEach void prepare() {
        properties.setOrderTimeoutTaskEnabled(false);
        properties.setOrderTimeoutMinutes(30);
        for (String table : List.of("v_member_session", "v_push_record", "v_settlement_record", "v_stock_log",
                "v_payment_record", "v_order_item", "v_order", "v_pay_config", "v_stock", "v_goods_sku",
                "v_goods_spu", "v_member_point_ledger", "v_member_balance_ledger", "v_member")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("INSERT INTO v_member(member_id,username,balance_fen,points) VALUES(1,'buyer',1000,80)");
        jdbc.update("INSERT INTO v_order(order_id,order_no,member_id,pay_amount_fen,points_used,status,create_time) VALUES(1,'LAUNCH1',1,300,20,'PENDING_PAYMENT',?)", Timestamp.from(Instant.now().minusSeconds(3600)));
        jdbc.update("INSERT INTO v_order_item(order_id,quantity,unit_price_fen,subtotal_fen,goods_snapshot) VALUES(1,1,300,300,?)", "{\"skuId\":1,\"spuId\":1}");
        jdbc.update("INSERT INTO v_stock(sku_id,available_stock,locked_stock) VALUES(1,9,1)");
        jdbc.update("INSERT INTO v_goods_sku VALUES(1,10,0)");
        jdbc.update("INSERT INTO v_goods_spu(spu_id,total_stock,sales_count) VALUES(1,10,0)");
        jdbc.update("INSERT INTO v_pay_config(pay_type,enabled) VALUES('BALANCE',1)");
    }

    @Test void sessionsSurviveServiceRecreationAndRevokeImmediately() {
        var first = new MemberTokenService(jdbc, 168);
        String token = first.issue(1L);
        var restarted = new MemberTokenService(jdbc, 168);
        assertEquals(1L, restarted.resolve(token));
        assertNotEquals(token, jdbc.queryForObject("SELECT token_hash FROM v_member_session", String.class));
        restarted.revoke(token);
        assertNull(first.resolve(token));
        String expired = first.issue(1L);
        jdbc.update("UPDATE v_member_session SET expires_at = ?", Timestamp.from(Instant.now().minusSeconds(1)));
        assertNull(restarted.resolve(expired));
        restarted.removeExpired();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM v_member_session", Integer.class));
    }

    @Test void expiredOrderCannotChargeAndRepeatedTimeoutReturnsAssetsOnce() {
        assertThrows(BizException.class, () -> payments.pay(1L,"LAUNCH1","BALANCE"));
        assertEquals(1000L, jdbc.queryForObject("SELECT balance_fen FROM v_member", Long.class));
        properties.setOrderTimeoutTaskEnabled(true);
        try { timeoutTask.closeExpiredOrders(); timeoutTask.closeExpiredOrders(); }
        finally { properties.setOrderTimeoutTaskEnabled(false); }
        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM v_order", String.class));
        assertEquals(10, jdbc.queryForObject("SELECT available_stock FROM v_stock", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT locked_stock FROM v_stock", Integer.class));
        assertEquals(100, jdbc.queryForObject("SELECT points FROM v_member", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM v_member_point_ledger", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM v_push_record", Integer.class));
    }

    @Test void brokenStockRollsBackTimeoutAndPaidOrdersStayPaid() {
        jdbc.update("UPDATE v_stock SET locked_stock=0");
        assertThrows(BizException.class, () -> orders.expire(1L));
        assertEquals("PENDING_PAYMENT", jdbc.queryForObject("SELECT status FROM v_order", String.class));
        assertEquals(80, jdbc.queryForObject("SELECT points FROM v_member", Integer.class));
        jdbc.update("UPDATE v_order SET status='PAID'");
        orders.expire(1L);
        assertEquals("PAID", jdbc.queryForObject("SELECT status FROM v_order", String.class));
    }
}
