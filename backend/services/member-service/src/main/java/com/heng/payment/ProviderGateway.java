package com.heng.payment;
import com.heng.config.BizException;
import org.springframework.stereotype.Service;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import com.alipay.api.*;
import com.alipay.api.request.*;
import com.alipay.api.domain.*;
import com.alipay.api.internal.util.AlipaySignature;
import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.*;
import com.wechat.pay.java.service.payments.model.Transaction;

/** Official SDKs sign requests and authenticate responses. No credentials are stored in business rows. */
@Service
public class ProviderGateway {
 private final ProviderProperties p;
 public ProviderGateway(ProviderProperties p){this.p=p;}
 public record VerifiedPayment(String channel,String outTradeNo,String transactionId,long amountFen,boolean paid){}
 public record RefundResult(String status,String providerId){}
 public String environment(String channel){return "ALIPAY".equals(channel)&&p.getAlipay().isSandboxEnabled()?"ALIPAY_SANDBOX":"PRODUCTION";}
 public String alipayGateway(){return p.getAlipay().isSandboxEnabled()?"https://openapi-sandbox.dl.alipaydev.com/gateway.do":"https://openapi.alipay.com/gateway.do";}
 public boolean ready(String channel){
  boolean officialSandbox="ALIPAY".equals(channel)&&p.getAlipay().isSandboxEnabled();
  if((officialSandbox?p.isProductionEnabled():!p.isProductionEnabled())||!p.getPublicBaseUrl().matches("https://[^\\s/]+"))return false;
  if(channel.equals("WECHAT")){var w=p.getWechat();return nonempty(w.getAppId(),w.getMerchantId(),w.getMerchantSerial(),w.getApiV3Key(),w.getPlatformPublicKeyId())&&w.getApiV3Key().getBytes(StandardCharsets.UTF_8).length==32&&file(w.getPrivateKeyPath())&&file(w.getPlatformPublicKeyPath());}
  if(channel.equals("ALIPAY")){var a=p.getAlipay();return nonempty(a.getAppId(),a.getSellerId())&&Set.of("/api/pay/provider/alipay","/alipay-sandbox-api/pay/provider/alipay").contains(a.getNotifyPath())&&file(a.getPrivateKeyPath())&&file(a.getPublicKeyPath());}return false;
 }
 private boolean nonempty(String...s){return Arrays.stream(s).allMatch(v->v!=null&&!v.isBlank());}
 private boolean file(String path){try{return path!=null&&!path.isBlank()&&Files.isRegularFile(Path.of(path))&&Files.isReadable(Path.of(path));}catch(Exception e){return false;}}
 private void require(String c){if(!ready(c))throw new BizException("真实支付渠道尚未配置完成");}
 private String pem(String path){try{return Files.readString(Path.of(path)).replaceAll("-----[^-]+-----","").replaceAll("\\s","");}catch(Exception e){throw new BizException("支付密钥不可读取");}}
 private RSAPublicKeyConfig wxConfig(){require("WECHAT");var w=p.getWechat();return new RSAPublicKeyConfig.Builder().merchantId(w.getMerchantId()).privateKeyFromPath(w.getPrivateKeyPath()).merchantSerialNumber(w.getMerchantSerial()).apiV3Key(w.getApiV3Key()).publicKeyFromPath(w.getPlatformPublicKeyPath()).publicKeyId(w.getPlatformPublicKeyId()).build();}
 private com.wechat.pay.java.core.http.HttpClient wxHttp(){return new com.wechat.pay.java.core.http.DefaultHttpClientBuilder().config(wxConfig()).connectTimeoutMs(5000).readTimeoutMs(10000).writeTimeoutMs(10000).disableRetryOnConnectionFailure().build();}
 private NativePayService wx(){return new NativePayService.Builder().httpClient(wxHttp()).build();}
 private AlipayClient ali(){require("ALIPAY");var a=p.getAlipay();var client=new DefaultAlipayClient(alipayGateway(),a.getAppId(),pem(a.getPrivateKeyPath()),"json","UTF-8",pem(a.getPublicKeyPath()),"RSA2");client.setConnectTimeout(5000);client.setReadTimeout(10000);return client;}
 private String base(){return p.getPublicBaseUrl().replaceAll("/+$","");}
 public Map<String,Object> prepay(String channel,String trade,long fen,Instant expires){
  require(channel);try{
   if(channel.equals("WECHAT")){PrepayRequest q=new PrepayRequest();q.setAppid(p.getWechat().getAppId());q.setMchid(p.getWechat().getMerchantId());q.setDescription("Viridac 订单");q.setOutTradeNo(trade);q.setNotifyUrl(base()+"/api/pay/provider/wechat");q.setTimeExpire(OffsetDateTime.ofInstant(expires,java.time.ZoneOffset.UTC).toString());com.wechat.pay.java.service.payments.nativepay.model.Amount a=new com.wechat.pay.java.service.payments.nativepay.model.Amount();a.setTotal(Math.toIntExact(fen));a.setCurrency("CNY");q.setAmount(a);return Map.of("codeUrl",wx().prepay(q).getCodeUrl(),"sandbox",false);}
   if(p.getAlipay().isSandboxEnabled()){
    var q=new AlipayTradePrecreateRequest();var m=new AlipayTradePrecreateModel();m.setOutTradeNo(trade);m.setTotalAmount(BigDecimal.valueOf(fen,2).toPlainString());m.setSubject("Viridac 沙盒测试订单");m.setSellerId(p.getAlipay().getSellerId());m.setTimeExpire(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(java.time.ZoneId.of("Asia/Shanghai")).format(expires));q.setBizModel(m);q.setNotifyUrl(base()+p.getAlipay().getNotifyPath());var result=ali().execute(q);
    if(!result.isSuccess()||!trade.equals(result.getOutTradeNo())||result.getQrCode()==null||!result.getQrCode().startsWith("https://qr.alipay.com/"))throw new IllegalStateException();
    return Map.of("codeUrl",result.getQrCode(),"sandbox",false,"providerSandbox",true);
   }
   AlipayTradeWapPayRequest q=new AlipayTradeWapPayRequest();AlipayTradeWapPayModel m=new AlipayTradeWapPayModel();m.setOutTradeNo(trade);m.setTotalAmount(BigDecimal.valueOf(fen,2).toPlainString());m.setSubject("Viridac 订单");m.setProductCode("QUICK_WAP_WAY");m.setSellerId(p.getAlipay().getSellerId());m.setTimeExpire(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(java.time.ZoneId.of("Asia/Shanghai")).format(expires));q.setBizModel(m);q.setNotifyUrl(base()+"/api/pay/provider/alipay");q.setReturnUrl(base()+"/#/orders");String url=ali().pageExecute(q,"GET").getBody();
   if(!url.startsWith("https://openapi.alipay.com/"))throw new IllegalStateException();return Map.of("redirectUrl",url,"sandbox",false,"providerSandbox",false);
  }catch(BizException e){throw e;}catch(Exception e){throw new BizException("渠道下单失败，请稍后重试");}
 }
 public VerifiedPayment query(String channel,String trade){require(channel);try{
  if(channel.equals("WECHAT")){var q=new QueryOrderByOutTradeNoRequest();q.setMchid(p.getWechat().getMerchantId());q.setOutTradeNo(trade);try{return verifiedWx(wx().queryOrderByOutTradeNo(q));}catch(com.wechat.pay.java.core.exception.ServiceException e){if("ORDER_NOT_EXIST".equals(e.getErrorCode()))return new VerifiedPayment("WECHAT",trade,null,0,false);throw e;}}
  var q=new AlipayTradeQueryRequest();var m=new AlipayTradeQueryModel();m.setOutTradeNo(trade);q.setBizModel(m);var r=ali().execute(q);if("ACQ.TRADE_NOT_EXIST".equals(r.getSubCode()))return new VerifiedPayment("ALIPAY",trade,null,0,false);if(!r.isSuccess())throw new IllegalStateException();return new VerifiedPayment("ALIPAY",r.getOutTradeNo(),r.getTradeNo(),fen(r.getTotalAmount()),List.of("TRADE_SUCCESS","TRADE_FINISHED").contains(r.getTradeStatus()));
 }catch(BizException e){throw e;}catch(Exception e){throw new BizException("渠道查单失败，请稍后刷新");}}
 private VerifiedPayment verifiedWx(Transaction t){if(!p.getWechat().getAppId().equals(t.getAppid())||!p.getWechat().getMerchantId().equals(t.getMchid())||t.getAmount()==null||!"CNY".equals(t.getAmount().getCurrency()))throw new BizException("渠道回执商户或币种不匹配");return new VerifiedPayment("WECHAT",t.getOutTradeNo(),t.getTransactionId(),t.getAmount().getTotal(),t.getTradeState()==Transaction.TradeStateEnum.SUCCESS);}
 public VerifiedPayment wechatNotification(String body,String serial,String timestamp,String nonce,String signature){
  try{if(Math.abs(Instant.now().getEpochSecond()-Long.parseLong(timestamp))>300)throw new IllegalStateException();var q=new RequestParam.Builder().serialNumber(serial).timestamp(timestamp).nonce(nonce).signature(signature).body(body).build();return verifiedWx(new NotificationParser(wxConfig()).parse(q,Transaction.class));}catch(Exception e){throw new BizException("微信通知验签失败");}
 }
 public VerifiedPayment alipayNotification(Map<String,String> fields){require("ALIPAY");try{var a=p.getAlipay();if(!"RSA2".equals(fields.get("sign_type"))||!AlipaySignature.rsaCheckV1(fields,pem(a.getPublicKeyPath()),"UTF-8","RSA2")||!a.getAppId().equals(fields.get("app_id"))||!a.getSellerId().equals(fields.get("seller_id")))throw new IllegalStateException();return new VerifiedPayment("ALIPAY",fields.get("out_trade_no"),fields.get("trade_no"),fen(fields.get("total_amount")),List.of("TRADE_SUCCESS","TRADE_FINISHED").contains(fields.get("trade_status")));}catch(Exception e){throw new BizException("支付宝通知验签失败");}}
 private long fen(String amount){return new BigDecimal(amount).movePointRight(2).longValueExact();}
 public void close(String channel,String trade){require(channel);try{if(channel.equals("WECHAT")){var q=new CloseOrderRequest();q.setMchid(p.getWechat().getMerchantId());q.setOutTradeNo(trade);try{wx().closeOrder(q);}catch(com.wechat.pay.java.core.exception.ServiceException e){if(!"ORDER_NOT_EXIST".equals(e.getErrorCode()))throw e;}}else{var q=new AlipayTradeCloseRequest();var m=new AlipayTradeCloseModel();m.setOutTradeNo(trade);q.setBizModel(m);var r=ali().execute(q);if(!r.isSuccess()&&!"ACQ.TRADE_NOT_EXIST".equals(r.getSubCode()))throw new IllegalStateException();}}catch(Exception e){throw new BizException("渠道关单未确认，请刷新支付状态后重试");}}
 public RefundResult refund(String channel,String trade,String refundNo,long amount){require(channel);try{
  if(channel.equals("WECHAT")){var service=new com.wechat.pay.java.service.refund.RefundService.Builder().httpClient(wxHttp()).build();var q=new com.wechat.pay.java.service.refund.model.CreateRequest();q.setOutTradeNo(trade);q.setOutRefundNo(refundNo);var a=new com.wechat.pay.java.service.refund.model.AmountReq();a.setRefund(amount);a.setTotal(amount);a.setCurrency("CNY");q.setAmount(a);var r=service.create(q);return new RefundResult(r.getStatus().name(),r.getRefundId());}
  var q=new AlipayTradeRefundRequest();var m=new AlipayTradeRefundModel();m.setOutTradeNo(trade);m.setOutRequestNo(refundNo);m.setRefundAmount(BigDecimal.valueOf(amount,2).toPlainString());q.setBizModel(m);var r=ali().execute(q);if(!r.isSuccess())throw new IllegalStateException();return new RefundResult("SUCCESS",r.getTradeNo());
 }catch(Exception e){throw new BizException("退款渠道暂未确认，系统将按同一退款编号重试");}}
 public RefundResult queryRefund(String channel,String trade,String refundNo){require(channel);try{
  if(channel.equals("WECHAT")){var q=new com.wechat.pay.java.service.refund.model.QueryByOutRefundNoRequest();q.setOutRefundNo(refundNo);var r=new com.wechat.pay.java.service.refund.RefundService.Builder().httpClient(wxHttp()).build().queryByOutRefundNo(q);return new RefundResult(r.getStatus().name(),r.getRefundId());}
  var q=new AlipayTradeFastpayRefundQueryRequest();var m=new AlipayTradeFastpayRefundQueryModel();m.setOutTradeNo(trade);m.setOutRequestNo(refundNo);q.setBizModel(m);var r=ali().execute(q);if(!r.isSuccess())throw new IllegalStateException();return new RefundResult("REFUND_SUCCESS".equals(r.getRefundStatus())?"SUCCESS":"PROCESSING",r.getTradeNo());
 }catch(Exception e){throw new BizException("渠道退款查询暂未确认");}}
 public String alipayBillUrl(String date){try{var q=new AlipayDataDataserviceBillDownloadurlQueryRequest();var m=new AlipayDataDataserviceBillDownloadurlQueryModel();m.setBillType("trade");m.setBillDate(date);q.setBizModel(m);var r=ali().execute(q);if(!r.isSuccess())throw new IllegalStateException();return r.getBillDownloadUrl();}catch(Exception e){throw new BizException("渠道账单暂未可用");}}
}
