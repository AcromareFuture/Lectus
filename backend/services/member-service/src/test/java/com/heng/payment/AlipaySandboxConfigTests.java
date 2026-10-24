package com.heng.payment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;
class AlipaySandboxConfigTests {
 @TempDir Path directory;
 @Test void officialSandboxCannotAdvertiseProductionOrWechat() throws Exception {
  var p=new ProviderProperties();p.setPublicBaseUrl("https://viridac.ckdxypt.top");p.getAlipay().setSandboxEnabled(true);p.getAlipay().setAppId("test");p.getAlipay().setSellerId("seller");
  Path key=directory.resolve("key");Files.writeString(key,"test-key");p.getAlipay().setPrivateKeyPath(key.toString());p.getAlipay().setPublicKeyPath(key.toString());
  var g=new ProviderGateway(p);assertTrue(g.ready("ALIPAY"));assertFalse(g.ready("WECHAT"));assertEquals("ALIPAY_SANDBOX",g.environment("ALIPAY"));assertEquals("https://openapi-sandbox.dl.alipaydev.com/gateway.do",g.alipayGateway());
  p.setProductionEnabled(true);assertFalse(g.ready("ALIPAY"));p.getAlipay().setSandboxEnabled(false);assertTrue(g.ready("ALIPAY"));assertEquals("PRODUCTION",g.environment("ALIPAY"));assertEquals("https://openapi.alipay.com/gateway.do",g.alipayGateway());
 }
}
