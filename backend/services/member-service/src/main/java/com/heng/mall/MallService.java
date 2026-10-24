package com.heng.mall;

import com.heng.config.BizException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.math.BigDecimal;

/** Shared domestic marketplace workflow. Monetary values are integer fen in storage. */
@Service
public class MallService {
 private final JdbcTemplate db;
 private final WorkspaceAuth auth;
 public MallService(JdbcTemplate db,WorkspaceAuth auth){this.db=db;this.auth=auth;}
 private static String id(String prefix){return prefix+UUID.randomUUID().toString().replace("-","");}
 private static String time(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));}
 private static Timestamp now(){return Timestamp.from(Instant.now());}
 private void log(String actor,String action,String target,String detail){db.update("INSERT INTO lc_audit(created_at,actor,action,target,detail) VALUES(?,?,?,?,?)",now(),actor,action,target,detail);}
 @PostConstruct void seed(){
  if(!auth.local()||db.queryForObject("SELECT COUNT(*) FROM lc_product",Long.class)>0)return;
  seedProduct("P-1001","M-1001","ASICS GEL-KAYANO 14","复古跑鞋 · 银绿","鞋履","product-asics-480.webp",109000,128,"销售中");
  seedProduct("P-1002","M-1002","adidas SAMBA OG","经典低帮板鞋 · 黑白","鞋履","product-samba-480.webp",79900,86,"销售中");
  seedProduct("P-1003","M-1001","莱择 日常条纹衬衫","宽松棉质衬衫 · 雾蓝","服饰","product-shirt-blue-480.webp",36900,64,"待审核");
 }
 private void seedProduct(String id,String m,String n,String s,String c,String img,long p,int stock,String status){db.update("INSERT INTO lc_product(id,merchant_id,name,spec,category,image,price_fen,stock,status,submitted) VALUES(?,?,?,?,?,?,?,?,?,?)",id,m,n,s,c,img,p,stock,status,time());}
 private List<Map<String,Object>> products(String where,Object...args){return db.query("SELECT p.*,merchant.name AS merchant_name,COALESCE(s.sold,0) sales FROM lc_product p JOIN lc_merchant merchant ON merchant.id=p.merchant_id LEFT JOIN (SELECT product_id,SUM(quantity) sold FROM lc_order WHERE paid_at IS NOT NULL AND status<>'已退款' GROUP BY product_id) s ON s.product_id=p.id WHERE "+where+" ORDER BY submitted DESC,id",(r,i)->{
  var m=new LinkedHashMap<String,Object>();m.put("id",r.getString("id"));m.put("merchantId",r.getString("merchant_id"));m.put("merchantName",r.getString("merchant_name"));m.put("sourceProductId",r.getString("source_product_id"));m.put("name",r.getString("name"));m.put("spec",r.getString("spec"));m.put("subtitle",r.getString("spec"));m.put("category",r.getString("category"));m.put("image",r.getString("image"));m.put("price",BigDecimal.valueOf(r.getLong("price_fen"),2));m.put("stock",r.getInt("stock"));m.put("sales",r.getInt("sales"));m.put("status",r.getString("status"));m.put("submitted",r.getString("submitted"));m.put("reason",r.getString("reason"));return m;
 },args);}
 @Transactional public List<Map<String,Object>> publicProducts(){expireOrders();return products("p.status='销售中' AND EXISTS(SELECT 1 FROM lc_merchant m WHERE m.id=p.merchant_id AND m.status='营业中')");}
 public List<Map<String,Object>> publicVariants(String sourceId){
  if(sourceId==null||!sourceId.matches("SPU-[A-F0-9]{12}|wp-(kobe|samba)"))throw new BizException("商品编号无效");
  return products("p.source_product_id=? AND p.status='销售中' AND merchant.status='营业中'",sourceId);
 }
 public Map<String,Object> product(String id){return products("p.id=?",id).stream().findFirst().orElseThrow(()->new BizException("商品不存在"));}
 private Map<String,Object> lockProduct(String id){var rows=db.queryForList("SELECT * FROM lc_product WHERE id=? FOR UPDATE",id);if(rows.isEmpty())throw new BizException("商品不存在");return rows.getFirst();}
 private void owner(WorkspaceAuth.Staff s,Map<String,Object> p){if(!Objects.equals(s.merchantId(),p.get("merchant_id")))throw new WorkspaceException(403,"无权操作其他商家的数据");}
 private void active(String merchant){if(!"营业中".equals(db.queryForObject("SELECT status FROM lc_merchant WHERE id=?",String.class,merchant)))throw new BizException("商家当前未营业");}
 public List<Map<String,Object>> merchants(){return db.query("SELECT * FROM lc_merchant ORDER BY id",(r,i)->{
  var m=new LinkedHashMap<String,Object>();for(String c:List.of("id","name","type","category","company","contact","submitted","status","reason"))m.put(c,r.getString(c));m.put("initials",r.getString("name").substring(0,1));return m;
 });}
 private List<Map<String,Object>> orders(String where,Object...args){return db.query("SELECT o.*,m.nickname,m.username FROM lc_order o JOIN v_member m ON m.member_id=o.member_id WHERE "+where+" ORDER BY o.created_at DESC",(r,i)->{
  var m=new LinkedHashMap<String,Object>();m.put("id",r.getString("id"));m.put("productId",r.getString("product_id"));m.put("merchantId",r.getString("merchant_id"));m.put("productName",r.getString("product_name"));m.put("variant",r.getString("spec"));m.put("image",r.getString("image"));m.put("amount",BigDecimal.valueOf(r.getLong("amount_fen"),2));m.put("quantity",r.getInt("quantity"));m.put("count",r.getInt("quantity"));m.put("status",r.getString("status"));m.put("time",r.getTimestamp("created_at").toLocalDateTime().toString().replace('T',' ').substring(0,16));m.put("customer",(r.getString("nickname")==null||r.getString("nickname").isBlank()?r.getString("username"):r.getString("nickname")).substring(0,1)+"**");m.put("carrier",r.getString("carrier"));m.put("tracking",r.getString("tracking"));m.put("recipient",r.getString("recipient"));m.put("phone",r.getString("phone"));m.put("address",r.getString("address"));
  m.put("merchantName",db.queryForObject("SELECT name FROM lc_merchant WHERE id=?",String.class,r.getString("merchant_id")));
  for(String c:List.of("created_at","expires_at","paid_at","shipped_at","completed_at","closed_at")){
   var stamp=r.getTimestamp(c);m.put(switch(c){case "created_at"->"createdAt";case "expires_at"->"expiresAt";case "paid_at"->"paidAt";case "shipped_at"->"shippedAt";case "completed_at"->"completedAt";default->"closedAt";},stamp==null?null:stamp.toInstant().toString());
  }
  m.put("paymentMethod",r.getString("payment_method"));m.put("paymentRef",r.getString("payment_ref"));m.put("closeReason",r.getString("close_reason"));m.put("deliveryStage",r.getInt("delivery_stage"));
  m.put("events",events(r.getString("id")));
  var after=aftersalesFor(r.getString("id"));m.put("aftersale",after.isEmpty()?null:after.getFirst());
  return m;
 },args);}
 @Transactional public List<Map<String,Object>> memberOrders(long member){expireOrders();return orders("o.member_id=?",member);}
 @Transactional public Map<String,Object> snapshot(WorkspaceAuth.Staff staff){
  expireOrders();
  boolean platform="PLATFORM".equals(staff.role());
  var ps=platform?products("1=1"):products("p.merchant_id=?",staff.merchantId());
  var os=platform?orders("1=1"):orders("o.merchant_id=?",staff.merchantId());
  var ms=platform?merchants():merchants().stream().filter(m->m.get("id").equals(staff.merchantId())).toList();
  return Map.of("localSimulation",auth.local(),"products",ps,"orders",os,"merchants",ms,"aftersales",platform?aftersales():List.of(),"settlements",List.of(),"auditLogs",platform?logs():List.of());
 }
 public record ProductForm(String name,String subtitle,String category,BigDecimal price,Integer stock){}
 @Transactional public Map<String,Object> save(WorkspaceAuth.Staff staff,String id,ProductForm f){
  if(f.name()==null||f.name().isBlank()||f.name().length()>100||f.subtitle()==null||f.subtitle().isBlank()||f.subtitle().length()>150||!List.of("鞋履","服饰","生活方式").contains(f.category())||f.price()==null||f.price().signum()<=0||f.price().compareTo(new BigDecimal("99999999"))>0||f.stock()==null||f.stock()<0||f.stock()>9999999)throw new BizException("请填写有效的商品名称、描述、价格和库存");
  long fen;try{fen=f.price().movePointRight(2).longValueExact();}catch(Exception e){throw new BizException("价格最多保留两位小数");}
  active(staff.merchantId());
  if(id==null){id=id("P-");seedProduct(id,staff.merchantId(),f.name().trim(),f.subtitle().trim(),f.category(),"product-shirt-blue-480.webp",fen,f.stock(),"草稿");}
  else{var p=lockProduct(id);owner(staff,p);if(List.of("销售中","待审核").contains(p.get("status")))throw new BizException("请先下架或撤回审核后编辑商品");db.update("UPDATE lc_product SET name=?,spec=?,category=?,price_fen=?,stock=?,status='草稿',reason='' WHERE id=?",f.name().trim(),f.subtitle().trim(),f.category(),fen,f.stock(),id);}
  log(staff.username(),"保存商品",id,"草稿已保存");return product(id);
 }
 @Transactional public void productAction(WorkspaceAuth.Staff staff,String id,String action,String reason){
  var p=lockProduct(id);String status=(String)p.get("status");String next;
  if("MERCHANT".equals(staff.role())){
   owner(staff,p);active(staff.merchantId());
   if("submit".equals(action)){if(!List.of("草稿","已下架","已驳回").contains(status))throw new BizException("当前状态不能提交审核");if(((Number)p.get("stock")).intValue()<=0)throw new BizException("请先补充库存");next="待审核";}
   else if("down".equals(action)&&List.of("销售中","待审核").contains(status))next="已下架";
   else throw new BizException("当前状态不能执行此操作");
  }else{
   if("approve".equals(action)||"reject".equals(action)){if(!"待审核".equals(status))throw new BizException("商品已处理，请刷新");next="approve".equals(action)?"销售中":"已驳回";if("销售中".equals(next)){active((String)p.get("merchant_id"));if(((Number)p.get("stock")).intValue()<=0)throw new BizException("库存不足");}}
   else if("down".equals(action)&&"销售中".equals(status))next="已下架";else throw new BizException("当前状态不能执行此操作");
   if(!"approve".equals(action))reason(reason);
  }
  db.update("UPDATE lc_product SET status=?,reason=?,submitted=? WHERE id=?",next,Objects.toString(reason,""),time(),id);log(staff.username(),"商品"+next,id,Objects.toString(reason,""));
 }
 @Transactional public void merchantAction(WorkspaceAuth.Staff staff,String id,String action,String reason){
  var rows=db.queryForList("SELECT * FROM lc_merchant WHERE id=? FOR UPDATE",id);if(rows.isEmpty())throw new BizException("商家不存在");var m=rows.getFirst();String status=(String)m.get("status");String next;
  if("approve".equals(action)&&"待审核".equals(status))next="营业中";
  else if("reject".equals(action)&&"待审核".equals(status))next="已驳回";
  else if("disable".equals(action)&&"营业中".equals(status)&&!"自营".equals(m.get("type")))next="已停用";
  else if("enable".equals(action)&&"已停用".equals(status))next="营业中";else throw new BizException("商家状态已变化");
  if(!"approve".equals(action))reason(reason);
  db.update("UPDATE lc_merchant SET status=?,reason=? WHERE id=?",next,Objects.toString(reason,""),id);
  if("已停用".equals(next))db.update("UPDATE lc_product SET status='已下架',reason='商家已停用' WHERE merchant_id=? AND status='销售中'",id);
  log(staff.username(),"商家"+next,id,Objects.toString(reason,""));
 }
 public record Create(String productId,Integer quantity,String requestKey,String recipient,String phone,String address){}
 @Transactional public Map<String,Object> create(long member,Create f){
  expireOrders();
  if(f.quantity()==null||f.quantity()<1||f.quantity()>99||f.requestKey()==null||!f.requestKey().matches("[a-f0-9]{32}")||f.recipient()==null||f.recipient().isBlank()||f.recipient().length()>40||f.phone()==null||!f.phone().matches("1[3-9][0-9]{9}")||f.address()==null||f.address().isBlank()||f.address().length()>250)throw new BizException("请填写有效的数量及收货信息");
  db.queryForObject("SELECT member_id FROM v_member WHERE member_id=? FOR UPDATE",Long.class,member);
  var old=db.queryForList("SELECT id,product_id,quantity FROM lc_order WHERE member_id=? AND request_key=?",member,f.requestKey());
  if(!old.isEmpty()){var o=old.getFirst();if(!Objects.equals(o.get("product_id"),f.productId())||((Number)o.get("quantity")).intValue()!=f.quantity())throw new BizException("下单请求标识已用于其他商品");return own(member,(String)o.get("id"));}
  var p=lockProduct(f.productId());active((String)p.get("merchant_id"));
  if(!"销售中".equals(p.get("status")))throw new BizException("商品尚未上架");
  if(((Number)p.get("stock")).intValue()<f.quantity())throw new BizException("商品库存不足");
  db.update("UPDATE lc_product SET stock=stock-? WHERE id=?",f.quantity(),f.productId());String id=id("LC");
  db.update("INSERT INTO lc_order(id,member_id,request_key,product_id,merchant_id,product_name,spec,image,amount_fen,quantity,status,created_at,recipient,phone,address) VALUES(?,?,?,?,?,?,?,?,?,?,'待付款',?,?,?,?)",id,member,f.requestKey(),f.productId(),p.get("merchant_id"),p.get("name"),p.get("spec"),p.get("image"),((Number)p.get("price_fen")).longValue()*f.quantity(),f.quantity(),now(),f.recipient(),f.phone(),f.address());
  db.update("UPDATE lc_order SET expires_at=? WHERE id=?",Timestamp.from(Instant.now().plusSeconds(1800)),id);
  event(id,"created","订单已提交","请在 30 分钟内完成付款，超时订单将自动关闭。");
  return own(member,id);
 }
 private Map<String,Object> own(long member,String id){return orders("o.member_id=? AND o.id=?",member,id).stream().findFirst().orElseThrow(()->new BizException("订单不存在"));}
 private Map<String,Object> lockOrder(String id){var rows=db.queryForList("SELECT * FROM lc_order WHERE id=? FOR UPDATE",id);if(rows.isEmpty())throw new BizException("订单不存在");return rows.getFirst();}
 @Transactional public Map<String,Object> memberAction(long member,String id,String action,String detail){
  var o=lockOrder(id);if(((Number)o.get("member_id")).longValue()!=member)throw new WorkspaceException(403,"无权操作其他用户订单");String state=(String)o.get("status");
  if("pay".equals(action)){
   if(!auth.local())throw new BizException("仅本地联调允许模拟支付");
   if("待付款".equals(state)){
    if(expire(o))return own(member,id);
    String method=detail==null||detail.isBlank()?"微信支付":detail;
    if(!List.of("微信支付","支付宝").contains(method))throw new BizException("请选择有效的支付方式");
    db.update("UPDATE lc_order SET status='待发货',paid_at=?,payment_method=?,payment_ref=? WHERE id=?",now(),method,id("TEST-P-"),id);
    event(id,"paid","支付成功",method+"测试通道已确认，等待商家发货。");
   }
   else if(o.get("paid_at")==null)throw new BizException("当前订单不能支付");
  }else if("cancel".equals(action)){
   if("待付款".equals(state)){db.update("UPDATE lc_order SET status='已取消',closed_at=?,close_reason='用户取消订单' WHERE id=?",now(),id);restock(o);event(id,"cancelled","订单已取消","库存已释放，订单关闭。");}else if(!"已取消".equals(state))throw new BizException("仅待付款订单可以取消");
  }else if("receive".equals(action)){
   if("运输中".equals(state)){db.update("UPDATE lc_order SET status='已完成',completed_at=?,delivery_stage=4 WHERE id=?",now(),id);event(id,"received","已确认收货","买家已确认收到商品，本次交易完成。");}else if(!"已完成".equals(state))throw new BizException("仅运输中订单可以确认收货");
  }else if("refund".equals(action)){
   if(!"待发货".equals(state))throw new BizException("当前仅支持未发货订单申请退款");reason(detail);
   if(db.queryForObject("SELECT COUNT(*) FROM lc_aftersale WHERE order_id=?",Long.class,id)>0)throw new BizException("该订单已有售后申请");
   db.update("INSERT INTO lc_aftersale(id,order_id,reason,status,previous_status,created_at) VALUES(?,?,?,'待处理',?,?)",id("AS-"),id,detail,state,now());db.update("UPDATE lc_order SET status='退款中' WHERE id=?",id);event(id,"refund-requested","退款申请已提交",detail);
  }else throw new BizException("未知订单操作");
  log("member:"+member,"订单"+action,id,Objects.toString(detail,""));return own(member,id);
 }
 private void restock(Map<String,Object> o){db.update("UPDATE lc_product SET stock=stock+? WHERE id=?",o.get("quantity"),o.get("product_id"));}
 @Transactional public void ship(WorkspaceAuth.Staff staff,String id,String carrier,String tracking){
  if(carrier==null||carrier.isBlank()||carrier.length()>40||tracking==null||!tracking.matches("[a-zA-Z0-9-]{6,60}"))throw new BizException("请填写物流公司和有效运单号");
  var o=lockOrder(id);owner(staff,o);if(!"待发货".equals(o.get("status")))throw new BizException("仅待发货订单可以发货");
  db.update("UPDATE lc_order SET status='运输中',carrier=?,tracking=?,shipped_at=? WHERE id=?",carrier,tracking,now(),id);event(id,"shipped","商家已发货",carrier+" · "+tracking+"，等待快递揽收。");log(staff.username(),"订单发货",id,carrier+" "+tracking);
 }
 private List<Map<String,Object>> aftersales(){return aftersalesFor(null);}
 private List<Map<String,Object>> aftersalesFor(String order){return db.query("SELECT a.*,o.amount_fen,o.payment_method FROM lc_aftersale a JOIN lc_order o ON o.id=a.order_id "+(order==null?"":"WHERE a.order_id=? ")+"ORDER BY a.created_at DESC",(r,i)->{
  var m=new LinkedHashMap<String,Object>();for(String c:List.of("id","reason","status","result"))m.put(c,r.getString(c));
  m.put("orderId",r.getString("order_id"));m.put("previousStatus",r.getString("previous_status"));m.put("amount",BigDecimal.valueOf(r.getLong("amount_fen"),2));m.put("time",r.getTimestamp("created_at").toLocalDateTime().toString().replace('T',' ').substring(0,16));m.put("createdAt",r.getTimestamp("created_at").toInstant().toString());
  m.put("resolvedAt",r.getTimestamp("resolved_at")==null?null:r.getTimestamp("resolved_at").toInstant().toString());m.put("refundRef",r.getString("refund_ref"));m.put("paymentMethod",r.getString("payment_method"));return m;
 },order==null?new Object[]{}:new Object[]{order});}
 @Transactional public void resolve(WorkspaceAuth.Staff staff,String id,boolean approve,String reason){
  if(!auth.local())throw new BizException("真实退款渠道尚未接入");reason(reason);
  var rows=db.queryForList("SELECT * FROM lc_aftersale WHERE id=?",id);if(rows.isEmpty())throw new BizException("售后不存在");var a=rows.getFirst();var o=lockOrder((String)a.get("order_id"));
  a=db.queryForList("SELECT * FROM lc_aftersale WHERE id=? FOR UPDATE",id).getFirst();
  if(!"待处理".equals(a.get("status"))||!"退款中".equals(o.get("status")))throw new BizException("售后已处理");
  db.update("UPDATE lc_aftersale SET status=?,result=?,resolved_at=?,refund_ref=? WHERE id=?",approve?"已处理":"已驳回",reason,now(),approve?id("TEST-R-"):"",id);
  db.update("UPDATE lc_order SET status=? WHERE id=?",approve?"已退款":a.get("previous_status"),o.get("id"));if(approve)restock(o);
  event((String)o.get("id"),approve?"refunded":"refund-rejected",approve?"退款已完成":"退款申请已驳回",reason+(approve?"，测试通道原路退回。":"，订单恢复待发货。"));
  log(staff.username(),approve?"退款通过（测试通道）":"退款驳回",id,reason);
 }
 private void event(String order,String eventKey,String title,String detail){
  db.update("INSERT INTO lc_order_event(order_id,event_key,title,detail,created_at) VALUES(?,?,?,?,?)",order,eventKey,title,detail,now());
 }
 private List<Map<String,Object>> events(String order){return db.query("SELECT event_key,title,detail,created_at FROM lc_order_event WHERE order_id=? ORDER BY id DESC",(r,i)->Map.<String,Object>of("key",r.getString("event_key"),"title",r.getString("title"),"detail",r.getString("detail"),"time",r.getTimestamp("created_at").toInstant().toString()),order);}
 private boolean expire(Map<String,Object> o){
  Timestamp deadline=(Timestamp)o.get("expires_at");if(deadline==null)deadline=Timestamp.from(((Timestamp)o.get("created_at")).toInstant().plusSeconds(1800));
  if(!"待付款".equals(o.get("status"))||deadline.toInstant().isAfter(Instant.now()))return false;
  db.update("UPDATE lc_order SET status='已取消',closed_at=?,close_reason='付款超时，订单自动关闭' WHERE id=?",now(),o.get("id"));restock(o);
  event((String)o.get("id"),"expired","订单已关闭","超过付款时限，库存已释放。");return true;
 }
 private void expireOrders(){
  var pending=db.queryForList("SELECT * FROM lc_order WHERE status='待付款' ORDER BY id FOR UPDATE");for(var o:pending)expire(o);
 }
 @Transactional public void advanceDelivery(WorkspaceAuth.Staff staff,String id,Integer expectedStage){
  if(!auth.local())throw new BizException("物流测试仅在本地环境开放");
  var o=lockOrder(id);owner(staff,o);if(!"运输中".equals(o.get("status")))throw new BizException("仅已发货订单可以更新配送进度");
  int stage=((Number)o.get("delivery_stage")).intValue();if(expectedStage==null||expectedStage!=stage)throw new BizException("配送进度已更新，请刷新后重试");
  if(stage>=3)throw new BizException("包裹已进入派送，请由买家确认收货");
  int next=stage+1;String title=List.of("已揽收","运输中","派送中").get(stage);
  db.update("UPDATE lc_order SET delivery_stage=? WHERE id=?",next,id);
  event(id,"delivery-"+next,title,List.of("快递已揽收包裹。","包裹已到达转运节点，正在发往收货地。","包裹已到达收货地区，正在安排派送。").get(stage)+"（测试轨迹）");
  log(staff.username(),"更新测试物流",id,title);
 }
 private List<Map<String,Object>> logs(){return db.query("SELECT * FROM lc_audit ORDER BY id DESC LIMIT 200",(r,i)->Map.<String,Object>of("id",r.getLong("id"),"time",r.getTimestamp("created_at").toLocalDateTime().toString().replace('T',' ').substring(0,19),"action",r.getString("action"),"target",r.getString("target"),"detail",r.getString("actor")+" · "+r.getString("detail")));}
 private static void reason(String r){if(r==null||r.trim().length()<4||r.length()>200)throw new BizException("请填写4至200字的处理原因");}
}
