package com.heng.biz;

import com.heng.config.BizException;
import com.heng.config.TradeProperties;
import com.heng.domain.*;
import com.heng.mapper.AtomicWriteMapper;
import com.heng.service.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;

/**
 * 支付与支付后结算。回调可重复投递，靠订单状态条件更新和流水幂等键保证只结算一次。
 */
@Service
public class PaymentService {
    @org.springframework.beans.factory.annotation.Autowired private com.heng.payment.ProviderGateway gateway;
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.transaction.support.TransactionTemplate transactions;
    private final OrderService orderService;
    private final VOrderService vOrderService;
    private final MemberAssetService memberAssetService;
    private final StockService stockService;
    private final AtomicWriteMapper atomic;
    private final VPaymentRecordService paymentService;
    private final VPayConfigService payConfigService;
    private final VSettlementRecordService settlementService;
    private final VPushRecordService pushRecordService;
    private final TradeProperties properties;

    public PaymentService(OrderService orderService, VOrderService vOrderService,
                          MemberAssetService memberAssetService,
                          StockService stockService, AtomicWriteMapper atomic,
                          VPaymentRecordService paymentService, VPayConfigService payConfigService,
                          VSettlementRecordService settlementService, VPushRecordService pushRecordService,
                          TradeProperties properties) {
        this.orderService = orderService;
        this.vOrderService = vOrderService;
        this.memberAssetService = memberAssetService;
        this.stockService = stockService;
        this.atomic = atomic;
        this.paymentService = paymentService;
        this.payConfigService = payConfigService;
        this.settlementService = settlementService;
        this.pushRecordService = pushRecordService;
        this.properties = properties;
    }

    public Map<String,Object> pay(Long memberId,String orderNo,String payType){
        var prepared=transactions.execute(s->preparePayment(memberId,orderNo,payType));
        if(prepared.containsKey("providerTrade")){
            String channel=(String)prepared.get("payType");String trade=(String)prepared.remove("providerTrade");long amount=((Number)prepared.remove("providerAmount")).longValue();java.time.Instant expires=(java.time.Instant)prepared.remove("providerExpires");
            prepared.put("prepay",transactions.execute(s->{
                var payment=paymentService.lambdaQuery().eq(VPaymentRecord::getOutTradeNo,trade).one();var order=atomic.lockOrder(payment.getOrderId());
                payment=paymentService.lambdaQuery().eq(VPaymentRecord::getOutTradeNo,trade).last("FOR UPDATE").one();
                if(!"PENDING_PAYMENT".equals(order.getStatus())||orderService.isExpired(order)||!"PENDING".equals(payment.getStatus()))throw new BizException("订单状态已变更，请刷新");
                return gateway.prepay(channel,trade,amount,expires);
            }));
        }
        return prepared;
    }
    private Map<String, Object> preparePayment(Long memberId, String orderNo, String payType) {
        payType = normalizePayType(payType);
        VOrder order = vOrderService.lambdaQuery()
                .eq(VOrder::getOrderNo, orderNo).eq(VOrder::getMemberId, memberId).last("FOR UPDATE").one();
        if (order == null) throw new BizException("订单不存在");
        if ("PAID".equals(order.getStatus())) throw new BizException("订单已支付");
        if (!"PENDING_PAYMENT".equals(order.getStatus())) throw new BizException("订单当前状态不可支付");
        if (orderService.isExpired(order)) throw new BizException("订单已超过支付期限，请重新下单");
        requireEnabled(payType);
        if ("ALIPAY_SANDBOX".equals(gateway.environment(payType)) && !Boolean.TRUE.equals(order.getDemoMode())) throw new BizException("正式订单不能使用沙盒支付，请重新创建测试订单");
        if (Boolean.TRUE.equals(order.getDemoMode()) && !"BALANCE".equals(payType) && !properties.isSandboxPaymentEnabled() && !"ALIPAY_SANDBOX".equals(gateway.environment(payType))) throw new BizException("演示订单仅可使用余额或显式支付沙箱");
        if (!"BALANCE".equals(payType) && properties.isSandboxPaymentEnabled()) requireSandbox();
        if (!"BALANCE".equals(payType) && !properties.isSandboxPaymentEnabled() && !gateway.ready(payType)) throw new BizException("真实支付渠道尚未配置完成");

        closeOtherChannels(order,payType);
        VPaymentRecord payment = upsertPendingPayment(order, payType);
        if ("SUCCESS".equals(payment.getStatus())) {
            return result(order.getOrderNo(), payType, "SUCCESS", null);
        }
        if ("BALANCE".equals(payType)) {
            memberAssetService.payWithBalance(memberId, order.getPayAmountFen(), order.getOrderId(),
                    "BALANCE_PAY:" + order.getOrderNo());
            markPaymentSuccess(payment, "BAL-" + payment.getPaymentId(), Map.of("channel", "BALANCE"));
            settle(order, "BALANCE");
            return result(order.getOrderNo(), payType, "SUCCESS", null);
        }
        if (List.of("PRODUCTION","ALIPAY_SANDBOX").contains(payment.getEnvironment())) {
            var expires=order.getCreateTime().toInstant().plusSeconds(properties.getOrderTimeoutMinutes()*60L);
            var prepared=result(orderNo,payType,"PENDING",null);prepared.put("providerTrade",payment.getOutTradeNo());prepared.put("providerAmount",payment.getAmountFen());prepared.put("providerExpires",expires);return prepared;
        }
        // Explicit sandbox bridge.
        Map<String, Object> prepay = new LinkedHashMap<>();
        prepay.put("outTradeNo", payment.getOutTradeNo());
        prepay.put("amountFen", order.getPayAmountFen());
        prepay.put("sandbox", true);
        return result(order.getOrderNo(), payType, "PENDING", prepay);
    }

