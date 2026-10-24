package com.heng.mall;
import com.heng.controller.ApiResponse;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/mall")
public class MallController {
 private final MallService mall; private final WorkspaceAuth auth; private final MallCartService cart;
 public MallController(MallService mall,WorkspaceAuth auth,MallCartService cart){this.mall=mall;this.auth=auth;this.cart=cart;}
 public record Login(String username,String password){}
 public record Action(String action,String reason){}
 public record Ship(String carrier,String tracking){}
 public record Delivery(Integer expectedStage){}
 @GetMapping("/public/config") public ApiResponse<?> config(){return ApiResponse.ok(Map.of("localSimulation",auth.local()));}
 @GetMapping("/public/products") public ApiResponse<?> products(){return ApiResponse.ok(mall.publicProducts());}
 @GetMapping("/public/products/{sourceId}/variants") public ApiResponse<?> variants(@PathVariable String sourceId){return ApiResponse.ok(mall.publicVariants(sourceId));}
 @PostMapping("/workspace/login") public ApiResponse<?> login(@RequestBody Login f,HttpServletRequest r){return ApiResponse.ok(auth.login(f.username(),f.password(),r.getRemoteAddr()));}
 @PostMapping("/workspace/logout") public ApiResponse<?> logout(@RequestHeader(value="Authorization",required=false) String token){auth.logout(token);return ApiResponse.ok(null);}
 @GetMapping("/workspace/snapshot") public ApiResponse<?> snapshot(@RequestHeader(value="Authorization",required=false) String token){return ApiResponse.ok(mall.snapshot(auth.require(token,null)));}
 @PostMapping("/workspace/products") public ApiResponse<?> createProduct(@RequestHeader(value="Authorization",required=false) String token,@RequestBody MallService.ProductForm f){return ApiResponse.ok(mall.save(auth.require(token,"MERCHANT"),null,f));}
 @PutMapping("/workspace/products/{id}") public ApiResponse<?> saveProduct(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String id,@RequestBody MallService.ProductForm f){return ApiResponse.ok(mall.save(auth.require(token,"MERCHANT"),id,f));}
 @PostMapping("/workspace/products/{id}/action") public ApiResponse<?> productAction(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String id,@RequestBody Action f){mall.productAction(auth.require(token,null),id,f.action(),f.reason());return ApiResponse.ok(null);}
 @PostMapping("/workspace/merchants/{id}/action") public ApiResponse<?> merchantAction(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String id,@RequestBody Action f){mall.merchantAction(auth.require(token,"PLATFORM"),id,f.action(),f.reason());return ApiResponse.ok(null);}
 @PostMapping("/workspace/orders/{id}/ship") public ApiResponse<?> ship(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String id,@RequestBody Ship f){mall.ship(auth.require(token,"MERCHANT"),id,f.carrier(),f.tracking());return ApiResponse.ok(null);}
 @PostMapping("/workspace/orders/{id}/delivery") public ApiResponse<?> delivery(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String id,@RequestBody Delivery f){mall.advanceDelivery(auth.require(token,"MERCHANT"),id,f.expectedStage());return ApiResponse.ok(null);}
 @PostMapping("/workspace/aftersales/{id}/action") public ApiResponse<?> resolve(@RequestHeader(value="Authorization",required=false) String token,@PathVariable String id,@RequestBody Action f){if(!java.util.List.of("approve","reject").contains(f.action()))throw new com.heng.config.BizException("未知售后操作");mall.resolve(auth.require(token,"PLATFORM"),id,"approve".equals(f.action()),f.reason());return ApiResponse.ok(null);}
 @GetMapping("/orders") public ApiResponse<?> orders(@RequestAttribute("viridac.memberId") Long member){return ApiResponse.ok(mall.memberOrders(member));}
 @PostMapping("/orders") public ApiResponse<?> create(@RequestAttribute("viridac.memberId") Long member,@RequestBody MallService.Create f){return ApiResponse.ok(mall.create(member,f));}
 @PostMapping("/orders/{id}/action") public ApiResponse<?> orderAction(@RequestAttribute("viridac.memberId") Long member,@PathVariable String id,@RequestBody Action f){return ApiResponse.ok(mall.memberAction(member,id,f.action(),f.reason()));}
 @GetMapping("/cart") public ApiResponse<?> cart(@RequestAttribute("viridac.memberId") Long member){return ApiResponse.ok(cart.cart(member));}
 @PostMapping("/cart/items") public ApiResponse<?> addCart(@RequestAttribute("viridac.memberId") Long member,@RequestBody MallCartService.CartItem f){return ApiResponse.ok(cart.put(member,f.productId(),f.quantity(),true));}
 @PutMapping("/cart/items/{id}") public ApiResponse<?> updateCart(@RequestAttribute("viridac.memberId") Long member,@PathVariable String id,@RequestBody MallCartService.CartItem f){return ApiResponse.ok(cart.put(member,id,f.quantity(),false));}
 @DeleteMapping("/cart/items/{id}") public ApiResponse<?> removeCart(@RequestAttribute("viridac.memberId") Long member,@PathVariable String id){return ApiResponse.ok(cart.remove(member,id));}
 @PostMapping("/checkouts") public ApiResponse<?> checkout(@RequestAttribute("viridac.memberId") Long member,@RequestBody MallCartService.Checkout f){return ApiResponse.ok(cart.checkout(member,f));}
 @GetMapping("/checkouts/{id}") public ApiResponse<?> checkout(@RequestAttribute("viridac.memberId") Long member,@PathVariable String id){return ApiResponse.ok(cart.summary(member,id));}
 @PostMapping("/checkouts/{id}/pay") public ApiResponse<?> payCheckout(@RequestAttribute("viridac.memberId") Long member,@PathVariable String id,@RequestBody MallCartService.Payment f){return ApiResponse.ok(cart.pay(member,id,f));}
}
