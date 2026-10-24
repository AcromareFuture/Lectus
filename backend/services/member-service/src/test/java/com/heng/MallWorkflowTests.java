package com.heng;

import com.heng.config.BizException;
import com.heng.mall.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"lectus.mall.local-enabled=true","lectus.mall.bootstrap-password=Lectus@2026", "spring.datasource.url=jdbc:h2:mem:mall_workflow_tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"})
@ActiveProfiles("test")
class MallWorkflowTests {
 @Autowired MallService mall;
 @Autowired JdbcTemplate db;
 static final long OWNER=780001, OTHER=780002;
 static final String PRODUCT="MALL-TEST-P";
 final WorkspaceAuth.Staff merchant=new WorkspaceAuth.Staff("merchant","MERCHANT","M-1001");
 final WorkspaceAuth.Staff otherMerchant=new WorkspaceAuth.Staff("yunye","MERCHANT","M-1002");
 final WorkspaceAuth.Staff platform=new WorkspaceAuth.Staff("platform","PLATFORM",null);
 @BeforeEach void setup(){
  db.update("DELETE FROM lc_order_event WHERE order_id IN(SELECT id FROM lc_order WHERE product_id=?)",PRODUCT);
  db.update("DELETE FROM lc_aftersale WHERE order_id IN(SELECT id FROM lc_order WHERE product_id=?)",PRODUCT);
  db.update("DELETE FROM lc_order WHERE product_id=?",PRODUCT);
  db.update("DELETE FROM lc_product WHERE id=?",PRODUCT);
  db.update("DELETE FROM v_member WHERE member_id IN (?,?)",OWNER,OTHER);
  db.update("INSERT INTO v_member(member_id,username,nickname,status) VALUES(?,'mall_test_owner','',1),(?,'mall_test_other','测试',1)",OWNER,OTHER);
  db.update("INSERT INTO lc_product(id,merchant_id,name,spec,category,image,price_fen,stock,status,submitted) VALUES(?,'M-1001','测试商品','标准款','服饰','product-shirt-blue-480.webp',36900,10,'销售中','2026-10-23')",PRODUCT);
 }
 @Test void sourceVariantsRespectProductStatusAndMerchantStatus(){
  db.update("UPDATE lc_product SET source_product_id='SPU-000FE86E738A' WHERE id=?",PRODUCT);
  var variants=mall.publicVariants("SPU-000FE86E738A");
  assertEquals(1,variants.size());assertEquals(PRODUCT,variants.getFirst().get("id"));
  assertEquals("SPU-000FE86E738A",variants.getFirst().get("sourceProductId"));
  db.update("UPDATE lc_product SET status='已下架' WHERE id=?",PRODUCT);
  assertTrue(mall.publicVariants("SPU-000FE86E738A").isEmpty());
  assertThrows(BizException.class,()->mall.publicVariants("not-a-source-id"));
 }
 Map<String,Object> create(){return mall.create(OWNER,new MallService.Create(PRODUCT,2,UUID.randomUUID().toString().replace("-",""),"测试收货人","13800000000","测试省测试市测试区测试路 1 号"));}
 int stock(){return db.queryForObject("SELECT stock FROM lc_product WHERE id=?",Integer.class,PRODUCT);}
 long events(String id,String event){return db.queryForObject("SELECT COUNT(*) FROM lc_order_event WHERE order_id=? AND event_key=?",Long.class,id,event);}
 @Test void checkoutUsesTrustedPriceAndDuplicatePaymentKeepsOneReceipt(){
  var order=create();String id=(String)order.get("id");assertEquals("738.00",order.get("amount").toString());assertEquals(8,stock());assertNotNull(order.get("expiresAt"));
  assertThrows(BizException.class,()->mall.memberAction(OWNER,id,"pay","银行卡"));assertEquals(0,events(id,"paid"));
  var paid=mall.memberAction(OWNER,id,"pay","支付宝");assertEquals("待发货",paid.get("status"));assertEquals("支付宝",paid.get("paymentMethod"));assertTrue(((String)paid.get("paymentRef")).startsWith("TEST-P-"));
  var again=mall.memberAction(OWNER,id,"pay","微信支付");assertEquals(paid.get("paymentRef"),again.get("paymentRef"));assertEquals("支付宝",again.get("paymentMethod"));assertEquals(1,events(id,"paid"));
 }
 @Test void expiredOrderCannotBePaidAndStockIsRestoredOnlyOnce(){
  var order=create();String id=(String)order.get("id");db.update("UPDATE lc_order SET expires_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(1)),id);
  var result=mall.memberAction(OWNER,id,"pay","微信支付");assertEquals("已取消",result.get("status"));assertNull(result.get("paidAt"));assertEquals(10,stock());
  mall.memberOrders(OWNER);mall.memberAction(OWNER,id,"cancel",null);assertEquals(10,stock());assertEquals(1,events(id,"expired"));
 }
 @Test void fulfilmentIsOwnedOrderedAndVisibleAcrossWorkspaces(){
  String id=(String)create().get("id");assertThrows(WorkspaceException.class,()->mall.memberAction(OTHER,id,"pay",null));
  assertThrows(BizException.class,()->mall.ship(merchant,id,"顺丰速运","SF1234567890"));mall.memberAction(OWNER,id,"pay","微信支付");
  assertThrows(WorkspaceException.class,()->mall.ship(otherMerchant,id,"顺丰速运","SF1234567890"));mall.ship(merchant,id,"顺丰速运","SF1234567890");
  assertThrows(WorkspaceException.class,()->mall.advanceDelivery(otherMerchant,id,0));mall.advanceDelivery(merchant,id,0);
  assertThrows(BizException.class,()->mall.advanceDelivery(merchant,id,0));mall.advanceDelivery(merchant,id,1);mall.advanceDelivery(merchant,id,2);
  assertThrows(BizException.class,()->mall.advanceDelivery(merchant,id,3));var done=mall.memberAction(OWNER,id,"receive",null);assertEquals("已完成",done.get("status"));assertNotNull(done.get("completedAt"));
  mall.memberAction(OWNER,id,"receive",null);assertEquals(1,events(id,"received"));assertEquals(8,stock());assertEquals(4,done.get("deliveryStage"));
  assertTrue(mall.snapshot(platform).get("orders").toString().contains(done.get("paymentRef").toString()));
 }
 @Test void refundBlocksShipmentAndProducesOriginalChannelReceipt(){
  String id=(String)create().get("id");mall.memberAction(OWNER,id,"pay","支付宝");var requested=mall.memberAction(OWNER,id,"refund","暂时不需要购买");
  assertThrows(BizException.class,()->mall.ship(merchant,id,"顺丰速运","SF1234567890"));var a=(Map<?,?>)requested.get("aftersale");mall.resolve(platform,(String)a.get("id"),true,"同意买家退款申请");
  var refunded=mall.memberOrders(OWNER).getFirst();assertEquals("已退款",refunded.get("status"));var receipt=(Map<?,?>)refunded.get("aftersale");assertEquals("支付宝",receipt.get("paymentMethod"));assertNotNull(receipt.get("resolvedAt"));assertTrue(receipt.get("refundRef").toString().startsWith("TEST-R-"));assertEquals(10,stock());
  assertThrows(BizException.class,()->mall.resolve(platform,(String)a.get("id"),true,"再次同意退款申请"));assertEquals(10,stock());
 }
 @Test void rejectedRefundRestoresFulfilmentWithoutRefundingInventory(){
  String id=(String)create().get("id");mall.memberAction(OWNER,id,"pay",null);var requested=mall.memberAction(OWNER,id,"refund","订单信息需要核对");var a=(Map<?,?>)requested.get("aftersale");mall.resolve(platform,(String)a.get("id"),false,"请联系店铺核对信息");
  assertEquals("待发货",mall.memberOrders(OWNER).getFirst().get("status"));assertEquals(8,stock());mall.ship(merchant,id,"顺丰速运","SF1234567890");
 }
 @Test void simultaneousPaymentsProduceOneFinancialRecord() throws Exception {
  String id=(String)create().get("id");var pool=Executors.newFixedThreadPool(2);
  try{var a=pool.submit(()->mall.memberAction(OWNER,id,"pay","微信支付"));var b=pool.submit(()->mall.memberAction(OWNER,id,"pay","支付宝"));assertEquals(a.get(10,TimeUnit.SECONDS).get("paymentRef"),b.get(10,TimeUnit.SECONDS).get("paymentRef"));}finally{pool.shutdownNow();}
  assertEquals(1,events(id,"paid"));assertEquals(8,stock());
 }
}
