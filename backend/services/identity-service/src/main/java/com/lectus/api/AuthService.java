package com.lectus.api;

import static com.lectus.api.Contracts.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService implements SessionUserResolver {
    private final JdbcTemplate db;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
    private final String dummyHash = passwords.encode(UUID.randomUUID().toString());
    private final SecureRandom random = new SecureRandom();
    private final long sessionHours;
    public AuthService(JdbcTemplate db, @Value("${lectus.session-hours}") long sessionHours) {
        this.db = db;
        this.sessionHours = sessionHours;
    }
    @Transactional
    public Session register(Register request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw ApiException.bad("PASSWORD_TOO_LONG", "密码不能超过72字节");
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (Boolean.TRUE.equals(db.queryForObject("SELECT COUNT(*) > 0 FROM accounts WHERE email=?", Boolean.class, email)))
            throw ApiException.conflict("ACCOUNT_EXISTS", "该邮箱已注册");
        User user = new User(UUID.randomUUID().toString(), email, request.displayName().strip());
        db.update("INSERT INTO accounts(id,email,password_hash,display_name,created_at) VALUES(?,?,?,?,?)",
                user.id(), email, passwords.encode(request.password()), user.displayName(), OffsetDateTime.now(ZoneOffset.UTC));
        return createSession(user);
    }
    @Transactional
    public Session login(Login request) {
        var rows = db.queryForList("SELECT * FROM accounts WHERE email=?", request.email().strip().toLowerCase(Locale.ROOT));
        String hash = rows.isEmpty() ? dummyHash : (String) rows.getFirst().get("password_hash");
        boolean matches = request.password().getBytes(StandardCharsets.UTF_8).length <= 72 && passwords.matches(request.password(), hash);
        if (rows.isEmpty() || !matches) throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "邮箱或密码不正确");
        var row = rows.getFirst();
        return createSession(new User((String) row.get("id"), (String) row.get("email"), (String) row.get("display_name")));
    }
    private Session createSession(User user) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        OffsetDateTime expiry = OffsetDateTime.now(ZoneOffset.UTC).plusHours(sessionHours);
        db.update("DELETE FROM auth_sessions WHERE expires_at < ?", OffsetDateTime.now(ZoneOffset.UTC));
        db.update("INSERT INTO auth_sessions(token_hash, account_id, expires_at) VALUES(?,?,?)", digest(token), user.id(), expiry);
        return new Session(token, expiry.toString(), user);
    }
    public User authenticate(String token) {
        if (token == null || token.length() != 43) return null;
        var users = db.query("SELECT a.id,a.email,a.display_name FROM accounts a JOIN auth_sessions s ON s.account_id=a.id WHERE s.token_hash=? AND s.expires_at>?",
                (rs,n)->new User(rs.getString(1),rs.getString(2),rs.getString(3)), digest(token), OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
        return users.isEmpty() ? null : users.getFirst();
    }
    public void logout(String token) { db.update("DELETE FROM auth_sessions WHERE token_hash=?", digest(token)); }
    static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
