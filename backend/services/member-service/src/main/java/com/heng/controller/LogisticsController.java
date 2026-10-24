package com.heng.controller;
import com.heng.logistics.LogisticsService;
import com.heng.logistics.ParcelTrackingService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api")
public class LogisticsController {
    private final LogisticsService service;
    private final ParcelTrackingService tracking;
    public LogisticsController(LogisticsService service,ParcelTrackingService tracking){this.service=service;this.tracking=tracking;}
    @GetMapping("/logistics/tracking-demo") public ApiResponse<Map<String,Object>> preview(){return ApiResponse.ok(tracking.preview());}
    @GetMapping("/orders/{id}/logistics") public ApiResponse<Map<String,Object>> tracking(@RequestAttribute("viridac.memberId") Long member,@PathVariable Long id){return ApiResponse.ok(tracking.tracking(member,id));}
    @GetMapping("/logistics/demo") public ApiResponse<Map<String,Object>> demo(){return ApiResponse.ok(service.demo());}
    @GetMapping("/orders/{id}/logistics/simulation") public ApiResponse<Map<String,Object>> order(@RequestAttribute("viridac.memberId") Long member,@PathVariable Long id){return ApiResponse.ok(service.orderDemo(member,id));}
}
