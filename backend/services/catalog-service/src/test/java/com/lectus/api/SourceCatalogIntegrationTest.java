package com.lectus.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={"spring.datasource.password=","spring.datasource.url=jdbc:h2:mem:catalog-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","lectus.seed-catalog=false"})
@AutoConfigureMockMvc
class SourceCatalogIntegrationTest {
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @BeforeEach void seed() {
        for(String table:new String[]{"catalog_sku_images","catalog_models","catalog_skus","catalog_images","catalog_colorways","catalog_issues","catalog_products","catalog_brands","catalog_batches"}) db.update("DELETE FROM "+table);
        db.update("INSERT INTO catalog_batches VALUES('batch-test','hash',CURRENT_TIMESTAMP,'IMPORTED','{}')");
        db.update("INSERT INTO catalog_brands VALUES('brand-test','ASICS')");
        db.update("INSERT INTO catalog_products VALUES('SPU-000000000001','brand-test','鞋类','Gel Kayano 14',FALSE,'PREVIEW','IMG-1','batch-test','{}')");
        db.update("INSERT INTO catalog_products VALUES('SPU-000000000002','brand-test','鞋类','Hidden shoe',FALSE,'HIDDEN',NULL,'batch-test','{}')");
        db.update("INSERT INTO catalog_colorways VALUES('CLR-1','SPU-000000000001','White',TRUE,'CODE-W','IMG-1','{}')");
        db.update("INSERT INTO catalog_colorways VALUES('CLR-2','SPU-000000000001','Pink',TRUE,'CODE-P','IMG-2','{}')");
        db.update("INSERT INTO catalog_skus(id,product_id,colorway_id,specs_json,source_sku,source_variant_id,source_url,source_available,source_price_amount,raw_json) VALUES('SKU-1','SPU-000000000001','CLR-1','{\"Size\":\"7\",\"Version\":\"Wide\"}','real-source','source-1','https://example.test',TRUE,100,'{}')");
        for(int n=1;n<=2;n++) db.update("INSERT INTO catalog_images(id,product_id,colorway_id,status,thumb_key,detail_key,source_position,raw_json) VALUES(?,?,?,'ok',?,?,1,'{}')","IMG-"+n,"SPU-000000000001","CLR-"+n,"catalog/v1/hash/thumb.webp","catalog/v1/hash/detail.webp");
        db.update("INSERT INTO catalog_sku_images VALUES('SKU-1','IMG-1','source_gallery',FALSE)");
        db.update("INSERT INTO catalog_sku_images VALUES('SKU-1','IMG-2','unconfirmed',TRUE)");
        db.update("INSERT INTO catalog_models(id,product_id,colorway_id,status,object_key,sha256,license_name,reviewed) VALUES('model','SPU-000000000001','CLR-1','READY','models/test.glb','hash','CC BY',FALSE)");
    }
    @Test void publicCatalogPreservesUnknownCommerceFieldsAndFiltersWithoutWildcards() throws Exception {
        mvc.perform(get("/api/catalog/products").param("q","ASICS Kayano").param("category","鞋类"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].priceCents").isEmpty()).andExpect(jsonPath("$.items[0].purchasable").value(false));
        mvc.perform(get("/api/catalog/products").param("q","%"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/catalog/products").param("brand","missing"))
            .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/catalog/products").param("size","61")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/catalog/products").param("page","-1")).andExpect(status().isBadRequest());
    }
    @Test void hidesUnreviewedModelsAndCrossColorImageLinks() throws Exception {
        mvc.perform(get("/api/catalog/products/SPU-000000000001"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.skus[0].specs.Version").value("Wide"))
            .andExpect(jsonPath("$.skus[0].stock").isEmpty()).andExpect(jsonPath("$.skus[0].priceCents").isEmpty())
            .andExpect(jsonPath("$.skus[0].imageIds.length()").value(1)).andExpect(jsonPath("$.colorways[0].model").isEmpty())
            .andExpect(jsonPath("$.colorways[1].imageIds.length()").value(0));
        mvc.perform(get("/api/catalog/products/SPU-000000000002")).andExpect(status().isNotFound());
        mvc.perform(get("/api/catalog/products/not-a-product")).andExpect(status().isNotFound());
    }
    @Test void metadataDoesNotCountHiddenItemsAndAssetsRejectUnsafeKeys() throws Exception {
        mvc.perform(get("/api/catalog/meta")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.categories[0].count").value(1));
        mvc.perform(get("/api/catalog/assets/catalog/v1/invalid/secret.txt")).andExpect(status().isNotFound());
    }
    @Test void importedSkuCannotEnterLegacyCheckout() throws Exception {
        mvc.perform(put("/api/cart/items").contentType("application/json").content("{\"skuId\":\"SKU-1\",\"quantity\":1,\"checked\":true}"))
            .andExpect(status().isUnauthorized());
        // Imported and legacy SKU tables have different foreign-key targets.
        org.junit.jupiter.api.Assertions.assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name='product_skus'",Integer.class));
    }
}
