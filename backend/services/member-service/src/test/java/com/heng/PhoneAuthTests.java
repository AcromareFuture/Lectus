package com.heng;

import com.heng.auth.*;
import com.heng.biz.MemberCenterService;
import com.heng.config.BizException;
import com.heng.domain.VMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.heng.auth.PhoneAuthService.Purpose.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PhoneAuthTests {
    @Autowired PhoneAuthService auth;
    @Autowired SmsProperties properties;
    @Autowired JdbcTemplate jdbc;
    @Autowired FakeSms gateway;
    @Autowired MemberTokenService tokens;
    @Autowired MemberCenterService center;
    private static final String PHONE = "13900000001";

    @TestConfiguration static class TestConfig {
        @Bean @Primary FakeSms fakeSms() { return new FakeSms(); }
    }
    // 仅存在于测试类中，不存在任何生产绕过/固定验证码入口。
    static class FakeSms implements SmsGateway {
        boolean enabled = true, failSend = false, failVerify = false;
        final AtomicInteger sends = new AtomicInteger(), checks = new AtomicInteger();
        final ConcurrentMap<String, String> schemes = new ConcurrentHashMap<>();
        public boolean available() { return enabled; }
        public void send(String phone, String scheme, String id) {
            sends.incrementAndGet();
            if (failSend) throw new BizException("provider failure");
            schemes.put(id, phone + ":" + scheme);
        }
        public boolean verify(String phone, String scheme, String id, String code) {
            checks.incrementAndGet();
            if (failVerify) throw new BizException("provider failure");
            return "654321".equals(code) && (phone + ":" + scheme).equals(schemes.get(id));
        }
    }
    @BeforeEach void prepare() {
        jdbc.update("DELETE FROM v_member_session");
        jdbc.update("DELETE FROM v_member_phone_identity");
        jdbc.update("DELETE FROM v_sms_challenge");
        jdbc.update("DELETE FROM v_member");
        properties.setSchemePrefix("Viridac");
        properties.setPhoneHourlyLimit(5); properties.setPhoneDailyLimit(10);
        properties.setIpHourlyLimit(20); properties.setDailyLimit(200);
        gateway.enabled = true; gateway.failSend = false; gateway.failVerify = false;
        gateway.sends.set(0); gateway.checks.set(0); gateway.schemes.clear();
    }
    @Test void resetVerifiedPhonePasswordRevokesSessionsAndRejectsReplay(){
        String register=send(PHONE,REGISTER);var session=auth.authenticate(PHONE,REGISTER,register,"654321","Buyer");
        jdbc.update("UPDATE v_sms_challenge SET created_at=?",Timestamp.from(Instant.now().minusSeconds(100)));
        String reset=send(PHONE,RESET);auth.resetPassword(PHONE,reset,"654321","new-password-123");
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM v_member_session",Integer.class));assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches("new-password-123",jdbc.queryForObject("SELECT password FROM v_member",String.class)));
        assertThrows(BizException.class,()->auth.resetPassword(PHONE,reset,"654321","new-password-456"));
    }
    @Test void resetChallengeBudgetAndPurposeCannotBeBypassed(){
        String register=send(PHONE,REGISTER);auth.authenticate(PHONE,REGISTER,register,"654321","Buyer");jdbc.update("UPDATE v_sms_challenge SET created_at=?",Timestamp.from(Instant.now().minusSeconds(100)));
        String reset=send(PHONE,RESET);for(int i=0;i<5;i++)assertThrows(BizException.class,()->auth.resetPassword(PHONE,reset,"000000","new-password-123"));assertThrows(BizException.class,()->auth.resetPassword(PHONE,reset,"654321","new-password-123"));assertEquals(5,jdbc.queryForObject("SELECT attempts FROM v_sms_challenge WHERE challenge_id=?",Integer.class,reset));
        assertThrows(BizException.class,()->auth.resetPassword(PHONE,register,"654321","new-password-123"));
    }
    String send(String phone, PhoneAuthService.Purpose purpose) {
        return (String) auth.send(phone, purpose, "127.0.0.1").get("challengeId");
    }
    Map<String, Object> verify(String phone, PhoneAuthService.Purpose purpose, String id, String code) {
        return auth.authenticate(phone, purpose, id, code, "手机会员");
    }
    void age() { jdbc.update("UPDATE v_sms_challenge SET created_at=?", Timestamp.from(Instant.now().minusSeconds(61))); }

    @Test void registrationAndLoginIssuePersistentSessionWithoutReusingCode() {
        String id = send(PHONE, REGISTER);
        var registered = verify(PHONE, REGISTER, id, "654321");
        assertEquals(registered.get("memberId"), tokens.resolve((String)registered.get("token")));
        assertEquals(0L, jdbc.queryForObject("SELECT balance_fen FROM v_member", Long.class));
        assertEquals(PHONE, jdbc.queryForObject("SELECT phone FROM v_member_phone_identity", String.class));
        assertThrows(BizException.class, () -> verify(PHONE, REGISTER, id, "654321"));
        age();
        var loggedIn = verify(PHONE, LOGIN, send(PHONE, LOGIN), "654321");
        assertEquals(registered.get("memberId"), loggedIn.get("memberId"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM v_member", Integer.class));
    }

    @Test void legacyUnverifiedPhoneCannotGrantAccessToExistingAccount() {
        jdbc.update("INSERT INTO v_member(member_id,username,phone) VALUES(100,'legacy',?)", PHONE);
        String id = send(PHONE, LOGIN);
        assertThrows(BizException.class, () -> verify(PHONE, LOGIN, id, "654321"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM v_member_session", Integer.class));
        age();
        var registered = verify(PHONE, REGISTER, send(PHONE, REGISTER), "654321");
        assertNotEquals(100L, registered.get("memberId"));
        assertEquals(registered.get("memberId"), jdbc.queryForObject("SELECT member_id FROM v_member_phone_identity", Long.class));
    }

    @Test void wrongCodesConsumeAttemptsEvenWhenErrorsAreThrown() {
        String id = send(PHONE, REGISTER);
        for (int i = 0; i < 5; i++) assertThrows(BizException.class, () -> verify(PHONE, REGISTER, id, "000000"));
        assertEquals(5, jdbc.queryForObject("SELECT attempts FROM v_sms_challenge", Integer.class));
        assertThrows(BizException.class, () -> verify(PHONE, REGISTER, id, "654321"));
        assertEquals(5, gateway.checks.get());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM v_member_session", Integer.class));
    }

    @Test void expiryAndPhoneAndPurposeMismatchNeverReachProvider() {
        String id = send(PHONE, REGISTER);
        assertThrows(BizException.class, () -> verify("13900000002", REGISTER, id, "654321"));
        assertThrows(BizException.class, () -> verify(PHONE, LOGIN, id, "654321"));
        jdbc.update("UPDATE v_sms_challenge SET expires_at=?", Timestamp.from(Instant.now().minusSeconds(1)));
        assertThrows(BizException.class, () -> verify(PHONE, REGISTER, id, "654321"));
        assertEquals(0, gateway.checks.get());
    }

    @Test void cooldownSurvivesRestartAndReplacementInvalidatesOlderCode() {
        String first = send(PHONE, REGISTER);
        assertThrows(BizException.class, () -> send(PHONE, LOGIN));
        assertEquals(1, gateway.sends.get());
        age();
        String second = send(PHONE, REGISTER);
        assertThrows(BizException.class, () -> verify(PHONE, REGISTER, first, "654321"));
        verify(PHONE, REGISTER, second, "654321");
    }

    @Test void failedSendKeepsCooldownAndCannotBeVerified() {
        gateway.failSend = true;
        assertThrows(BizException.class, () -> send(PHONE, REGISTER));
        gateway.failSend = false;
        assertThrows(BizException.class, () -> send(PHONE, REGISTER));
        String id = jdbc.queryForObject("SELECT challenge_id FROM v_sms_challenge", String.class);
        assertThrows(BizException.class, () -> verify(PHONE, REGISTER, id, "654321"));
        assertEquals("FAILED", jdbc.queryForObject("SELECT state FROM v_sms_challenge", String.class));
    }

    @Test void providerFailureConsumesAttemptAndNeverAuthenticates() {
        String id = send(PHONE, REGISTER);
        gateway.failVerify = true;
        assertThrows(BizException.class, () -> verify(PHONE, REGISTER, id, "654321"));
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM v_sms_challenge", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM v_member_session", Integer.class));
    }

    @Test void phoneHourlyAndDailyQuotasIncludeFailedSends() {
        properties.setPhoneHourlyLimit(1);
        send(PHONE, REGISTER); age();
        assertThrows(BizException.class, () -> send(PHONE, REGISTER));
        properties.setPhoneHourlyLimit(5); properties.setPhoneDailyLimit(1);
        jdbc.update("UPDATE v_sms_challenge SET created_at=?", Timestamp.from(Instant.now().minusSeconds(3700)));
        assertThrows(BizException.class, () -> send(PHONE, REGISTER));
    }

    @Test void ipAndGlobalQuotasPreventNewNumberBypass() {
        properties.setIpHourlyLimit(1);
        send(PHONE, REGISTER);
        assertThrows(BizException.class, () -> send("13900000002", REGISTER));
        properties.setIpHourlyLimit(20); properties.setDailyLimit(1);
        assertThrows(BizException.class, () -> auth.send("13900000002", REGISTER, "192.0.2.2"));
        assertEquals(1, gateway.sends.get());
    }

    @Test void simultaneousVerificationCreatesExactlyOneAccountAndToken() throws Exception {
        String id = send(PHONE, REGISTER);
        var pool = Executors.newFixedThreadPool(2);
        var barrier = new CyclicBarrier(2);
        Callable<Boolean> action = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            try { verify(PHONE, REGISTER, id, "654321"); return true; }
            catch (BizException e) { return false; }
        };
        try {
            var one = pool.submit(action); var two = pool.submit(action);
            assertNotEquals(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM v_member", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM v_member_session", Integer.class));
        assertEquals(1, gateway.checks.get());
    }

    @Test void simultaneousSendReservesOnlyOneBudget() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var barrier = new CyclicBarrier(2);
        Callable<Boolean> action = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            try { send(PHONE, REGISTER); return true; } catch (BizException e) { return false; }
        };
        try {
            var one = pool.submit(action); var two = pool.submit(action);
            assertNotEquals(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
        assertEquals(1, gateway.sends.get());
    }

    @Test void disabledMemberAndDuplicateRegistrationNeverIssueNewToken() {
        verify(PHONE, REGISTER, send(PHONE, REGISTER), "654321");
        jdbc.update("DELETE FROM v_member_session");
        age();
        assertThrows(BizException.class, () -> verify(PHONE, REGISTER, send(PHONE, REGISTER), "654321"));
        jdbc.update("UPDATE v_member SET status=0"); age();
        assertThrows(BizException.class, () -> verify(PHONE, LOGIN, send(PHONE, LOGIN), "654321"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM v_member_session", Integer.class));
    }

    @Test void profileCannotChangeVerifiedPhoneAndDisabledServiceDoesNotSend() {
        var session = verify(PHONE, REGISTER, send(PHONE, REGISTER), "654321");
        VMember form = new VMember(); form.setPhone("13900000002");
        assertThrows(BizException.class, () -> center.updateProfile((Long)session.get("memberId"), form));
        form.setPhone(PHONE); form.setNickname("新昵称");
        center.updateProfile((Long)session.get("memberId"), form);
        gateway.enabled = false;
        assertThrows(BizException.class, () -> send("13900000002", REGISTER));
        assertEquals(1, gateway.sends.get());
    }
}
