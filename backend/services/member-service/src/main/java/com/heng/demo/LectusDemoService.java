package com.heng.demo;

import com.heng.config.BizException;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** Local simulated payments. Never calls the trade, inventory, wallet or payment-provider services. */
@Service
@ConditionalOnProperty(name="lectus.demo.enabled", havingValue="true")
public class LectusDemoService {
    private final JdbcTemplate db;
    private final Map<String, Product> products = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    public LectusDemoService(JdbcTemplate db) { this.db=db; }
    @PostConstruct void init() throws Exception {
        try (var stream=getClass().getResourceAsStream("/lectus-demo-prices.json")) {
            if(stream==null) throw new IllegalStateException("Missing demo prices");
            for(var p:JsonMapper.builder().build().readTree(stream).get("products"))
                products.put(p.get("id").asString(),new Product(p.get("name").asString(),p.get("priceCents").asLong()));
        }
        db.execute("""
            CREATE TABLE IF NOT EXISTS lectus_demo_order (
              id CHAR(32) PRIMARY KEY, member_id BIGINT NOT NULL, request_key CHAR(32) NOT NULL,
              product_id VARCHAR(80) NOT NULL, product_name VARCHAR(255) NOT NULL, amount_fen BIGINT NOT NULL,
              pay_method VARCHAR(10) NOT NULL, state VARCHAR(12) NOT NULL, scan_token CHAR(64) NOT NULL UNIQUE,
              created_at TIMESTAMP NOT NULL, expires_at TIMESTAMP NOT NULL, paid_at TIMESTAMP NULL,
              UNIQUE(member_id,request_key))
            """);
    }
    private record Product(String name,long price) {}
    public record Order(String id,String productId,String productName,long amountFen,String payMethod,String state,
                        String scanToken,long createdAt,long expiresAt,Long paidAt) {}
    private String token() { byte[] b=new byte[32]; random.nextBytes(b); return HexFormat.of().formatHex(b); }
    private void method(String value) { if(!List.of("wechat","alipay").contains(value)) throw new BizException("请选择微信或支付宝"); }
    private void expire(long member) { db.update("UPDATE lectus_demo_order SET state='EXPIRED' WHERE member_id=? AND state='PENDING' AND expires_at<=?",member,Timestamp.from(Instant.now())); }
    private List<Order> find(String where,Object...args) {
        return db.query("SELECT * FROM lectus_demo_order WHERE "+where,(r,i)->new Order(r.getString("id"),r.getString("product_id"),r.getString("product_name"),r.getLong("amount_fen"),r.getString("pay_method"),r.getString("state"),r.getString("scan_token"),r.getTimestamp("created_at").getTime(),r.getTimestamp("expires_at").getTime(),r.getTimestamp("paid_at")==null?null:r.getTimestamp("paid_at").getTime()),args);
    }
    public List<Order> list(long member) { expire(member); return find("member_id=? ORDER BY created_at DESC LIMIT 100",member); }
    public Order own(long member,String id) {
        db.update("UPDATE lectus_demo_order SET state='EXPIRED' WHERE member_id=? AND id=? AND state='PENDING' AND expires_at<=?",member,id,Timestamp.from(Instant.now()));
        var rows=find("member_id=? AND id=?",member,id);
        if(rows.isEmpty()) throw new BizException("模拟订单不存在"); return rows.get(0);
    }
    @Transactional public Order create(long member,String product,String payMethod,String key) {
        method(payMethod);
        if(key==null || !key.matches("[a-f0-9]{32}")) throw new BizException("下单请求无效");
        // Serialize retries for one member, including identical simultaneous POSTs.
        db.queryForObject("SELECT member_id FROM v_member WHERE member_id=? FOR UPDATE",Long.class,member);
        var previous=find("member_id=? AND request_key=?",member,key);
        if(!previous.isEmpty()) return own(member,previous.get(0).id());
        var p=products.get(product); if(p==null || p.price()<=0) throw new BizException("该商品暂不能模拟购买");
        expire(member);
        if(db.queryForObject("SELECT COUNT(*) FROM lectus_demo_order WHERE member_id=? AND state='PENDING'",Long.class,member)>=10) throw new BizException("请先完成或取消待支付的模拟订单");
        var now=Instant.now(); String id=UUID.randomUUID().toString().replace("-", "");
        db.update("INSERT INTO lectus_demo_order(id,member_id,request_key,product_id,product_name,amount_fen,pay_method,state,scan_token,created_at,expires_at) VALUES(?,?,?,?,?,?,?,'PENDING',?,?,?)",id,member,key,product,p.name(),p.price(),payMethod,token(),Timestamp.from(now),Timestamp.from(now.plusSeconds(600)));
        return own(member,id);
    }
    @Transactional public Order change(long member,String id,String payMethod) {
        method(payMethod); lock(member,id); var order=own(member,id);
        if(!"PENDING".equals(order.state())) throw new BizException("订单已结束，不能切换支付方式");
        db.update("UPDATE lectus_demo_order SET pay_method=?,scan_token=? WHERE member_id=? AND id=? AND state='PENDING'",payMethod,token(),member,id);
        return own(member,id);
    }
    @Transactional public Order cancel(long member,String id) {
        lock(member,id); own(member,id);
        db.update("UPDATE lectus_demo_order SET state='CANCELLED' WHERE member_id=? AND id=? AND state='PENDING'",member,id);
        return own(member,id);
    }
    private void lock(long member,String id) {
        if(db.query("SELECT id FROM lectus_demo_order WHERE member_id=? AND id=? FOR UPDATE",(r,i)->r.getString(1),member,id).isEmpty()) throw new BizException("模拟订单不存在");
    }
    @Transactional public Map<String,Object> scan(String token) {
        if(token==null || !token.matches("[a-f0-9]{64}")) throw new BizException("二维码无效，请重新扫码");
        var rows=find("scan_token=? FOR UPDATE",token); if(rows.isEmpty()) throw new BizException("二维码已更新，请扫描最新二维码");
        var order=rows.get(0); var now=Timestamp.from(Instant.now());
        db.update("UPDATE lectus_demo_order SET state='EXPIRED' WHERE id=? AND state='PENDING' AND expires_at<=?",order.id(),now);
        db.update("UPDATE lectus_demo_order SET state='PAID',paid_at=? WHERE id=? AND state='PENDING' AND expires_at>?",now,order.id(),now);
        order=find("id=?",order.id()).get(0);
        // Invalid terminal states must commit expiration, so expose them as a result rather than throwing.
        return Map.of("id",order.id(),"state",order.state(),"productName",order.productName(),"amountFen",order.amountFen(),"payMethod",order.payMethod());
    }
}
