package com.heng.biz;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heng.config.BizException;
import com.heng.domain.*;
import com.heng.mapper.AtomicWriteMapper;
import com.heng.service.*;
import com.heng.vo.*;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * C 端商品与首页只读查询。列表和详情都按当前会员等级折算价格，未登录时按原价。
 */
@Service
public class CatalogService {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final VGoodsSpuService spuService;
    private final VGoodsSkuService skuService;
    private final VCategoryService categoryService;
    private final VLabelService labelService;
    private final VGoodsLabelService goodsLabelService;
    private final VStockService stockService;
    private final VMemberService memberService;
    private final VMemberLevelService levelService;
    private final VMemberCommentService commentService;
    private final VMemberFavoriteService favoriteService;
    private final VAdvertisementService advertisementService;
    private final VArticleService articleService;
    private final PricingService pricingService;
    private final AtomicWriteMapper atomic;

    public CatalogService(VGoodsSpuService spuService, VGoodsSkuService skuService, VCategoryService categoryService,
                          VLabelService labelService, VGoodsLabelService goodsLabelService, VStockService stockService,
                          VMemberService memberService, VMemberLevelService levelService,
                          VMemberCommentService commentService, VMemberFavoriteService favoriteService,
                          VAdvertisementService advertisementService, VArticleService articleService,
                          PricingService pricingService, AtomicWriteMapper atomic) {
        this.spuService = spuService;
        this.skuService = skuService;
        this.categoryService = categoryService;
        this.labelService = labelService;
        this.goodsLabelService = goodsLabelService;
        this.stockService = stockService;
        this.memberService = memberService;
        this.levelService = levelService;
        this.commentService = commentService;
        this.favoriteService = favoriteService;
        this.advertisementService = advertisementService;
        this.articleService = articleService;
        this.pricingService = pricingService;
        this.atomic = atomic;
    }

    public int levelDiscountBps(Long memberId) {
        if (memberId == null) return 10000;
        VMember member = memberService.getById(memberId);
        if (member == null || member.getLevelId() == null) return 10000;
        VMemberLevel level = levelService.getById(member.getLevelId());
        if (level == null || level.getStatus() == null || level.getStatus() != 1) return 10000;
        return level.getDiscountBps() == null ? 10000 : level.getDiscountBps();
    }

    public PageResult<ProductCardVO> page(Long categoryId, String keyword, String sort,
                                         long page, long size, Long memberId) {
        return page(categoryId,keyword,sort,page,size,memberId,false,false);
    }

