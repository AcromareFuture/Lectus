package com.lectus.api;

import static com.lectus.api.Contracts.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class CommerceController {
    private final CatalogService catalog;
    private final CommerceService commerce;
    CommerceController(CatalogService catalog,CommerceService commerce){this.catalog=catalog;this.commerce=commerce;}
    @GetMapping("/health") Map<String,String> health(){return Map.of("status","UP","service","trade-service","version","0.2.0");}
    @GetMapping("/products") Map<String,Object> products(@RequestParam(required=false) String category,
            @RequestParam(defaultValue="") String q,@RequestParam(defaultValue="recommended") String sort,
            @RequestParam(required=false) Long minPriceCents,@RequestParam(required=false) Long maxPriceCents,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return catalog.list(category,q,sort,minPriceCents,maxPriceCents,page,size);
    }
    @GetMapping("/products/{id}") Product product(@PathVariable String id){return catalog.product(id);}
    @GetMapping("/cart") List<CartItem> cart(@AuthenticationPrincipal User user){return commerce.cart(user.id());}
    @PutMapping("/cart/items") List<CartItem> putCart(@AuthenticationPrincipal User user,@Valid @RequestBody CartWrite body){return commerce.putCart(user.id(),body);}
    @DeleteMapping("/cart/items/{skuId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteCart(@AuthenticationPrincipal User user,@PathVariable String skuId){commerce.deleteCart(user.id(),skuId);}
    @GetMapping("/addresses") List<Address> addresses(@AuthenticationPrincipal User user){return commerce.addresses(user.id());}
    @PostMapping("/addresses") @ResponseStatus(HttpStatus.CREATED)
    Address addAddress(@AuthenticationPrincipal User user,@Valid @RequestBody AddressWrite body){return commerce.saveAddress(user.id(),null,body);}
    @PutMapping("/addresses/{id}") Address editAddress(@AuthenticationPrincipal User user,@PathVariable String id,@Valid @RequestBody AddressWrite body){return commerce.saveAddress(user.id(),id,body);}
    @DeleteMapping("/addresses/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAddress(@AuthenticationPrincipal User user,@PathVariable String id){commerce.deleteAddress(user.id(),id);}
    @PostMapping("/orders") @ResponseStatus(HttpStatus.CREATED)
    Order createOrder(@AuthenticationPrincipal User user,@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody OrderWrite body){return commerce.createOrder(user.id(),key,body);}
    @GetMapping("/orders") List<Order> orders(@AuthenticationPrincipal User user,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return commerce.orders(user.id(),page,size);}
    @GetMapping("/orders/{id}") Order order(@AuthenticationPrincipal User user,@PathVariable String id){return commerce.order(user.id(),id);}
    @PostMapping("/orders/{id}/cancel") Order cancel(@AuthenticationPrincipal User user,@PathVariable String id){return commerce.cancel(user.id(),id);}
}
