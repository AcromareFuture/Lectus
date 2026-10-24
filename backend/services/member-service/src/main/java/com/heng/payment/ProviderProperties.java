package com.heng.payment;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
@Data @Component @ConfigurationProperties(prefix="viridac.providers")
public class ProviderProperties {
 private boolean productionEnabled=false;
 private String publicBaseUrl="";
 private Wechat wechat=new Wechat(); private Alipay alipay=new Alipay();
 @Data public static class Wechat {private String appId="",merchantId="",privateKeyPath="",merchantSerial="",apiV3Key="",platformPublicKeyPath="",platformPublicKeyId="";}
 @Data public static class Alipay {private String appId="",privateKeyPath="",publicKeyPath="",sellerId="",notifyPath="/api/pay/provider/alipay";private boolean sandboxEnabled=false;}
}
