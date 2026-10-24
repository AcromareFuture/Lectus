package com.heng;

import com.heng.config.BizException;
import com.heng.demo.LectusDemoService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="lectus.demo.enabled=true")
@ActiveProfiles("test")
class LectusDemoTests {
    @Autowired LectusDemoService demo;
    @Autowired JdbcTemplate db;
    private static final long OWNER=700001,OTHER=700002;
    @BeforeEach void setup() {
        db.update("DELETE FROM lectus_demo_order");
        db.update("DELETE FROM v_member WHERE member_id IN (?,?)",OWNER,OTHER);
        db.update("INSERT INTO v_member(member_id,username,status) VALUES(?,'lectus_test_owner',1),(?,'lectus_test_other',1)",OWNER,OTHER);
    }
    String key() { return UUID.randomUUID().toString().replace("-", ""); }
    @Test void trustedPriceAndIdempotency() {
        String key=key(); var order=demo.create(OWNER,"wp-kobe","wechat",key);
        assertEquals(149900,order.amountFen());
        assertEquals(order.id(),demo.create(OWNER,"wp-kobe","wechat",key).id());
        assertThrows(BizException.class,()->demo.create(OWNER,"unknown","wechat",key()));
        assertThrows(BizException.class,()->demo.create(OWNER,"wp-kobe","balance",key()));
        assertEquals(1,demo.list(OWNER).size());
    }
    @Test void ownerRequiredForAllPrivateOperations() {
        var o=demo.create(OWNER,"wp-samba","alipay",key());
        assertTrue(demo.list(OTHER).isEmpty());
        assertThrows(BizException.class,()->demo.own(OTHER,o.id()));
        assertThrows(BizException.class,()->demo.cancel(OTHER,o.id()));
        assertThrows(BizException.class,()->demo.change(OTHER,o.id(),"wechat"));
    }
    @Test void switchingMethodRevokesOldCodeAndScanIsIdempotent() {
        var old=demo.create(OWNER,"wp-kobe","wechat",key()); var current=demo.change(OWNER,old.id(),"alipay");
        assertNotEquals(old.scanToken(),current.scanToken());
        assertThrows(BizException.class,()->demo.scan(old.scanToken()));
        var scanned=demo.scan(current.scanToken()); assertEquals("PAID",scanned.get("state"));
        assertFalse(scanned.containsKey("memberId")); assertFalse(scanned.containsKey("scanToken"));
        var paid=demo.own(OWNER,old.id()); assertNotNull(paid.paidAt());
        demo.scan(current.scanToken()); assertEquals(paid.paidAt(),demo.own(OWNER,old.id()).paidAt());
        assertEquals("PAID",demo.cancel(OWNER,old.id()).state());
        assertThrows(BizException.class,()->demo.change(OWNER,old.id(),"wechat"));
    }
    @Test void cancelledAndExpiredCannotBePaid() {
        var cancelled=demo.create(OWNER,"wp-kobe","wechat",key()); demo.cancel(OWNER,cancelled.id());
        assertEquals("CANCELLED",demo.scan(cancelled.scanToken()).get("state"));
        var expired=demo.create(OWNER,"wp-samba","alipay",key());
        db.update("UPDATE lectus_demo_order SET expires_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(1)),expired.id());
        assertEquals("EXPIRED",demo.scan(expired.scanToken()).get("state"));
        assertNull(demo.own(OWNER,expired.id()).paidAt());
        assertEquals("EXPIRED",demo.scan(expired.scanToken()).get("state"));
    }
    @Test void simultaneousScansOnlyCompleteOnce() throws Exception {
        var o=demo.create(OWNER,"wp-kobe","wechat",key());
        var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->demo.scan(o.scanToken())); var b=pool.submit(()->demo.scan(o.scanToken()));
            assertEquals("PAID",a.get(10,TimeUnit.SECONDS).get("state"));
            assertEquals("PAID",b.get(10,TimeUnit.SECONDS).get("state"));
        } finally { pool.shutdownNow(); }
        assertNotNull(demo.own(OWNER,o.id()).paidAt());
        assertEquals(1,demo.list(OWNER).size());
    }
}
