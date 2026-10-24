package com.heng;
import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.*;
import com.heng.auth.*;
import com.heng.config.BizException;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AliyunDysmsGatewayTests {
    Client client;SmsProperties config;JdbcTemplate jdbc;AliyunDysmsGateway gateway;
    @BeforeEach void setup() throws Exception {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:dysms;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE IF NOT EXISTS v_sms_local_code(challenge_id VARCHAR(32) PRIMARY KEY,phone VARCHAR(11),scheme VARCHAR(20),code_digest VARCHAR(64),expires_at TIMESTAMP)");jdbc.update("DELETE FROM v_sms_local_code");
        config=new SmsProperties();config.setEnabled(true);config.setProvider("dysms");config.setAccessKeyId("test-key");config.setAccessKeySecret("test-secret");config.setSignName("test-sign");config.setTemplateCode("SMS_TEST");config.setCodeSecret("test-pepper-at-least-thirty-two-characters");
        client=mock(Client.class);gateway=new AliyunDysmsGateway(config,jdbc);ReflectionTestUtils.setField(gateway,"client",client);
        when(client.sendSmsWithOptions(any(),any())).thenReturn(new SendSmsResponse().setBody(new SendSmsResponseBody().setCode("OK")));
    }
    String send() throws Exception {
        gateway.send("13900000001","Viridac_LOGIN","challenge123");
        var request=ArgumentCaptor.forClass(SendSmsRequest.class);var runtime=ArgumentCaptor.forClass(com.aliyun.teautil.models.RuntimeOptions.class);
        verify(client).sendSmsWithOptions(request.capture(),runtime.capture());
        assertFalse(runtime.getValue().getAutoretry());
        String param=request.getValue().getTemplateParam();assertTrue(param.matches("\\{\"code\":\"[0-9]{6}\"\\}"));return param.substring(9,15);
    }
    @Test void codeIsNotStoredPlaintextAndConsumedOnce() throws Exception {
        String code=send();assertNotEquals(code,jdbc.queryForObject("SELECT code_digest FROM v_sms_local_code",String.class));
        String wrong=code.equals("000000")?"111111":"000000";
        assertFalse(gateway.verify("13900000001","Viridac_LOGIN","challenge123",wrong));
        assertTrue(gateway.verify("13900000001","Viridac_LOGIN","challenge123",code));
        assertFalse(gateway.verify("13900000001","Viridac_LOGIN","challenge123",code));
    }
    @Test void phonePurposeAndExpiryAreEnforced() throws Exception {
        String code=send();assertFalse(gateway.verify("13900000002","Viridac_LOGIN","challenge123",code));
        assertFalse(gateway.verify("13900000001","Viridac_RESET","challenge123",code));
        jdbc.update("UPDATE v_sms_local_code SET expires_at=TIMESTAMP '2000-01-01 00:00:00'");
        assertFalse(gateway.verify("13900000001","Viridac_LOGIN","challenge123",code));
    }
    @Test void failureRemovesCodeAndSuppressesProviderDetails() throws Exception {
        when(client.sendSmsWithOptions(any(),any())).thenThrow(new RuntimeException("test-secret phone sensitive"));
        var e=assertThrows(BizException.class,()->gateway.send("13900000001","Viridac_LOGIN","challenge123"));
        assertFalse(e.getMessage().contains("test-secret"));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM v_sms_local_code",Integer.class));
    }
    @Test void providerRejectionNeverLeavesUsableCode() throws Exception {
        when(client.sendSmsWithOptions(any(),any())).thenReturn(new SendSmsResponse().setBody(new SendSmsResponseBody().setCode("isv.BUSINESS_LIMIT_CONTROL")));
        assertThrows(BizException.class,()->gateway.send("13900000001","Viridac_LOGIN","challenge123"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM v_sms_local_code",Integer.class));
    }
    @Test void missingSecretDisablesOrdinarySms() { assertTrue(gateway.available());config.setCodeSecret("");assertFalse(gateway.available()); }
}
