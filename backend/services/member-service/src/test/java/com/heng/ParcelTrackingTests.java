package com.heng;

import com.heng.auth.MemberTokenService;
import com.heng.logistics.ParcelTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test")
class ParcelTrackingTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    @Autowired MemberTokenService tokens;
    @Autowired ParcelTrackingService service;
    @BeforeEach void prepare() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc.update("DELETE FROM v_logistics_event_location");
        jdbc.update("DELETE FROM v_logistics_event");
        jdbc.update("DELETE FROM v_order_item");
        jdbc.update("DELETE FROM v_order");
        jdbc.update("DELETE FROM v_member_session");
        jdbc.update("DELETE FROM v_member");
        jdbc.update("INSERT INTO v_member(member_id,username,balance_fen,points) VALUES(1,'tracking-buyer',1000,0),(2,'other-buyer',0,0)");
        jdbc.update("INSERT INTO v_order(order_id,order_no,member_id,status,ship_company,ship_no,ship_time,contact_name,contact_phone,shipping_address) VALUES(501,'TRACK501',1,'SHIPPED','测试快递','TRACK-NUMBER','2026-10-13 08:00:00','买家','13812340000','上海市徐汇区测试路')");
    }
    @Test void ownershipAndAuthenticationProtectPrivateParcel() throws Exception {
        mvc.perform(get("/api/orders/501/logistics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders/501/logistics").header("Authorization","Bearer "+tokens.issue(2L))).andExpect(jsonPath("$.success").value(false));
        mvc.perform(get("/api/orders/501/logistics").header("Authorization","Bearer "+tokens.issue(1L))).andExpect(status().isOk()).andExpect(jsonPath("$.data.trackingNo").value("TRACK-NUMBER"));
    }
    @Test void registeredEventsAreNewestFirstAndDoNotConfirmReceipt() {
        jdbc.update("INSERT INTO v_logistics_event(order_id,event_time,content,source) VALUES(501,'2026-10-13 12:00:00','快件已揽收','OPERATOR'),(501,'2026-10-14 09:00:00','派件员正在派送','OPERATOR')");
        var result=service.tracking(1L,501L);
        assertEquals("OUT_FOR_DELIVERY",result.get("status"));
        assertNull(result.get("estimatedDelivery"));
        assertFalse((Boolean)result.get("mapAvailable"));
        assertEquals("SHIPPED",jdbc.queryForObject("SELECT status FROM v_order WHERE order_id=501",String.class));
        assertNull(jdbc.queryForObject("SELECT receive_time FROM v_order WHERE order_id=501",Object.class));
        var events=(java.util.List<?>)result.get("events");
        assertEquals(3,events.size());
        assertTrue(events.get(0).toString().contains("派件员正在派送"));
    }
    @Test void dispatchIsNotPickupAndNoEventsOrEtaAreInvented() {
        var result=service.tracking(1L,501L);
        assertEquals("SHIPPED",result.get("status"));
        jdbc.update("UPDATE v_order SET status='PAID',ship_time=NULL,ship_company=NULL,ship_no=NULL WHERE order_id=501");
        result=service.tracking(1L,501L);
        assertEquals("NOT_SHIPPED",result.get("status"));
        assertTrue(((java.util.List<?>)result.get("events")).isEmpty());
    }
    @Test void failedOrConditionalSignatureDoesNotLookDelivered() {
        for(String text:new String[]{"签收失败","未签收","无法签收","未能签收","签收不成功"})assertNotEquals("DELIVERED",ParcelTrackingService.phase(text));
        for(String text:new String[]{"等待签收","预计明日签收","待签收","请签收"})assertNotEquals("DELIVERED",ParcelTrackingService.phase(text));
        assertEquals("DELIVERED",ParcelTrackingService.phase("快件已签收"));
        assertEquals("EXCEPTION",ParcelTrackingService.phase("快件派送失败，需联系收件人"));
    }
    @Test void publicPreviewIsExplicitlySyntheticAndDoesNotReadPrivateOrders() throws Exception {
        mvc.perform(get("/api/logistics/tracking-demo")).andExpect(status().isOk()).andExpect(jsonPath("$.data.simulation").value(true)).andExpect(jsonPath("$.data.trackingNo").value("DEMO20261014001"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM v_order",Integer.class));
    }
    @Test void verifiedLocationAndExplicitPhaseOverrideTextWithoutMutatingOrder() {
        jdbc.update("INSERT INTO v_logistics_event(event_id,order_id,event_time,content,source) VALUES(9001,501,'2026-10-14 09:00:00','包裹到站，等待配送','OPERATOR')");
        jdbc.update("INSERT INTO v_logistics_event_location(event_id,phase,city,longitude,latitude,courier_name,courier_phone,estimated_delivery) VALUES(9001,'OUT_FOR_DELIVERY','保定市',115.464590,38.873890,'张师傅','13812345678','2026-10-14 18:00:00')");
        var result=service.tracking(1L,501L);
        assertEquals("OUT_FOR_DELIVERY",result.get("status"));
        assertEquals(true,result.get("mapAvailable"));
        assertEquals(1,((java.util.List<?>)result.get("mapNodes")).size());
        assertEquals("13812345678",result.get("courierPhone"));
        assertEquals("2026-10-14T18:00",result.get("estimatedDelivery"));
        assertEquals("SHIPPED",jdbc.queryForObject("SELECT status FROM v_order WHERE order_id=501",String.class));
        jdbc.update("INSERT INTO v_logistics_event(event_id,order_id,event_time,content,source) VALUES(9002,501,'2026-10-14 10:00:00','快件已签收','OPERATOR')");
        result=service.tracking(1L,501L);
        assertEquals("DELIVERED",result.get("status"));
        assertNull(result.get("courierPhone"));
        assertNull(result.get("estimatedDelivery"));
    }
}
