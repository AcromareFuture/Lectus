package com.heng.auth;
import com.heng.config.BizException;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.scheduling.annotation.Scheduled;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
@Service
public class PasswordLoginGuard {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    public PasswordLoginGuard(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.transaction=new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public static void validatePassword(String password) {
        if(password==null || password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new BizException("密码 UTF-8 长度不能超过 72 字节");
    }
    public void attempt(String account, String ip) {
        String a=hash(account.toLowerCase(java.util.Locale.ROOT)), i=hash(ip==null?"unknown":ip);
        transaction.executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT guard_id FROM v_auth_guard WHERE guard_id=1 FOR UPDATE",Integer.class);
            java.sql.Timestamp since=java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(600));
            if(jdbc.queryForObject("SELECT COUNT(*) FROM v_password_attempt WHERE account_hash=? AND created_at>?",Long.class,a,since)>=20
                || jdbc.queryForObject("SELECT COUNT(*) FROM v_password_attempt WHERE ip_hash=? AND created_at>?",Long.class,i,since)>=100)
                throw new BizException("登录尝试过于频繁，请十分钟后重试");
            jdbc.update("INSERT INTO v_password_attempt(account_hash,ip_hash,created_at) VALUES(?,?,?)",a,i,java.sql.Timestamp.from(java.time.Instant.now()));
        });
    }
    @Scheduled(fixedDelay=3600000,initialDelay=120000)
    public void cleanup() { jdbc.update("DELETE FROM v_password_attempt WHERE created_at<?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(86400))); }
    private String hash(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
}
