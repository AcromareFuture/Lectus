package com.lectus.gateway;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
class LocalController {
 @GetMapping("/api/health") Map<String,Object> health(){return Map.of("status","UP","service","gateway","version","0.2.0");}
 @GetMapping("/api/lectus-local/config") Map<String,Object> local()throws Exception{
  String origin=System.getenv("FRONTEND_PUBLIC_ORIGIN");
  if(origin==null||origin.isBlank()){
   for(var ni:Collections.list(java.net.NetworkInterface.getNetworkInterfaces())){
    if(!ni.isUp()||ni.isLoopback()||!ni.getDisplayName().matches("(?i).*(wi-fi|wireless|wlan).*"))continue;
    for(var ip:Collections.list(ni.getInetAddresses()))if(ip instanceof java.net.Inet4Address && ip.isSiteLocalAddress()){origin="http://"+ip.getHostAddress()+":4206";break;}
    if(origin!=null)break;
   }
  }
  return Map.of("success",origin!=null,"data",Collections.singletonMap("scanOrigin",origin),"message",origin!=null?"ok":"请设置 FRONTEND_PUBLIC_ORIGIN 为手机可访问的前端地址");
 }
}