    public PageResult<ProductCardVO> page(Long categoryId,String keyword,String sort,long page,long size,Long memberId,boolean offers,boolean favorites) {
        int bps = levelDiscountBps(memberId);
        QueryWrapper<VGoodsSpu> wrapper = new QueryWrapper<>();
        wrapper.eq("del_flag", "0").eq("on_sale", 1);
        if (categoryId != null) {
            List<Long> ids = descendants(categoryId);
            wrapper.in("category_id", ids);
        }
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like("name", trimmed).or().like("subtitle", trimmed));
        }
        if(offers) wrapper.apply("original_price_fen > min_price_fen");
        if(favorites) {
            if(memberId == null) throw new BizException("请先登录后查看收藏");
            var ids=favoriteService.lambdaQuery().eq(VMemberFavorite::getMemberId,memberId).list().stream().map(VMemberFavorite::getSpuId).toList();
            if(ids.isEmpty()) return new PageResult<>(List.of(),0L,Math.max(page,1),size,false);
            wrapper.in("spu_id",ids);
        }
        applySort(wrapper, sort);

        Page<VGoodsSpu> result = spuService.page(new Page<>(Math.max(page,1), Math.max(size,1)), wrapper);
        return new PageResult<>(cards(result.getRecords(), bps), result.getTotal(),
                result.getCurrent(), result.getSize(), result.hasNext());
    }

    private void applySort(QueryWrapper<VGoodsSpu> wrapper, String sort) {
        switch (sort == null ? "" : sort) {
            case "sales" -> wrapper.orderByDesc("sales_count").orderByAsc("sort");
            case "price_asc" -> wrapper.orderByAsc("min_price_fen");
            case "price_desc" -> wrapper.orderByDesc("min_price_fen");
            case "new" -> wrapper.orderByDesc("is_new").orderByDesc("create_time");
            default -> wrapper.orderByAsc("sort").orderByDesc("is_hot");
        }
    }

    public ProductDetailVO detail(Long spuId, Long memberId) {
        int bps = levelDiscountBps(memberId);
        VGoodsSpu spu = spuService.lambdaQuery().eq(VGoodsSpu::getSpuId, spuId)
                .eq(VGoodsSpu::getDelFlag, "0").one();
        if (spu == null) throw new BizException("商品不存在");
        List<VGoodsSku> skus = skuService.lambdaQuery().eq(VGoodsSku::getSpuId, spuId)
                .eq(VGoodsSku::getStatus, 1).orderByAsc(VGoodsSku::getSkuId).list();
        Map<Long, Integer> stockBySku = stockBySku(skus.stream().map(VGoodsSku::getSkuId).toList());

        List<SkuVO> skuViews = skus.stream().map(sku -> new SkuVO(sku.getSkuId(), sku.getSpuId(),
                pricingService.specText(sku.getSpecValue()), sku.getSpecImageUrl(), sku.getSkuCode(),
                sku.getPriceFen(), sku.getPriceFen() * bps / 10000, sku.getOriginalPriceFen(),
                stockBySku.getOrDefault(sku.getSkuId(), 0), sku.getSalesCount(), sku.getWeightGram(),
                sku.getStatus())).toList();

        ProductCardVO card = cards(List.of(spu), bps).get(0);
        List<CommentVO> comments = topComments(spuId, 3);
        boolean favorited = memberId != null && favoriteService.lambdaQuery()
                .eq(VMemberFavorite::getMemberId, memberId).eq(VMemberFavorite::getSpuId, spuId).exists();

        return new ProductDetailVO(card, spu.getDescription(), spu.getVideoUrl(), skuViews, favorited, comments,
                Integer.valueOf(1).equals(spu.getOnSale()));
    }

    public PageResult<CommentVO> commentPage(Long spuId, long page, long size) {
        Page<VMemberComment> result = commentService.page(new Page<>(Math.max(page,1), Math.max(size,1)),
                new QueryWrapper<VMemberComment>().eq("spu_id", spuId).eq("status", 1)
                        .orderByDesc("create_time"));
        List<CommentVO> list = result.getRecords().stream().map(this::toComment).toList();
        return new PageResult<>(list, result.getTotal(), result.getCurrent(), result.getSize(), result.hasNext());
    }

    public List<CategoryNodeVO> categoryTree() {
        List<VCategory> all = enabledCategories();
        Map<Long, List<VCategory>> children = new HashMap<>();
        for (VCategory category : all) {
            if (category.getParentId() != null && category.getParentId() != 0) {
                children.computeIfAbsent(category.getParentId(), key -> new ArrayList<>()).add(category);
            }
        }
        List<CategoryNodeVO> roots = new ArrayList<>();
        for (VCategory category : all) {
            if (category.getParentId() == null || category.getParentId() == 0) {
                roots.add(node(category, children));
            }
        }
        return roots;
    }

    public Map<String, Object> home(Long memberId) {
        int bps = levelDiscountBps(memberId);
        Map<String, Object> home = new LinkedHashMap<>();
        home.put("banners", banners("HOME_BANNER"));
        home.put("categories", categoryTree());
        home.put("hot", cards(spuService.lambdaQuery().eq(VGoodsSpu::getDelFlag, "0")
                .eq(VGoodsSpu::getOnSale, 1).eq(VGoodsSpu::getIsHot, 1)
                .orderByAsc(VGoodsSpu::getSort).last("LIMIT 8").list(), bps));
        home.put("new", cards(spuService.lambdaQuery().eq(VGoodsSpu::getDelFlag, "0")
                .eq(VGoodsSpu::getOnSale, 1).eq(VGoodsSpu::getIsNew, 1)
                .orderByDesc(VGoodsSpu::getCreateTime).last("LIMIT 4").list(), bps));
        home.put("notices", articles("NOTICE", 3));
        return home;
    }

    public List<BannerVO> banners(String position) {
        return advertisementService.lambdaQuery()
                .eq(VAdvertisement::getPosition, position).eq(VAdvertisement::getStatus, 1)
                .le(VAdvertisement::getStartTime, new Date())
                .ge(VAdvertisement::getEndTime, new Date())
                .orderByAsc(VAdvertisement::getSort).list().stream()
                .map(item -> new BannerVO(item.getAdvertisementId(), item.getTitle(), item.getImageUrl(),
                        item.getLinkType(), item.getLinkValue())).toList();
    }

    public List<Map<String, Object>> articles(String category, int limit) {
        return articleService.lambdaQuery().eq(VArticle::getStatus, "PUBLISHED")
                .eq(category != null, VArticle::getCategory, category)
                .orderByDesc(VArticle::getIsTop).orderByAsc(VArticle::getSort)
                .last("LIMIT " + limit).list().stream().map(this::articleView).toList();
    }

    public PageResult<Map<String,Object>> articlePage(String category,long page,long size) {
        var result=articleService.lambdaQuery().eq(VArticle::getStatus,"PUBLISHED").eq(category!=null && !category.isBlank(),VArticle::getCategory,category)
            .orderByDesc(VArticle::getIsTop).orderByAsc(VArticle::getSort).orderByDesc(VArticle::getArticleId)
            .page(new Page<>(com.heng.vo.PageBounds.page(page),com.heng.vo.PageBounds.size(size)));
        return new PageResult<>(result.getRecords().stream().map(this::articleView).toList(),result.getTotal(),result.getCurrent(),result.getSize(),result.hasNext());
    }
    public Map<String, Object> article(Long articleId) {
        VArticle article = articleService.getById(articleId);
        if (article == null || !"PUBLISHED".equals(article.getStatus())) throw new BizException("内容不存在");
        atomic.increaseArticleView(articleId);
        Map<String, Object> view = articleView(article);
        view.put("viewCount", article.getViewCount() + 1);
        view.put("content", article.getContent());
        return view;
    }

    public List<ProductCardVO> cards(List<VGoodsSpu> spus, int bps) {
        if (spus.isEmpty()) return List.of();
        List<Long> spuIds = spus.stream().map(VGoodsSpu::getSpuId).toList();
        Map<Long, String> categoryNames = new HashMap<>();
        categoryService.list().forEach(category -> categoryNames.put(category.getCategoryId(), category.getName()));

        List<VGoodsSku> skus = skuService.lambdaQuery().in(VGoodsSku::getSpuId, spuIds)
                .eq(VGoodsSku::getStatus, 1).list();
        Map<Long, Integer> stockBySkuId = stockBySku(skus.stream().map(VGoodsSku::getSkuId).toList());
        Map<Long, Integer> stockBySpu = new HashMap<>();
        Map<Long, List<VGoodsSku>> skusBySpu = new HashMap<>();
        for (VGoodsSku sku : skus) {
            skusBySpu.computeIfAbsent(sku.getSpuId(), key -> new ArrayList<>()).add(sku);
            stockBySpu.merge(sku.getSpuId(), stockBySkuId.getOrDefault(sku.getSkuId(), 0), Integer::sum);
        }

        Map<Long, List<LabelVO>> labelsBySpu = labelsBySpu(spuIds);
        Map<Long, double[]> ratingBySpu = ratingsBySpu(spuIds);

        List<ProductCardVO> cards = new ArrayList<>();
        for (VGoodsSpu spu : spus) {
            VGoodsSku cheapest = skusBySpu.getOrDefault(spu.getSpuId(), List.of()).stream()
                    .min(Comparator.comparingLong(sku -> sku.getPriceFen() * bps / 10000)).orElse(null);
            long minLevelPrice = cheapest == null ? spu.getMinPriceFen() : cheapest.getPriceFen() * bps / 10000;
            double[] rating = ratingBySpu.getOrDefault(spu.getSpuId(), new double[]{0, 0});
            cards.add(new ProductCardVO(spu.getSpuId(), spu.getCategoryId(),
                    categoryNames.get(spu.getCategoryId()), spu.getName(), spu.getSubtitle(),
                    spu.getMainImageUrl(), spu.getImageUrls() == null ? List.of() : spu.getImageUrls(),
                    spu.getMinPriceFen(), minLevelPrice, spu.getOriginalPriceFen(), spu.getSalesCount(),
                    stockBySpu.getOrDefault(spu.getSpuId(), 0), spu.getIsHot() != null && spu.getIsHot() == 1,
                    spu.getIsNew() != null && spu.getIsNew() == 1,
                    rating[1] == 0 ? null : Math.round(rating[0] / rating[1] * 10) / 10.0,
                    (int) rating[1], labelsBySpu.getOrDefault(spu.getSpuId(), List.of()), spu.getSort(),
                    cheapest == null ? null : cheapest.getSkuId(),
                    cheapest == null ? null : pricingService.specText(cheapest.getSpecValue())));
        }
        return cards;
    }

    private Map<Long, Integer> stockBySku(List<Long> skuIds) {
        Map<Long, Integer> map = new HashMap<>();
        if (skuIds.isEmpty()) return map;
        stockService.lambdaQuery().in(VStock::getSkuId, skuIds).list()
                .forEach(stock -> map.put(stock.getSkuId(), stock.getAvailableStock()));
        return map;
    }

    private Map<Long, List<LabelVO>> labelsBySpu(List<Long> spuIds) {
        List<VGoodsLabel> relations = goodsLabelService.lambdaQuery().in(VGoodsLabel::getSpuId, spuIds).list();
        if (relations.isEmpty()) return Map.of();
        Map<Long, VLabel> labels = new HashMap<>();
        labelService.lambdaQuery().eq(VLabel::getStatus, 1).list()
                .forEach(label -> labels.put(label.getLabelId(), label));
        Map<Long, List<LabelVO>> result = new HashMap<>();
        for (VGoodsLabel relation : relations) {
            VLabel label = labels.get(relation.getLabelId());
            if (label == null) continue;
            result.computeIfAbsent(relation.getSpuId(), key -> new ArrayList<>())
                    .add(new LabelVO(label.getName(), label.getColor(), label.getIconUrl()));
        }
        return result;
    }

    /** 评分只统计展示的评论，返回 [评分总和, 评论数]。 */
    private Map<Long, double[]> ratingsBySpu(List<Long> spuIds) {
        Map<Long, double[]> map = new HashMap<>();
        for (VMemberComment comment : commentService.lambdaQuery()
                .in(VMemberComment::getSpuId, spuIds).eq(VMemberComment::getStatus, 1)
                .select(VMemberComment::getSpuId, VMemberComment::getRating).list()) {
            double[] value = map.computeIfAbsent(comment.getSpuId(), key -> new double[]{0, 0});
            value[0] += comment.getRating() == null ? 0 : comment.getRating();
            value[1] += 1;
        }
        return map;
    }

    private List<CommentVO> topComments(Long spuId, int limit) {
        return commentService.lambdaQuery().eq(VMemberComment::getSpuId, spuId).eq(VMemberComment::getStatus, 1)
                .orderByDesc(VMemberComment::getCreateTime).last("LIMIT " + limit).list()
                .stream().map(this::toComment).toList();
    }

    private CommentVO toComment(VMemberComment comment) {
        VMember member = memberService.getById(comment.getMemberId());
        return new CommentVO(comment.getCommentId(),
                member == null ? "匿名用户" : member.getNickname(),
                member == null ? null : member.getAvatarUrl(), comment.getRating(), comment.getContent(),
                comment.getImageUrls(), comment.getLikeCount(), comment.getMerchantReply(),
                comment.getCreateTime() == null ? null
                        : TIME_FORMAT.format(comment.getCreateTime().toInstant().atZone(ZoneId.systemDefault())));
    }

    private Map<String, Object> articleView(VArticle article) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("articleId", article.getArticleId());
        view.put("title", article.getTitle());
        view.put("category", article.getCategory());
        view.put("summary", article.getSummary());
        view.put("coverUrl", article.getCoverUrl());
        view.put("author", article.getAuthor());
        view.put("viewCount", article.getViewCount());
        view.put("publishTime", article.getPublishTime() == null ? null
                : TIME_FORMAT.format(article.getPublishTime().toInstant().atZone(ZoneId.systemDefault())));
        return view;
    }

    private List<VCategory> enabledCategories() {
        return categoryService.lambdaQuery().eq(VCategory::getStatus, 1)
                .orderByAsc(VCategory::getSort).list();
    }

    private CategoryNodeVO node(VCategory category, Map<Long, List<VCategory>> children) {
        List<VCategory> childList = children.getOrDefault(category.getCategoryId(), List.of());
        List<CategoryNodeVO> nodes = childList.stream().map(child -> node(child, children)).toList();
        return new CategoryNodeVO(category.getCategoryId(), category.getName(), category.getIconUrl(),
                category.getImageUrl(), category.getSort(), nodes);
    }

    /** 选择父分类时同时包含其下所有子分类的商品。 */
    private List<Long> descendants(Long categoryId) {
        List<VCategory> all = enabledCategories();
        Map<Long, List<Long>> childIds = new HashMap<>();
        all.forEach(category -> childIds.computeIfAbsent(category.getParentId(), key -> new ArrayList<>())
                .add(category.getCategoryId()));
        List<Long> result = new ArrayList<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(categoryId);
        while (!stack.isEmpty()) {
            Long current = stack.pop();
            result.add(current);
            childIds.getOrDefault(current, List.of()).forEach(stack::push);
        }
        return result;
    }
}
