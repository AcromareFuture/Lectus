package com.heng.mall;

import com.heng.config.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** Account-owned cart. Checkout reserves all selected lines in one local transaction. */
@Service
public class MallCartService {
 private final JdbcTemplate db;
 private final MallService mall;
 private final WorkspaceAuth auth;
 public MallCartService(JdbcTemplate db,MallService mall,WorkspaceAuth auth){this.db=db;this.mall=mall;this.auth=auth;}
 public record CartItem(String productId,Integer quantity){}
 public record Line(String productId,Integer quantity,BigDecimal expectedPrice){}
 public record Checkout(String requestKey,List<Line> items,String recipient,String phone,String address){}
 public record Payment(String method,BigDecimal expectedAmount){}
 private static String id(String prefix){return prefix+UUID.randomUUID().toString().replace("-","");}
 private void lockMember(long member){db.queryForObject("SELECT member_id FROM v_member WHERE member_id=? FOR UPDATE",Long.class,member);}
 private static void quantity(Integer value){if(value==null||value<1||value>99)throw new BizException("每件商品可购买 1 至 99 件");}
 private static long fen(BigDecimal value){try{if(value==null||value.signum()<0)throw new ArithmeticException();return value.movePointRight(2).longValueExact();}catch(ArithmeticException e){throw new BizException("金额无效，请刷新后重新核对");}}
 @Transactional(readOnly=true) public List<Map<String,Object>> cart(long member){
  return db.query("SELECT c.quantity,p.*,m.name merchant_name,m.status merchant_status FROM lc_cart c JOIN lc_product p ON p.id=c.product_id JOIN lc_merchant m ON m.id=p.merchant_id WHERE c.member_id=? ORDER BY c.updated_at DESC,p.id",(r,n)->{
   int stock=r.getInt("stock"),count=r.getInt("quantity");
   String issue=!"营业中".equals(r.getString("merchant_status"))?"店铺暂停营业":!"销售中".equals(r.getString("status"))?"商品已下架":stock==0?"商品暂时缺货":stock<count?"库存仅剩 "+stock+" 件":"";
   var item=new LinkedHashMap<String,Object>();
   item.put("id",r.getString("id"));item.put("merchantId",r.getString("merchant_id"));item.put("merchantName",r.getString("merchant_name"));
   item.put("name",r.getString("name"));item.put("spec",r.getString("spec"));item.put("image",r.getString("image"));
   item.put("price",BigDecimal.valueOf(r.getLong("price_fen"),2));item.put("stock",stock);item.put("quantity",count);item.put("issue",issue);item.put("available",issue.isEmpty());return item;
  },member);
 }
 @Transactional public List<Map<String,Object>> put(long member,String product,Integer count,boolean add){
  quantity(count);lockMember(member);
  var rows=db.queryForList("SELECT p.*,m.status merchant_status FROM lc_product p JOIN lc_merchant m ON m.id=p.merchant_id WHERE p.id=?",product);
  if(rows.isEmpty())throw new BizException("商品不存在");var p=rows.getFirst();
  if(!"销售中".equals(p.get("status"))||!"营业中".equals(p.get("merchant_status")))throw new BizException("商品当前不可购买");
  var old=db.queryForList("SELECT quantity FROM lc_cart WHERE member_id=? AND product_id=?",member,product);
  int next=count+(add&&!old.isEmpty()?((Number)old.getFirst().get("quantity")).intValue():0);quantity(next);
  if(((Number)p.get("stock")).intValue()<next)throw new BizException("商品库存不足，请调整数量");
  if(old.isEmpty()){
   if(db.queryForObject("SELECT COUNT(*) FROM lc_cart WHERE member_id=?",Integer.class,member)>=100)throw new BizException("购物车最多保存 100 种商品，请先结算或移除部分商品");
   db.update("INSERT INTO lc_cart(member_id,product_id,quantity,updated_at) VALUES(?,?,?,?)",member,product,next,Timestamp.from(Instant.now()));
  }else db.update("UPDATE lc_cart SET quantity=?,updated_at=? WHERE member_id=? AND product_id=?",next,Timestamp.from(Instant.now()),member,product);
  return cart(member);
 }
 @Transactional public List<Map<String,Object>> remove(long member,String product){lockMember(member);db.update("DELETE FROM lc_cart WHERE member_id=? AND product_id=?",member,product);return cart(member);}
 @Transactional public Map<String,Object> checkout(long member,Checkout f){
  if(f==null||f.requestKey()==null||!f.requestKey().matches("[a-f0-9]{32}")||f.items()==null||f.items().isEmpty()||f.items().size()>100)throw new BizException("请选择需要结算的商品");
  if(f.recipient()==null||f.recipient().isBlank()||f.recipient().length()>40||f.phone()==null||!f.phone().matches("1[3-9][0-9]{9}")||f.address()==null||f.address().isBlank()||f.address().length()>250)throw new BizException("请填写有效的收货信息");
  var unique=new HashSet<String>();
  for(var item:f.items()){if(item==null||item.productId()==null||!unique.add(item.productId()))throw new BizException("结算商品不能重复");quantity(item.quantity());fen(item.expectedPrice());}
  var sorted=f.items().stream().sorted(Comparator.comparing(Line::productId)).toList();
  String fingerprint=fingerprint(f,sorted);
  // Existing order operations acquire pending orders before member/product locks.
  mall.memberOrders(member);lockMember(member);
  var old=db.queryForList("SELECT id,fingerprint FROM lc_checkout WHERE member_id=? AND request_key=?",member,f.requestKey());
  if(!old.isEmpty()){
   if(!fingerprint.equals(old.getFirst().get("fingerprint")))throw new BizException("本次提交内容已变化，请重新确认订单");
   return summary(member,(String)old.getFirst().get("id"));
  }
  // Lock in a stable order; no line is committed if any other line fails validation.
  for(var item:sorted){
   var product=db.queryForList("SELECT * FROM lc_product WHERE id=? FOR UPDATE",item.productId());
   if(product.isEmpty())throw new BizException("商品不存在，请刷新购物车");var p=product.getFirst();
   var count=db.queryForList("SELECT quantity FROM lc_cart WHERE member_id=? AND product_id=?",member,item.productId());
   if(count.isEmpty()||((Number)count.getFirst().get("quantity")).intValue()!=item.quantity())throw new BizException("购物车数量已变化，请重新确认");
   if(((Number)p.get("price_fen")).longValue()!=fen(item.expectedPrice()))throw new BizException("商品价格已变化，请刷新购物车后重新确认");
   if(((Number)p.get("stock")).intValue()<item.quantity())throw new BizException("商品库存不足，请刷新购物车后调整数量");
  }
  String checkout=id("CK");
  db.update("INSERT INTO lc_checkout(id,member_id,request_key,fingerprint,created_at) VALUES(?,?,?,?,?)",checkout,member,f.requestKey(),fingerprint,Timestamp.from(Instant.now()));
  for(var item:sorted){
   var order=mall.create(member,new MallService.Create(item.productId(),item.quantity(),UUID.randomUUID().toString().replace("-",""),f.recipient().trim(),f.phone(),f.address().trim()));
   db.update("INSERT INTO lc_checkout_order(checkout_id,order_id) VALUES(?,?)",checkout,order.get("id"));
   db.update("DELETE FROM lc_cart WHERE member_id=? AND product_id=?",member,item.productId());
  }
  return summary(member,checkout);
 }
 private static String fingerprint(Checkout f,List<Line> sorted){
  // Length prefixes make field boundaries unambiguous, including user-entered delimiters.
  var fields=new ArrayList<String>(List.of(f.recipient().trim(),f.phone(),f.address().trim()));
  for(var line:sorted){fields.add(line.productId());fields.add(line.quantity().toString());fields.add(Long.toString(fen(line.expectedPrice())));}
  var content=new StringBuilder();for(String field:fields)content.append(field.length()).append(':').append(field);
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.toString().getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}
 }
 private void requireOwner(long member,String checkout){
  var owner=db.queryForList("SELECT member_id FROM lc_checkout WHERE id=?",checkout);
  if(owner.isEmpty()||((Number)owner.getFirst().get("member_id")).longValue()!=member)throw new BizException("结算单不存在");
 }
 @Transactional public Map<String,Object> summary(long member,String checkout){
  requireOwner(member,checkout);
  var all=mall.memberOrders(member);
  var ids=new HashSet<>(db.queryForList("SELECT order_id FROM lc_checkout_order WHERE checkout_id=?",String.class,checkout));
  var orders=all.stream().filter(o->ids.contains(o.get("id"))).toList();
  BigDecimal total=BigDecimal.ZERO,pending=BigDecimal.ZERO;int unpaid=0;boolean closed=false;
  for(var order:orders){total=total.add((BigDecimal)order.get("amount"));if("待付款".equals(order.get("status"))){pending=pending.add((BigDecimal)order.get("amount"));unpaid++;}else if(order.get("paidAt")==null)closed=true;}
  return Map.of("id",checkout,"orders",orders,"total",total,"pendingAmount",pending,"status",closed?"unavailable":unpaid==0?"paid":"pending","localSimulation",auth.local());
 }
 @Transactional public Map<String,Object> pay(long member,String checkout,Payment f){
  if(!auth.local())throw new BizException("仅本地联调允许模拟支付");
  if(f==null||!List.of("微信支付","支付宝").contains(Objects.toString(f.method(),"")))throw new BizException("请选择有效的支付方式");
  requireOwner(member,checkout);
  mall.memberOrders(member);
  var ids=db.queryForList("SELECT order_id FROM lc_checkout_order WHERE checkout_id=? ORDER BY order_id",String.class,checkout);
  for(String id:ids)db.queryForList("SELECT id FROM lc_order WHERE id=? FOR UPDATE",id);
  var current=summary(member,checkout);
  if(!"pending".equals(current.get("status")))return current;
  if(fen(f.expectedAmount())!=fen((BigDecimal)current.get("pendingAmount")))throw new BizException("待付金额已变化，请刷新收银台重新确认");
  for(String id:ids){
   String status=db.queryForObject("SELECT status FROM lc_order WHERE id=?",String.class,id);
   if("待付款".equals(status)){
    var paid=mall.memberAction(member,id,"pay",f.method());
    if(!"待发货".equals(paid.get("status")))throw new BizException("付款时限已过，请刷新订单重新确认");
   }
  }
  return summary(member,checkout);
 }
}
