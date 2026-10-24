package com.heng.biz;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heng.config.BizException;
import com.heng.config.TradeProperties;
import com.heng.domain.*;
import com.heng.mapper.AtomicWriteMapper;
import com.heng.service.*;
import com.heng.vo.OrderDetailVO;
import com.heng.vo.OrderVO;
import com.heng.vo.PageResult;
import com.heng.vo.QuoteVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * C 端下单、取消和收货。所有金额与库存判定都在服务端完成，客户端只提交选择项。
 */
@Service
public class OrderService {
    @org.springframework.beans.factory.annotation.Autowired @org.springframework.context.annotation.Lazy private AftersalesService refundGuard;
    @org.springframework.beans.factory.annotation.Autowired @org.springframework.context.annotation.Lazy private PaymentService paymentGuard;
    private static final DateTimeFormatter NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PricingService pricingService;
    private final StockService stockService;
    private final MemberAssetService memberAssetService;
    private final AtomicWriteMapper atomic;
    private final VOrderService orderService;
    private final VOrderItemService orderItemService;
    private final VMemberAddressService addressService;
    private final VCartService cartService;
    private final VSeckillOrderService seckillOrderService;
    private final VPushRecordService pushRecordService;
    private final TradeProperties properties;

    public OrderService(PricingService pricingService, StockService stockService,
                        MemberAssetService memberAssetService, AtomicWriteMapper atomic,
                        VOrderService orderService, VOrderItemService orderItemService,
                        VMemberAddressService addressService, VCartService cartService,
                        VSeckillOrderService seckillOrderService, VPushRecordService pushRecordService,
                        TradeProperties properties) {
        this.pricingService = pricingService;
        this.stockService = stockService;
        this.memberAssetService = memberAssetService;
        this.atomic = atomic;
        this.orderService = orderService;
        this.orderItemService = orderItemService;
        this.addressService = addressService;
        this.cartService = cartService;
        this.seckillOrderService = seckillOrderService;
        this.pushRecordService = pushRecordService;
        this.properties = properties;
    }

    @Transactional(rollbackFor = Exception.class)
    public VOrder create(Long memberId, List<PricingService.Line> lines, Long addressId,
                         Long couponReceiveId, Integer pointsRequested, String remark) {
        return create(memberId,lines,addressId,couponReceiveId,pointsRequested,remark,null);
    }
    @Transactional(rollbackFor = Exception.class)
    public VOrder create(Long memberId, List<PricingService.Line> lines, Long addressId,
                         Long couponReceiveId, Integer pointsRequested, String remark, Long expectedPayAmountFen) {
        VMember member = atomic.lockMember(memberId);
        if(member==null || !Integer.valueOf(1).equals(member.getStatus())) throw new BizException("会员不存在或已停用");
        List<PricingService.ResolvedLine> resolved = pricingService.resolveLines(lines);
        QuoteVO quote = pricingService.quote(memberId, resolved, couponReceiveId, pointsRequested);
        if(expectedPayAmountFen!=null && !expectedPayAmountFen.equals(quote.payAmountFen())) throw new BizException("报价已变化，请刷新并重新确认费用");
        VMemberAddress address = resolveAddress(memberId, addressId);

        VOrder order = new VOrder();
        order.setOrderNo(generateOrderNo());
        order.setMemberId(memberId);
        order.setAddressId(address.getAddressId());
        order.setContactName(address.getContactName());
        order.setContactPhone(address.getPhone());
        order.setShippingAddress(fullAddress(address));
        order.setGoodsAmountFen(quote.goodsAmountFen());
        order.setDiscountAmountFen(quote.discountAmountFen() + quote.pointsDiscountFen());
        order.setFreightAmountFen(quote.freightAmountFen());
        order.setPayAmountFen(quote.payAmountFen());
        order.setPointsUsed(quote.pointsUsed());
        order.setCouponReceiveId(quote.couponReceiveId());
        order.setStatus("PENDING_PAYMENT");
        order.setDemoMode(properties.isDemoMode());
        order.setFundingSource(properties.isDemoMode()?"DEMO":"UNPAID");
        order.setRemark(remark);
        orderService.save(order);

        saveItems(order.getOrderId(), resolved, quote.lines());

        for (PricingService.ResolvedLine line : resolved) {
            if (line.seckill() != null) {
                reserveSeckill(memberId, line);
            }
            stockService.lock(line.sku().getSkuId(), line.quantity(), "ORDER", order.getOrderId(), memberId);
        }

        if (quote.couponReceiveId() != null
                && atomic.lockCoupon(quote.couponReceiveId(), memberId, order.getOrderId()) == 0) {
            throw new BizException("优惠券已被使用，请重新选择");
        }
        if (quote.pointsUsed() > 0) {
            memberAssetService.usePoints(memberId, quote.pointsUsed(), "ORDER_USE", order.getOrderId(),
                    "ORDER_USE:" + order.getOrderNo());
        }

        List<Long> skuIds = resolved.stream().map(line -> line.sku().getSkuId()).toList();
        cartService.lambdaUpdate().eq(VCart::getMemberId, memberId).in(VCart::getSkuId, skuIds).remove();

        push(memberId, "ORDER", "订单待支付", "订单 " + order.getOrderNo() + " 已创建，请在 "
                + properties.getOrderTimeoutMinutes() + " 分钟内完成支付。", order.getOrderId());
        return order;
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long memberId, Long orderId) {
        VOrder order = atomic.lockOrder(orderId);
        if (order == null || !memberId.equals(order.getMemberId())) throw new BizException("订单不存在");
        cancelPending(order, false);
    }