    @Transactional(rollbackFor = Exception.class)
    public void callback(String payType, String outTradeNo, String transactionId, boolean success,
                        String signature, Map<String, Object> payload) {
        requireSandbox();
        payType = normalizePayType(payType);
        if ("BALANCE".equals(payType)) throw new BizException("余额支付不接受外部回调");
        VPaymentRecord payment = paymentService.lambdaQuery()
                .eq(VPaymentRecord::getOutTradeNo, outTradeNo).one();
        if (payment == null) throw new BizException("支付流水不存在");
        // Lock orders before payment rows in both payment paths to avoid reversing lock order.
        VOrder order = atomic.lockOrder(payment.getOrderId());
        if (order == null) throw new BizException("订单不存在");
        payment = paymentService.lambdaQuery().eq(VPaymentRecord::getOutTradeNo, outTradeNo)
                .last("FOR UPDATE").one();
        if (!List.of("SANDBOX","DEMO").contains(payment.getEnvironment())) throw new BizException("沙箱通知不能处理生产支付流水");
        verifyCallback(payType, outTradeNo, transactionId, success, payment, signature, payload);
        if (List.of("SUCCESS","REFUNDED").contains(payment.getStatus())) {
            if (!Objects.equals(payment.getTransactionId(), transactionId) || !success) {
                throw new BizException("支付回调与已处理流水不一致");
            }
            return;
        }
        if (!"PENDING_PAYMENT".equals(order.getStatus())) throw new BizException("订单当前状态不可支付");
        if (orderService.isExpired(order)) throw new BizException("订单已超过支付期限，请重新下单");
        if (!Objects.equals(order.getPayAmountFen(), payment.getAmountFen())) {
            throw new BizException("订单金额与支付流水不一致");
        }
        if (!success) {
            payment.setStatus("FAILED");
            payment.setCallbackPayload(payload);
            paymentService.updateById(payment);
            return;
        }
        markPaymentSuccess(payment, transactionId, payload);
        settle(order, payType);
        vOrderService.lambdaUpdate().eq(VOrder::getOrderId,order.getOrderId()).set(VOrder::getFundingSource,"SANDBOX".equals(payment.getEnvironment())?"SANDBOX":"DEMO").update();
    }

