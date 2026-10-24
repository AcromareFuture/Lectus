package com.heng.biz;

import com.heng.domain.VOrder;
import com.heng.service.VOrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 自动关闭超时未支付订单，释放其锁定的库存、积分和优惠券。
 * 任务可以安全重复执行：OrderService.cancel 只接受 PENDING_PAYMENT 状态。
 */
@Component
public class PendingOrderTimeoutTask {
    private static final Logger log = LoggerFactory.getLogger(PendingOrderTimeoutTask.class);
    private final VOrderService orderService;
    private final OrderService tradeOrderService;
    private final com.heng.config.TradeProperties properties;

    public PendingOrderTimeoutTask(VOrderService orderService, OrderService tradeOrderService,
                                   com.heng.config.TradeProperties properties) {
        this.orderService = orderService;
        this.tradeOrderService = tradeOrderService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${viridac.trade.order-timeout-scan-ms:60000}", initialDelayString = "${viridac.trade.order-timeout-initial-delay-ms:15000}")
    public void closeExpiredOrders() {
        if (!properties.isOrderTimeoutTaskEnabled()) return;
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(properties.getOrderTimeoutMinutes());
        Date cutoff = Date.from(deadline.atZone(ZoneId.systemDefault()).toInstant());
        orderService.lambdaQuery().eq(VOrder::getStatus, "PENDING_PAYMENT")
                .lt(VOrder::getCreateTime, cutoff).orderByAsc(VOrder::getCreateTime).last("LIMIT 100").list()
                .forEach(order -> {
                    try {
                        tradeOrderService.expire(order.getOrderId());
                    } catch (RuntimeException error) {
                        log.warn("Unable to expire order {}; transaction rolled back", order.getOrderId(), error);
                    }
                });
    }
}
