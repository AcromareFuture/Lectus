package com.lectus.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CatalogSeeder implements ApplicationRunner {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final boolean enabled;
    public CatalogSeeder(JdbcTemplate db, ObjectMapper json, @Value("${lectus.seed-catalog}") boolean enabled) {
        this.db=db;this.json=json;this.enabled=enabled;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if(!enabled) return;
        try(var stream=new ClassPathResource("catalog.json").getInputStream()) {
            for(JsonNode p:json.readTree(stream)) {
                String id=p.path("id").asText();
                if(db.queryForObject("SELECT COUNT(*) FROM products WHERE id=?",Integer.class,id)>0) continue;
                db.update("INSERT INTO products(id,name,english_name,brand,category,price_cents,image,description,tag,popularity) VALUES(?,?,?,?,?,?,?,?,?,?)",
                        id,p.path("name").asText(),p.path("en").asText(),p.path("brand").asText(),p.path("category").asText(),
                        p.path("price").decimalValue().movePointRight(2).longValueExact(),p.path("image").asText(),p.path("description").asText(),p.path("tag").asText(),p.path("sold").asInt());
                for(JsonNode size:p.path("sizes")) for(JsonNode color:p.path("colors")) {
                    String sku=UUID.nameUUIDFromBytes((id+"|"+size.asText()+"|"+color.asText()).getBytes(StandardCharsets.UTF_8)).toString();
                    db.update("INSERT INTO product_skus(id,product_id,size,color,stock) VALUES(?,?,?,?,?)",sku,id,size.asText(),color.asText(),20);
                }
            }
        }
    }
}
