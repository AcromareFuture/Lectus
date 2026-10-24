package com.heng.auth;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/** 跨重启/实例的会员会话；仅持久化令牌 SHA-256 摘要。 */
@Service
public class MemberTokenService {
    private final SecureRandom random = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final Duration ttl;

    public MemberTokenService(JdbcTemplate jdbc, @Value("${viridac.auth.token-ttl-hours:168}") long ttlHours) {
        this.jdbc = jdbc;
        this.ttl = Duration.ofHours(Math.max(1, ttlHours));
    }

    public String issue(Long memberId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO v_member_session(token_hash, member_id, expires_at) VALUES (?, ?, ?)",
                hash(token), memberId, Timestamp.from(Instant.now().plus(ttl)));
        return token;
    }

    public Long resolve(String token) {
        if (token == null || token.length() != 43 || !token.matches("[A-Za-z0-9_-]+")) return null;
        var members = jdbc.query("SELECT member_id FROM v_member_session WHERE token_hash = ? AND expires_at > ?",
                (rs, row) -> rs.getLong("member_id"), hash(token), Timestamp.from(Instant.now()));
        return members.isEmpty() ? null : members.get(0);
    }

    public void revokeAll(Long memberId) { jdbc.update("DELETE FROM v_member_session WHERE member_id=?",memberId); }

    public void revoke(String token) {
        if (token != null && !token.isBlank()) jdbc.update("DELETE FROM v_member_session WHERE token_hash = ?", hash(token));
    }

    @Scheduled(fixedDelay = 3600000, initialDelay = 60000)
    public void removeExpired() {
        jdbc.update("DELETE FROM v_member_session WHERE expires_at <= ?", Timestamp.from(Instant.now()));
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
