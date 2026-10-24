package com.heng.logistics;
import com.heng.config.BizException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
@Component
public class AmapGateway {
    private final AmapProperties config;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    public AmapGateway(AmapProperties config){this.config=config;}
    public Map<String,Object> route() {
        try {
            // Fixed public sample destinations: no customer address or caller-supplied URL is sent.
            String url="https://restapi.amap.com/v5/direction/driving?origin=121.473701,31.230416&destination=121.436525,31.188523&show_fields=cost,polyline&strategy=0&key="+URLEncoder.encode(config.getWebKey(),StandardCharsets.UTF_8);
            var response=http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
            try(var body=response.body()) {
                if(response.statusCode()!=200)throw new IllegalStateException();
                byte[] bytes=body.readNBytes(2_000_001);
                if(bytes.length>2_000_000)throw new IllegalStateException();
                return parse(new String(bytes,StandardCharsets.UTF_8));
            }
        } catch(InterruptedException e){Thread.currentThread().interrupt();throw new BizException("高德路线暂不可用，请稍后重试");}
        catch(Exception e){throw new BizException("高德路线暂不可用，请稍后重试");}
    }
    public Map<String,Object> parse(String json) {
        try {
            JsonNode root=JsonMapper.builder().build().readTree(json);
            if(!"1".equals(root.path("status").asText()))throw new IllegalStateException();
            JsonNode path=root.path("route").path("paths").get(0);
            if(path==null)throw new IllegalStateException();
            long distance=Long.parseLong(path.path("distance").asText());
            long seconds=Long.parseLong(path.path("cost").path("duration").asText());
            if(distance<1||distance>100_000||seconds<1||seconds>86400)throw new IllegalStateException();
            List<List<Double>> points=new ArrayList<>();List<Map<String,Object>> steps=new ArrayList<>();
            for(JsonNode step:path.path("steps")) {
                if(steps.size()>=500)throw new IllegalStateException();
                steps.add(Map.of("instruction",step.path("instruction").asText(),"road",step.path("road").asText()));
                for(String pair:step.path("polyline").asText().split(";")) {
                    String[] xy=pair.split(",");if(xy.length!=2)throw new IllegalStateException();
                    double lng=Double.parseDouble(xy[0]),lat=Double.parseDouble(xy[1]);
                    if(!Double.isFinite(lng)||!Double.isFinite(lat)||lng<120||lng>123||lat<30||lat>33)throw new IllegalStateException();
                    List<Double> point=List.of(lng,lat);
                    if(points.isEmpty()||!points.get(points.size()-1).equals(point))points.add(point);
                    if(points.size()>20000)throw new IllegalStateException();
                }
            }
            if(points.size()<2)throw new IllegalStateException();
            return Map.of("distanceMetres",distance,"durationSeconds",seconds,"points",List.copyOf(points),"steps",List.copyOf(steps));
        }catch(Exception ignored){throw new BizException("高德路线暂不可用，请稍后重试");}
    }
}
