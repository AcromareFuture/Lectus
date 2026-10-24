package com.heng.biz;

import com.heng.config.BizException;
import com.heng.domain.*;
import com.heng.mapper.AtomicWriteMapper;
import com.heng.service.*;
import com.heng.vo.PageResult;
import com.heng.vo.ProductCardVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 营销域只读展示与积分兑换。积分兑换用条件更新保证同一刻不会超兑或重复扣分。
 */
@Service
public class MarketingService {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final VSeckillService seckillService;
    private final VPointsMallService pointsMallService;
    private final VPointsExchangeService exchangeService;
    private final VMemberCouponService memberCouponService;
    private final VCouponService couponService;
    private final VGoodsSpuService spuService;
    private final AtomicWriteMapper atomic;
    private final CatalogService catalogService;
    private final MemberAssetService memberAssetService;
    private final VMemberAddressService addresses;

    public MarketingService(VSeckillService seckillService, VPointsMallService pointsMallService,
                            VPointsExchangeService exchangeService, VMemberCouponService memberCouponService,
                            VCouponService couponService, VGoodsSpuService spuService, AtomicWriteMapper atomic,
                            CatalogService catalogService, MemberAssetService memberAssetService, VMemberAddressService addresses) {
        this.addresses=addresses;
        this.seckillService = seckillService;
        this.pointsMallService = pointsMallService;
        this.exchangeService = exchangeService;
        this.memberCouponService = memberCouponService;
        this.couponService = couponService;
        this.spuService = spuService;
        this.atomic = atomic;
        this.catalogService = catalogService;
        this.memberAssetService = memberAssetService;
    }

    /** 秒杀下单复用通用下单链路，这里只负责把活动解析成带活动价的商品行。 */
    public PricingService.Line seckillLine(Long seckillId, int quantity) {
        VSeckill seckill = seckillService.getById(seckillId);
        if (seckill == null || !"RUNNING".equals(seckill.getStatus())) {
            throw new BizException("秒杀活动未开始或已结束");
        }
        Date now = new Date();
        if (seckill.getStartTime().after(now) || seckill.getEndTime().before(now)) {
            throw new BizException("秒杀活动不在进行中");
        }
        return new PricingService.Line(seckill.getSkuId(), quantity, seckillId);
    }

    public List<Map<String, Object>> seckills() {
        Date now = new Date();
        List<VSeckill> list = seckillService.lambdaQuery().eq(VSeckill::getStatus, "RUNNING")
                .le(VSeckill::getStartTime, now).ge(VSeckill::getEndTime, now)
                .orderByAsc(VSeckill::getStartTime).list();
        if (list.isEmpty()) return List.of();
        Map<Long, VGoodsSpu> spus = new HashMap<>();
        spuService.listByIds(list.stream().map(VSeckill::getSpuId).distinct().toList())
                .forEach(spu -> spus.put(spu.getSpuId(), spu));

        List<Map<String, Object>> result = new ArrayList<>();
        for (VSeckill seckill : list) {
            VGoodsSpu spu = spus.get(seckill.getSpuId());
            if (spu == null || !"0".equals(spu.getDelFlag()) || spu.getOnSale() != 1) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("seckillId", seckill.getSeckillId());
            row.put("name", seckill.getName());
            row.put("spuId", spu.getSpuId());
            row.put("skuId", seckill.getSkuId());
            row.put("goodsName", spu.getName());
            row.put("imageUrl", spu.getMainImageUrl());
            row.put("priceFen", seckill.getPriceFen());
            row.put("originalPriceFen", spu.getMinPriceFen());
            row.put("remainingStock", Math.max(0, seckill.getStock()));
            row.put("limitNum", seckill.getLimitNum());
            row.put("startTime", time(seckill.getStartTime()));
            row.put("endTime", time(seckill.getEndTime()));
            result.add(row);
        }
        return result;
    }