    /** Sandbox signature: HMAC-SHA256(payType|outTradeNo|transactionId|amountFen|success), hex encoded. */
    private void verifyCallback(String payType, String outTradeNo, String transactionId, boolean success,
                                VPaymentRecord payment, String signature, Map<String, Object> payload) {
        String secret = properties.getPaymentCallbackSecret();
        if (secret == null || secret.isBlank()) {
            throw new BizException("支付回调未配置验签密钥");
        }
        if (!payment.getPayType().equalsIgnoreCase(payType)) {
            throw new BizException("支付渠道不匹配");
        }
        if (transactionId == null || transactionId.isBlank() || transactionId.length() > 128
                || !(payload.get("success") instanceof Boolean)) {
            throw new BizException("支付回调参数不合法");
        }
        long amountFen;
        try {
            amountFen = new java.math.BigDecimal(String.valueOf(payload.get("amountFen"))).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new BizException("支付金额不合法");
        }
        if (amountFen != payment.getAmountFen()) throw new BizException("支付金额不匹配");
        String raw = payType + "|" + outTradeNo + "|" + transactionId + "|" + amountFen + "|" + success;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = java.util.HexFormat.of().formatHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
            if (signature == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    signature.trim().toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
                throw new BizException("支付回调签名无效");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("支付回调验签失败");
        }
    }

    /** 支付成功后的唯一结算入口：扣减锁定库存、核销优惠券、发积分和成长值、生成结算记录。 */
    private void settle(VOrder order, String payType) {
        if(atomic.lockMember(order.getMemberId())==null)throw new BizException("会员不存在");
        if (atomic.markOrderPaid(order.getOrderId(), payType) == 0) {
            throw new BizException("订单状态已变更，支付已回滚");
        }
        for (VOrderItem item : orderService.items(order.getOrderId())) {
            Long skuId = orderService.snapshotLong(item, "skuId");
            Long spuId = orderService.snapshotLong(item, "spuId");
            stockService.consume(skuId, item.getQuantity(), "ORDER_PAID", order.getOrderId(), order.getMemberId());
            atomic.increaseSkuSales(skuId, item.getQuantity());
            if (spuId != null) atomic.increaseSpuSales(spuId, item.getQuantity());
        }
        if (order.getCouponReceiveId() != null) {
            if (atomic.useCoupon(order.getCouponReceiveId(), order.getOrderId()) == 0) {
                throw new BizException("优惠券核销失败");
            }
        }
        int reward = Math.toIntExact(order.getPayAmountFen() / 100);
        memberAssetService.addPoints(order.getMemberId(), reward, "ORDER_REWARD", order.getOrderId(),
                "ORDER_REWARD:" + order.getOrderNo(), "下单支付赠送积分");
        memberAssetService.addGrowth(order.getMemberId(), reward);
        vOrderService.lambdaUpdate().eq(VOrder::getOrderId,order.getOrderId()).set(VOrder::getFundingSource,
                Boolean.TRUE.equals(order.getDemoMode())?"DEMO":("BALANCE".equals(payType)?"WALLET":(properties.isSandboxPaymentEnabled()?"SANDBOX":"REAL_EXTERNAL"))).update();
        settleRecord(order);
        push(order.getMemberId(), "ORDER", "支付成功",
                "订单 " + order.getOrderNo() + " 已支付 ¥" + String.format("%.2f", order.getPayAmountFen() / 100.0)
                        + "，等待发货。",
                order.getOrderId());
    }

    private VPaymentRecord upsertPendingPayment(VOrder order, String payType) {
        String outTradeNo = "P" + order.getOrderNo() + "-" + payType;
        if (!properties.isSandboxPaymentEnabled() && !"BALANCE".equals(payType)) {
            var pending=paymentService.lambdaQuery().eq(VPaymentRecord::getOrderId,order.getOrderId()).eq(VPaymentRecord::getPayType,payType).eq(VPaymentRecord::getEnvironment,java.util.Objects.requireNonNullElse(gateway.environment(payType),"PRODUCTION")).eq(VPaymentRecord::getStatus,"PENDING").last("FOR UPDATE").one();
            if(pending!=null)return pending;
            outTradeNo="P"+java.util.UUID.randomUUID().toString().replace("-","").substring(0,31);
        }
        VPaymentRecord existing = paymentService.lambdaQuery()
                .eq(VPaymentRecord::getOutTradeNo, outTradeNo).one();
        if (existing != null) return existing;
        VPaymentRecord payment = new VPaymentRecord();
        payment.setOrderId(order.getOrderId());
        payment.setOrderNo(order.getOrderNo());
        payment.setPayType(payType);
        payment.setOutTradeNo(outTradeNo);
        payment.setAmountFen(order.getPayAmountFen());
        payment.setStatus("PENDING");
        payment.setEnvironment("ALIPAY_SANDBOX".equals(gateway.environment(payType))&&!properties.isSandboxPaymentEnabled()?"ALIPAY_SANDBOX":(Boolean.TRUE.equals(order.getDemoMode())?"DEMO":("BALANCE".equals(payType)?"WALLET":(properties.isSandboxPaymentEnabled()?"SANDBOX":"PRODUCTION"))));
        try {
            paymentService.save(payment);
        } catch (DuplicateKeyException e) {
            return paymentService.lambdaQuery().eq(VPaymentRecord::getOutTradeNo, outTradeNo).one();
        }
        return payment;
    }