    private void cancelPending(VOrder order, boolean timeout) {
        Long orderId = order.getOrderId();
        Long memberId = order.getMemberId();
        atomic.lockMember(memberId);
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            throw new BizException("当前状态不可取消");
        }
        paymentGuard.closeBeforeCancel(order);
        if (atomic.cancelOrder(orderId) == 0) {
            throw new BizException("订单状态已变更，请刷新后重试");
        }
        for (VOrderItem item : items(orderId)) {
            Long skuId = snapshotLong(item, "skuId");
            stockService.unlock(skuId, item.getQuantity(), "ORDER_CANCEL", orderId, memberId);
            Long seckillId = snapshotLong(item, "seckillId");
            if (seckillId != null) {
                atomic.releaseSeckill(seckillId, item.getQuantity());
                releaseSeckillQualification(seckillId, memberId, item.getQuantity());
            }
        }
        if (order.getCouponReceiveId() != null) {
            atomic.releaseCoupon(order.getCouponReceiveId(), orderId);
        }
        if (order.getPointsUsed() != null && order.getPointsUsed() > 0) {
            memberAssetService.addPoints(memberId, order.getPointsUsed(), "ORDER_RETURN", orderId,
                    "ORDER_RETURN:" + order.getOrderNo(), "取消订单退回积分");
        }
        push(memberId, "ORDER", timeout ? "订单超时关闭" : "订单已取消", "订单 " + order.getOrderNo()
                        + (timeout ? " 超过支付期限已自动取消，" : " 已取消，") + "锁定的库存、优惠券和积分已退回。",
                orderId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void expire(Long orderId) {
        VOrder order = atomic.lockOrder(orderId);
        if (order == null || !"PENDING_PAYMENT".equals(order.getStatus()) || !isExpired(order)) return;
        cancelPending(order, true);
    }

    public boolean isExpired(VOrder order) {
        return order.getCreateTime() != null && order.getCreateTime().toInstant()
                .plusSeconds(properties.getOrderTimeoutMinutes() * 60L).isBefore(java.time.Instant.now());
    }

    @Transactional(rollbackFor = Exception.class)
    public void confirmReceive(Long memberId, Long orderId) {
        VOrder order = atomic.lockOrder(orderId);
        if(order==null||!memberId.equals(order.getMemberId()))throw new BizException("订单不存在");
        if(refundGuard.activeRefund(orderId))throw new BizException("售后处理中，暂不能确认收货");
        if (!"SHIPPED".equals(order.getStatus())) {
            throw new BizException("订单未发货，暂不能确认收货");
        }
        if (atomic.completeOrder(orderId) == 0) {
            throw new BizException("订单状态已变更，请刷新后重试");
        }
        push(memberId, "ORDER", "订单已完成", "订单 " + order.getOrderNo() + " 已确认收货，欢迎评价。", orderId);
    }

    public PageResult<OrderVO> page(Long memberId, String status, long page, long size) {
        Page<VOrder> result = orderService.page(new Page<>(com.heng.vo.PageBounds.page(page), com.heng.vo.PageBounds.size(size)),
                new QueryWrapper<VOrder>().eq("member_id", memberId)
                        .eq(status != null && !status.isBlank(), "status", status)
                        .orderByDesc("create_time"));
        List<VOrder> records = result.getRecords();
        Map<Long, List<VOrderItem>> itemsByOrder = records.isEmpty() ? Map.of()
                : orderItemService.lambdaQuery()
                        .in(VOrderItem::getOrderId, records.stream().map(VOrder::getOrderId).toList())
                        .list().stream().collect(Collectors.groupingBy(VOrderItem::getOrderId));
        List<OrderVO> list = records.stream()
                .map(order -> summary(order, itemsByOrder.getOrDefault(order.getOrderId(), List.of())))
                .toList();
        return new PageResult<>(list, result.getTotal(), result.getCurrent(), result.getSize(), result.hasNext());
    }

    public Map<String, Long> counts(Long memberId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("ALL", orderService.lambdaQuery().eq(VOrder::getMemberId, memberId).count());
        for (String status : List.of("PENDING_PAYMENT", "PAID", "SHIPPED", "COMPLETED", "CANCELLED")) {
            counts.put(status, orderService.lambdaQuery().eq(VOrder::getMemberId, memberId)
                    .eq(VOrder::getStatus, status).count());
        }
        return counts;
    }

    public OrderDetailVO detail(Long memberId, Long orderId) {
        VOrder order = requireOrder(memberId, orderId);
        List<Map<String, Object>> items = items(orderId).stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            if (item.getGoodsSnapshot() != null) row.putAll(item.getGoodsSnapshot());
            row.put("itemId", item.getItemId());
            row.put("quantity", item.getQuantity());
            row.put("unitPriceFen", item.getUnitPriceFen());
            row.put("subtotalFen", item.getSubtotalFen());
            return row;
        }).toList();
        boolean pending = "PENDING_PAYMENT".equals(order.getStatus());
        return new OrderDetailVO(order.getOrderId(), order.getOrderNo(), order.getStatus(), order.getPayType(),
                order.getContactName(), order.getContactPhone(), order.getShippingAddress(),
                order.getGoodsAmountFen(), order.getDiscountAmountFen(), order.getFreightAmountFen(),
                order.getPayAmountFen(), order.getPointsUsed(), order.getCouponReceiveId(), order.getRemark(),
                order.getShipCompany(), order.getShipNo(),
                time(order.getCreateTime()), time(order.getPayTime()), time(order.getShipTime()),
                time(order.getReceiveTime()), time(order.getCancelTime()), items,
                pending && !isExpired(order), pending, "SHIPPED".equals(order.getStatus()), "COMPLETED".equals(order.getStatus()),
                order.getCreateTime() == null ? null : time(java.util.Date.from(order.getCreateTime().toInstant()
                        .plusSeconds(properties.getOrderTimeoutMinutes() * 60L))), Boolean.TRUE.equals(order.getDemoMode()));
    }

    private OrderVO summary(VOrder order, List<VOrderItem> items) {
        Map<String, Object> first = items.isEmpty() || items.get(0).getGoodsSnapshot() == null
                ? Map.of() : items.get(0).getGoodsSnapshot();
        boolean pending = "PENDING_PAYMENT".equals(order.getStatus());
        int totalQuantity = items.stream().mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity()).sum();
        return new OrderVO(order.getOrderId(), order.getOrderNo(), order.getStatus(), order.getPayType(),
                order.getGoodsAmountFen(), order.getDiscountAmountFen(), order.getFreightAmountFen(),
                order.getPayAmountFen(), order.getPointsUsed(), totalQuantity,
                String.valueOf(first.getOrDefault("goodsName", "")),
                String.valueOf(first.getOrDefault("imageUrl", "")),
                time(order.getCreateTime()), time(order.getPayTime()),
                pending && !isExpired(order), pending, "SHIPPED".equals(order.getStatus()), "COMPLETED".equals(order.getStatus()));
    }

    private String time(java.util.Date date) {
        return date == null ? null
                : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .format(date.toInstant().atZone(ZoneId.systemDefault()));
    }

    public VOrder getCreated(Long memberId,Long orderId) { return requireOrder(memberId,orderId); }
    public VOrder requireOrder(Long memberId, Long orderId) {
        VOrder order = orderService.lambdaQuery()
                .eq(VOrder::getOrderId, orderId).eq(VOrder::getMemberId, memberId).one();
        if (order == null) throw new BizException("订单不存在");
        return order;
    }

    public List<VOrderItem> items(Long orderId) {
        return orderItemService.lambdaQuery().eq(VOrderItem::getOrderId, orderId).list();
    }

    public Long snapshotLong(VOrderItem item, String key) {
        Object value = item.getGoodsSnapshot() == null ? null : item.getGoodsSnapshot().get(key);
        return value instanceof Number number ? number.longValue() : null;
    }

    private void saveItems(Long orderId, List<PricingService.ResolvedLine> resolved,
                           List<com.heng.vo.QuoteLineVO> quoteLines) {
        for (int index = 0; index < resolved.size(); index++) {
            PricingService.ResolvedLine line = resolved.get(index);
            com.heng.vo.QuoteLineVO quoteLine = quoteLines.get(index);
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("skuId", line.sku().getSkuId());
            snapshot.put("spuId", line.spu().getSpuId());
            snapshot.put("goodsName", line.spu().getName());
            snapshot.put("subtitle", line.spu().getSubtitle());
            snapshot.put("skuCode", line.sku().getSkuCode());
            snapshot.put("spec", pricingService.specText(line.sku().getSpecValue()));
            snapshot.put("specValue", line.sku().getSpecValue());
            snapshot.put("imageUrl", line.sku().getSpecImageUrl() != null
                    ? line.sku().getSpecImageUrl() : line.spu().getMainImageUrl());
            snapshot.put("originalPriceFen", line.sku().getPriceFen());
            snapshot.put("categoryId", line.spu().getCategoryId());
            snapshot.put("weightGram", line.sku().getWeightGram());
            if (line.seckill() != null) {
                snapshot.put("seckillId", line.seckill().getSeckillId());
                snapshot.put("seckillName", line.seckill().getName());
            }

            VOrderItem item = new VOrderItem();
            item.setOrderId(orderId);
            item.setQuantity(line.quantity());
            item.setUnitPriceFen(quoteLine.unitPriceFen());
            item.setSubtotalFen(quoteLine.subtotalFen());
            item.setGoodsSnapshot(snapshot);
            orderItemService.save(item);
        }
    }

    /** 秒杀资格与活动库存：每人每场一条记录，限购件数按累计数量校验。 */
    private void reserveSeckill(Long memberId, PricingService.ResolvedLine line) {
        VSeckill seckill = line.seckill();
        VSeckillOrder existing = seckillOrderService.lambdaQuery()
                .eq(VSeckillOrder::getSeckillId, seckill.getSeckillId())
                .eq(VSeckillOrder::getMemberId, memberId).last("FOR UPDATE").one();
        int bought = existing == null ? 0 : existing.getQuantity();
        if (bought + line.quantity() > seckill.getLimitNum()) {
            throw new BizException("该活动每人限购 " + seckill.getLimitNum() + " 件");
        }
        if (atomic.reserveSeckill(seckill.getSeckillId(), line.quantity()) == 0) {
            throw new BizException("秒杀库存不足");
        }
        if (existing == null) {
            VSeckillOrder record = new VSeckillOrder();
            record.setSeckillId(seckill.getSeckillId());
            record.setMemberId(memberId);
            record.setQuantity(line.quantity());
            record.setStatus("ORDERED");
            seckillOrderService.save(record);
        } else {
            existing.setQuantity(bought + line.quantity());
            existing.setStatus("ORDERED");
            seckillOrderService.updateById(existing);
        }
    }

    private void releaseSeckillQualification(Long seckillId, Long memberId, int quantity) {
        VSeckillOrder existing = seckillOrderService.lambdaQuery()
                .eq(VSeckillOrder::getSeckillId, seckillId)
                .eq(VSeckillOrder::getMemberId, memberId).last("FOR UPDATE").one();
        if (existing == null) return;
        int rest = existing.getQuantity() - quantity;
        if (rest <= 0) {
            existing.setStatus("RELEASED");
            existing.setQuantity(0);
        } else {
            existing.setQuantity(rest);
        }
        seckillOrderService.updateById(existing);
    }

    private VMemberAddress resolveAddress(Long memberId, Long addressId) {
        VMemberAddress address = addressId == null
                ? addressService.lambdaQuery().eq(VMemberAddress::getMemberId, memberId)
                        .eq(VMemberAddress::getIsDefault, 1).orderByDesc(VMemberAddress::getAddressId).last("LIMIT 1").one()
                : addressService.lambdaQuery().eq(VMemberAddress::getAddressId, addressId)
                        .eq(VMemberAddress::getMemberId, memberId).one();
        if (address == null) throw new BizException("请先添加收货地址");
        return address;
    }

    private String fullAddress(VMemberAddress address) {
        return address.getProvince() + address.getCity() + address.getDistrict() + address.getDetail();
    }

    private String generateOrderNo() {
        return "V" + LocalDateTime.now().format(NO_FORMAT) + (100000 + RANDOM.nextInt(900000));
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
}