    public List<Map<String, Object>> pointsMall() {
        Date now = new Date();
        return pointsMallService.lambdaQuery().eq(VPointsMall::getStatus, 1)
                .and(w -> w.isNull(VPointsMall::getStartTime).or().le(VPointsMall::getStartTime, now))
                .and(w -> w.isNull(VPointsMall::getEndTime).or().ge(VPointsMall::getEndTime, now))
                .orderByAsc(VPointsMall::getItemId).list().stream().map(item -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("itemId", item.getItemId());
                    row.put("name", item.getName());
                    row.put("imageUrl", item.getImageUrl());
                    row.put("pointsPrice", item.getPointsPrice());
                    row.put("stock", item.getStock());
                    row.put("description", item.getDescription());
                    row.put("couponReward",item.getCouponId()!=null);
                    row.put("rewardType",item.getCouponId()!=null?"COUPON":item.getRewardType());
                    row.put("exchangeable",item.getCouponId()!=null || "PHYSICAL".equals(item.getRewardType()));
                    return row;
                }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> exchange(Long memberId, Long itemId, int quantity) {
        return exchange(memberId,itemId,quantity,null);
    }
    @Transactional(rollbackFor=Exception.class)
    public Map<String,Object> exchange(Long memberId,Long itemId,int quantity,Long addressId) {
        atomic.lockMember(memberId);
        VPointsMall item = pointsMallService.getById(itemId);
        if (item == null || item.getStatus() == null || item.getStatus() != 1) {
            throw new BizException("兑换商品不存在或已下架");
        }
        Date now=new Date();
        if((item.getStartTime()!=null && item.getStartTime().after(now)) || (item.getEndTime()!=null && item.getEndTime().before(now))) throw new BizException("兑换活动不在有效期内");
        if(quantity<1 || quantity>20 || item.getPointsPrice()<1) throw new BizException("兑换数量或积分价格无效");
        String rewardType=item.getCouponId()!=null?"COUPON":item.getRewardType();
        VMemberAddress address=null;
        if("PHYSICAL".equals(rewardType)) {
            address=addresses.lambdaQuery().eq(VMemberAddress::getMemberId,memberId).eq(VMemberAddress::getAddressId,addressId).one();
            if(address==null) throw new BizException("实物兑换请选择收货地址");
        } else if(!"COUPON".equals(rewardType)) throw new BizException("该奖励尚不支持交付，已暂停兑换");
        int points = Math.multiplyExact(item.getPointsPrice(),quantity);
        VCoupon reward=null;
        if(item.getCouponId()!=null) {
            reward=couponService.getById(item.getCouponId());
            if(reward==null || !Integer.valueOf(1).equals(reward.getStatus()) || reward.getValidFrom().after(now) || reward.getValidTo().before(now)) throw new BizException("兑换优惠券已失效");
            if(atomic.reserveRewardCoupon(reward.getCouponId(),quantity)==0) throw new BizException("兑换优惠券库存不足");
        }
        String key = "EXCHANGE:" + memberId + ":" + itemId + ":" + LocalDate.now().atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli() + ":" + UUID.randomUUID();
        if (atomic.deductPointsMallStock(itemId, quantity) == 0) {
            throw new BizException("兑换商品库存不足");
        }
        memberAssetService.usePoints(memberId, points, "EXCHANGE", itemId, key);

        VPointsExchange exchange = new VPointsExchange();
        exchange.setExchangeNo("E" + UUID.randomUUID().toString().replace("-", "").substring(0,30));
        exchange.setMemberId(memberId);
        exchange.setItemId(itemId);
        exchange.setPointsUsed(points);
        exchange.setQuantity(quantity);
        exchange.setStatus("PHYSICAL".equals(rewardType)?"PENDING_SHIPMENT":"SUCCESS");
        exchange.setNameSnapshot(item.getName());exchange.setImageSnapshot(item.getImageUrl());exchange.setRewardType(rewardType);
        if(address!=null) exchange.setAddressSnapshot(address.getContactName()+" "+address.getPhone()+" "+address.getProvince()+address.getCity()+address.getDistrict()+address.getDetail());
        exchangeService.save(exchange);
        if(reward!=null) for(int i=0;i<quantity;i++) {
            VMemberCoupon received=new VMemberCoupon();
            received.setCouponId(reward.getCouponId());received.setMemberId(memberId);received.setStatus("AVAILABLE");
            received.setReceivedTime(now);received.setExpireTime(reward.getValidTo());memberCouponService.save(received);
        }
        return exchangeResult(memberId,exchange.getExchangeId());
    }

    public Map<String,Object> exchangeResult(Long memberId,Long id) {
        VPointsExchange row=exchangeService.lambdaQuery().eq(VPointsExchange::getMemberId,memberId).eq(VPointsExchange::getExchangeId,id).one();
        if(row==null) throw new BizException("兑换记录不存在");return exchangeView(row);
    }
    private Map<String,Object> exchangeView(VPointsExchange row) {
        Map<String,Object> v=new LinkedHashMap<>();
        v.put("exchangeId",row.getExchangeId());v.put("exchangeNo",row.getExchangeNo());v.put("name",row.getNameSnapshot());
        v.put("imageUrl",row.getImageSnapshot());v.put("pointsUsed",row.getPointsUsed());v.put("quantity",row.getQuantity());
        v.put("status",row.getStatus());v.put("createTime",row.getCreateTime());v.put("shipCompany",row.getShipCompany());v.put("shipNo",row.getShipNo());
        return v;
    }
    public PageResult<Map<String,Object>> exchangePage(Long memberId,long page,long size) {
        var result=exchangeService.lambdaQuery().eq(VPointsExchange::getMemberId,memberId).orderByDesc(VPointsExchange::getExchangeId)
            .page(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(com.heng.vo.PageBounds.page(page),com.heng.vo.PageBounds.size(size)));
        return new PageResult<>(result.getRecords().stream().map(this::exchangeView).toList(),result.getTotal(),result.getCurrent(),result.getSize(),result.hasNext());
    }
    @Transactional(rollbackFor=Exception.class)
    public void cancelExchange(Long memberId,Long id) {
        atomic.lockMember(memberId);
        VPointsExchange row=exchangeService.lambdaQuery().eq(VPointsExchange::getMemberId,memberId).eq(VPointsExchange::getExchangeId,id).last("FOR UPDATE").one();
        if(row==null) throw new BizException("兑换记录不存在");
        if("CANCELLED".equals(row.getStatus())) return;
        if(!"PENDING_SHIPMENT".equals(row.getStatus())) throw new BizException("当前兑换不可取消");
        exchangeService.lambdaUpdate().eq(VPointsExchange::getExchangeId,id).set(VPointsExchange::getStatus,"CANCELLED").update();
        pointsMallService.lambdaUpdate().eq(VPointsMall::getItemId,row.getItemId()).setSql("stock=stock+"+row.getQuantity()+",exchanged_count=GREATEST(exchanged_count-"+row.getQuantity()+",0)").update();
        memberAssetService.addPoints(memberId,row.getPointsUsed(),"EXCHANGE_RETURN",id,"EXCHANGE_RETURN:"+id,"取消实物兑换退回积分");
    }

    /** 我的优惠券：过期的可用券在读取时归入 EXPIRED，不额外跑批改状态。 */
    public PageResult<Map<String,Object>> couponPage(Long memberId,String status,long page,long size) {
        Date now=new Date();var query=memberCouponService.lambdaQuery().eq(VMemberCoupon::getMemberId,memberId);
        if("EXPIRED".equals(status)) query.and(w->w.eq(VMemberCoupon::getStatus,"EXPIRED").or(x->x.eq(VMemberCoupon::getStatus,"AVAILABLE").le(VMemberCoupon::getExpireTime,now)));
        else if("AVAILABLE".equals(status)) query.eq(VMemberCoupon::getStatus,status).gt(VMemberCoupon::getExpireTime,now);
        else if(status!=null && !status.isBlank()) query.eq(VMemberCoupon::getStatus,status);
        var result=query.orderByDesc(VMemberCoupon::getReceiveId).page(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(com.heng.vo.PageBounds.page(page),com.heng.vo.PageBounds.size(size)));
        return new PageResult<>(couponViews(result.getRecords(),status),result.getTotal(),result.getCurrent(),result.getSize(),result.hasNext());
    }
    public List<Map<String, Object>> myCoupons(Long memberId, String status) {
        Date now = new Date();
        List<VMemberCoupon> owned = memberCouponService.lambdaQuery()
                .eq(VMemberCoupon::getMemberId, memberId)
                .orderByDesc(VMemberCoupon::getReceiveId).list();
        return couponViews(owned,status);
    }
    private List<Map<String,Object>> couponViews(List<VMemberCoupon> owned,String status) {
        Date now=new Date();
        if (owned.isEmpty()) return List.of();
        Map<Long, VCoupon> templates = new HashMap<>();
        couponService.listByIds(owned.stream().map(VMemberCoupon::getCouponId).distinct().toList())
                .forEach(template -> templates.put(template.getCouponId(), template));

        List<Map<String, Object>> result = new ArrayList<>();
        for (VMemberCoupon coupon : owned) {
            VCoupon template = templates.get(coupon.getCouponId());
            if (template == null) continue;
            String effective = "AVAILABLE".equals(coupon.getStatus()) && coupon.getExpireTime().before(now)
                    ? "EXPIRED" : coupon.getStatus();
            if (status != null && !status.isBlank() && !status.equals(effective)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("receiveId", coupon.getReceiveId());
            row.put("couponId", template.getCouponId());
            row.put("name", template.getName());
            row.put("couponType", template.getCouponType());
            row.put("valueFen", template.getValueFen());
            row.put("discountBps", template.getDiscountBps());
            row.put("minAmountFen", template.getMinAmountFen());
            row.put("useScope", template.getUseScope());
            row.put("description", template.getDescription());
            row.put("status", effective);
            row.put("lockedOrderId",coupon.getLockedOrderId());
            row.put("expireTime", time(coupon.getExpireTime()));
            result.add(row);
        }
        return result;
    }

    public List<ProductCardVO> hotProducts(Long memberId, int limit) {
        List<VGoodsSpu> spus = spuService.lambdaQuery().eq(VGoodsSpu::getDelFlag, "0")
                .eq(VGoodsSpu::getOnSale, 1).eq(VGoodsSpu::getIsHot, 1)
                .orderByDesc(VGoodsSpu::getSalesCount).last("LIMIT " + limit).list();
        return catalogService.cards(spus, catalogService.levelDiscountBps(memberId));
    }

    private String time(Date date) {
        return date == null ? null : TIME_FORMAT.format(date.toInstant().atZone(ZoneId.systemDefault()));
    }
}
