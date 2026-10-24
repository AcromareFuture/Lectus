package com.heng;

import com.heng.biz.MemberAssetService;
import com.heng.biz.MemberCenterService;
import com.heng.biz.PaymentService;
import com.heng.config.BizException;
import com.heng.config.TradeProperties;
import com.heng.domain.VMemberAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TradeIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberAssetService assets;
    @Autowired MemberCenterService members;
    @Autowired PaymentService payments;
    @Autowired TradeProperties properties;
    @Autowired com.heng.biz.AftersalesService aftersales;
    @Autowired com.heng.biz.RequestDeduplicator dedup;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.heng.payment.ProviderGateway gateway;

    @BeforeEach
    void prepare() {
        for (String table : List.of("v_refund","v_invoice","v_logistics_event","v_reconciliation","v_member_coupon","v_request_result","v_member_level","v_push_record", "v_settlement_record", "v_stock_log", "v_payment_record",
                "v_order_item", "v_order", "v_pay_config", "v_stock", "v_goods_sku", "v_goods_spu",
                "v_member_point_ledger", "v_member_balance_ledger", "v_member_address",
                "v_member_favorite", "v_member")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("INSERT INTO v_member(member_id, username, balance_fen, points) VALUES (1, 'buyer', 1000, 100)");
        jdbc.update("INSERT INTO v_order(order_id, order_no, member_id, pay_amount_fen, status) "
                + "VALUES (1, 'VTEST1', 1, 300, 'PENDING_PAYMENT')");
        jdbc.update("INSERT INTO v_order_item(order_id, quantity, unit_price_fen, subtotal_fen, goods_snapshot) "
                + "VALUES (1, 1, 300, 300, ?)", "{\"skuId\":1,\"spuId\":1}");
        jdbc.update("INSERT INTO v_stock(sku_id, available_stock, locked_stock) VALUES (1, 9, 1)");
        jdbc.update("INSERT INTO v_goods_sku VALUES (1, 10, 0)");
        jdbc.update("INSERT INTO v_goods_spu(spu_id,total_stock,sales_count) VALUES (1, 10, 0)");
        jdbc.update("INSERT INTO v_pay_config(pay_type, enabled) VALUES ('BALANCE', 1), ('WECHAT', 1)");
        properties.setAllowDevDeposit(false);
        properties.setDemoMode(false);
        properties.setSandboxPaymentEnabled(false);
        properties.setPaymentCallbackSecret("");
        org.mockito.Mockito.when(gateway.environment(org.mockito.ArgumentMatchers.anyString())).thenReturn("PRODUCTION");
    }


    @Test void simulatedConfirmationRequiresOwnerAndCannotCompleteProductionRows(){
        enableSandbox();properties.setDemoMode(true);jdbc.update("UPDATE v_order SET demo_mode=1");payments.pay(1L,"VTEST1","WECHAT");
        var sandbox=new com.heng.biz.SandboxPaymentService(jdbc,payments,properties,true);
        var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        assertThrows(BizException.class,()->tx.execute(s->sandbox.confirm(2L,"VTEST1","WECHAT")));
        jdbc.update("UPDATE v_payment_record SET environment='PRODUCTION'");
        assertThrows(BizException.class,()->tx.execute(s->sandbox.confirm(1L,"VTEST1","WECHAT")));
        assertEquals("PENDING_PAYMENT",jdbc.queryForObject("SELECT status FROM v_order",String.class));assertEquals(1,number("SELECT locked_stock FROM v_stock"));
    }
    @Test void simulatedConfirmationReplaySettlesOnlyOnceWithoutWalletCharge(){
        enableSandbox();properties.setDemoMode(true);jdbc.update("UPDATE v_order SET demo_mode=1");payments.pay(1L,"VTEST1","WECHAT");
        var sandbox=new com.heng.biz.SandboxPaymentService(jdbc,payments,properties,true);
        var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        tx.execute(s->sandbox.confirm(1L,"VTEST1","WECHAT"));tx.execute(s->sandbox.confirm(1L,"VTEST1","WECHAT"));
        assertEquals("PAID",jdbc.queryForObject("SELECT status FROM v_order",String.class));assertEquals(1000,number("SELECT balance_fen FROM v_member"));assertEquals(1,number("SELECT COUNT(*) FROM v_settlement_record"));assertEquals(0,number("SELECT locked_stock FROM v_stock"));assertEquals(103,number("SELECT points FROM v_member"));
    }
    @Test void officialSandboxCannotSettleProductionOrders() {
        jdbc.update("UPDATE v_order SET demo_mode=0");
        jdbc.update("INSERT INTO v_pay_config(pay_type,enabled) VALUES('ALIPAY',1)");
        org.mockito.Mockito.when(gateway.ready("ALIPAY")).thenReturn(true);
        org.mockito.Mockito.when(gateway.environment("ALIPAY")).thenReturn("ALIPAY_SANDBOX");
        assertThrows(BizException.class, () -> payments.pay(1L, "VTEST1", "ALIPAY"));
        assertEquals(0, number("SELECT COUNT(*) FROM v_payment_record"));
        assertEquals("PENDING_PAYMENT", jdbc.queryForObject("SELECT status FROM v_order", String.class));
    }
    @Test void officialSandboxReceiptsKeepTestFundingAndRejectSimulatedCompletion(){
        properties.setDemoMode(true);jdbc.update("UPDATE v_order SET demo_mode=1");jdbc.update("INSERT INTO v_pay_config(pay_type,enabled) VALUES('ALIPAY',1)");
        org.mockito.Mockito.when(gateway.ready("ALIPAY")).thenReturn(true);org.mockito.Mockito.when(gateway.environment("ALIPAY")).thenReturn("ALIPAY_SANDBOX");
        org.mockito.Mockito.when(gateway.prepay(org.mockito.ArgumentMatchers.eq("ALIPAY"),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.any())).thenReturn(Map.of("redirectUrl","https://openapi-sandbox.dl.alipaydev.com/test","providerSandbox",true));
        payments.pay(1L,"VTEST1","ALIPAY");String trade=jdbc.queryForObject("SELECT out_trade_no FROM v_payment_record",String.class);
        assertEquals("ALIPAY_SANDBOX",jdbc.queryForObject("SELECT environment FROM v_payment_record",String.class));
        payments.verifiedCallback(new com.heng.payment.ProviderGateway.VerifiedPayment("ALIPAY",trade,"OFFICIAL-TEST",300,true));
        payments.verifiedCallback(new com.heng.payment.ProviderGateway.VerifiedPayment("ALIPAY",trade,"OFFICIAL-TEST",300,true));
        assertEquals("ALIPAY_SANDBOX",jdbc.queryForObject("SELECT funding_source FROM v_order",String.class));assertEquals(1,number("SELECT COUNT(*) FROM v_settlement_record"));
        org.mockito.Mockito.when(gateway.environment("ALIPAY")).thenReturn("PRODUCTION");
        assertThrows(BizException.class,()->payments.verifiedCallback(new com.heng.payment.ProviderGateway.VerifiedPayment("ALIPAY",trade,"OFFICIAL-TEST",300,true)));
    }
    @Test void simulatedConfirmationDisabledByDefault(){
        enableSandbox();properties.setDemoMode(true);
        var sandbox=new com.heng.biz.SandboxPaymentService(jdbc,payments,properties,false);
        assertThrows(BizException.class,()->sandbox.confirm(1L,"VTEST1","WECHAT"));
    }
    @Test void failedProductionPrepayKeepsDurableIntentForRetry(){ jdbc.update("UPDATE v_order SET demo_mode=0");
        org.mockito.Mockito.when(gateway.ready("WECHAT")).thenReturn(true);
        org.mockito.Mockito.when(gateway.prepay(org.mockito.ArgumentMatchers.eq("WECHAT"),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq(300L),org.mockito.ArgumentMatchers.any())).thenThrow(new BizException("network"));
        assertThrows(BizException.class,()->payments.pay(1L,"VTEST1","WECHAT"));
        String trade=jdbc.queryForObject("SELECT out_trade_no FROM v_payment_record",String.class);assertEquals(32,trade.length());assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM v_payment_record",String.class));
        org.mockito.Mockito.when(gateway.prepay(org.mockito.ArgumentMatchers.eq("WECHAT"),org.mockito.ArgumentMatchers.eq(trade),org.mockito.ArgumentMatchers.eq(300L),org.mockito.ArgumentMatchers.any())).thenReturn(Map.of("codeUrl","weixin://test"));
        payments.pay(1L,"VTEST1","WECHAT");assertEquals(1,number("SELECT COUNT(*) FROM v_payment_record"));
        payments.verifiedCallback(new com.heng.payment.ProviderGateway.VerifiedPayment("WECHAT",trade,"PRODUCTION-TX",300,true));
        assertEquals("REAL_EXTERNAL",jdbc.queryForObject("SELECT funding_source FROM v_order",String.class));assertEquals(1000,number("SELECT balance_fen FROM v_member"));
    }
    @Test void switchingChannelMustConfirmOldProductionTradeClosed(){ jdbc.update("UPDATE v_order SET demo_mode=0");
        org.mockito.Mockito.when(gateway.ready("WECHAT")).thenReturn(true);
        org.mockito.Mockito.when(gateway.prepay(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.any())).thenReturn(Map.of("codeUrl","test"));
        payments.pay(1L,"VTEST1","WECHAT");String trade=jdbc.queryForObject("SELECT out_trade_no FROM v_payment_record",String.class);
        org.mockito.Mockito.when(gateway.query("WECHAT",trade)).thenReturn(new com.heng.payment.ProviderGateway.VerifiedPayment("WECHAT",trade,null,0,false));
        org.mockito.Mockito.doThrow(new BizException("close unknown")).when(gateway).close("WECHAT",trade);
        assertThrows(BizException.class,()->payments.pay(1L,"VTEST1","BALANCE"));assertEquals(1000,number("SELECT balance_fen FROM v_member"));
        org.mockito.Mockito.doNothing().when(gateway).close("WECHAT",trade);payments.pay(1L,"VTEST1","BALANCE");assertEquals("CLOSED",jdbc.queryForObject("SELECT status FROM v_payment_record WHERE out_trade_no=?",String.class,trade));assertEquals(700,number("SELECT balance_fen FROM v_member"));
    }
    @Test void demoOrderCannotImplicitlyCreateSandboxTradeInProduction(){
        jdbc.update("UPDATE v_order SET demo_mode=1");org.mockito.Mockito.when(gateway.ready("WECHAT")).thenReturn(true);
        assertThrows(BizException.class,()->payments.pay(1L,"VTEST1","WECHAT"));assertEquals(0,number("SELECT COUNT(*) FROM v_payment_record"));
    }
    @Test void sandboxMissingSecretNeverAdvertisesProductionFallback(){
        properties.setSandboxPaymentEnabled(true);org.mockito.Mockito.when(gateway.ready("WECHAT")).thenReturn(true);
        assertTrue(payments.enabledPayMethods().stream().noneMatch(x->"WECHAT".equals(x.getPayType())));
    }

    @Test void concurrentRequestReplayRunsBusinessOnce() throws Exception {
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var results=race(6,()->dedup.execute(1L,"ORDER","a-request-key-12345","same",()->{calls.incrementAndGet();assets.usePoints(1L,10,"TEST",1L,"DEDUPE-USE");return 42L;}));
        assertTrue(results.stream().allMatch(x->x==42));assertEquals(1,calls.get());assertEquals(90,number("SELECT points FROM v_member"));
        assertThrows(BizException.class,()->dedup.execute(1L,"ORDER","a-request-key-12345","different",()->43L));
    }
    @Test void failedDedupActionDoesNotCacheFailure(){
        assertThrows(BizException.class,()->dedup.execute(1L,"ORDER","a-request-key-12345","same",()->{assets.usePoints(1L,10,"TEST",1L,"DEDUPE-FAIL");throw new BizException("fail");}));
        assertEquals(100,number("SELECT points FROM v_member"));assertEquals(0,number("SELECT COUNT(*) FROM v_request_result"));
        assertEquals(42L,dedup.execute(1L,"ORDER","a-request-key-12345","same",()->42L));
    }
    @Test void profileChangesDoNotOverwriteAssetsOrPassword() throws Exception {
        jdbc.update("UPDATE v_member SET password='secret'");
        var form=new com.heng.domain.VMember();form.setNickname("Updated");form.setEmail("buyer@example.com");form.setBalanceFen(999999L);form.setPoints(999999);form.setPassword("injected");form.setGrowth(999999);form.setStatus(0);
        race(2,()->{assets.usePoints(1L,10,"PROFILE",1L,"PROFILE-POINTS");members.updateProfile(1L,form);return true;});
        assertEquals(90,number("SELECT points FROM v_member"));assertEquals(1000,number("SELECT balance_fen FROM v_member"));assertEquals(1,number("SELECT status FROM v_member"));assertEquals(0,number("SELECT growth FROM v_member"));assertEquals("secret",jdbc.queryForObject("SELECT password FROM v_member",String.class));
    }
    @Test void growthThresholdUpgradesAndRefundDowngrades(){
        jdbc.update("INSERT INTO v_member_level(level_id,name,min_growth,discount_bps,status) VALUES(1,'Base',0,10000,1),(2,'Plus',3,9000,1)");
        assets.addGrowth(1L,3);assertEquals(2,number("SELECT level_id FROM v_member"));assets.reverseReward(1L,3,1L,"REVERSE:1");assets.reverseReward(1L,3,1L,"REVERSE:1");assertEquals(1,number("SELECT level_id FROM v_member"));assertEquals(0,number("SELECT growth FROM v_member"));
    }
    private long paidProductionRefund(){
        jdbc.update("UPDATE v_order SET demo_mode=0");
        org.mockito.Mockito.when(gateway.ready("WECHAT")).thenReturn(true);
        org.mockito.Mockito.when(gateway.prepay(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.any())).thenReturn(Map.of("codeUrl","weixin://test"));
        payments.pay(1L,"VTEST1","WECHAT");String trade=jdbc.queryForObject("SELECT out_trade_no FROM v_payment_record",String.class);
        payments.verifiedCallback(new com.heng.payment.ProviderGateway.VerifiedPayment("WECHAT",trade,"TX-PROD",300,true));return requestApprovedRefund();
    }
    @Test void pendingProductionRefundNeverCompensatesUntilConfirmed(){
        long id=paidProductionRefund();
        org.mockito.Mockito.when(gateway.queryRefund(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(new com.heng.payment.ProviderGateway.RefundResult("PROCESSING","RF1"));
        aftersales.process(id);assertEquals("PROCESSING",jdbc.queryForObject("SELECT status FROM v_refund",String.class));assertEquals("PAID",jdbc.queryForObject("SELECT status FROM v_order",String.class));assertEquals(9,number("SELECT available_stock FROM v_stock"));assertEquals(103,number("SELECT points FROM v_member"));
        org.mockito.Mockito.when(gateway.queryRefund(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(new com.heng.payment.ProviderGateway.RefundResult("SUCCESS","RF1"));
        aftersales.process(id);aftersales.process(id);assertEquals("REFUNDED",jdbc.queryForObject("SELECT status FROM v_order",String.class));assertEquals(10,number("SELECT available_stock FROM v_stock"));assertEquals(100,number("SELECT points FROM v_member"));assertEquals(1000,number("SELECT balance_fen FROM v_member"));
    }
    @Test void failedRefundRetriesWithSameProviderRefundNumber(){
        long id=paidProductionRefund();
        org.mockito.Mockito.when(gateway.queryRefund(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenThrow(new BizException("not confirmed"));
        org.mockito.Mockito.when(gateway.refund(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyLong())).thenThrow(new BizException("timeout"));
        assertThrows(BizException.class,()->aftersales.process(id));assertEquals(9,number("SELECT available_stock FROM v_stock"));
        String trade=jdbc.queryForObject("SELECT out_trade_no FROM v_payment_record",String.class);
        org.mockito.Mockito.doReturn(new com.heng.payment.ProviderGateway.RefundResult("SUCCESS","RF1")).when(gateway).refund("WECHAT",trade,"RVTEST1",300);
        aftersales.process(id);aftersales.process(id);org.mockito.Mockito.verify(gateway,org.mockito.Mockito.times(2)).refund("WECHAT",trade,"RVTEST1",300);assertEquals(2,number("SELECT attempts FROM v_refund"));assertEquals(10,number("SELECT available_stock FROM v_stock"));
    }
    @Test void refundResubmissionAfterShipmentRequiresReturn(){
        payments.pay(1L,"VTEST1","BALANCE");aftersales.request(1L,1L,"first");aftersales.cancel(1L,1L);jdbc.update("UPDATE v_order SET status='SHIPPED'");aftersales.request(1L,1L,"second");assertEquals("RETURN_REFUND",jdbc.queryForObject("SELECT refund_type FROM v_refund",String.class));assertEquals("SHIPPED",jdbc.queryForObject("SELECT original_status FROM v_refund",String.class));
    }
    private long requestApprovedRefund(){
        aftersales.request(1L,1L,"test return");long id=jdbc.queryForObject("SELECT refund_id FROM v_refund",Long.class);jdbc.update("UPDATE v_refund SET status='APPROVED' WHERE refund_id=?",id);return id;
    }
    @Test void fullWalletRefundRestoresAssetsStockAndCouponsOnce() throws Exception {
        jdbc.update("INSERT INTO v_member_coupon(receive_id,member_id,status,expire_time,locked_order_id) VALUES(1,1,'LOCKED',DATEADD('DAY',1,CURRENT_TIMESTAMP),1)");jdbc.update("UPDATE v_order SET coupon_receive_id=1,points_used=10");assets.usePoints(1L,10,"ORDER",1L,"ORDER:1");payments.pay(1L,"VTEST1","BALANCE");
        long id=requestApprovedRefund();race(3,()->{aftersales.process(id);return true;});aftersales.process(id);
        assertEquals(1000,number("SELECT balance_fen FROM v_member"));assertEquals(100,number("SELECT points FROM v_member"));assertEquals(0,number("SELECT growth FROM v_member"));assertEquals(10,number("SELECT available_stock FROM v_stock"));assertEquals(0,number("SELECT sales_count FROM v_goods_sku"));assertEquals(2,number("SELECT COUNT(*) FROM v_member_balance_ledger"));assertEquals("AVAILABLE",jdbc.queryForObject("SELECT status FROM v_member_coupon",String.class));assertEquals("REFUNDED",jdbc.queryForObject("SELECT status FROM v_order",String.class));assertEquals("SUCCESS",jdbc.queryForObject("SELECT status FROM v_refund",String.class));
    }
    @Test void approvedRefundStillCompensatesDisabledMemberAndSpentRewards(){
        payments.pay(1L,"VTEST1","BALANCE");long id=requestApprovedRefund();jdbc.update("UPDATE v_member SET status=0,points=0");aftersales.process(id);assertEquals(1000,number("SELECT balance_fen FROM v_member"));assertEquals(-3,number("SELECT points FROM v_member"));assertEquals("SUCCESS",jdbc.queryForObject("SELECT status FROM v_refund",String.class));
    }
    @Test void refundOwnershipCancellationAndResubmission(){
        payments.pay(1L,"VTEST1","BALANCE");assertThrows(BizException.class,()->aftersales.request(2L,1L,"not owned"));aftersales.request(1L,1L,"first");aftersales.cancel(1L,1L);aftersales.request(1L,1L,"again");assertEquals(1,number("SELECT COUNT(*) FROM v_refund"));assertEquals("REQUESTED",jdbc.queryForObject("SELECT status FROM v_refund",String.class));
    }
    @Test void returnedGoodsRequireAcceptedReturnBeforeRestocking(){
        payments.pay(1L,"VTEST1","BALANCE");jdbc.update("UPDATE v_order SET status='SHIPPED'");aftersales.request(1L,1L,"return");assertThrows(BizException.class,()->aftersales.returnShipment(1L,1L,"Carrier","TRACK"));jdbc.update("UPDATE v_refund SET status='AWAITING_RETURN'");aftersales.returnShipment(1L,1L,"Carrier","TRACK");jdbc.update("UPDATE v_refund SET status='APPROVED',return_received=1");aftersales.process(jdbc.queryForObject("SELECT refund_id FROM v_refund",Long.class));assertEquals(10,number("SELECT available_stock FROM v_stock"));
    }
    @Test void invoiceAndReceiptBlockedDuringRefund(){
        payments.pay(1L,"VTEST1","BALANCE");aftersales.invoice(1L,1L,"Buyer",null,"a@example.com","PERSONAL");assertThrows(BizException.class,()->aftersales.invoice(2L,1L,"Buyer",null,"a@example.com","PERSONAL"));jdbc.update("UPDATE v_order SET status='SHIPPED'");aftersales.request(1L,1L,"return");assertThrows(BizException.class,()->aftersales.invoice(1L,1L,"Buyer",null,"a@example.com","PERSONAL"));
    }
    @Test void sandboxRefundCompletesWithoutRealGatewayAndAcceptsPaidReplay() throws Exception {
        enableSandbox();payments.pay(1L,"VTEST1","WECHAT");var payload=Map.<String,Object>of("amountFen",300,"success",true);payments.callback("WECHAT","PVTEST1-WECHAT","TX1",true,signature("WECHAT",300),payload);long id=requestApprovedRefund();aftersales.process(id);payments.callback("WECHAT","PVTEST1-WECHAT","TX1",true,signature("WECHAT",300),payload);assertEquals(1000,number("SELECT balance_fen FROM v_member"));assertEquals(100,number("SELECT points FROM v_member"));assertTrue(jdbc.queryForObject("SELECT provider_id FROM v_refund",String.class).startsWith("SIMULATED:"));
    }
    @Test void untrustedReceiptCannotConvertSandboxPaymentToProduction(){
        enableSandbox();payments.pay(1L,"VTEST1","WECHAT");assertThrows(BizException.class,()->payments.verifiedCallback(new com.heng.payment.ProviderGateway.VerifiedPayment("WECHAT","PVTEST1-WECHAT","TX1",300,true)));assertEquals("PENDING_PAYMENT",jdbc.queryForObject("SELECT status FROM v_order",String.class));
    }
    @Test
    void repeatedPointsEventChangesAssetsOnce() {
        assets.addPoints(1L, 20, "TEST", 1L, "REWARD:1", "test");
        assets.addPoints(1L, 20, "TEST", 1L, "REWARD:1", "test");
        assets.usePoints(1L, 10, "TEST", 1L, "USE:1");
        assets.usePoints(1L, 10, "TEST", 1L, "USE:1");
        assertEquals(110, number("SELECT points FROM v_member WHERE member_id = 1"));
        assertEquals(2, number("SELECT COUNT(*) FROM v_member_point_ledger"));
    }

    @Test
    void simultaneousDepositEventIsRecordedOnce() throws Exception {
        race(8, () -> { assets.deposit(1L, 100, "DEPOSIT:1"); return true; });
        assertEquals(1100, number("SELECT balance_fen FROM v_member WHERE member_id = 1"));
        assertEquals(1, number("SELECT COUNT(*) FROM v_member_balance_ledger"));
        assertEquals(1100, number("SELECT balance_after_fen FROM v_member_balance_ledger"));
    }

    @Test
    void ledgerFailureRollsBackAssetChange() {
        assertThrows(Exception.class, () -> assets.deposit(1L, 100, "X".repeat(101)));
        assertEquals(1000, number("SELECT balance_fen FROM v_member WHERE member_id = 1"));
        assertEquals(0, number("SELECT COUNT(*) FROM v_member_balance_ledger"));
    }

    @Test
    void insufficientBalanceDoesNotLeaveLedger() {
        assertThrows(BizException.class, () -> assets.payWithBalance(1L, 1001, 1L, "PAY:1"));
        assertEquals(1000, number("SELECT balance_fen FROM v_member WHERE member_id = 1"));
        assertEquals(0, number("SELECT COUNT(*) FROM v_member_balance_ledger"));
    }

    @Test
    void simultaneousBalancePaymentsSettleAndDeductOnce() throws Exception {
        List<Boolean> results = race(2, () -> {
            try { payments.pay(1L, "VTEST1", "balance"); return true; }
            catch (BizException expected) { return false; }
        });
        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(700, number("SELECT balance_fen FROM v_member WHERE member_id = 1"));
        assertEquals(103, number("SELECT points FROM v_member WHERE member_id = 1"));
        assertEquals(0, number("SELECT locked_stock FROM v_stock WHERE sku_id = 1"));
        assertEquals(1, number("SELECT COUNT(*) FROM v_settlement_record"));
        assertEquals(1, number("SELECT COUNT(*) FROM v_member_balance_ledger"));
        assertEquals("PAID", jdbc.queryForObject("SELECT status FROM v_order WHERE order_id = 1", String.class));
    }

    @Test
    void stockFailureRollsBackBalanceAndPayment() {
        jdbc.update("UPDATE v_stock SET locked_stock = 0");
        assertThrows(BizException.class, () -> payments.pay(1L, "VTEST1", "BALANCE"));
        assertEquals(1000, number("SELECT balance_fen FROM v_member WHERE member_id = 1"));
        assertEquals(0, number("SELECT COUNT(*) FROM v_payment_record"));
        assertEquals(0, number("SELECT COUNT(*) FROM v_member_balance_ledger"));
        assertEquals("PENDING_PAYMENT", jdbc.queryForObject("SELECT status FROM v_order", String.class));
    }

    @Test
    void sandboxAndDepositAreDisabledByDefault() {
        assertThrows(BizException.class, () -> payments.pay(1L, "VTEST1", "WECHAT"));
        assertThrows(BizException.class, () -> members.deposit(1L, 100));
        assertEquals(1000, number("SELECT balance_fen FROM v_member WHERE member_id = 1"));
        assertEquals(List.of("BALANCE"), payments.enabledPayMethods().stream().map(p -> p.getPayType()).toList());
    }

    @Test
    void authenticatedCallbackIsIdempotentAndRejectsForgedReplay() throws Exception {
        enableSandbox();
        payments.pay(1L, "VTEST1", "WECHAT");
        String signature = signature("WECHAT", 300);
        Map<String, Object> payload = Map.of("amountFen", 300, "success", true);
        payments.callback("WECHAT", "PVTEST1-WECHAT", "TX1", true, signature, payload);
        payments.callback("WECHAT", "PVTEST1-WECHAT", "TX1", true, signature, payload);
        assertThrows(BizException.class, () -> payments.callback("WECHAT", "PVTEST1-WECHAT", "TX1",
                true, "forged", payload));
        assertEquals(1, number("SELECT COUNT(*) FROM v_settlement_record"));
        assertEquals(103, number("SELECT points FROM v_member WHERE member_id = 1"));
        assertEquals(1000, number("SELECT balance_fen FROM v_member WHERE member_id = 1"));
    }

    @Test
    void cancelledOrderCannotBecomeSuccessfulPayment() throws Exception {
        enableSandbox();
        payments.pay(1L, "VTEST1", "WECHAT");
        jdbc.update("UPDATE v_order SET status = 'CANCELLED'");
        String signature = signature("WECHAT", 300);
        assertThrows(BizException.class, () -> payments.callback("WECHAT", "PVTEST1-WECHAT", "TX1",
                true, signature, Map.of("amountFen", 300, "success", true)));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM v_payment_record", String.class));
        assertEquals(0, number("SELECT COUNT(*) FROM v_settlement_record"));
    }

    @Test
    void callbackRequiresAmountAndMatchingChannel() throws Exception {
        enableSandbox();
        payments.pay(1L, "VTEST1", "WECHAT");
        String signature = signature("WECHAT", 300);
        assertThrows(BizException.class, () -> payments.callback("WECHAT", "PVTEST1-WECHAT", "TX1",
                true, signature, Map.of("success", true)));
        assertThrows(BizException.class, () -> payments.callback("ALIPAY", "PVTEST1-WECHAT", "TX1",
                true, signature, Map.of("amountFen", 300, "success", true)));
        assertThrows(BizException.class, () -> payments.callback("WECHAT", "PVTEST1-WECHAT", "TX1",
                true, signature, Map.of("amountFen", 299, "success", true)));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM v_payment_record", String.class));
    }

    @Test
    void addressCreationAndEditingPreserveExplicitDefault() {
        VMemberAddress first = members.saveAddress(1L, null, addressForm());
        VMemberAddress second = members.saveAddress(1L, null, addressForm());
        assertEquals(1, number("SELECT is_default FROM v_member_address WHERE address_id = " + first.getAddressId()));
        assertEquals(0, second.getIsDefault());
        members.saveAddress(1L, first.getAddressId(), addressForm());
        assertEquals(1, number("SELECT COUNT(*) FROM v_member_address WHERE is_default = 1"));
        members.setDefaultAddress(1L, second.getAddressId());
        members.deleteAddress(1L, second.getAddressId());
        assertEquals(1, number("SELECT is_default FROM v_member_address WHERE address_id = " + first.getAddressId()));
    }

    @Test
    void simultaneousAddressCreationLeavesOneDefault() throws Exception {
        race(4, () -> members.saveAddress(1L, null, addressForm()));
        assertEquals(4, number("SELECT COUNT(*) FROM v_member_address"));
        assertEquals(1, number("SELECT COUNT(*) FROM v_member_address WHERE is_default = 1"));
    }

    @Test
    void addressMutationsCannotTargetAnotherMember() {
        VMemberAddress address = members.saveAddress(1L, null, addressForm());
        jdbc.update("INSERT INTO v_member(member_id, username) VALUES (2, 'other')");
        assertThrows(BizException.class, () -> members.saveAddress(2L, address.getAddressId(), addressForm()));
        assertThrows(BizException.class, () -> members.setDefaultAddress(2L, address.getAddressId()));
        assertThrows(BizException.class, () -> members.deleteAddress(2L, address.getAddressId()));
        assertEquals(1, number("SELECT COUNT(*) FROM v_member_address WHERE member_id = 1"));
    }

    @Test
    void deletingFavoriteIsIdempotentAndMemberScoped() {
        jdbc.update("INSERT INTO v_member_favorite(member_id, spu_id) VALUES (1, 1), (2, 1)");
        members.removeFavorite(1L, 1L);
        members.removeFavorite(1L, 1L);
        assertEquals(0, number("SELECT COUNT(*) FROM v_member_favorite WHERE member_id = 1"));
        assertEquals(1, number("SELECT COUNT(*) FROM v_member_favorite WHERE member_id = 2"));
    }

    private VMemberAddress addressForm() {
        VMemberAddress address = new VMemberAddress();
        address.setContactName("Buyer");
        address.setPhone("13800000000");
        address.setProvince("Province");
        address.setCity("City");
        address.setDistrict("District");
        address.setDetail("Street 1");
        return address;
    }

    private long number(String sql) { return jdbc.queryForObject(sql, Long.class); }

    private void enableSandbox() {
        properties.setSandboxPaymentEnabled(true);
        properties.setPaymentCallbackSecret("test-secret");
    }

    private String signature(String channel, long amount) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((channel + "|PVTEST1-WECHAT|TX1|" + amount + "|true")
                .getBytes(StandardCharsets.UTF_8)));
    }

    private <T> List<T> race(int count, Supplier<T> work) throws Exception {
        var executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                    return work.get();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<T> result = new ArrayList<>();
            for (Future<T> future : futures) result.add(future.get(20, TimeUnit.SECONDS));
            return result;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
