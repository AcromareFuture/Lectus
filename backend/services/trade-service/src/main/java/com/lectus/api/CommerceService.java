package com.lectus.api;

import static com.lectus.api.Contracts.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommerceService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public CommerceService(JdbcTemplate db, ObjectMapper json) {this.db=db;this.json=json;}
    // One account's mutations serialize; SKU locks then protect stock across accounts.
    private void lockAccount(String account) {db.update("INSERT INTO account_locks(id) VALUES(?) ON CONFLICT DO NOTHING",account);db.queryForObject("SELECT id FROM account_locks WHERE id=? FOR UPDATE",String.class,account);}
    public List<CartItem> cart(String account) {
        return db.query("SELECT c.*,s.product_id,s.size,s.color,s.stock,p.name,p.image,p.price_cents FROM cart_items c JOIN product_skus s ON s.id=c.sku_id JOIN products p ON p.id=s.product_id WHERE c.account_id=? ORDER BY c.sku_id",
                (rs,n)->new CartItem(rs.getString("sku_id"),rs.getString("product_id"),rs.getString("name"),rs.getString("image"),rs.getString("size"),rs.getString("color"),rs.getLong("price_cents"),rs.getInt("quantity"),rs.getBoolean("checked"),rs.getInt("stock")),account);
    }
    @Transactional
    public List<CartItem> putCart(String account, CartWrite body) {
        lockAccount(account);
        var sku=stock(body.skuId(),false);
        if(body.quantity()>sku.stock()) throw ApiException.conflict("OUT_OF_STOCK","当前规格库存不足");
        int count=db.update("UPDATE cart_items SET quantity=?,checked=? WHERE account_id=? AND sku_id=?",body.quantity(),body.checked(),account,body.skuId());
        if(count==0) db.update("INSERT INTO cart_items(account_id,sku_id,quantity,checked) VALUES(?,?,?,?)",account,body.skuId(),body.quantity(),body.checked());
        return cart(account);
    }
    @Transactional
    public void deleteCart(String account,String sku) {
        lockAccount(account);db.update("DELETE FROM cart_items WHERE account_id=? AND sku_id=?",account,sku);
    }
    public List<Address> addresses(String account) {
        return db.query("SELECT * FROM addresses WHERE account_id=? ORDER BY is_default DESC,id",
                (rs,n)->new Address(rs.getString("id"),rs.getString("recipient"),rs.getString("phone"),rs.getString("region"),rs.getString("detail"),rs.getBoolean("is_default")),account);
    }
    private Address address(String account,String id) {
        return addresses(account).stream().filter(a->a.id().equals(id)).findFirst().orElseThrow(ApiException::missing);
    }
    @Transactional
    public Address saveAddress(String account,String id,AddressWrite body) {
        lockAccount(account);
        if(id!=null) address(account,id);
        var existing=addresses(account);
        if(id==null && existing.size()>=20) throw ApiException.bad("ADDRESS_LIMIT","最多保存20个收货地址");
        boolean isDefault=body.isDefault() || existing.isEmpty();
        if(isDefault) db.update("UPDATE addresses SET is_default=FALSE WHERE account_id=?",account);
        if(id==null) {
            id=UUID.randomUUID().toString();
            db.update("INSERT INTO addresses(id,account_id,recipient,phone,region,detail,is_default) VALUES(?,?,?,?,?,?,?)",id,account,body.recipient().strip(),body.phone().strip(),body.region().strip(),body.detail().strip(),isDefault);
        } else {
            db.update("UPDATE addresses SET recipient=?,phone=?,region=?,detail=?,is_default=? WHERE account_id=? AND id=?",body.recipient().strip(),body.phone().strip(),body.region().strip(),body.detail().strip(),isDefault,account,id);
        }
        ensureDefault(account);return address(account,id);
    }
    private void ensureDefault(String account) {
        var rows=addresses(account);
        if(!rows.isEmpty() && rows.stream().noneMatch(Address::isDefault)) db.update("UPDATE addresses SET is_default=TRUE WHERE account_id=? AND id=?",account,rows.getFirst().id());
    }
    @Transactional
    public void deleteAddress(String account,String id) {
        lockAccount(account);address(account,id);
        db.update("DELETE FROM addresses WHERE account_id=? AND id=?",account,id);ensureDefault(account);
    }
    private record Stock(String skuId,String productId,String name,String image,String size,String color,long price,int stock) {}
    private Stock stock(String id,boolean lock) {
        var rows=db.query("SELECT s.*,p.name,p.image,p.price_cents FROM product_skus s JOIN products p ON p.id=s.product_id WHERE s.id=? AND p.active=TRUE"+(lock?" FOR UPDATE":""),
                (rs,n)->new Stock(rs.getString("id"),rs.getString("product_id"),rs.getString("name"),rs.getString("image"),rs.getString("size"),rs.getString("color"),rs.getLong("price_cents"),rs.getInt("stock")),id);
        if(rows.isEmpty()) throw ApiException.missing();return rows.getFirst();
    }
    @Transactional
    public Order createOrder(String account,String key,OrderWrite body) {
        if(key==null || !key.matches("[A-Za-z0-9_-]{8,80}")) throw ApiException.bad("IDEMPOTENCY_KEY_REQUIRED","请提供8至80位 Idempotency-Key");
        lockAccount(account);
        var items=new TreeMap<String,Integer>();
        for(OrderLine line:body.items()) if(items.put(line.skuId(),line.quantity())!=null) throw ApiException.bad("DUPLICATE_SKU","同一规格请合并数量");
        String hash=Digests.sha256(encode(new OrderWrite(body.addressId(),items.entrySet().stream()
                .map(item->new OrderLine(item.getKey(),item.getValue())).toList(),body.fromCart())));
        var existing=db.queryForList("SELECT id,request_hash FROM shop_orders WHERE account_id=? AND idempotency_key=?",account,key);
        if(!existing.isEmpty()) {
            if(!existing.getFirst().get("request_hash").equals(hash)) throw ApiException.conflict("IDEMPOTENCY_CONFLICT","同一个提交标识不能用于不同订单");
            return order(account,(String)existing.getFirst().get("id"));
        }
        Address address=address(account,body.addressId());
        var lines=new ArrayList<OrderItem>();long total=0;
        for(var item:items.entrySet()) {
            Stock sku=stock(item.getKey(),true);
            if(sku.stock()<item.getValue()) throw ApiException.conflict("OUT_OF_STOCK","当前规格库存不足");
            if(body.fromCart()) {
                var cartQuantity=db.queryForList("SELECT quantity FROM cart_items WHERE account_id=? AND sku_id=?",account,item.getKey());
                if(cartQuantity.isEmpty() || ((Number)cartQuantity.getFirst().get("quantity")).intValue()!=item.getValue())
                    throw ApiException.conflict("CART_CHANGED","购物袋已变化，请刷新后提交");
            }
            db.update("UPDATE product_skus SET stock=stock-? WHERE id=?",item.getValue(),sku.skuId());
            total=Math.addExact(total,Math.multiplyExact(sku.price(),item.getValue().longValue()));
            lines.add(new OrderItem(sku.skuId(),sku.productId(),sku.name(),sku.image(),sku.size(),sku.color(),sku.price(),item.getValue()));
        }
        String id=UUID.randomUUID().toString();OffsetDateTime created=OffsetDateTime.now(ZoneOffset.UTC);
        db.update("INSERT INTO shop_orders(id,account_id,status,total_cents,address_snapshot,idempotency_key,request_hash,created_at) VALUES(?,?,?,?,?,?,?,?)",id,account,"PENDING_PAYMENT",total,encode(address),key,hash,created);
        for(OrderItem line:lines) {
            db.update("INSERT INTO order_items(order_id,sku_id,product_id,product_name,image,size,color,unit_price_cents,quantity) VALUES(?,?,?,?,?,?,?,?,?)",id,line.skuId(),line.productId(),line.name(),line.image(),line.size(),line.color(),line.unitPriceCents(),line.quantity());
            if(body.fromCart()) db.update("DELETE FROM cart_items WHERE account_id=? AND sku_id=?",account,line.skuId());
        }
        return order(account,id);
    }
    public List<Order> orders(String account,int page,int size) {
        if(page<0 || page>10000 || size<1 || size>100) throw ApiException.bad("INVALID_PAGE","分页不正确");
        return db.query("SELECT id FROM shop_orders WHERE account_id=? ORDER BY created_at DESC,id LIMIT ? OFFSET ?",
                (rs,n)->order(account,rs.getString("id")),account,size,page*size);
    }
    public Order order(String account,String id) {
        var rows=db.query("SELECT * FROM shop_orders WHERE account_id=? AND id=?",(rs,n)->new Order(id,rs.getString("status"),rs.getLong("total_cents"),"CNY",decode(rs.getString("address_snapshot"),Address.class),rs.getObject("created_at",OffsetDateTime.class).toString(),orderItems(id)),account,id);
        if(rows.isEmpty()) throw ApiException.missing();return rows.getFirst();
    }
    private List<OrderItem> orderItems(String id) {
        return db.query("SELECT * FROM order_items WHERE order_id=? ORDER BY sku_id",(rs,n)->new OrderItem(rs.getString("sku_id"),rs.getString("product_id"),rs.getString("product_name"),rs.getString("image"),rs.getString("size"),rs.getString("color"),rs.getLong("unit_price_cents"),rs.getInt("quantity")),id);
    }
    @Transactional
    public Order cancel(String account,String id) {
        lockAccount(account);
        var rows=db.queryForList("SELECT status FROM shop_orders WHERE account_id=? AND id=? FOR UPDATE",account,id);
        if(rows.isEmpty()) throw ApiException.missing();
        if(rows.getFirst().get("status").equals("CANCELLED")) return order(account,id);
        if(!rows.getFirst().get("status").equals("PENDING_PAYMENT")) throw ApiException.conflict("INVALID_ORDER_STATE","当前订单不能取消");
        for(OrderItem item:orderItems(id)) {
            db.queryForObject("SELECT stock FROM product_skus WHERE id=? FOR UPDATE",Integer.class,item.skuId());
            db.update("UPDATE product_skus SET stock=stock+? WHERE id=?",item.quantity(),item.skuId());
        }
        db.update("UPDATE shop_orders SET status='CANCELLED' WHERE account_id=? AND id=?",account,id);
        return order(account,id);
    }
    private String encode(Object value) {
        try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException(e);}
    }
    private <T> T decode(String value,Class<T> type) {
        try{return json.readValue(value,type);}catch(JsonProcessingException e){throw new IllegalStateException(e);}
    }
}
