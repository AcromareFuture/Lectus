package com.heng.biz;

import com.heng.config.BizException;
import com.heng.config.TradeProperties;
import com.heng.domain.VCart;
import com.heng.domain.VGoodsSku;
import com.heng.domain.VGoodsSpu;
import com.heng.domain.VStock;
import com.heng.service.VCartService;
import com.heng.service.VGoodsSkuService;
import com.heng.service.VGoodsSpuService;
import com.heng.service.VStockService;
import com.heng.vo.CartItemVO;
import com.heng.vo.CartVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 购物车行需要商品名、图片、规格和实时价格才能渲染，因此这些字段在这里按当前会员等级折算后再返回。
 */
@Service
public class CartService {
    private final VCartService cartService;
    private final VGoodsSkuService skuService;
    private final VGoodsSpuService spuService;
    private final VStockService stockService;
    private final CatalogService catalogService;
    private final PricingService pricingService;
    private final TradeProperties properties;
    private final com.heng.mapper.AtomicWriteMapper atomic;

    public CartService(VCartService cartService, VGoodsSkuService skuService, VGoodsSpuService spuService,
                       VStockService stockService, CatalogService catalogService, PricingService pricingService,
                       TradeProperties properties, com.heng.mapper.AtomicWriteMapper atomic) {
        this.atomic=atomic;
        this.cartService = cartService;
        this.skuService = skuService;
        this.spuService = spuService;
        this.stockService = stockService;
        this.catalogService = catalogService;
        this.pricingService = pricingService;
        this.properties = properties;
    }

    public CartVO view(Long memberId) {
        List<VCart> rows = cartService.lambdaQuery().eq(VCart::getMemberId, memberId)
                .orderByDesc(VCart::getUpdateTime).list();
        if (rows.isEmpty()) {
            return new CartVO(List.of(), 0, 0, 0L, properties.getFreeShippingThresholdFen(),
                    properties.getFreeShippingThresholdFen());
        }
        List<Long> skuIds = rows.stream().map(VCart::getSkuId).toList();
        Map<Long, VGoodsSku> skus = new HashMap<>();
        skuService.listByIds(skuIds).forEach(sku -> skus.put(sku.getSkuId(), sku));
        List<Long> spuIds = skus.values().stream().map(VGoodsSku::getSpuId).distinct().toList();
        Map<Long, VGoodsSpu> spus = new HashMap<>();
        if (!spuIds.isEmpty()) {
            spuService.listByIds(spuIds).forEach(spu -> spus.put(spu.getSpuId(), spu));
        }
        Map<Long, Integer> stocks = new HashMap<>();
        stockService.lambdaQuery().in(VStock::getSkuId, skuIds).list()
                .forEach(stock -> stocks.put(stock.getSkuId(), stock.getAvailableStock()));

        int bps = catalogService.levelDiscountBps(memberId);
        List<CartItemVO> items = new ArrayList<>();
        int totalQuantity = 0;
        int checkedQuantity = 0;
        long checkedAmount = 0L;
        for (VCart row : rows) {
            VGoodsSku sku = skus.get(row.getSkuId());
            if (sku == null) continue;
            VGoodsSpu spu = spus.get(sku.getSpuId());
            if (spu == null) continue;
            int available = stocks.getOrDefault(sku.getSkuId(), 0);
            boolean onSale = "0".equals(spu.getDelFlag()) && spu.getOnSale() != null && spu.getOnSale() == 1
                    && sku.getStatus() != null && sku.getStatus() == 1;
            long levelPrice = sku.getPriceFen() * bps / 10000;
            boolean enough = available >= row.getQuantity();
            items.add(new CartItemVO(row.getCartId(), sku.getSkuId(), spu.getSpuId(), spu.getName(),
                    spu.getSubtitle(),
                    sku.getSpecImageUrl() != null ? sku.getSpecImageUrl() : spu.getMainImageUrl(),
                    pricingService.specText(sku.getSpecValue()), sku.getPriceFen(), levelPrice,
                    levelPrice * row.getQuantity(), row.getQuantity(), row.getChecked(), available, onSale, enough));
            totalQuantity += row.getQuantity();
            if (onSale && enough && row.getChecked() != null && row.getChecked() == 1) {
                checkedQuantity += row.getQuantity();
                checkedAmount += levelPrice * row.getQuantity();
            }
        }
        long toFree = Math.max(0, properties.getFreeShippingThresholdFen() - checkedAmount);
        return new CartVO(items, totalQuantity, checkedQuantity, checkedAmount,
                properties.getFreeShippingThresholdFen(), toFree);
    }

