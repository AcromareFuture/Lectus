package com.heng.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "viridac.sms")
@Getter @Setter
public class SmsProperties {
    private boolean enabled = false;
    private String provider = "pnvs";
    private String codeSecret = "";
    private String accessKeyId = "";
    private String accessKeySecret = "";
    private String signName = "";
    private String templateCode = "";
    // 不同用途使用不同云端方案，避免登录码与注册码互相替代。
    private String schemePrefix = "Viridac";
    private int phoneHourlyLimit = 5;
    private int phoneDailyLimit = 10;
    private int ipHourlyLimit = 20;
    private int dailyLimit = 200;
}
