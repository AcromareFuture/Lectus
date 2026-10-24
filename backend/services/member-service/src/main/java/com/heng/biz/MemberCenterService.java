package com.heng.biz;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heng.config.BizException;
import com.heng.config.TradeProperties;
import com.heng.mapper.AtomicWriteMapper;
import com.heng.domain.*;
import com.heng.service.*;
import com.heng.vo.PageResult;
import com.heng.vo.ProductCardVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会员域：收货地址、收藏、评论、签到、资产概览。地址和收藏都按 memberId 收口，跨会员访问一律视为不存在。
 */
@Service
public class MemberCenterService {
    private final VMemberService memberService;
    private final VMemberLevelService levelService;
    private final VMemberAddressService addressService;
    private final VMemberFavoriteService favoriteService;
    private final VMemberCommentService commentService;
    private final VMemberSigninLogService signinService;
    private final VMemberPointLedgerService pointLedgerService;
    private final VMemberBalanceLedgerService balanceLedgerService;
    private final VOrderService orderService;
    private final VOrderItemService orderItemService;
    private final VGoodsSpuService spuService;
    private final VPushRecordService pushRecordService;
    private final CatalogService catalogService;
    private final MemberAssetService memberAssetService;
    private final TradeProperties properties;
    private final AtomicWriteMapper atomic;
    private final JdbcTemplate jdbc;

    public MemberCenterService(VMemberService memberService, VMemberLevelService levelService,
                               VMemberAddressService addressService, VMemberFavoriteService favoriteService,
                               VMemberCommentService commentService, VMemberSigninLogService signinService,
                               VMemberPointLedgerService pointLedgerService,
                               VMemberBalanceLedgerService balanceLedgerService, VOrderService orderService,
                               VOrderItemService orderItemService, VGoodsSpuService spuService,
                               VPushRecordService pushRecordService,
                               CatalogService catalogService, MemberAssetService memberAssetService,
                               TradeProperties properties, AtomicWriteMapper atomic, JdbcTemplate jdbc) {
        this.memberService = memberService;
        this.levelService = levelService;
        this.addressService = addressService;
        this.favoriteService = favoriteService;
        this.commentService = commentService;
        this.signinService = signinService;
        this.pointLedgerService = pointLedgerService;
        this.balanceLedgerService = balanceLedgerService;
        this.orderService = orderService;
        this.orderItemService = orderItemService;
        this.spuService = spuService;
        this.pushRecordService = pushRecordService;
        this.catalogService = catalogService;
        this.memberAssetService = memberAssetService;
        this.properties = properties;
        this.atomic = atomic;
        this.jdbc = jdbc;
    }

    public Object levels() {
        return levelService.lambdaQuery().eq(VMemberLevel::getStatus,1).orderByAsc(VMemberLevel::getMinGrowth).list();
    }

    public VMember member(Long memberId) {
        VMember member = memberService.getById(memberId);
        if (member == null) throw new BizException("会员不存在");
        return member;
    }

    public List<VMemberAddress> addresses(Long memberId) {
        return addressService.lambdaQuery().eq(VMemberAddress::getMemberId, memberId)
                .orderByDesc(VMemberAddress::getIsDefault).orderByAsc(VMemberAddress::getAddressId).list();
    }

