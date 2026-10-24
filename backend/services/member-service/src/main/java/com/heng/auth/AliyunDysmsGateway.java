package com.heng.auth;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.heng.config.BizException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

/** Ordinary SMS templates: six-digit codes remain server-side and are consumed once. */
@Component
@ConditionalOnProperty(prefix="viridac.sms",name="provider",havingValue="dysms")
public class AliyunDysmsGateway implements SmsGateway {
    private final SmsProperties properties;
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();
    private volatile Client client;
    public AliyunDysmsGateway(SmsProperties properties,JdbcTemplate jdbc) { this.properties=properties;this.jdbc=jdbc; }
    private boolean present(String s) { return s!=null && !s.isBlank(); }
    @Override public boolean available() {
        return properties.isEnabled() && present(properties.getAccessKeyId()) && present(properties.getAccessKeySecret())
            && present(properties.getSignName()) && present(properties.getTemplateCode())
            && present(properties.getCodeSecret()) && properties.getCodeSecret().length()>=32
            && present(properties.getSchemePrefix()) && properties.getSchemePrefix().length()<=11;
    }
    private Client client() throws Exception {
        if(!available()) throw new BizException("短信登录暂未开通，请使用账号密码登录");
        if(client==null) synchronized(this) {
            if(client==null) client=new Client(new Config().setAccessKeyId(properties.getAccessKeyId())
                .setAccessKeySecret(properties.getAccessKeySecret()).setEndpoint("dysmsapi.aliyuncs.com"));
        }
        return client;
    }
    private String digest(String phone,String scheme,String id,String code) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getCodeSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((phone+"|"+scheme+"|"+id+"|"+code).getBytes(StandardCharsets.UTF_8)));
        } catch(Exception ignored) { throw new BizException("短信核验暂不可用，请稍后重试"); }
    }
    @Override public void send(String phone,String scheme,String outId) {
        if(!available()) throw new BizException("短信登录暂未开通，请使用账号密码登录");
        String code=String.format(Locale.ROOT,"%06d",random.nextInt(1000000));
        jdbc.update("INSERT INTO v_sms_local_code(challenge_id,phone,scheme,code_digest,expires_at) VALUES(?,?,?,?,?)",
            outId,phone,scheme,digest(phone,scheme,outId,code),Timestamp.from(Instant.now().plusSeconds(300)));
        try {
            var body=client().sendSmsWithOptions(new SendSmsRequest().setPhoneNumbers(phone)
                .setSignName(properties.getSignName()).setTemplateCode(properties.getTemplateCode())
                .setTemplateParam("{\"code\":\""+code+"\"}").setOutId(outId),
                new RuntimeOptions().setConnectTimeout(3000).setReadTimeout(5000).setAutoretry(false)).getBody();
            if(body==null || !"OK".equals(body.getCode())) throw new BizException("短信发送失败");
        } catch(Exception ignored) {
            jdbc.update("DELETE FROM v_sms_local_code WHERE challenge_id=?",outId);
            throw new BizException("短信暂时无法发送，请稍后重试；也可使用账号密码登录");
        }
    }
    @Override public boolean verify(String phone,String scheme,String outId,String code) {
        if(!available()) throw new BizException("短信核验暂不可用，请稍后重试");
        if(code==null || !code.matches("[0-9]{6}")) return false;
        var rows=jdbc.queryForList("SELECT code_digest FROM v_sms_local_code WHERE challenge_id=? AND phone=? AND scheme=? AND expires_at>?",outId,phone,scheme,Timestamp.from(Instant.now()));
        if(rows.size()!=1) return false;
        String expected=String.valueOf(rows.get(0).get("code_digest"));
        if(!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),digest(phone,scheme,outId,code).getBytes(StandardCharsets.US_ASCII))) return false;
        return jdbc.update("DELETE FROM v_sms_local_code WHERE challenge_id=? AND code_digest=?",outId,expected)==1;
    }
    @Scheduled(fixedDelay=3600000,initialDelay=120000)
    public void cleanup() { jdbc.update("DELETE FROM v_sms_local_code WHERE expires_at<?",Timestamp.from(Instant.now())); }
}
