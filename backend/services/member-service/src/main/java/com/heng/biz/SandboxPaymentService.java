package com.heng.biz;

import com.heng.config.BizException;
import com.heng.config.TradeProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.List;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Explicit simulated completion for an isolated test deployment. No provider payment is made. */
@Service
public class SandboxPaymentService {
 private final JdbcTemplate db; private final PaymentService payments; private final TradeProperties trade; private final boolean enabled;
 public SandboxPaymentService(JdbcTemplate db,PaymentService payments,TradeProperties trade,@Value("${viridac.sandbox.confirm-enabled:false}") boolean enabled){this.db=db;this.payments=payments;this.trade=trade;this.enabled=enabled;}
 @Transactional(rollbackFor=Exception.class)
 public Map<String,Object> confirm(long member,String orderNo,String channel){
  if(!enabled||!trade.isDemoMode()||!trade.isSandboxPaymentEnabled())throw new BizException("测试支付仅在独立沙盒环境开放");
  if(orderNo==null||orderNo.length()>64||!List.of("WECHAT","ALIPAY").contains(channel==null?"":channel))throw new BizException("测试支付参数不合法");
  var orders=db.queryForList("SELECT order_id,demo_mode FROM v_order WHERE order_no=? AND member_id=? FOR UPDATE",orderNo,member);
  if(orders.isEmpty())throw new BizException("订单不存在");
  Object demo=orders.get(0).get("demo_mode");if(!Boolean.TRUE.equals(demo)&&!(demo instanceof Number&&((Number)demo).intValue()==1))throw new BizException("测试支付不能处理正式订单");
  var rows=db.queryForList("SELECT payment_id,out_trade_no,amount_fen,environment FROM v_payment_record WHERE order_id=? AND pay_type=? FOR UPDATE",orders.get(0).get("order_id"),channel);
  if(rows.size()!=1)throw new BizException("请先选择测试付款方式");var row=rows.get(0);
  if(!List.of("DEMO","SANDBOX").contains(row.get("environment")))throw new BizException("测试支付不能处理生产流水");
  String transaction="SIMULATED-"+row.get("payment_id"),tradeNo=row.get("out_trade_no").toString();long amount=((Number)row.get("amount_fen")).longValue();
  String secret=trade.getPaymentCallbackSecret();if(secret==null||secret.isBlank())throw new BizException("测试支付配置缺失");
  try{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
   String signature=HexFormat.of().formatHex(mac.doFinal((channel+"|"+tradeNo+"|"+transaction+"|"+amount+"|true").getBytes(StandardCharsets.UTF_8)));
   payments.callback(channel,tradeNo,transaction,true,signature,Map.of("amountFen",amount,"success",true,"simulated",true));
  }catch(BizException e){throw e;}catch(Exception e){throw new BizException("测试支付暂不可用");}
  return Map.of("orderNo",orderNo,"status","SUCCESS","simulated",true,"chargedRealMoney",false);
 }
}
