package com.heng.biz;

import com.heng.config.BizException;
import com.heng.domain.VStock;
import com.heng.domain.VStockLog;
import com.heng.mapper.AtomicWriteMapper;
import com.heng.service.VStockLogService;
import com.heng.service.VStockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
public class StockService {
    private final AtomicWriteMapper atomic;
    private final VStockService stockService;
    private final VStockLogService stockLogService;

    public StockService(AtomicWriteMapper atomic, VStockService stockService, VStockLogService stockLogService) {
        this.atomic = atomic;
        this.stockService = stockService;
        this.stockLogService = stockLogService;
    }

    public void lock(Long skuId, int quantity, String bizType, Long bizId, Long operatorId) {
        int before = requireRow(skuId).getAvailableStock();
        if (atomic.lockStock(skuId, quantity) == 0) {
            throw new BizException("商品库存不足，请调整数量后重试");
        }
        writeLog(skuId, "LOCK", quantity, before, before - quantity, bizType, bizId, operatorId);
    }

    public void unlock(Long skuId, int quantity, String bizType, Long bizId, Long operatorId) {
        int before = requireRow(skuId).getAvailableStock();
        if (atomic.unlockStock(skuId, quantity) == 0) {
            throw new BizException("库存解锁失败，请联系客服");
        }
        writeLog(skuId, "UNLOCK", quantity, before, before + quantity, bizType, bizId, operatorId);
    }

    public void consume(Long skuId, int quantity, String bizType, Long bizId, Long operatorId) {
        int before = requireRow(skuId).getAvailableStock();
        if (atomic.consumeLockedStock(skuId, quantity) == 0) {
            throw new BizException("库存核销失败，请联系客服");
        }
        writeLog(skuId, "OUT", quantity, before, before, bizType, bizId, operatorId);
    }

    private VStock requireRow(Long skuId) {
        VStock stock = atomic.readStockForUpdate(skuId);
        if (stock == null) {
            throw new BizException("商品库存记录不存在");
        }
        return stock;
    }

    private void writeLog(Long skuId, String changeType, int quantity, int before, int after,
                          String bizType, Long bizId, Long operatorId) {
        VStockLog log = new VStockLog();
        log.setSkuId(skuId);
        log.setChangeType(changeType);
        log.setQuantity(quantity);
        log.setBeforeAvailable(before);
        log.setAfterAvailable(after);
        log.setBizType(bizType);
        log.setBizId(bizId);
        log.setOperatorId(operatorId);
        stockLogService.save(log);
    }
}
