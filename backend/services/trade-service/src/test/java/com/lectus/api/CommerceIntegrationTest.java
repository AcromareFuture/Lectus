package com.lectus.api;

import static com.lectus.api.Contracts.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={"lectus.seed-catalog=true","spring.datasource.password=","spring.datasource.url=jdbc:h2:mem:lectus-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class CommerceIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @org.springframework.test.context.bean.override.mockito.MockitoBean IdentityClient auth;
    @Autowired CommerceService commerce;
    @Autowired CatalogService catalog;
    Session session;
    Address address;
    Sku sku;
    @BeforeEach void setup() {
        session=account();
        address=commerce.saveAddress(session.user().id(),null,new AddressWrite("测试用户","13800000000","上海市 浦东新区","测试路100号",true));
        sku=catalog.product("wp-kobe").skus().getFirst();
        db.update("UPDATE product_skus SET stock=20");
    }
    Session account() {
        var user=new User(UUID.randomUUID().toString(),UUID.randomUUID()+"@example.test","莱择测试");
        var token=UUID.randomUUID().toString()+"1234567";
        org.mockito.Mockito.when(auth.authenticate(token)).thenReturn(user);
        return new Session(token,OffsetDateTime.now().plusHours(1).toString(),user);
    }
    String token() {return "Bearer "+session.token();}
    String encode(Object value) throws Exception {return json.writeValueAsString(value);}
    OrderWrite orderBody(int qty,boolean cart) {return new OrderWrite(address.id(),List.of(new OrderLine(sku.id(),qty)),cart);}

    @Test void protectsPrivateRoutesAndSupportsOnlyConfiguredOrigins() throws Exception {
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(options("/api/cart").header("Origin","http://127.0.0.1:4200").header("Access-Control-Request-Method","GET"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://127.0.0.1:4200"));
        mvc.perform(options("/api/cart").header("Origin","https://untrusted.example").header("Access-Control-Request-Method","GET"))
                .andExpect(status().isForbidden());
    }
    @Test void filtersCatalogAndRejectsBadPaging() throws Exception {
        mvc.perform(get("/api/products").param("q","Nike Kobe").param("category","鞋履"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].priceCents").value(149900));
        mvc.perform(get("/api/products").param("size","500")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("sort","price;drop table products")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("q","%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }
    @Test void validatesCartAndKeepsAccountsIsolated() throws Exception {
        mvc.perform(put("/api/cart/items").header("Authorization",token()).contentType(MediaType.APPLICATION_JSON).content(encode(new CartWrite(sku.id(),2,true))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].quantity").value(2));
        var other=account();
        mvc.perform(get("/api/cart").header("Authorization","Bearer "+other.token())).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete("/api/cart/items/"+sku.id()).header("Authorization","Bearer "+other.token())).andExpect(status().isNoContent());
        assertEquals(1,commerce.cart(session.user().id()).size());
        mvc.perform(put("/api/cart/items").header("Authorization",token()).contentType(MediaType.APPLICATION_JSON).content(encode(new CartWrite(sku.id(),0,true))))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/cart/items").header("Authorization",token()).contentType(MediaType.APPLICATION_JSON).content("{\"skuId\":\""+sku.id()+"\",\"quantity\":1.5,\"checked\":true}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/cart/items").header("Authorization",token()).contentType(MediaType.APPLICATION_JSON).content(encode(new CartWrite(sku.id(),21,true))))
                .andExpect(status().isConflict());
    }
    @Test void checkoutIsIdempotentAndCancellingRestocksExactlyOnce() throws Exception {
        commerce.putCart(session.user().id(),new CartWrite(sku.id(),2,true));
        var req=orderBody(2,true);
        String body=encode(req);
        var first=mvc.perform(post("/api/orders").header("Authorization",token()).header("Idempotency-Key","test-order-123").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.totalCents").value(299800)).andReturn();
        String id=json.readTree(first.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(post("/api/orders").header("Authorization",token()).header("Idempotency-Key","test-order-123").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(id));
        assertTrue(commerce.cart(session.user().id()).isEmpty());assertEquals(18,remaining());
        mvc.perform(post("/api/orders").header("Authorization",token()).header("Idempotency-Key","test-order-123").contentType(MediaType.APPLICATION_JSON).content(encode(orderBody(1,false))))
                .andExpect(status().isConflict());
        for(int i=0;i<2;i++) mvc.perform(post("/api/orders/"+id+"/cancel").header("Authorization",token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertEquals(20,remaining());
    }
    int remaining(){return db.queryForObject("SELECT stock FROM product_skus WHERE id=?",Integer.class,sku.id());}
    @Test void refusesForgedPricesAndInvalidOrderLines() throws Exception {
        String body=encode(orderBody(1,false));
        mvc.perform(post("/api/orders").header("Authorization",token()).header("Idempotency-Key","price-test-123").contentType(MediaType.APPLICATION_JSON).content(body.substring(0,body.length()-1)+",\"totalCents\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders").header("Authorization",token()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders").header("Authorization",token()).header("Idempotency-Key","duplicate-test").contentType(MediaType.APPLICATION_JSON)
                        .content(encode(new OrderWrite(address.id(),List.of(new OrderLine(sku.id(),1),new OrderLine(sku.id(),1)),false))))
                .andExpect(status().isBadRequest());
        assertEquals(20,remaining());
    }
    @Test void rejectsOtherAccountsAddressesOrdersAndCancellation() throws Exception {
        var order=commerce.createOrder(session.user().id(),"isolated-order",orderBody(1,false));
        var other=account();String bearer="Bearer "+other.token();
        mvc.perform(get("/api/orders/"+order.id()).header("Authorization",bearer)).andExpect(status().isNotFound());
        mvc.perform(post("/api/orders/"+order.id()+"/cancel").header("Authorization",bearer)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/addresses/"+address.id()).header("Authorization",bearer)).andExpect(status().isNotFound());
        mvc.perform(post("/api/orders").header("Authorization",bearer).header("Idempotency-Key","foreign-address").contentType(MediaType.APPLICATION_JSON).content(encode(orderBody(1,false)))).andExpect(status().isNotFound());
        assertEquals("PENDING_PAYMENT",commerce.order(session.user().id(),order.id()).status());
    }
    @Test void snapshotsAddressAndPriceAcrossLaterChanges() {
        var order=commerce.createOrder(session.user().id(),"snapshot-order",orderBody(1,false));
        commerce.saveAddress(session.user().id(),address.id(),new AddressWrite("新的收件人","13800000000","北京市 朝阳区","新测试路200号",true));
        db.update("UPDATE products SET price_cents=999 WHERE id=?",sku.productId());
        try {
            var saved=commerce.order(session.user().id(),order.id());
            assertEquals("测试用户",saved.address().recipient());assertEquals(149900,saved.totalCents());
        } finally {db.update("UPDATE products SET price_cents=149900 WHERE id=?",sku.productId());}
    }
    @Test void atomicRollbackWhenLaterSkuHasNoStock() {
        var second=catalog.product("wp-asics").skus().getFirst();
        db.update("UPDATE product_skus SET stock=0 WHERE id=?",second.id());
        var body=new OrderWrite(address.id(),List.of(new OrderLine(sku.id(),1),new OrderLine(second.id(),1)),false);
        assertThrows(ApiException.class,()->commerce.createOrder(session.user().id(),"rollback-order",body));
        assertEquals(20,remaining());assertTrue(commerce.orders(session.user().id(),0,20).isEmpty());
    }
    @Test void concurrentCheckoutsCannotOversell() throws Exception {
        var other=account();
        var otherAddress=commerce.saveAddress(other.user().id(),null,new AddressWrite("其他用户","13900000000","北京市 朝阳区","测试路300号",true));
        db.update("UPDATE product_skus SET stock=1 WHERE id=?",sku.id());
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> futures=new ArrayList<>();
            for(var pair:List.of(Map.entry(session.user().id(),address.id()),Map.entry(other.user().id(),otherAddress.id()))) {
                futures.add(pool.submit(()->{
                    start.await();
                    try {commerce.createOrder(pair.getKey(),UUID.randomUUID().toString(),new OrderWrite(pair.getValue(),List.of(new OrderLine(sku.id(),1)),false));return true;}
                    catch(ApiException e) {assertEquals("OUT_OF_STOCK",e.code);return false;}
                }));
            }
            start.countDown();int success=0;
            for(var future:futures) if(future.get(15,TimeUnit.SECONDS))success++;
            assertEquals(1,success);assertEquals(0,remaining());
        }
    }
    @Test void defaultAddressRemainsUniqueAndInvalidInputIsRejected() throws Exception {
        var next=commerce.saveAddress(session.user().id(),null,new AddressWrite("第二地址","+1 202 555 0100","California","Example road 100",true));
        assertEquals(1,commerce.addresses(session.user().id()).stream().filter(Address::isDefault).count());
        commerce.deleteAddress(session.user().id(),next.id());
        assertTrue(commerce.addresses(session.user().id()).getFirst().isDefault());
        mvc.perform(post("/api/addresses").header("Authorization",token()).contentType(MediaType.APPLICATION_JSON)
                        .content(encode(new AddressWrite("", "abc","x","a",true))))
                .andExpect(status().isBadRequest());
    }
    @Test void concurrentRetriesProduceOneOrderAndOneStockDeduction() throws Exception {
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var tasks=new ArrayList<Future<Order>>();
            for(int i=0;i<2;i++) tasks.add(pool.submit(()->{
                start.await();return commerce.createOrder(session.user().id(),"same-concurrent-key",orderBody(2,false));
            }));
            start.countDown();
            assertEquals(tasks.get(0).get(15,TimeUnit.SECONDS).id(),tasks.get(1).get(15,TimeUnit.SECONDS).id());
            assertEquals(18,remaining());assertEquals(1,commerce.orders(session.user().id(),0,20).size());
        }
    }

    @Test void noClientPaymentOrUnknownApiIsExposed() throws Exception {
        mvc.perform(post("/api/orders/fake/pay").header("Authorization",token()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
