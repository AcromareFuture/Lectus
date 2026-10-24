package com.lectus.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Source catalog is intentionally separate from the prototype's sellable inventory. */
@Service
public class SourceCatalogService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final StringRedisTemplate redis;
    private final boolean cache;
    private volatile long retryCacheAt;
    public SourceCatalogService(JdbcTemplate db, ObjectMapper json, StringRedisTemplate redis,
                                @Value("${lectus.catalog-cache:false}") boolean cache) {
        this.db=db; this.json=json; this.redis=redis; this.cache=cache;
    }
    private JsonNode cached(String suffix, Supplier<Object> loader) {
        String revision=db.queryForObject("SELECT value_text FROM catalog_meta WHERE id='revision'",String.class);
        String key="lectus:catalog:v1:"+revision+":"+Digests.sha256(suffix);
        boolean useCache=cache && System.currentTimeMillis()>=retryCacheAt;
        if(useCache) try {
            String hit=redis.opsForValue().get(key);
            if(hit!=null) return json.readTree(hit);
        } catch(Exception e) { retryCacheAt=System.currentTimeMillis()+30000; useCache=false; }
        JsonNode value=json.valueToTree(loader.get());
        if(useCache) try { redis.opsForValue().set(key,value.toString(),Duration.ofMinutes(5)); }
        catch(Exception e) { retryCacheAt=System.currentTimeMillis()+30000; }
        return value;
    }
    public JsonNode metadata() {
        return cached("metadata",()->{
            var categories=db.queryForList("SELECT category AS name,COUNT(*) AS count FROM catalog_products WHERE state<>'HIDDEN' GROUP BY category ORDER BY category");
            var brands=db.queryForList("SELECT b.id,b.name,COUNT(*) AS count FROM catalog_brands b JOIN catalog_products p ON p.brand_id=b.id WHERE p.state<>'HIDDEN' GROUP BY b.id,b.name ORDER BY b.name");
            return Map.of("categories",categories,"brands",brands,"total",db.queryForObject("SELECT COUNT(*) FROM catalog_products WHERE state<>'HIDDEN'",Long.class),
                    "skuCount",db.queryForObject("SELECT COUNT(*) FROM catalog_skus s JOIN catalog_products p ON p.id=s.product_id WHERE p.state<>'HIDDEN'",Long.class));
        });
    }
    public JsonNode list(String category,String brand,String q,int page,int size) {
        if(page<0 || page>10000 || size<1 || size>60 || q.length()>100 || category.length()>40 || brand.length()>32)
            throw ApiException.bad("INVALID_FILTER","请检查搜索条件和分页范围");
        return cached("list:"+Arrays.asList(category,brand,q,page,size),()->{
            var args=new ArrayList<Object>();
            StringBuilder where=new StringBuilder(" WHERE p.state<>'HIDDEN'");
            if(!category.isBlank()) {where.append(" AND p.category=?");args.add(category);}
            if(!brand.isBlank()) {where.append(" AND p.brand_id=?");args.add(brand);}
            for(String word:q.strip().toLowerCase(Locale.ROOT).split("\\s+")) if(!word.isBlank()) {
                where.append(" AND POSITION(? IN LOWER(CONCAT(p.name,' ',b.name,' ',p.category)))>0");args.add(word);
            }
            String from=" FROM catalog_products p JOIN catalog_brands b ON b.id=p.brand_id LEFT JOIN catalog_images i ON i.id=p.main_image_id AND i.status='ok'";
            Long total=db.queryForObject("SELECT COUNT(*)"+from+where,Long.class,args.toArray());
            args.add(size);args.add(page*size);
            var items=db.queryForList("SELECT p.id,p.name,p.category,p.state,p.featured,b.name AS brand,i.thumb_key,i.detail_key,(SELECT COUNT(*) FROM catalog_colorways c WHERE c.product_id=p.id) AS colorway_count,(SELECT COUNT(*) FROM catalog_skus s WHERE s.product_id=p.id) AS sku_count"+from+where+" ORDER BY p.featured DESC,p.id LIMIT ? OFFSET ?",args.toArray())
                    .stream().map(this::summary).toList();
            return Map.of("items",items,"total",total,"page",page,"size",size,"hasMore",(long)(page+1)*size<total);
        });
    }
    private Map<String,Object> summary(Map<String,Object> row) {
        var item=new LinkedHashMap<String,Object>();
        for(String key:List.of("id","name","brand","category","state","featured")) item.put(key,row.get(key));
        item.put("image",row.get("thumb_key"));item.put("detailImage",row.get("detail_key"));
        item.put("colorwayCount",row.get("colorway_count"));item.put("skuCount",row.get("sku_count"));
        // The source package has no confirmed selling currency, price or own inventory.
        item.put("priceCents",null);item.put("currency",null);item.put("purchasable",false);
        return item;
    }
    public JsonNode product(String id) {
        if(!id.matches("SPU-[A-F0-9]{12}")) throw ApiException.missing();
        return cached("product:"+id,()->{
            var rows=db.queryForList("SELECT p.id,p.name,p.category,p.state,p.featured,b.name AS brand,i.thumb_key,i.detail_key,(SELECT COUNT(*) FROM catalog_colorways c WHERE c.product_id=p.id) AS colorway_count,(SELECT COUNT(*) FROM catalog_skus s WHERE s.product_id=p.id) AS sku_count FROM catalog_products p JOIN catalog_brands b ON b.id=p.brand_id LEFT JOIN catalog_images i ON i.id=p.main_image_id AND i.status='ok' WHERE p.id=? AND p.state<>'HIDDEN'",id);
            if(rows.isEmpty()) throw ApiException.missing();
            var item=summary(rows.getFirst());
            var links=db.queryForList("SELECT l.sku_id,l.image_id FROM catalog_sku_images l JOIN catalog_skus s ON s.id=l.sku_id JOIN catalog_images i ON i.id=l.image_id WHERE s.product_id=? AND i.status='ok' AND l.review_required=FALSE ORDER BY i.source_position,i.id",id);
            var skuImages=new LinkedHashMap<String,List<String>>();
            for(var link:links) skuImages.computeIfAbsent((String)link.get("sku_id"),k->new ArrayList<>()).add((String)link.get("image_id"));
            var skus=db.queryForList("SELECT * FROM catalog_skus WHERE product_id=? ORDER BY id",id).stream().map(s->{
                var sku=new LinkedHashMap<String,Object>();
                sku.put("id",s.get("id"));sku.put("colorwayId",s.get("colorway_id"));sku.put("sourceSku",s.get("source_sku"));
                sku.put("specs",parse((String)s.get("specs_json")));
                sku.put("imageIds",skuImages.getOrDefault((String)s.get("id"),List.of()));
                sku.put("priceCents",s.get("sale_price_cents"));sku.put("currency",s.get("sale_currency"));sku.put("stock",s.get("own_stock"));
                sku.put("purchasable",false);return sku;
            }).toList();
            item.put("skus",skus);
            item.put("images",db.queryForList("SELECT id,thumb_key AS thumbnail,detail_key AS image FROM catalog_images WHERE product_id=? AND status='ok' ORDER BY source_position,id",id));
            var readyModels=new HashMap<String,Map<String,Object>>();
            db.queryForList("SELECT * FROM catalog_models WHERE product_id=? AND status='READY' AND reviewed=TRUE AND object_key IS NOT NULL AND sha256 IS NOT NULL AND license_name IS NOT NULL",id)
                    .forEach(model->readyModels.put((String)model.get("colorway_id"),model));
            item.put("colorways",db.queryForList("SELECT id,name,color_explicit,style_code,main_image_id FROM catalog_colorways WHERE product_id=? ORDER BY id",id).stream().map(c->{
                var color=new LinkedHashMap<String,Object>();
                color.put("id",c.get("id"));color.put("name",c.get("name"));color.put("explicit",c.get("color_explicit"));color.put("styleCode",c.get("style_code"));color.put("mainImageId",c.get("main_image_id"));
                var allowed=new LinkedHashSet<String>();
                skus.stream().filter(s->c.get("id").equals(s.get("colorwayId"))).forEach(s->{
                    @SuppressWarnings("unchecked") var ids=(List<String>)s.get("imageIds");allowed.addAll(ids);
                });
                color.put("imageIds",allowed);
                var readyModel=readyModels.get(c.get("id"));
                color.put("model",readyModel==null?null:model(readyModel,(String)item.get("name")));
                color.put("modelStatus",readyModel==null?"PENDING_ASSET":"READY");
                return color;
            }).toList());
            return item;
        });
    }
    private JsonNode parse(String text) {
        try {return json.readTree(text);}catch(Exception e) {throw new IllegalStateException("Invalid stored catalog JSON",e);}
    }
    private Map<String,Object> model(Map<String,Object> row,String name) {
        var value=new LinkedHashMap<String,Object>();value.put("asset",row.get("object_key"));value.put("sha256",row.get("sha256"));
        value.put("alt",name);value.put("sample",false);value.put("productId",row.get("product_id"));value.put("colorwayId",row.get("colorway_id"));value.put("variantName",row.get("material_variant"));
        var attribution=new LinkedHashMap<String,Object>();attribution.put("copyright",row.get("copyright_name"));attribution.put("source",row.get("source_url"));attribution.put("license",row.get("license_name"));attribution.put("licenseUrl",row.get("license_url"));
        value.put("attribution",attribution);return value;
    }
}