    @Transactional(rollbackFor = Exception.class)
    public VMemberAddress saveAddress(Long memberId, Long addressId, VMemberAddress form) {
        lockAddressOwner(memberId);
        validateAddress(form);
        VMemberAddress entity = addressId == null ? new VMemberAddress() : ownAddress(memberId, addressId);
        entity.setMemberId(memberId);
        entity.setContactName(required(form.getContactName(), "联系人"));
        entity.setPhone(required(form.getPhone(), "手机号"));
        entity.setProvince(required(form.getProvince(), "省份"));
        entity.setCity(required(form.getCity(), "城市"));
        entity.setDistrict(required(form.getDistrict(), "区县"));
        entity.setDetail(required(form.getDetail(), "详细地址"));
        boolean asDefault = Integer.valueOf(1).equals(form.getIsDefault())
                || Integer.valueOf(1).equals(entity.getIsDefault())
                || !addressService.lambdaQuery().eq(VMemberAddress::getMemberId, memberId)
                        .eq(VMemberAddress::getIsDefault, 1).exists();
        if (asDefault) clearDefault(memberId);
        entity.setIsDefault(asDefault ? 1 : 0);
        if (addressId == null) addressService.save(entity);
        else addressService.updateById(entity);
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAddress(Long memberId, Long addressId) {
        lockAddressOwner(memberId);
        VMemberAddress address = ownAddress(memberId, addressId);
        addressService.removeById(addressId);
        if (!Integer.valueOf(1).equals(address.getIsDefault())) return;
        VMemberAddress fallback = addressService.lambdaQuery().eq(VMemberAddress::getMemberId, memberId)
                .orderByAsc(VMemberAddress::getAddressId).last("LIMIT 1").one();
        if (fallback != null) {
            fallback.setIsDefault(1);
            addressService.updateById(fallback);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void setDefaultAddress(Long memberId, Long addressId) {
        lockAddressOwner(memberId);
        VMemberAddress address = ownAddress(memberId, addressId);
        clearDefault(memberId);
        address.setIsDefault(1);
        addressService.updateById(address);
    }

    public Map<String, Object> profile(Long memberId) {
        VMember member = member(memberId);
        VMemberLevel level = levelService.getById(member.getLevelId());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("memberId", member.getMemberId());
        view.put("username", member.getUsername());
        view.put("birthday", member.getBirthday() == null ? null : new java.text.SimpleDateFormat("yyyy-MM-dd").format(member.getBirthday()));
        view.put("createTime", member.getCreateTime());
        view.put("nickname", member.getNickname());
        view.put("avatarUrl", member.getAvatarUrl());
        var verifiedPhones = verifiedPhones(memberId);
        view.put("phone", verifiedPhones.isEmpty() ? member.getPhone() : verifiedPhones.get(0));
        view.put("phoneVerified", !verifiedPhones.isEmpty());
        view.put("email", member.getEmail());
        view.put("sex", member.getSex());
        view.put("points", member.getPoints());
        view.put("balanceFen", member.getBalanceFen());
        view.put("demoMode", properties.isDemoMode());
        view.put("allowDemoDeposit", properties.isDemoMode() && properties.isAllowDevDeposit());
        view.put("growth", member.getGrowth());
        view.put("levelName", level == null ? "普通会员" : level.getName());
        view.put("levelDiscountBps", level == null ? 10000 : level.getDiscountBps());
        view.put("favoriteCount", favoriteService.lambdaQuery()
                .eq(VMemberFavorite::getMemberId, memberId).count());
        view.put("addressCount", addressService.lambdaQuery()
                .eq(VMemberAddress::getMemberId, memberId).count());
        view.put("unreadMessageCount", unreadCount(memberId));
        view.put("todaySignedIn", signinService.lambdaQuery().eq(VMemberSigninLog::getMemberId, memberId)
                .eq(VMemberSigninLog::getSigninDate, Date.from(LocalDate.now()
                        .atStartOfDay(ZoneId.systemDefault()).toInstant())).exists());
        return view;
    }

    @Transactional(rollbackFor = Exception.class)
    public VMember updateProfile(Long memberId, VMember form) {
        VMember member = atomic.lockMember(memberId);
        if (member == null) throw new BizException("会员不存在");
        var update = memberService.lambdaUpdate().eq(VMember::getMemberId,memberId);
        if (form.getNickname() != null && (form.getNickname().isBlank() || form.getNickname().length() > 30)) throw new BizException("昵称应为 1–30 个字符");
        if (form.getEmail() != null && !form.getEmail().isBlank() && (form.getEmail().length() > 80 || !form.getEmail().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))) throw new BizException("请输入有效的邮箱");
        if (form.getSex() != null && !java.util.List.of("0","1","2").contains(form.getSex())) throw new BizException("性别选项无效");
        if (form.getBirthday() != null && form.getBirthday().after(new Date())) throw new BizException("生日不能晚于今天");
        if (form.getAvatarUrl() != null && !form.getAvatarUrl().matches("/api/member/avatar/[a-f0-9]{32}\\.png")) throw new BizException("请通过头像上传功能设置头像");
        if (form.getPhone() != null && !form.getPhone().isBlank() && !form.getPhone().matches("1[3-9][0-9]{9}")) throw new BizException("请输入有效的中国大陆手机号");
        if (form.getNickname() != null && !form.getNickname().isBlank()) update.set(VMember::getNickname, form.getNickname());
        if (form.getAvatarUrl() != null) update.set(VMember::getAvatarUrl, form.getAvatarUrl());
        if (form.getPhone() != null && !form.getPhone().isBlank()) {
            var verified = verifiedPhones(memberId);
            if (!verified.isEmpty() && !verified.get(0).equals(form.getPhone())) {
                throw new BizException("登录手机号已验证，不能通过资料编辑修改");
            }
            if (verified.isEmpty()) {
                if (jdbc.queryForObject("SELECT COUNT(*) FROM v_member_phone_identity WHERE phone=?", Long.class, form.getPhone()) > 0) {
                    throw new BizException("该手机号已被使用");
                }
                boolean taken = memberService.lambdaQuery().eq(VMember::getPhone, form.getPhone())
                        .ne(VMember::getMemberId, memberId).exists();
                if (taken) throw new BizException("该手机号已被使用");
                update.set(VMember::getPhone, form.getPhone());
            }
        }
        if (form.getEmail() != null) update.set(VMember::getEmail, form.getEmail());
        if (form.getSex() != null) update.set(VMember::getSex, form.getSex());
        if (form.getBirthday() != null) update.set(VMember::getBirthday,form.getBirthday());
        if (form.getNickname()!=null || form.getAvatarUrl()!=null || form.getPhone()!=null || form.getEmail()!=null || form.getSex()!=null || form.getBirthday()!=null) update.update();
        return member(memberId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> signIn(Long memberId) {
        LocalDate today = LocalDate.now();
        VMemberSigninLog existing = signinService.lambdaQuery()
                .eq(VMemberSigninLog::getMemberId, memberId)
                .eq(VMemberSigninLog::getSigninDate, Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant()))
                .one();
        if (existing != null) throw new BizException("今天已经签到过了");

        VMemberSigninLog yesterday = signinService.lambdaQuery()
                .eq(VMemberSigninLog::getMemberId, memberId)
                .eq(VMemberSigninLog::getSigninDate,
                        Date.from(today.minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()))
                .one();
        int consecutive = yesterday == null ? 1 : yesterday.getConsecutiveDays() + 1;
        int points = properties.getSigninPoints()
                + (consecutive % 7 == 0 ? properties.getSigninConsecutiveBonusPoints() : 0);

        VMemberSigninLog log = new VMemberSigninLog();
        log.setMemberId(memberId);
        log.setSigninDate(Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        log.setPointsEarned(points);
        log.setConsecutiveDays(consecutive);
        try {
            signinService.save(log);
        } catch (DuplicateKeyException e) {
            throw new BizException("今天已经签到过了");
        }
        memberAssetService.addPoints(memberId, points, "SIGNIN", log.getSigninId(),
                "SIGNIN:" + memberId + ":" + today, "每日签到");
        VMember member = member(memberId);
        return Map.of("pointsEarned", points, "consecutiveDays", consecutive, "points", member.getPoints());
    }

    public PageResult<Map<String, Object>> pointLedgers(Long memberId, long page, long size) {
        Page<VMemberPointLedger> result = pointLedgerService.page(new Page<>(com.heng.vo.PageBounds.page(page), com.heng.vo.PageBounds.size(size)),
                new QueryWrapper<VMemberPointLedger>().eq("member_id", memberId).orderByDesc("ledger_id"));
        List<Map<String, Object>> list = result.getRecords().stream().map(ledger -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ledgerId", ledger.getLedgerId());
            row.put("changePoints", ledger.getChangePoints());
            row.put("balanceAfter", ledger.getBalanceAfter());
            row.put("bizType", ledger.getBizType());
            row.put("remark", ledger.getRemark());
            row.put("createTime", ledger.getCreateTime());
            return row;
        }).toList();
        return new PageResult<>(list, result.getTotal(), result.getCurrent(), result.getSize(), result.hasNext());
    }

    public PageResult<Map<String, Object>> balanceLedgers(Long memberId, long page, long size) {
        Page<VMemberBalanceLedger> result = balanceLedgerService.page(new Page<>(com.heng.vo.PageBounds.page(page), com.heng.vo.PageBounds.size(size)),
                new QueryWrapper<VMemberBalanceLedger>().eq("member_id", memberId).orderByDesc("ledger_id"));
        List<Map<String, Object>> list = result.getRecords().stream().map(ledger -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ledgerId", ledger.getLedgerId());
            row.put("changeAmountFen", ledger.getChangeAmountFen());
            row.put("balanceAfterFen", ledger.getBalanceAfterFen());
            row.put("bizType", ledger.getBizType());
            row.put("createTime", ledger.getCreateTime());
            return row;
        }).toList();
        return new PageResult<>(list, result.getTotal(), result.getCurrent(), result.getSize(), result.hasNext());
    }

    public void deposit(Long memberId, long amountFen) {
        if (!isDevDepositAllowed()) throw new BizException("演示额度暂未开放");
        String key = "DEPOSIT:" + memberId + ":" + System.currentTimeMillis();
        memberAssetService.deposit(memberId, amountFen, key);
    }

    public boolean isDevDepositAllowed() {
        return properties.isDemoMode() && properties.isAllowDevDeposit();
    }

    public PageResult<ProductCardVO> favoritePage(Long memberId,long page,long size) {
        var result=favoriteService.lambdaQuery().eq(VMemberFavorite::getMemberId,memberId).orderByDesc(VMemberFavorite::getFavoriteId)
            .page(new Page<>(com.heng.vo.PageBounds.page(page),com.heng.vo.PageBounds.size(size)));
        var ids=result.getRecords().stream().map(VMemberFavorite::getSpuId).toList();
        var cards=ids.isEmpty()?List.<ProductCardVO>of():catalogService.cards(spuService.listByIds(ids),catalogService.levelDiscountBps(memberId));
        Map<Long,ProductCardVO> byId=new LinkedHashMap<>();cards.forEach(c->byId.put(c.spuId(),c));
        return new PageResult<>(ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList(),result.getTotal(),result.getCurrent(),result.getSize(),result.hasNext());
    }
    public List<ProductCardVO> favorites(Long memberId) {
        List<Long> spuIds = favoriteService.lambdaQuery().eq(VMemberFavorite::getMemberId, memberId)
                .orderByDesc(VMemberFavorite::getCreateTime).last("LIMIT 50").list().stream()
                .map(VMemberFavorite::getSpuId).toList();
        if (spuIds.isEmpty()) return List.of();
        List<VGoodsSpu> spus = spuService.listByIds(spuIds);
        int bps = catalogService.levelDiscountBps(memberId);
        List<ProductCardVO> cards = catalogService.cards(spus, bps);
        Map<Long, ProductCardVO> byId = new LinkedHashMap<>();
        cards.forEach(card -> byId.put(card.spuId(), card));
        return spuIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean toggleFavorite(Long memberId, Long spuId) {
        if (spuService.getById(spuId) == null) throw new BizException("商品不存在");
        VMemberFavorite existing = favoriteService.lambdaQuery()
                .eq(VMemberFavorite::getMemberId, memberId).eq(VMemberFavorite::getSpuId, spuId).one();
        if (existing != null) {
            favoriteService.removeById(existing.getFavoriteId());
            return false;
        }
        VMemberFavorite favorite = new VMemberFavorite();
        favorite.setMemberId(memberId);
        favorite.setSpuId(spuId);
        try {
            favoriteService.save(favorite);
        } catch (DuplicateKeyException e) {
            return true;
        }
        return true;
    }

    public void removeFavorite(Long memberId, Long spuId) {
        favoriteService.lambdaUpdate().eq(VMemberFavorite::getMemberId, memberId)
                .eq(VMemberFavorite::getSpuId, spuId).remove();
    }

    public List<Map<String, Object>> orderReviews(Long memberId, Long orderId) {
        VOrder order = orderService.lambdaQuery().eq(VOrder::getOrderId, orderId)
                .eq(VOrder::getMemberId, memberId).one();
        if (order == null) throw new BizException("订单不存在");
        return commentService.lambdaQuery().eq(VMemberComment::getMemberId, memberId)
                .eq(VMemberComment::getOrderId, orderId).list().stream().map(comment -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("commentId", comment.getCommentId());
                    row.put("spuId", comment.getSpuId());
                    row.put("rating", comment.getRating());
                    row.put("content", comment.getContent());
                    return row;
                }).toList();
    }

    /** 评论权限：只允许已完成订单的购买人评论该订单内的商品，且同一订单同一商品只能评一次。 */
    @Transactional(rollbackFor = Exception.class)
    public VMemberComment comment(Long memberId, Long orderId, Long spuId, Integer rating, String content,
                                  List<String> imageUrls) {
        VOrder order = atomic.lockOrder(orderId);
        if (order == null || !memberId.equals(order.getMemberId())) throw new BizException("订单不存在");
        if (!"COMPLETED".equals(order.getStatus())) throw new BizException("收货后才能评价");
        VOrderItem target = orderItemService.lambdaQuery().eq(VOrderItem::getOrderId, orderId).list().stream()
                .filter(item -> {
                    Object value = item.getGoodsSnapshot() == null ? null : item.getGoodsSnapshot().get("spuId");
                    return value instanceof Number number && number.longValue() == spuId;
                }).findFirst().orElseThrow(() -> new BizException("该订单不包含此商品"));
        atomic.lockMember(memberId);
        boolean commented = commentService.lambdaQuery().eq(VMemberComment::getOrderId, orderId)
                .eq(VMemberComment::getSpuId, spuId).last("FOR UPDATE").one() != null;
        if (commented) throw new BizException("该商品已评价过了");
        if (rating == null || rating < 1 || rating > 5) throw new BizException("评分需在 1 到 5 之间");

        VMemberComment comment = new VMemberComment();
        comment.setMemberId(memberId);
        comment.setOrderId(orderId);
        comment.setSpuId(spuId);
        comment.setSkuId(target.getGoodsSnapshot().get("skuId") instanceof Number number
                ? number.longValue() : null);
        comment.setRating(rating);
        if (content == null || content.isBlank() || content.trim().length() > 1000)
            throw new BizException("评价内容需为 1 到 1000 个字符");
        if (imageUrls != null && !imageUrls.isEmpty())
            throw new BizException("暂不支持评价图片，请提交文字评价");
        comment.setContent(content.trim());
        comment.setImageUrls(imageUrls);
        comment.setStatus(1);
        try { commentService.save(comment); } catch (DuplicateKeyException e) { throw new BizException("该商品已评价过了"); }
        return comment;
    }

    @Transactional(rollbackFor=Exception.class)
    public void setAvatar(Long memberId,String url) {
        lockAddressOwner(memberId);
        memberService.lambdaUpdate().eq(VMember::getMemberId,memberId).set(VMember::getAvatarUrl,url).update();
    }
    public boolean avatarReferenced(String url) {return memberService.lambdaQuery().eq(VMember::getAvatarUrl,url).exists();}
    public long unreadCount(Long memberId) {
        return pushRecordService.lambdaQuery().eq(VPushRecord::getMemberId, memberId)
                .isNull(VPushRecord::getReadTime).count();
    }

    private VMemberAddress ownAddress(Long memberId, Long addressId) {
        VMemberAddress address = addressService.lambdaQuery()
                .eq(VMemberAddress::getAddressId, addressId)
                .eq(VMemberAddress::getMemberId, memberId).one();
        if (address == null) throw new BizException("收货地址不存在");
        return address;
    }

    private void lockAddressOwner(Long memberId) {
        VMember member = atomic.lockMember(memberId);
        if (member == null || !Integer.valueOf(1).equals(member.getStatus())) {
            throw new BizException("会员不存在或已停用");
        }
    }

    private void clearDefault(Long memberId) {
        addressService.lambdaUpdate().eq(VMemberAddress::getMemberId, memberId)
                .eq(VMemberAddress::getIsDefault, 1).set(VMemberAddress::getIsDefault, 0).update();
    }

    private void validateAddress(VMemberAddress form) {
        String[] values={form.getContactName(),form.getProvince(),form.getCity(),form.getDistrict(),form.getDetail()};
        int[] limits={40,40,40,40,200};
        for(int i=0;i<values.length;i++) if(values[i]==null || values[i].isBlank() || values[i].trim().length()>limits[i])
            throw new BizException("地址字段不能为空或超过长度上限");
        if(form.getPhone()==null || !form.getPhone().matches("1[3-9][0-9]{9}")) throw new BizException("请输入有效的中国大陆手机号");
        if(form.getIsDefault()!=null && form.getIsDefault()!=0 && form.getIsDefault()!=1) throw new BizException("默认地址选项无效");
    }
    private String required(String value, String label) {
        if (value == null || value.isBlank()) throw new BizException(label + "不能为空");
        return value.trim();
    }

    private List<String> verifiedPhones(Long memberId) {
        return jdbc.query("SELECT phone FROM v_member_phone_identity WHERE member_id=?", (rs, row) -> rs.getString(1), memberId);
    }
}
