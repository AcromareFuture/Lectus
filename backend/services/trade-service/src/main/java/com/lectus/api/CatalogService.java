package com.lectus.api;

import static com.lectus.api.Contracts.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
    private final JdbcTemplate db;
    public CatalogService(JdbcTemplate db) {this.db=db;}
    public Map<String,Object> list(String category, String query, String sort, Long min, Long max, int page, int size) {
        if(page<0 || page>10000 || size<1 || size>100 || (min!=null && min<0) || (max!=null && max<0) || (min!=null && max!=null && min>max))
            throw ApiException.bad("INVALID_FILTER","分页或价格范围不正确");
        StringBuilder where=new StringBuilder(" WHERE active=TRUE");
        List<Object> args=new ArrayList<>();
        if(category!=null && !category.isBlank() && !category.equals("全部")) {where.append(" AND category=?");args.add(category);}
        if(query!=null && !query.isBlank()) {
            if(query.length()>100) throw ApiException.bad("INVALID_QUERY","搜索词过长");
            for(String word:query.strip().toLowerCase(java.util.Locale.ROOT).split("\\s+")) {
                where.append(" AND POSITION(? IN LOWER(CONCAT(name,' ',english_name,' ',brand,' ',category)))>0");args.add(word);
            }
        }
        if(min!=null) {where.append(" AND price_cents>=?");args.add(min);}
        if(max!=null) {where.append(" AND price_cents<=?");args.add(max);}
        String order=switch(sort) {
            case "recommended" -> "id";
            case "price_asc" -> "price_cents,id";
            case "price_desc" -> "price_cents DESC,id";
            case "popular" -> "popularity DESC,id";
            default -> throw ApiException.bad("INVALID_SORT","排序方式不正确");
        };
        Long count=db.queryForObject("SELECT COUNT(*) FROM products"+where,Long.class,args.toArray());
        List<Object> pageArgs=new ArrayList<>(args);pageArgs.add(size);pageArgs.add(page*size);
        var items=db.query("SELECT * FROM products"+where+" ORDER BY "+order+" LIMIT ? OFFSET ?",
                (rs,n)->new Product(rs.getString("id"),rs.getString("name"),rs.getString("english_name"),rs.getString("brand"),rs.getString("category"),
                        rs.getLong("price_cents"),rs.getString("image"),rs.getString("description"),rs.getString("tag"),skus(rs.getString("id"))),pageArgs.toArray());
        return Map.of("items",items,"total",count,"page",page,"size",size,"currency","CNY");
    }
    public List<Sku> skus(String productId) {
        return db.query("SELECT * FROM product_skus WHERE product_id=? ORDER BY size,color",
                (rs,n)->new Sku(rs.getString("id"),productId,rs.getString("size"),rs.getString("color"),rs.getInt("stock")),productId);
    }
    public Product product(String id) {
        var rows=db.query("SELECT * FROM products WHERE id=? AND active=TRUE",
                (rs,n)->new Product(rs.getString("id"),rs.getString("name"),rs.getString("english_name"),rs.getString("brand"),rs.getString("category"),
                        rs.getLong("price_cents"),rs.getString("image"),rs.getString("description"),rs.getString("tag"),skus(id)),id);
        if(rows.isEmpty()) throw ApiException.missing();return rows.getFirst();
    }
}