    private void markPaymentSuccess(VPaymentRecord payment, String transactionId, Map<String, Object> payload) {
        payment.setStatus("SUCCESS");
        payment.setTransactionId(transactionId);
        payment.setCallbackPayload(payload);
        payment.setPaidTime(new java.util.Date());
        try {
            paymentService.updateById(payment);
        } catch (DuplicateKeyException e) {
            throw new BizException("支付回调重复，已忽略");
        }
    }

    private void requireEnabled(String payType) {
        VPayConfig config = payConfigService.lambdaQuery().eq(VPayConfig::getPayType, payType).one();
        if (config == null || config.getEnabled() == null || config.getEnabled() != 1) {
            throw new BizException("该支付方式未开启");
        }
    }

    private void settleRecord(VOrder order) {
        VSettlementRecord record = new VSettlementRecord();
        record.setOrderId(order.getOrderId());
        record.setOrderNo(order.getOrderNo());
        record.setAmountFen(order.getPayAmountFen());
        record.setStatus("PENDING");
        settlementService.save(record);
    }

    private void push(Long memberId, String type, String title, String content, Long bizId) {
        VPushRecord record = new VPushRecord();
        record.setMemberId(memberId);
        record.setPushType(type);
        record.setTitle(title);
        record.setContent(content);
        record.setBizId(bizId);
        pushRecordService.save(record);
    }

