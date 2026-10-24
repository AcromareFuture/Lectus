package com.heng.biz;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.heng.config.BizException;
import com.heng.config.TradeProperties;
import com.heng.domain.*;
import com.heng.service.*;
import com.heng.vo.CouponOptionVO;
import com.heng.vo.QuoteLineVO;
import com.heng.vo.QuoteVO;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * C 端唯一计价入口，结算预览和创建订单共用，保证展示价与实际成交价一致。
 */
@Service
public class PricingService {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final VMemberService memberService;
    private final VMemberLevelService levelService;
    private final VGoodsSkuService skuService;
    private final VGoodsSpuService spuService;
    private final VCategoryService categoryService;
    private final VStockService stockService;
    private final VMemberCouponService memberCouponService;
    private final VCouponService couponService;
    private final VSeckillService seckillService;
    private final TradeProperties properties;
    private final ObjectMapper objectMapper;

    public PricingService(VMemberService memberService, VMemberLevelService levelService,
                          VGoodsSkuService skuService, VGoodsSpuService spuService,
                          VCategoryService categoryService, VStockService stockService,
                          VMemberCouponService memberCouponService, VCouponService couponService,
                          VSeckillService seckillService, TradeProperties properties, ObjectMapper objectMapper) {
        this.memberService = memberService;
        this.levelService = levelService;
        this.skuService = skuService;
        this.spuService = spuService;
        this.categoryService = categoryService;
        this.stockService = stockService;
        this.memberCouponService = memberCouponService;
        this.couponService = couponService;
        this.seckillService = seckillService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public record Line(Long skuId, Integer quantity, Long seckillId) {
    }

    public record ResolvedLine(VGoodsSku sku, VGoodsSpu spu, VSeckill seckill, int quantity,
                               long unitPriceFen, long subtotalFen, int availableStock) {
    }

    public VMember requireMember(Long memberId) {
        VMember member = memberService.getById(memberId);
        if (member == null || member.getStatus() == null || member.getStatus() != 1) {
            throw new BizException("会员不存在或已停用");
        }
        return member;
    }

    /** 解析并校验商品行，秒杀行使用活动价且不受会员折扣影响。 */
    public List<ResolvedLine> resolveLines(List<Line> lines) {
        if (lines == null || lines.isEmpty()) throw new BizException("请先选择商品");
        if (lines.size() > 100) throw new BizException("一次最多结算 100 种商品");
        Set<Long> seen = new HashSet<>();
        for (Line line : lines) {
            if (line == null || line.skuId() == null || line.skuId() < 1 || line.quantity() == null
                    || line.quantity() < 1 || line.quantity() > 999) {
                throw new BizException("商品数量不合法");
            }
            if (!seen.add(line.skuId())) throw new BizException("同一商品不能重复提交");
        }
        List<Long> skuIds = lines.stream().map(Line::skuId).filter(Objects::nonNull).distinct().toList();
        Map<Long, VGoodsSku> skuMap = new HashMap<>();
        skuService.listByIds(skuIds).forEach(sku -> skuMap.put(sku.getSkuId(), sku));
        Map<Long, VGoodsSpu> spuMap = new HashMap<>();
        List<Long> spuIds = skuMap.values().stream().map(VGoodsSku::getSpuId).distinct().toList();
        if (!spuIds.isEmpty()) {
            spuService.listByIds(spuIds).forEach(spu -> spuMap.put(spu.getSpuId(), spu));
        }
        Map<Long, VStock> stockMap = new HashMap<>();
        if (!skuIds.isEmpty()) {
            stockService.lambdaQuery().in(VStock::getSkuId, skuIds).list()
                    .forEach(stock -> stockMap.put(stock.getSkuId(), stock));
        }

        List<ResolvedLine> resolved = new ArrayList<>();
        for (Line line : lines) {
            if (line.skuId() == null || line.quantity() == null || line.quantity() < 1) {
                throw new BizException("商品数量不合法");
            }
            VGoodsSku sku = skuMap.get(line.skuId());
            if (sku == null || sku.getStatus() == null || sku.getStatus() != 1) {
                throw new BizException("商品规格不存在或已下架");
            }
            VGoodsSpu spu = spuMap.get(sku.getSpuId());
            if (spu == null || !"0".equals(spu.getDelFlag()) || spu.getOnSale() == null || spu.getOnSale() != 1) {
                throw new BizException("商品已下架");
            }
            VStock stock = stockMap.get(sku.getSkuId());
            int available = stock == null ? 0 : stock.getAvailableStock();
            if (available < line.quantity()) {
                throw new BizException(spu.getName() + " 库存不足，当前仅剩 " + available + " 件");
            }
            VSeckill seckill = null;
            long unitPrice = sku.getPriceFen();
            if (line.seckillId() != null) {
                seckill = seckillService.getById(line.seckillId());
                if (seckill == null || !"RUNNING".equals(seckill.getStatus())) {
                    throw new BizException("秒杀活动未开始或已结束");
                }
                if (!Objects.equals(seckill.getSkuId(), sku.getSkuId())) {
                    throw new BizException("秒杀活动与商品规格不匹配");
                }
                unitPrice = seckill.getPriceFen();
            }
            resolved.add(new ResolvedLine(sku, spu, seckill, line.quantity(), unitPrice,
                    unitPrice * line.quantity(), available));
        }
        return resolved;
    }

    public QuoteVO quote(Long memberId, List<ResolvedLine> resolved, Long couponReceiveId, Integer pointsRequested) {
        VMember member = requireMember(memberId);
        int discountBps = levelDiscountBps(member.getLevelId());

        long goodsAmount = 0L;
        long levelDiscount = 0L;
        long normalScopeAmount = 0L;
        List<QuoteLineVO> lines = new ArrayList<>();
        Map<Long, Long> categoryLineAmount = new HashMap<>();
        Map<Long, Long> spuLineAmount = new HashMap<>();

        for (ResolvedLine line : resolved) {
            goodsAmount += line.subtotalFen();
            long unit = line.unitPriceFen();
            if (line.seckill() == null) {
                long discountedUnit = unit * discountBps / 10000;
                levelDiscount += (unit - discountedUnit) * line.quantity();
                unit = discountedUnit;
                normalScopeAmount += discountedUnit * line.quantity();
                categoryLineAmount.merge(line.spu().getCategoryId(), discountedUnit * line.quantity(), Long::sum);
                spuLineAmount.merge(line.spu().getSpuId(), discountedUnit * line.quantity(), Long::sum);
            }
            long subtotal = unit * line.quantity();
            lines.add(new QuoteLineVO(line.spu().getSpuId(), line.sku().getSkuId(), line.spu().getName(),
                    specText(line.sku().getSpecValue()),
                    line.sku().getSpecImageUrl() != null ? line.sku().getSpecImageUrl() : line.spu().getMainImageUrl(),
                    line.quantity(), unit, line.sku().getOriginalPriceFen(), subtotal,
                    line.availableStock(), line.seckill() == null ? null : line.seckill().getSeckillId()));
        }

        long payGoodsAmount = goodsAmount - levelDiscount;
        boolean hasNormalLine = normalScopeAmount > 0;
        List<CouponOptionVO> options = hasNormalLine
                ? couponOptions(memberId, normalScopeAmount, categoryLineAmount, spuLineAmount)
                : List.of();

        long couponDiscount = 0L;
        if (couponReceiveId != null) {
            CouponOptionVO chosen = options.stream()
                    .filter(option -> option.receiveId().equals(couponReceiveId))
                    .findFirst().orElse(null);
            if (chosen == null) {
                throw new BizException("优惠券不可用");
            }
            if (!Boolean.TRUE.equals(chosen.usable())) {
                throw new BizException(chosen.reason());
            }
            couponDiscount = chosen.discountFen();
        }

        long afterCoupon = payGoodsAmount - couponDiscount;
        int pointsAvailable = member.getPoints() == null ? 0 : member.getPoints();
        int pointsMaxUse = (int) Math.min(Math.max(0,pointsAvailable),
                afterCoupon * properties.getPointsMaxDeductPercent() / 100 / properties.getFenPerPoint());
        int pointsUsed = clampPoints(pointsRequested, pointsMaxUse);
        long pointsDiscount = (long) pointsUsed * properties.getFenPerPoint();
        long goodsPay = afterCoupon - pointsDiscount;
        long freight = goodsPay >= properties.getFreeShippingThresholdFen() ? 0L : properties.getShippingFeeFen();

        return new QuoteVO(lines, goodsAmount, levelDiscount, couponDiscount, levelDiscount + couponDiscount,
                pointsUsed, pointsDiscount, pointsAvailable, pointsMaxUse, freight,
                properties.getFreeShippingThresholdFen(), goodsPay + freight, couponReceiveId, options);
    }

    private int clampPoints(Integer requested, int maxUse) {
        if (requested == null || requested <= 0) return 0;
        return Math.min(requested, maxUse);
    }

    private int levelDiscountBps(Long levelId) {
        if (levelId == null) return 10000;
        VMemberLevel level = levelService.getById(levelId);
        if (level == null || level.getStatus() == null || level.getStatus() != 1) return 10000;
        return level.getDiscountBps() == null ? 10000 : level.getDiscountBps();
    }

    /** 按券模板的适用范围计算可抵扣金额，范围外的商品不参与门槛判断。 */
    private List<CouponOptionVO> couponOptions(Long memberId, long normalScopeAmount,
                                               Map<Long, Long> categoryLineAmount, Map<Long, Long> spuLineAmount) {
        List<VMemberCoupon> owned = memberCouponService.lambdaQuery()
                .eq(VMemberCoupon::getMemberId, memberId)
                .eq(VMemberCoupon::getStatus, "AVAILABLE")
                .list();
        if (owned.isEmpty()) return List.of();
        List<Long> templateIds = owned.stream().map(VMemberCoupon::getCouponId).distinct().toList();
        Map<Long, VCoupon> templates = new HashMap<>();
        couponService.listByIds(templateIds).forEach(template -> templates.put(template.getCouponId(), template));

        List<CouponOptionVO> result = new ArrayList<>();
        for (VMemberCoupon coupon : owned) {
            VCoupon template = templates.get(coupon.getCouponId());
            if (template == null) continue;
            Date now = new Date();
            if (coupon.getExpireTime() == null || !coupon.getExpireTime().after(now)
                    || template.getValidFrom() == null || template.getValidFrom().after(now)
                    || template.getValidTo() == null || !template.getValidTo().after(now)) continue;
            long scopeAmount = scopeAmount(template, normalScopeAmount, categoryLineAmount, spuLineAmount);
            long discount = computeDiscount(template, scopeAmount);
            boolean usable = discount > 0;
            result.add(new CouponOptionVO(coupon.getReceiveId(), template.getCouponId(), template.getName(),
                    template.getCouponType(), template.getMinAmountFen(), template.getValueFen(),
                    template.getDiscountBps(), template.getUseScope(), usable,
                    usable ? discount : 0L, usable ? null : unmetReason(template, scopeAmount),
                    formatTime(coupon.getExpireTime())));
        }
        result.sort(Comparator.comparing((CouponOptionVO option) -> option.usable() ? 0 : 1)
                .thenComparing(CouponOptionVO::discountFen, Comparator.reverseOrder()));
        return result;
    }

    private long scopeAmount(VCoupon template, long normalScopeAmount,
                             Map<Long, Long> categoryLineAmount, Map<Long, Long> spuLineAmount) {
        String scope = template.getUseScope() == null ? "ALL" : template.getUseScope();
        if ("ALL".equals(scope)) return normalScopeAmount;
        List<Long> ids = parseScopeIds(template.getScopeIds());
        if (ids.isEmpty()) return 0L;
        long amount = 0L;
        Map<Long, Long> parentMap = categoryParentMap();
        for (Map.Entry<Long, Long> entry : categoryLineAmount.entrySet()) {
            Long parentId = parentMap.get(entry.getKey());
            if (ids.contains(entry.getKey()) || (parentId != null && ids.contains(parentId))) {
                amount += entry.getValue();
            }
        }
        if ("PRODUCT".equals(scope)) {
            amount = 0L;
            for (Map.Entry<Long, Long> entry : spuLineAmount.entrySet()) {
                if (ids.contains(entry.getKey())) amount += entry.getValue();
            }
        }
        return amount;
    }

    private Map<Long, Long> categoryParentMap() {
        Map<Long, Long> map = new HashMap<>();
        categoryService.list().forEach(category -> map.put(category.getCategoryId(), category.getParentId()));
        return map;
    }

    private long computeDiscount(VCoupon template, long scopeAmount) {
        if (template.getStatus() == null || template.getStatus() != 1) return 0L;
        if (scopeAmount <= 0) return 0L;
        long min = template.getMinAmountFen() == null ? 0L : template.getMinAmountFen();
        if (scopeAmount < min) return 0L;
        String type = template.getCouponType();
        if ("FULL_REDUCTION".equals(type)) {
            long value = template.getValueFen() == null ? 0L : template.getValueFen();
            return Math.min(value, scopeAmount);
        }
        if ("DISCOUNT".equals(type)) {
            int bps = template.getDiscountBps() == null ? 10000 : template.getDiscountBps();
            return scopeAmount - scopeAmount * bps / 10000;
        }
        if ("FREE".equals(type)) {
            return scopeAmount;
        }
        return 0L;
    }

    private String unmetReason(VCoupon template, long scopeAmount) {
        long min = template.getMinAmountFen() == null ? 0L : template.getMinAmountFen();
        if (scopeAmount <= 0) return "订单中没有该券可用的商品";
        if (scopeAmount < min) return "还差 " + ((min - scopeAmount + 99) / 100) + " 元可用";
        return "优惠券不可用";
    }

    private List<Long> parseScopeIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    public String specText(String specValue) {
        if (specValue == null || specValue.isBlank()) return "";
        try {
            Map<String, Object> map = objectMapper.readValue(specValue, new TypeReference<Map<String, Object>>() {
            });
            return String.join(" ", map.values().stream().map(String::valueOf).toList());
        } catch (Exception e) {
            return specValue;
        }
    }

    private String formatTime(Date date) {
        if (date == null) return null;
        return TIME_FORMAT.format(date.toInstant().atZone(ZoneId.systemDefault()));
    }
}
