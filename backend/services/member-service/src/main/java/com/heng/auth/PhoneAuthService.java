package com.heng.auth;

import com.heng.config.BizException;
import com.heng.domain.VMember;
import com.heng.service.VMemberService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class PhoneAuthService {
    public enum Purpose { LOGIN, REGISTER, BIND, RESET }
    private static final int COOLDOWN = 60, VALID_SECONDS = 300, MAX_ATTEMPTS = 5;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final SmsGateway gateway;
    private final SmsProperties properties;
    private final VMemberService members;
    private final MemberTokenService tokens;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();

    public PhoneAuthService(JdbcTemplate jdbc, PlatformTransactionManager manager, SmsGateway gateway,
                            SmsProperties properties, VMemberService members, MemberTokenService tokens) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
        this.gateway = gateway;
        this.properties = properties;
        this.members = members;
        this.tokens = tokens;
    }

    public boolean available() { return gateway.available(); }

    public Map<String, Object> send(String phone, Purpose purpose, String ip) {
        validatePhone(phone);
        if (purpose == null) throw new BizException("请选择登录或注册");
        if (!available()) throw new BizException("短信登录暂未开通，请使用账号密码登录");
        String id = UUID.randomUUID().toString().replace("-", "");
        String scheme = properties.getSchemePrefix() + "_" + purpose.name();
        String ipHash = hash(ip == null ? "unknown" : ip);
        Instant now = Instant.now();
        // 所有实例共用数据库锁。失败/超时发送也消耗预算，避免自动重试造成短信费用。
        transaction.executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT guard_id FROM v_sms_guard WHERE guard_id=1 FOR UPDATE", Integer.class);
            long recent = count("phone=? AND created_at>?", phone, time(now.minusSeconds(COOLDOWN)));
            if (recent > 0) throw new BizException("验证码发送过于频繁，请 60 秒后重试");
            if (count("phone=? AND created_at>?", phone, time(now.minusSeconds(3600))) >= properties.getPhoneHourlyLimit()
                    || count("phone=? AND created_at>?", phone, time(now.minusSeconds(86400))) >= properties.getPhoneDailyLimit()
                    || count("ip_hash=? AND created_at>?", ipHash, time(now.minusSeconds(3600))) >= properties.getIpHourlyLimit()
                    || count("created_at>?", time(now.minusSeconds(86400))) >= properties.getDailyLimit()) {
                throw new BizException("验证码发送次数已达限额，请稍后重试");
            }
            jdbc.update("UPDATE v_sms_challenge SET state='SUPERSEDED' WHERE phone=? AND state IN ('READY','SENDING')", phone);
            jdbc.update("INSERT INTO v_sms_challenge(challenge_id,phone,purpose,scheme,ip_hash,state,attempts,created_at,expires_at) VALUES(?,?,?,?,?,'SENDING',0,?,?)",
                    id, phone, purpose.name(), scheme, ipHash, time(now), time(now.plusSeconds(VALID_SECONDS)));
        });
        try {
            gateway.send(phone, scheme, id);
            int updated = jdbc.update("UPDATE v_sms_challenge SET state='READY' WHERE challenge_id=? AND state='SENDING'", id);
            if (updated != 1) throw new BizException("本次验证码已失效，请重新获取");
        } catch (RuntimeException ignored) {
            jdbc.update("UPDATE v_sms_challenge SET state='FAILED' WHERE challenge_id=? AND state='SENDING'", id);
            throw new BizException("短信暂时无法发送，请稍后重试；也可使用账号密码登录");
        }
        return Map.of("challengeId", id, "retryAfterSeconds", COOLDOWN, "expiresInSeconds", VALID_SECONDS);
    }

    public Map<String, Object> authenticate(String phone, Purpose purpose, String challengeId, String code, String nickname) {
        validatePhone(phone);
        if (purpose != Purpose.LOGIN && purpose != Purpose.REGISTER) throw new BizException("请使用对应的账户安全流程");
        if (purpose == null || challengeId == null || !challengeId.matches("[a-f0-9]{32}")
                || code == null || !code.matches("[0-9]{6}")) throw new BizException("请输入 6 位短信验证码");
        if (nickname != null && nickname.length() > 30) throw new BizException("昵称最多 30 个字符");
        if (!available()) throw new BizException("短信登录暂未开通，请使用账号密码登录");
        // 错码尝试和消费结果提交后才向外抛错；不会因业务异常回滚次数。
        AuthResult result = transaction.execute(status -> {
            var challenges = jdbc.query("SELECT phone,purpose,scheme,state,attempts,expires_at FROM v_sms_challenge WHERE challenge_id=? FOR UPDATE",
                    (rs, index) -> new Challenge(rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getInt(5), rs.getTimestamp(6).toInstant()), challengeId);
            if (challenges.isEmpty()) return AuthResult.error("请先获取验证码");
            Challenge c = challenges.get(0);
            if (!phone.equals(c.phone()) || !purpose.name().equals(c.purpose()) || !"READY".equals(c.state())
                    || c.attempts() >= MAX_ATTEMPTS || !c.expiresAt().isAfter(Instant.now())) {
                return AuthResult.error("验证码已失效或用途不匹配，请重新获取");
            }
            jdbc.update("UPDATE v_sms_challenge SET attempts=attempts+1 WHERE challenge_id=?", challengeId);
            boolean pass;
            try { pass = gateway.verify(phone, c.scheme(), challengeId, code); }
            catch (RuntimeException ignored) { return AuthResult.error("短信核验暂不可用，请稍后重试"); }
            if (!pass) return AuthResult.error(c.attempts() + 1 >= MAX_ATTEMPTS
                    ? "尝试次数已用完，请重新获取验证码" : "验证码不正确，请重新输入");
            jdbc.update("UPDATE v_sms_challenge SET state='CONSUMED' WHERE challenge_id=?", challengeId);
            var identities = jdbc.query("SELECT member_id FROM v_member_phone_identity WHERE phone=?", (rs, row) -> rs.getLong(1), phone);
            VMember member;
            if (purpose == Purpose.LOGIN) {
                if (identities.isEmpty()) return AuthResult.error("该手机号尚未注册，请切换到注册；旧账号可使用账号密码登录");
                member = members.getById(identities.get(0));
            } else {
                if (!identities.isEmpty()) return AuthResult.error("该手机号已注册，请切换到登录");
                member = new VMember();
                member.setUsername("m_" + UUID.randomUUID().toString().replace("-", ""));
                member.setPassword(passwords.encode(UUID.randomUUID().toString()));
                member.setNickname(nickname == null || nickname.isBlank() ? "维达会员" + phone.substring(7) : nickname.trim());
                // 旧 phone 是未验证联系资料。其唯一索引冲突不能授予旧账号身份，也不能阻止号码所有人注册。
                if (!members.lambdaQuery().eq(VMember::getPhone, phone).exists()) member.setPhone(phone);
                member.setLevelId(1L);
                member.setGrowth(0);
                member.setPoints(0);
                member.setBalanceFen(0L);
                member.setStatus(1);
                members.save(member);
                jdbc.update("INSERT INTO v_member_phone_identity(phone,member_id) VALUES(?,?)", phone, member.getMemberId());
            }
            if (member == null || !Integer.valueOf(1).equals(member.getStatus())) return AuthResult.error("账户已停用，请联系管理员");
            members.lambdaUpdate().eq(VMember::getMemberId,member.getMemberId()).set(VMember::getLastLoginTime,new Date()).update();
            return new AuthResult(null, Map.of("memberId", member.getMemberId(), "nickname",
                    member.getNickname() == null ? "" : member.getNickname(), "token", tokens.issue(member.getMemberId())));
        });
        if (result.error() != null) throw new BizException(result.error());
        return result.session();
    }

    public void resetPassword(String phone,String challengeId,String code,String password) {
        validatePhone(phone);PasswordLoginGuard.validatePassword(password);
        if(password==null || password.length()<8 || password.length()>64 || challengeId==null || !challengeId.matches("[a-f0-9]{32}") || code==null || !code.matches("[0-9]{6}")) throw new BizException("密码或验证码参数无效");
        if(!available()) throw new BizException("短信服务暂未开通");
        var ids=jdbc.query("SELECT member_id FROM v_member_phone_identity WHERE phone=?",(rs,i)->rs.getLong(1),phone);
        AuthResult result=transaction.execute(status -> {
            if(!ids.isEmpty()) jdbc.queryForObject("SELECT member_id FROM v_member WHERE member_id=? FOR UPDATE",Long.class,ids.get(0));
            var rows=jdbc.query("SELECT phone,purpose,scheme,state,attempts,expires_at FROM v_sms_challenge WHERE challenge_id=? FOR UPDATE",
                (rs,i)->new Challenge(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getInt(5),rs.getTimestamp(6).toInstant()),challengeId);
            if(rows.isEmpty()) return AuthResult.error("请先获取验证码");
            Challenge c=rows.get(0);
            if(!phone.equals(c.phone()) || !"RESET".equals(c.purpose()) || !"READY".equals(c.state()) || c.attempts()>=MAX_ATTEMPTS || !c.expiresAt().isAfter(Instant.now())) return AuthResult.error("验证码已失效或用途不匹配");
            jdbc.update("UPDATE v_sms_challenge SET attempts=attempts+1 WHERE challenge_id=?",challengeId);
            boolean pass;try{pass=gateway.verify(phone,c.scheme(),challengeId,code);}catch(RuntimeException e){return AuthResult.error("短信核验暂不可用");}
            if(!pass) return AuthResult.error("验证码不正确");
            jdbc.update("UPDATE v_sms_challenge SET state='CONSUMED' WHERE challenge_id=?",challengeId);
            if(ids.isEmpty()) return AuthResult.error("手机号未绑定账户");
            long memberId=ids.get(0);
            var member=members.getById(memberId);
            if(member==null || !Integer.valueOf(1).equals(member.getStatus()) || jdbc.queryForObject("SELECT COUNT(*) FROM v_member_phone_identity WHERE phone=? AND member_id=?",Long.class,phone,memberId)!=1) return AuthResult.error("账户或手机号状态已变更");
            jdbc.update("UPDATE v_member SET password=? WHERE member_id=?",passwords.encode(password),memberId);
            tokens.revokeAll(memberId);
            return new AuthResult(null,Map.of());
        });
        if(result.error()!=null) throw new BizException(result.error());
    }
    public Map<String, Object> sendBinding(Long memberId, String phone, String password, String ip) {
        VMember member = members.getById(memberId);
        if (member == null || password == null || !passwords.matches(password, member.getPassword())) throw new BizException("当前密码不正确");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM v_member_phone_identity WHERE phone=? AND member_id<>?", Long.class, phone, memberId) > 0) throw new BizException("该手机号已绑定其他账户");
        Map<String, Object> result = send(phone, Purpose.BIND, ip);
        jdbc.update("UPDATE v_sms_challenge SET member_id=? WHERE challenge_id=?", memberId, result.get("challengeId"));
        return result;
    }

    public Map<String, Object> bindPhone(Long memberId, String phone, String challengeId, String code) {
        validatePhone(phone);
        if (challengeId == null || !challengeId.matches("[a-f0-9]{32}") || code == null || !code.matches("[0-9]{6}")) throw new BizException("请输入有效验证码");
        if (!available()) throw new BizException("短信服务暂未开通");
        AuthResult result = transaction.execute(status -> {
            // Serialize identity changes for this account and one-time code consumption.
            jdbc.queryForObject("SELECT member_id FROM v_member WHERE member_id=? FOR UPDATE", Long.class, memberId);
            var challenges = jdbc.query("SELECT phone,purpose,scheme,state,attempts,expires_at FROM v_sms_challenge WHERE challenge_id=? AND member_id=? FOR UPDATE",
                    (rs,i) -> new Challenge(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getInt(5),rs.getTimestamp(6).toInstant()), challengeId, memberId);
            if (challenges.isEmpty()) return AuthResult.error("验证码不属于当前账户");
            Challenge c=challenges.get(0);
            if (!phone.equals(c.phone()) || !"BIND".equals(c.purpose()) || !"READY".equals(c.state()) || c.attempts() >= MAX_ATTEMPTS || !c.expiresAt().isAfter(Instant.now())) return AuthResult.error("验证码已失效，请重新获取");
            jdbc.update("UPDATE v_sms_challenge SET attempts=attempts+1 WHERE challenge_id=?", challengeId);
            boolean pass;
            try { pass=gateway.verify(phone,c.scheme(),challengeId,code); } catch(RuntimeException e) { return AuthResult.error("短信核验暂不可用"); }
            if (!pass) return AuthResult.error("验证码不正确，请重新输入");
            jdbc.queryForObject("SELECT guard_id FROM v_sms_guard WHERE guard_id=1 FOR UPDATE",Integer.class);
            if (jdbc.queryForObject("SELECT COUNT(*) FROM v_member_phone_identity WHERE phone=? AND member_id<>?",Long.class,phone,memberId)>0
                    || members.lambdaQuery().eq(VMember::getPhone,phone).ne(VMember::getMemberId,memberId).exists()) return AuthResult.error("该手机号已被其他账户使用");
            jdbc.update("DELETE FROM v_member_phone_identity WHERE member_id=?",memberId);
            jdbc.update("INSERT INTO v_member_phone_identity(phone,member_id) VALUES(?,?)",phone,memberId);
            jdbc.update("UPDATE v_member SET phone=? WHERE member_id=?",phone,memberId);
            jdbc.update("UPDATE v_sms_challenge SET state='CONSUMED' WHERE challenge_id=?",challengeId);
            jdbc.update("DELETE FROM v_member_session WHERE member_id=?",memberId);
            return new AuthResult(null,Map.of("token",tokens.issue(memberId)));
        });
        if(result.error()!=null) throw new BizException(result.error());
        return result.session();
    }

    @Scheduled(fixedDelay = 3600000, initialDelay = 120000)
    public void removeOldChallenges() {
        jdbc.update("DELETE FROM v_sms_challenge WHERE created_at<?", time(Instant.now().minusSeconds(172800)));
    }

    private void validatePhone(String phone) {
        if (phone == null || !phone.matches("1[3-9][0-9]{9}")) throw new BizException("请输入有效的中国大陆手机号");
    }
    private long count(String where, Object... args) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM v_sms_challenge WHERE " + where, Long.class, args);
    }
    private Timestamp time(Instant instant) { return Timestamp.from(instant); }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("无法计算请求摘要"); }
    }
    private record Challenge(String phone, String purpose, String scheme, String state, int attempts, Instant expiresAt) { }
    private record AuthResult(String error, Map<String, Object> session) {
        static AuthResult error(String message) { return new AuthResult(message, null); }
    }
}
