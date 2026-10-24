package com.heng.logistics;
import com.heng.biz.OrderService;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
@Service
public class LogisticsService {
    private final AmapProperties config;private final AmapGateway gateway;private final OrderService orders;
    private Map<String,Object> cached;private long expires=0,nextAttempt=0;
    public LogisticsService(AmapProperties config,AmapGateway gateway,OrderService orders){this.config=config;this.gateway=gateway;this.orders=orders;}
    public synchronized Map<String,Object> demo() {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("simulation",true);result.put("vehicleCode","SIM-01");result.put("coordinateSystem","GCJ-02");
        result.put("origin",Map.of("name","上海人民广场","nameEn","People’s Square, Shanghai","point",List.of(121.473701,31.230416)));
        result.put("destination",Map.of("name","上海徐家汇","nameEn","Xujiahui, Shanghai","point",List.of(121.436525,31.188523)));
        boolean ready=config.isEnabled()&&!config.getWebKey().isBlank();
        result.put("routeReady",ready);result.put("mapReady",config.isJsProxyEnabled()&&!config.getJsKey().isBlank());
        result.put("jsKey",config.isJsProxyEnabled()?config.getJsKey():"");result.put("serviceHost","/_AMapService");
        if(!ready){result.put("state","CONFIG_REQUIRED");return result;}
        long now=System.currentTimeMillis();
        if(now<expires&&cached!=null){result.putAll(cached);return result;}
        // One shared sample route: cache and failed-request cooldown bound provider usage.
        if(now<nextAttempt){result.put("state","TEMPORARILY_UNAVAILABLE");return result;}
        nextAttempt=now+30_000;
        try {
            var route=new LinkedHashMap<String,Object>(gateway.route());route.put("state","READY");route.put("source","AMAP_DRIVING");route.put("generatedAt",Instant.now().toString());
            cached=Collections.unmodifiableMap(route);expires=now+600_000;result.putAll(cached);
        }catch(com.heng.config.BizException ignored){result.put("state","TEMPORARILY_UNAVAILABLE");}
        return result;
    }
    public Map<String,Object> orderDemo(Long member,Long id){orders.requireOrder(member,id);return demo();}
}
