package com.heng;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.*;
import com.aliyun.teautil.models.RuntimeOptions;
import com.heng.auth.AliyunSmsGateway;
import com.heng.auth.SmsProperties;
import com.heng.config.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AliyunSmsGatewayTests {
    Client client;
    AliyunSmsGateway gateway;
    SmsProperties config;
    @BeforeEach void prepare() {
        client = mock(Client.class);
        config = new SmsProperties();
        config.setEnabled(true); config.setAccessKeyId("test-key"); config.setAccessKeySecret("test-secret");
        config.setSignName("test-sign"); config.setTemplateCode("test-template");
        gateway = new AliyunSmsGateway(config);
        ReflectionTestUtils.setField(gateway, "client", client);
    }
    @Test void sendingUsesCloudGeneratedSixDigitCodeWithoutReturningItOrRetrying() throws Exception {
        when(client.sendSmsVerifyCodeWithOptions(any(), any())).thenReturn(new SendSmsVerifyCodeResponse()
                .setBody(new SendSmsVerifyCodeResponseBody().setCode("OK").setSuccess(true)));
        gateway.send("13900000001", "Viridac_REGISTER", "request123");
        var request = ArgumentCaptor.forClass(SendSmsVerifyCodeRequest.class);
        var runtime = ArgumentCaptor.forClass(RuntimeOptions.class);
        verify(client).sendSmsVerifyCodeWithOptions(request.capture(), runtime.capture());
        assertFalse(request.getValue().getReturnVerifyCode());
        assertEquals(6L, request.getValue().getCodeLength());
        assertTrue(request.getValue().getTemplateParam().contains("##code##"));
        assertEquals(0L, request.getValue().getAutoRetry());
        assertFalse(runtime.getValue().getAutoretry());
    }
    @Test void successfulApiCallWithUnknownVerificationDoesNotAuthenticate() throws Exception {
        var body = new CheckSmsVerifyCodeResponseBody().setCode("OK").setSuccess(true)
                .setModel(new CheckSmsVerifyCodeResponseBody.CheckSmsVerifyCodeResponseBodyModel().setVerifyResult("UNKNOWN"));
        when(client.checkSmsVerifyCodeWithOptions(any(), any())).thenReturn(new CheckSmsVerifyCodeResponse().setBody(body));
        assertFalse(gateway.verify("13900000001", "Viridac_LOGIN", "request123", "654321"));
        body.getModel().setVerifyResult("PASS");
        assertTrue(gateway.verify("13900000001", "Viridac_LOGIN", "request123", "654321"));
        var request = ArgumentCaptor.forClass(CheckSmsVerifyCodeRequest.class);
        verify(client, times(2)).checkSmsVerifyCodeWithOptions(request.capture(), any());
        assertEquals("Viridac_LOGIN", request.getValue().getSchemeName());
        assertEquals("request123", request.getValue().getOutId());
    }
    @Test void missingVerificationModelAndFailedApiCallNeverPass() throws Exception {
        var body = new CheckSmsVerifyCodeResponseBody().setCode("OK").setSuccess(true);
        when(client.checkSmsVerifyCodeWithOptions(any(), any())).thenReturn(new CheckSmsVerifyCodeResponse().setBody(body));
        assertFalse(gateway.verify("13900000001", "Viridac_LOGIN", "request123", "654321"));
        body.setSuccess(false);
        assertThrows(BizException.class, () -> gateway.verify("13900000001", "Viridac_LOGIN", "request123", "654321"));
    }
    @Test void sdkFailureIsSanitizedAndMissingConfigurationDisablesService() throws Exception {
        when(client.sendSmsVerifyCodeWithOptions(any(), any())).thenThrow(new RuntimeException("test-secret internal detail"));
        var error = assertThrows(BizException.class, () -> gateway.send("13900000001", "Viridac_LOGIN", "request123"));
        assertFalse(error.getMessage().contains("test-secret"));
        assertTrue(gateway.available()); config.setTemplateCode(""); assertFalse(gateway.available());
    }
}
