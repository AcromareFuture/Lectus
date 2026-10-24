package com.heng.controller;
import com.heng.biz.AftersalesService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/orders/{id}/aftersales")
public class AftersalesController {
 private final AftersalesService service;public AftersalesController(AftersalesService s){service=s;}
 @GetMapping public ApiResponse<Map<String,Object>> detail(@RequestAttribute("viridac.memberId") Long member,@PathVariable Long id){return ApiResponse.ok(service.detail(member,id));}
 public record Refund(String reason){} public record Shipment(String company,String trackingNo){} public record Invoice(String title,String taxNo,String email,String type){}
 @PostMapping("/refund") public ApiResponse<Map<String,Object>> refund(@RequestAttribute("viridac.memberId") Long member,@PathVariable Long id,@RequestBody Refund q){return ApiResponse.ok(service.request(member,id,q.reason()));}
 @PostMapping("/return") public ApiResponse<Void> returning(@RequestAttribute("viridac.memberId") Long member,@PathVariable Long id,@RequestBody Shipment q){service.returnShipment(member,id,q.company(),q.trackingNo());return ApiResponse.ok(null);}
 @PostMapping("/cancel") public ApiResponse<Void> cancel(@RequestAttribute("viridac.memberId") Long member,@PathVariable Long id){service.cancel(member,id);return ApiResponse.ok(null);}
 @PostMapping("/invoice") public ApiResponse<Void> invoice(@RequestAttribute("viridac.memberId") Long member,@PathVariable Long id,@RequestBody Invoice q){service.invoice(member,id,q.title(),q.taxNo(),q.email(),q.type());return ApiResponse.ok(null);}
}
