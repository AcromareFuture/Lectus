package com.heng;

import com.heng.config.BizException;
import com.heng.mall.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"lectus.mall.local-enabled=true","lectus.mall.bootstrap-password=Lectus@2026","spring.datasource.url=jdbc:h2:mem:mall_cart_tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"})
@ActiveProfiles("test")
class MallCartTests {
 @Autowired MallCartService cart;
 @Autowired MallService mall;
 @Autowired JdbcTemplate db;
 static final long OWNER=790001,OTHER=790002;
 static final String P1="CART-TEST-A",P2="CART-TEST-B";
 @BeforeEach void setup(){
  db.update("DELETE FROM lc_checkout_order");db.update("DELETE FROM lc_checkout");db.update("DELETE FROM lc_cart");
  db.update("DELETE FROM lc_order_event");db.update("DELETE FROM lc_aftersale");db.update("DELETE FROM lc_order");
  db.update("DELETE FROM lc_product WHERE id IN(?,?)",P1,P2);db.update("DELETE FROM v_member WHERE member_id IN(?,?)",OWNER,OTHER);
  db.update("INSERT INTO v_member(member_id,username,nickname,status) VALUES(?,'cart_test_owner','购物车用户',1),(?,'cart_test_other','其他用户',1)",OWNER,OTHER);
  db.update("INSERT INTO lc_product(id,merchant_id,name,spec,category,image,price_fen,stock,status,submitted) VALUES(?,'M-1001','衬衫','雾蓝','服饰','product-shirt-blue-480.webp',36900,10,'销售中','2026-10-23'),(?,'M-1002','运动鞋','黑白','鞋履','product-samba-480.webp',79900,10,'销售中','2026-10-23')",P1,P2);
 }
 String key(){return UUID.randomUUID().toString().replace("-","");}
 MallCartService.Checkout form(String request){return new MallCartService.Checkout(request,List.of(new MallCartService.Line(P1,2,new BigDecimal("369")),new MallCartService.Line(P2,1,new BigDecimal("799"))),"测试收货人","13800000000","测试省测试市测试路 1 号");}
 void fill(){cart.put(OWNER,P1,2,true);cart.put(OWNER,P2,1,true);}
 int stock(String product){return db.queryForObject("SELECT stock FROM lc_product WHERE id=?",Integer.class,product);}
 long orderCount(){return db.queryForObject("SELECT COUNT(*) FROM lc_order",Long.class);}
 @SuppressWarnings("unchecked") List<Map<String,Object>> orders(Map<String,Object> checkout){return (List<Map<String,Object>>)checkout.get("orders");}
 @Test void cartIsAccountOwnedAndDoesNotReserveStock(){
  cart.put(OWNER,P1,2,true);cart.put(OWNER,P1,1,true);assertEquals(3,cart.cart(OWNER).getFirst().get("quantity"));assertEquals(10,stock(P1));
  assertTrue(cart.cart(OTHER).isEmpty());cart.remove(OTHER,P1);assertEquals(1,cart.cart(OWNER).size());
  cart.put(OWNER,P1,1,false);assertEquals(1,cart.cart(OWNER).getFirst().get("quantity"));cart.remove(OWNER,P1);cart.remove(OWNER,P1);assertTrue(cart.cart(OWNER).isEmpty());
 }
 @Test void staleStockAndListingStateRemainVisible(){
  cart.put(OWNER,P1,2,true);db.update("UPDATE lc_product SET stock=1 WHERE id=?",P1);assertEquals(false,cart.cart(OWNER).getFirst().get("available"));
  assertThrows(BizException.class,()->cart.put(OWNER,P1,2,false));cart.put(OWNER,P1,1,false);assertEquals(true,cart.cart(OWNER).getFirst().get("available"));
  db.update("UPDATE lc_product SET status='已下架' WHERE id=?",P1);assertEquals("商品已下架",cart.cart(OWNER).getFirst().get("issue"));
 }
 @Test void crossStoreCheckoutReservesAllLinesAndRemovesOnlySelectedItems(){
  fill();cart.put(OTHER,P1,1,true);var result=cart.checkout(OWNER,form(key()));assertEquals(2,orders(result).size());assertEquals("1537.00",result.get("total").toString());
  assertEquals(8,stock(P1));assertEquals(9,stock(P2));assertTrue(cart.cart(OWNER).isEmpty());assertEquals(1,cart.cart(OTHER).size());
  assertThrows(BizException.class,()->cart.summary(OTHER,(String)result.get("id")));
 }
 @Test void checkoutRetryDoesNotDuplicateOrdersAndRejectsChangedPayload(){
  fill();String request=key();var first=cart.checkout(OWNER,form(request));var again=cart.checkout(OWNER,form(request));assertEquals(first.get("id"),again.get("id"));assertEquals(2,orderCount());assertEquals(8,stock(P1));
  var changed=new MallCartService.Checkout(request,form(request).items(),"另一收货人","13800000000","其他测试地址");assertThrows(BizException.class,()->cart.checkout(OWNER,changed));
 }
 @Test void priceChangesCannotBeSilentlyCharged(){
  fill();db.update("UPDATE lc_product SET price_fen=80000 WHERE id=?",P2);assertThrows(BizException.class,()->cart.checkout(OWNER,form(key())));
  assertEquals(0,orderCount());assertEquals(10,stock(P1));assertEquals(2,cart.cart(OWNER).size());
 }
 @Test void failureOnSecondLineRollsBackFirstLineOrderAndInventory(){
  fill();db.update("UPDATE lc_product SET status='已下架' WHERE id=?",P2);assertThrows(BizException.class,()->cart.checkout(OWNER,form(key())));
  assertEquals(0,orderCount());assertEquals(10,stock(P1));assertEquals(10,stock(P2));assertEquals(2,cart.cart(OWNER).size());assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM lc_checkout",Integer.class));
 }
 @Test void duplicateProductsAndChangedQuantitiesAreRejected(){
  fill();var base=form(key());var duplicate=new MallCartService.Checkout(base.requestKey(),List.of(base.items().getFirst(),base.items().getFirst()),base.recipient(),base.phone(),base.address());
  assertThrows(BizException.class,()->cart.checkout(OWNER,duplicate));cart.put(OWNER,P1,1,false);assertThrows(BizException.class,()->cart.checkout(OWNER,base));assertEquals(0,orderCount());
 }
 @Test void combinedPaymentHasPerOrderReceiptsAndIsIdempotent() throws Exception {
  fill();String id=(String)cart.checkout(OWNER,form(key())).get("id");assertThrows(BizException.class,()->cart.pay(OTHER,id,new MallCartService.Payment("支付宝",new BigDecimal("1537"))));
  assertThrows(BizException.class,()->cart.pay(OWNER,id,new MallCartService.Payment("支付宝",BigDecimal.ONE)));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM lc_order WHERE paid_at IS NOT NULL",Integer.class));
  var pool=Executors.newFixedThreadPool(2);
  try{
   var a=pool.submit(()->cart.pay(OWNER,id,new MallCartService.Payment("支付宝",new BigDecimal("1537"))));
   var b=pool.submit(()->cart.pay(OWNER,id,new MallCartService.Payment("微信支付",new BigDecimal("1537"))));
   var first=a.get(15,TimeUnit.SECONDS);var second=b.get(15,TimeUnit.SECONDS);assertEquals("paid",first.get("status"));assertEquals("paid",second.get("status"));
   assertEquals(orders(first).stream().map(o->o.get("paymentRef")).toList(),orders(second).stream().map(o->o.get("paymentRef")).toList());
  }finally{pool.shutdownNow();}
  assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM lc_order_event WHERE event_key='paid'",Integer.class));assertEquals(8,stock(P1));
 }
 @Test void expiredLineBlocksCombinedPaymentWithoutChargingOtherLines(){
  fill();var group=cart.checkout(OWNER,form(key()));String order=(String)orders(group).getFirst().get("id");
  db.update("UPDATE lc_order SET expires_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(1)),order);
  var result=cart.pay(OWNER,(String)group.get("id"),new MallCartService.Payment("支付宝",new BigDecimal("1537")));assertEquals("unavailable",result.get("status"));
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM lc_order WHERE paid_at IS NOT NULL",Integer.class));
 }
 @Test void anIndividuallyPaidLineIsExcludedFromRemainingAmount(){
  fill();var group=cart.checkout(OWNER,form(key()));var first=orders(group).getFirst();mall.memberAction(OWNER,(String)first.get("id"),"pay","微信支付");
  String id=(String)group.get("id");var current=cart.summary(OWNER,id);assertThrows(BizException.class,()->cart.pay(OWNER,id,new MallCartService.Payment("支付宝",new BigDecimal("1537"))));
  var paid=cart.pay(OWNER,id,new MallCartService.Payment("支付宝",(BigDecimal)current.get("pendingAmount")));assertEquals("paid",paid.get("status"));
  assertEquals("微信支付",orders(paid).stream().filter(o->o.get("id").equals(first.get("id"))).findFirst().orElseThrow().get("paymentMethod"));
 }
}