    private Map<String, Object> result(String orderNo, String payType, String status, Map<String, Object> prepay) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("orderNo", orderNo);
        map.put("payType", payType);
        map.put("status", status);
        if (prepay != null) map.put("prepay", prepay);
        return map;
    }

    public String paymentEnvironment(String payType) { return properties.isSandboxPaymentEnabled() ? "SIMULATED" : gateway.environment(payType); }

    public List<VPayConfig> enabledPayMethods() {
        return payConfigService.lambdaQuery().eq(VPayConfig::getEnabled, 1).list().stream()
                .filter(config -> "BALANCE".equals(config.getPayType())
                        || ((properties.isSandboxPaymentEnabled() ? (properties.getPaymentCallbackSecret()!=null && !properties.getPaymentCallbackSecret().isBlank()) : ((!properties.isDemoMode()||"ALIPAY_SANDBOX".equals(gateway.environment(config.getPayType()))) && gateway.ready(config.getPayType()))) && ("WECHAT".equals(config.getPayType())
                        || "ALIPAY".equals(config.getPayType()))))
                .toList();
    }

    @Transactional(rollbackFor=Exception.class)
    public void verifiedCallback(com.heng.payment.ProviderGateway.VerifiedPayment receipt) {
        VPaymentRecord payment=paymentService.lambdaQuery().eq(VPaymentRecord::getOutTradeNo,receipt.outTradeNo()).one();
        if(payment==null)throw new BizException("支付流水不存在");
        VOrder order=atomic.lockOrder(payment.getOrderId());
        payment=paymentService.lambdaQuery().eq(VPaymentRecord::getPaymentId,payment.getPaymentId()).last("FOR UPDATE").one();
        if(!List.of("PRODUCTION","ALIPAY_SANDBOX").contains(payment.getEnvironment())||!payment.getEnvironment().equals(java.util.Objects.requireNonNullElse(gateway.environment(receipt.channel()),"PRODUCTION"))||!payment.getPayType().equals(receipt.channel())||order==null)throw new BizException("支付回执与生产流水不匹配");
        if ("ALIPAY_SANDBOX".equals(payment.getEnvironment()) && !Boolean.TRUE.equals(order.getDemoMode())) throw new BizException("沙盒回执不能结算正式订单");
        if(!receipt.paid())return;
        if(payment.getAmountFen()!=receipt.amountFen()||order.getPayAmountFen()!=receipt.amountFen())throw new BizException("支付回执金额不匹配");
        if(receipt.transactionId()==null||receipt.transactionId().isBlank())throw new BizException("缺少渠道交易号");
        if(List.of("SUCCESS","REFUNDED").contains(payment.getStatus())){if(!receipt.transactionId().equals(payment.getTransactionId()))throw new BizException("渠道交易号不一致");return;}
        if(!"PENDING_PAYMENT".equals(order.getStatus()))throw new BizException("订单已关闭，渠道资金需对账处理");
        markPaymentSuccess(payment,receipt.transactionId(),Map.of("verified",true,"channel",receipt.channel()));
        settle(order,receipt.channel());
        vOrderService.lambdaUpdate().eq(VOrder::getOrderId,order.getOrderId()).set(VOrder::getFundingSource,"ALIPAY_SANDBOX".equals(payment.getEnvironment())?"ALIPAY_SANDBOX":"REAL_EXTERNAL").update();
    }
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> refresh(Long memberId,String orderNo) {
        VOrder order=vOrderService.lambdaQuery().eq(VOrder::getOrderNo,orderNo).eq(VOrder::getMemberId,memberId).last("FOR UPDATE").one();
        if(order==null)throw new BizException("订单不存在");
        var rows=paymentService.lambdaQuery().eq(VPaymentRecord::getOrderId,order.getOrderId()).in(VPaymentRecord::getEnvironment,List.of("PRODUCTION","ALIPAY_SANDBOX")).eq(VPaymentRecord::getStatus,"PENDING").list();
        for(var row:rows)verifiedCallback(gateway.query(row.getPayType(),row.getOutTradeNo()));
        return Map.of("status",atomic.lockOrder(order.getOrderId()).getStatus());
    }

    private void closeOtherChannels(VOrder order,String chosen){
        var pending=paymentService.lambdaQuery().eq(VPaymentRecord::getOrderId,order.getOrderId()).in(VPaymentRecord::getEnvironment,List.of("PRODUCTION","ALIPAY_SANDBOX")).eq(VPaymentRecord::getStatus,"PENDING").ne(VPaymentRecord::getPayType,chosen).last("FOR UPDATE").list();
        for(var payment:pending){var receipt=gateway.query(payment.getPayType(),payment.getOutTradeNo());if(receipt.paid())throw new BizException("其他渠道已支付，请刷新支付状态");gateway.close(payment.getPayType(),payment.getOutTradeNo());paymentService.lambdaUpdate().eq(VPaymentRecord::getPaymentId,payment.getPaymentId()).set(VPaymentRecord::getStatus,"CLOSED").update();}
    }
    public void closeBeforeCancel(VOrder order){
        var pending=paymentService.lambdaQuery().eq(VPaymentRecord::getOrderId,order.getOrderId()).in(VPaymentRecord::getEnvironment,List.of("PRODUCTION","ALIPAY_SANDBOX")).eq(VPaymentRecord::getStatus,"PENDING").list();
        for(var payment:pending){var receipt=gateway.query(payment.getPayType(),payment.getOutTradeNo());if(receipt.paid())throw new BizException("渠道已支付，请先刷新支付状态");gateway.close(payment.getPayType(),payment.getOutTradeNo());}
    }
    private void requireSandbox() {
        if (!properties.isSandboxPaymentEnabled()) throw new BizException("第三方支付暂未接入");
        if (properties.getPaymentCallbackSecret() == null || properties.getPaymentCallbackSecret().isBlank()) {
            throw new BizException("支付沙箱未配置验签密钥");
        }
    }

    private String normalizePayType(String payType) {
        String normalized = payType == null ? "" : payType.trim().toUpperCase(Locale.ROOT);
        if (!List.of("BALANCE", "WECHAT", "ALIPAY").contains(normalized)) {
            throw new BizException("支付方式不合法");
        }
        return normalized;
    }
}