    public List<PricingService.Line> checkedLines(Long memberId) {
        List<CartItemVO> items = view(memberId).items();
        List<PricingService.Line> lines = new ArrayList<>();
        for (CartItemVO item : items) {
            if (item.checked() != null && item.checked() == 1 && Boolean.TRUE.equals(item.onSale())) {
                lines.add(new PricingService.Line(item.skuId(), item.quantity(), null));
            }
        }
        if (lines.isEmpty()) throw new BizException("请先勾选要结算的商品");
        return lines;
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public void add(Long memberId, Long skuId, int quantity) {
        if (atomic.lockMember(memberId)==null) throw new BizException("会员不存在");
        if (quantity<1 || quantity>999) throw new BizException("数量需为 1–999");
        VGoodsSku sku = skuService.getById(skuId);
        if (sku == null || sku.getStatus() == null || sku.getStatus() != 1) {
            throw new BizException("商品规格不存在或已下架");
        }
        VCart existing = cartService.lambdaQuery().eq(VCart::getMemberId, memberId)
                .eq(VCart::getSkuId, skuId).one();
        if (existing == null) {
            VCart cart = new VCart();
            cart.setMemberId(memberId);
            cart.setSkuId(skuId);
            cart.setQuantity(quantity);
            cart.setChecked(1);
            cartService.save(cart);
        } else {
            if(existing.getQuantity()+quantity>999) throw new BizException("购物车累计数量不能超过 999");
            cartService.lambdaUpdate().eq(VCart::getCartId,existing.getCartId()).set(VCart::getQuantity,existing.getQuantity()+quantity).update();
        }
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public void updateQuantity(Long memberId, Long skuId, int quantity, Integer checked) {
        if (atomic.lockMember(memberId)==null) throw new BizException("会员不存在");
        VCart cart = requireRow(memberId, skuId);
        if(quantity<1 || quantity>999 || (checked!=null && checked!=0 && checked!=1)) throw new BizException("数量或勾选选项无效");
        var update=cartService.lambdaUpdate().eq(VCart::getCartId,cart.getCartId()).set(VCart::getQuantity,quantity);
        if(checked!=null) update.set(VCart::getChecked,checked);
        update.update();
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public void updateChecked(Long memberId, Long skuId, int checked) {
        if (atomic.lockMember(memberId)==null) throw new BizException("会员不存在");
        VCart cart = requireRow(memberId, skuId);
        if(checked!=0 && checked!=1) throw new BizException("勾选选项无效");
        cartService.lambdaUpdate().eq(VCart::getCartId,cart.getCartId()).set(VCart::getChecked,checked).update();
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public void checkAll(Long memberId, int checked) {
        if (atomic.lockMember(memberId)==null) throw new BizException("会员不存在");
        if(checked!=0 && checked!=1) throw new BizException("勾选选项无效");
        cartService.lambdaUpdate().eq(VCart::getMemberId, memberId).set(VCart::getChecked, checked).update();
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public void remove(Long memberId, List<Long> skuIds) {
        if (atomic.lockMember(memberId)==null) throw new BizException("会员不存在");
        cartService.lambdaUpdate().eq(VCart::getMemberId, memberId).in(VCart::getSkuId, skuIds).remove();
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public void clear(Long memberId) {
        if (atomic.lockMember(memberId)==null) throw new BizException("会员不存在");
        cartService.lambdaUpdate().eq(VCart::getMemberId, memberId).remove();
    }

    private VCart requireRow(Long memberId, Long skuId) {
        VCart cart = cartService.lambdaQuery().eq(VCart::getMemberId, memberId).eq(VCart::getSkuId, skuId).one();
        if (cart == null) throw new BizException("购物车中没有该商品");
        return cart;
    }
}
