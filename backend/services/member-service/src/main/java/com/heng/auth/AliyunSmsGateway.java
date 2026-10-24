package com.heng.auth;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.heng.config.BizException;
import org.springframework.stereotype.Component;

/** PNVS 短信认证：验证码由阿里云生成及核验，始终不返回验证码。 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="viridac.sms",name="provider",havingValue="pnvs",matchIfMissing=true)
public class AliyunSmsGateway implements SmsGateway {
    private final SmsProperties properties;
    private volatile Client client;

    public AliyunSmsGateway(SmsProperties properties) { this.properties = properties; }

    @Override public boolean available() {
        return properties.isEnabled() && present(properties.getAccessKeyId())
                && present(properties.getAccessKeySecret()) && present(properties.getSignName())
                && present(properties.getTemplateCode()) && present(properties.getSchemePrefix())
                && properties.getSchemePrefix().length() <= 11;
    }

    private boolean present(String value) { return value != null && !value.isBlank(); }

    private Client client() throws Exception {
        if (!available()) throw new BizException("短信登录暂未开通，请使用账号密码登录");
        if (client == null) {
            synchronized (this) {
                if (client == null) client = new Client(new Config()
                        .setAccessKeyId(properties.getAccessKeyId())
                        .setAccessKeySecret(properties.getAccessKeySecret())
                        .setEndpoint("dypnsapi.aliyuncs.com"));
            }
        }
        return client;
    }

    private RuntimeOptions runtime() {
        return new RuntimeOptions().setConnectTimeout(3000).setReadTimeout(5000).setAutoretry(false);
    }

    @Override public void send(String phone, String scheme, String outId) {
        try {
            var body = client().sendSmsVerifyCodeWithOptions(new SendSmsVerifyCodeRequest()
                    .setPhoneNumber(phone).setCountryCode("86").setSchemeName(scheme)
                    .setSignName(properties.getSignName()).setTemplateCode(properties.getTemplateCode())
                    .setTemplateParam("{\"code\":\"##code##\",\"min\":\"5\"}")
                    .setOutId(outId).setCodeType(1L).setCodeLength(6L).setValidTime(300L)
                    .setInterval(60L).setDuplicatePolicy(1L).setReturnVerifyCode(false)
                    .setAutoRetry(0L), runtime()).getBody();
            if (body == null || !Boolean.TRUE.equals(body.getSuccess()) || !"OK".equals(body.getCode())) {
                throw new BizException("短信发送失败，请稍后重试");
            }
        } catch (Exception ignored) {
            // 不向日志/浏览器传递 SDK 异常中的请求、密钥、验证码或手机号。
            throw new BizException("短信暂时无法发送，请稍后重试；也可使用账号密码登录");
        }
    }

    @Override public boolean verify(String phone, String scheme, String outId, String code) {
        try {
            var body = client().checkSmsVerifyCodeWithOptions(new CheckSmsVerifyCodeRequest()
                    .setPhoneNumber(phone).setCountryCode("86").setSchemeName(scheme)
                    .setOutId(outId).setVerifyCode(code), runtime()).getBody();
            if (body == null || !Boolean.TRUE.equals(body.getSuccess()) || !"OK".equals(body.getCode())) {
                throw new BizException("短信核验暂不可用，请稍后重试");
            }
            return body.getModel() != null && "PASS".equals(body.getModel().getVerifyResult());
        } catch (Exception ignored) {
            throw new BizException("短信核验暂不可用，请稍后重试");
        }
    }
}
