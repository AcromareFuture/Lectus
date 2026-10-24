package com.heng.biz;
import com.heng.config.BizException;
import com.heng.domain.*;
import com.heng.mapper.AtomicWriteMapper;
import com.heng.payment.ProviderGateway;
import com.heng.service.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class AftersalesService {
 @org.springframework.beans.factory.annotation.Value("${viridac.aftersales.worker-enabled:true}") private boolean workerEnabled;
 private final JdbcTemplate jdbc;private final AtomicWriteMapper atomic;private final OrderService orders;private final MemberAssetService assets;private final ProviderGateway gateway;private final TransactionTemplate tx;
 public AftersalesService(JdbcTemplate j,AtomicWriteMapper a,OrderService o,MemberAssetService m,ProviderGateway g,org.springframework.transaction.PlatformTransactionManager tm){jdbc=j;atomic=a;orders=o;assets=m;gateway=g;tx=new TransactionTemplate(tm);}
 private VOrder owned(Long member,Long id){VOrder o=atomic.lockOrder(id);if(o==null||!member.equals(o.getMemberId()))throw new BizException("订单不存在");return o;}
 public Map<String,Object> detail(Long member,Long id){orders.requireOrder(member,id);var map=new LinkedHashMap<String,Object>();var refunds=jdbc.queryForList("SELECT refund_id,refund_no,amount_fen,reason,refund_type,status,return_company,return_no,return_received,review_note,create_time FROM v_refund WHERE order_id=? AND member_id=?",id,member);map.put("refund",refunds.isEmpty()?null:refunds.get(0));var invoices=jdbc.queryForList("SELECT invoice_id,title,tax_no,email,invoice_type,status,invoice_no,file_url,create_time FROM v_invoice WHERE order_id=? AND member_id=?",id,member);map.put("invoice",invoices.isEmpty()?null:invoices.get(0));map.put("logistics",jdbc.queryForList("SELECT event_time,content,source FROM v_logistics_event WHERE order_id=? ORDER BY event_time DESC,event_id DESC LIMIT 100",id));return map;}
 @Transactional(rollbackFor=Exception.class)
 public Map<String,Object> request(Long member,Long id,String reason){VOrder o=owned(member,id);atomic.lockMember(member);if(reason==null||reason.isBlank()||reason.length()>500)throw new BizException("请填写 1–500 字的售后原因");if(!List.of("PAID","SHIPPED","COMPLETED").contains(o.getStatus()))throw new BizException("仅已支付订单可申请退款");if(o.getPayAmountFen()==null||o.getPayAmountFen()<=0)throw new BizException("此订单无可退资金");var rows=jdbc.queryForList("SELECT refund_id,status FROM v_refund WHERE order_id=? FOR UPDATE",id);if(!rows.isEmpty()){var row=rows.get(0);if(List.of("CANCELLED","REJECTED").contains(row.get("status"))){jdbc.update("UPDATE v_refund SET status='REQUESTED',reason=?,review_note=NULL,reviewer_id=NULL,return_company=NULL,return_no=NULL,return_received=0,original_status=?,refund_type=? WHERE refund_id=?",reason.trim(),o.getStatus(),"PAID".equals(o.getStatus())?"REFUND_ONLY":"RETURN_REFUND",row.get("refund_id"));return Map.of("status","REQUESTED");}return row;}String no="R"+o.getOrderNo();jdbc.update("INSERT INTO v_refund(refund_no,order_id,member_id,amount_fen,reason,refund_type,status,original_status) VALUES(?,?,?,?,?,?,'REQUESTED',?)",no,id,member,o.getPayAmountFen(),reason.trim(),"PAID".equals(o.getStatus())?"REFUND_ONLY":"RETURN_REFUND",o.getStatus());return Map.of("refundNo",no,"status","REQUESTED");}
 @Transactional(rollbackFor=Exception.class)
 public void returnShipment(Long member,Long id,String company,String no){owned(member,id);if(company==null||company.isBlank()||company.length()>40||no==null||no.isBlank()||no.length()>100)throw new BizException("退货物流信息不合法");if(jdbc.update("UPDATE v_refund SET return_company=?,return_no=? WHERE order_id=? AND member_id=? AND status='AWAITING_RETURN'",company.trim(),no.trim(),id,member)!=1)throw new BizException("当前售后状态不可提交退货物流");}
 @Transactional(rollbackFor=Exception.class)
 public void cancel(Long member,Long id){owned(member,id);if(jdbc.update("UPDATE v_refund SET status='CANCELLED' WHERE order_id=? AND member_id=? AND status IN ('REQUESTED','AWAITING_RETURN')",id,member)!=1)throw new BizException("当前退款已处理，无法取消");}
 @Transactional(rollbackFor=Exception.class)
 public void invoice(Long member,Long id,String title,String taxNo,String email,String type){VOrder o=owned(member,id);if(!List.of("PAID","SHIPPED","COMPLETED").contains(o.getStatus()))throw new BizException("仅已支付订单可申请发票");if(title==null||title.isBlank()||title.length()>200||email==null||email.length()>100||!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||!List.of("PERSONAL","COMPANY").contains(type)||("COMPANY".equals(type)&&(taxNo==null||!taxNo.matches("[A-Za-z0-9]{15,20}"))))throw new BizException("发票抬头、税号或邮箱不合法");if(activeRefund(id))throw new BizException("售后处理期间不能申请发票");if(jdbc.queryForObject("SELECT COUNT(*) FROM v_invoice WHERE order_id=?",Long.class,id)>0)throw new BizException("已提交发票申请，请查看处理状态");jdbc.update("INSERT INTO v_invoice(order_id,member_id,title,tax_no,email,invoice_type,status) VALUES(?,?,?,?,?,?,'REQUESTED')",id,member,title.trim(),"COMPANY".equals(type)?taxNo.toUpperCase(Locale.ROOT):null,email.trim(),type);}
 public boolean activeRefund(Long orderId){return jdbc.queryForObject("SELECT COUNT(*) FROM v_refund WHERE order_id=? AND status IN ('REQUESTED','AWAITING_RETURN','APPROVED','PROCESSING')",Long.class,orderId)>0;}
 @Scheduled(fixedDelayString="${viridac.aftersales.worker-delay-ms:30000}")
 public void refunds(){if(!workerEnabled)return;for(var r:jdbc.queryForList("SELECT refund_id FROM v_refund WHERE status IN ('APPROVED','PROCESSING') ORDER BY refund_id LIMIT 20")){try{process(((Number)r.get("refund_id")).longValue());}catch(Exception e){jdbc.update("UPDATE v_refund SET last_error='渠道或补偿未确认，系统重试中' WHERE refund_id=?",r.get("refund_id"));}}}
 public void process(Long id){
  Map<String,Object> job=tx.execute(s->{var hints=jdbc.queryForList("SELECT order_id FROM v_refund WHERE refund_id=?",id);if(hints.isEmpty())return null;long orderId=num(hints.get(0),"order_id");VOrder o=atomic.lockOrder(orderId);atomic.lockMember(o.getMemberId());var r=jdbc.queryForMap("SELECT * FROM v_refund WHERE refund_id=? FOR UPDATE",id);if(!List.of("APPROVED","PROCESSING").contains(r.get("status")))return null;if(!List.of("PAID","SHIPPED","COMPLETED").contains(o.getStatus())||num(r,"amount_fen")!=o.getPayAmountFen())throw new BizException("退款金额或订单状态异常");
   var p=jdbc.queryForList("SELECT * FROM v_payment_record WHERE order_id=? AND status='SUCCESS'",orderId);if(p.size()!=1)throw new BizException("需核对唯一成功支付流水");r.put("payment",p.get(0));jdbc.update("UPDATE v_refund SET status='PROCESSING',attempts=attempts+1 WHERE refund_id=?",id);return r;});
  if(job==null)return;
  @SuppressWarnings("unchecked") Map<String,Object> payment=(Map<String,Object>)job.get("payment");String channel=String.valueOf(payment.get("pay_type"));String environment=String.valueOf(payment.get("environment"));String provider="";
  if(!"BALANCE".equals(channel)){
   if(List.of("SANDBOX","DEMO").contains(environment)){provider="SIMULATED:"+job.get("refund_no");}
   else {if(!List.of("PRODUCTION","ALIPAY_SANDBOX").contains(environment)||!environment.equals(gateway.environment(channel)))throw new BizException("未知资金来源需人工核对");
   ProviderGateway.RefundResult result;
   try{result=gateway.queryRefund(channel,String.valueOf(payment.get("out_trade_no")),String.valueOf(job.get("refund_no")));}catch(BizException e){result=gateway.refund(channel,String.valueOf(payment.get("out_trade_no")),String.valueOf(job.get("refund_no")),num(job,"amount_fen"));}
   if(!"SUCCESS".equals(result.status()))return;provider=result.providerId();
   }
  }
  final String providerId=provider;
  tx.executeWithoutResult(s->{VOrder order=atomic.lockOrder(num(job,"order_id"));atomic.lockMember(order.getMemberId());var current=jdbc.queryForMap("SELECT * FROM v_refund WHERE refund_id=? FOR UPDATE",id);if("SUCCESS".equals(current.get("status")))return;if(!"PROCESSING".equals(current.get("status")))throw new BizException("退款状态已变更");
   if("BALANCE".equals(channel))assets.refundBalance(order.getMemberId(),order.getPayAmountFen(),order.getOrderId(),"REFUND:"+order.getOrderNo(),String.valueOf(order.getFundingSource()));
   int earned=Math.toIntExact(order.getPayAmountFen()/100);assets.reverseReward(order.getMemberId(),earned,order.getOrderId(),"REFUND_REWARD:"+order.getOrderNo());
   if(order.getPointsUsed()!=null&&order.getPointsUsed()>0)assets.refundPoints(order.getMemberId(),order.getPointsUsed(),order.getOrderId(),"REFUND_POINTS:"+order.getOrderNo());
   boolean restock="PAID".equals(current.get("original_status"))||num(current,"return_received")==1;
   if(restock)for(VOrderItem item:orders.items(order.getOrderId())){Long sku=orders.snapshotLong(item,"skuId"),spu=orders.snapshotLong(item,"spuId");var stock=atomic.readStockForUpdate(sku);int q=item.getQuantity();jdbc.update("UPDATE v_stock SET available_stock=available_stock+?,version=version+1 WHERE sku_id=?",q,sku);jdbc.update("UPDATE v_goods_sku SET stock=stock+?,sales_count=GREATEST(sales_count-?,0) WHERE sku_id=?",q,q,sku);jdbc.update("UPDATE v_goods_spu SET total_stock=total_stock+?,sales_count=GREATEST(sales_count-?,0) WHERE spu_id=?",q,q,spu);jdbc.update("INSERT INTO v_stock_log(sku_id,change_type,quantity,before_available,after_available,biz_type,biz_id) VALUES(?,'IN',?,?,?,'REFUND',?)",sku,q,stock.getAvailableStock(),stock.getAvailableStock()+q,id);}
   if(order.getCouponReceiveId()!=null)jdbc.update("UPDATE v_member_coupon SET status=CASE WHEN expire_time>NOW() THEN 'AVAILABLE' ELSE 'EXPIRED' END,locked_order_id=NULL,used_order_id=NULL,used_time=NULL WHERE receive_id=? AND used_order_id=? AND status='USED'",order.getCouponReceiveId(),order.getOrderId());
   jdbc.update("UPDATE v_order SET status='REFUNDED' WHERE order_id=?",order.getOrderId());jdbc.update("UPDATE v_payment_record SET status='REFUNDED' WHERE payment_id=?",payment.get("payment_id"));jdbc.update("UPDATE v_settlement_record SET status='REFUNDED' WHERE order_id=?",order.getOrderId());jdbc.update("UPDATE v_invoice SET status='VOID_REQUIRED' WHERE order_id=? AND status='ISSUED'",order.getOrderId());jdbc.update("UPDATE v_invoice SET status='CANCELLED' WHERE order_id=? AND status='REQUESTED'",order.getOrderId());jdbc.update("UPDATE v_refund SET status='SUCCESS',provider_id=?,last_error=NULL WHERE refund_id=?",providerId,id);jdbc.update("INSERT INTO v_push_record(member_id,push_type,title,content,biz_id) VALUES(?,'ORDER','退款完成','资金及积分补偿已处理，请查看流水',?)",order.getMemberId(),order.getOrderId());
  });
 }
 private long num(Map<String,Object> m,String key){return m.get(key)==null?0:((Number)m.get(key)).longValue();}
}
