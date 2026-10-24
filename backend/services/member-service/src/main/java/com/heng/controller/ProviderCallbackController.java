package com.heng.controller;
import com.heng.biz.PaymentService;
import com.heng.payment.ProviderGateway;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.Map;
@RestController @RequestMapping("/api/pay/provider")
public class ProviderCallbackController {
 private final PaymentService payments;private final ProviderGateway gateway;
 public ProviderCallbackController(PaymentService p,ProviderGateway g){payments=p;gateway=g;}
 @PostMapping("/wechat") public Map<String,String> wechat(@RequestBody String body,@RequestHeader("Wechatpay-Serial") String serial,@RequestHeader("Wechatpay-Timestamp") String timestamp,@RequestHeader("Wechatpay-Nonce") String nonce,@RequestHeader("Wechatpay-Signature") String signature){payments.verifiedCallback(gateway.wechatNotification(body,serial,timestamp,nonce,signature));return Map.of("code","SUCCESS","message","成功");}
 @PostMapping(value="/alipay",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE,produces=MediaType.TEXT_PLAIN_VALUE) public String alipay(@RequestParam Map<String,String> fields){payments.verifiedCallback(gateway.alipayNotification(fields));return "success";}
}
