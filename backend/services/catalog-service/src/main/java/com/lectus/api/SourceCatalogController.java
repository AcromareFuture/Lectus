package com.lectus.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/catalog")
public class SourceCatalogController {
    private final SourceCatalogService catalog;
    private final URI origin;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public SourceCatalogController(SourceCatalogService catalog,@Value("${lectus.assets-origin}") String origin) {
        this.catalog=catalog;this.origin=URI.create(origin.endsWith("/")?origin:origin+"/");
    }
    @GetMapping("/meta") public JsonNode meta(){return catalog.metadata();}
    @GetMapping("/products") public JsonNode products(@RequestParam(defaultValue="") String category,
            @RequestParam(defaultValue="") String brand,@RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="24") int size) {
        return catalog.list(category,brand,q,page,size);
    }
    @GetMapping("/products/{id}") public JsonNode product(@PathVariable String id){return catalog.product(id);}
    @GetMapping("/assets/{kind}/{version}/{hash}/{file}")
    public ResponseEntity<InputStreamResource> image(@PathVariable String kind,@PathVariable String version,
            @PathVariable String hash,@PathVariable String file,@RequestHeader(value="If-None-Match",required=false) String etag) throws Exception {
        if(!kind.equals("catalog") || !version.equals("v1") || !hash.matches("[a-f0-9]{64}") || !file.matches("(original\\.jpg|thumb\\.webp|detail\\.webp)")) throw ApiException.missing();
        return asset(kind+"/"+version+"/"+hash+"/"+file,etag,file.endsWith("webp")?MediaType.parseMediaType("image/webp"):MediaType.IMAGE_JPEG,hash+"-"+file);
    }
    @GetMapping("/assets/models/{hash}.glb")
    public ResponseEntity<InputStreamResource> model(@PathVariable String hash,@RequestHeader(value="If-None-Match",required=false) String etag) throws Exception {
        if(!hash.matches("[a-f0-9]{64}")) throw ApiException.missing();
        return asset("models/"+hash+".glb",etag,MediaType.parseMediaType("model/gltf-binary"),hash);
    }
    private ResponseEntity<InputStreamResource> asset(String key,String suppliedTag,MediaType type,String tag) throws IOException,InterruptedException {
        String quoted="\""+tag+"\"";
        if(quoted.equals(suppliedTag)) return ResponseEntity.status(304).eTag(quoted).cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable()).build();
        var response=http.send(java.net.http.HttpRequest.newBuilder(origin.resolve(key)).timeout(Duration.ofSeconds(30)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
        if(response.statusCode()!=200) {
            response.body().close();
            if(response.statusCode()==404) throw ApiException.missing();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"素材暂时无法加载");
        }
        var builder=ResponseEntity.ok().contentType(type).eTag(quoted).cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        response.headers().firstValueAsLong("Content-Length").ifPresent(builder::contentLength);
        return builder.body(new InputStreamResource(response.body()));
    }
}
