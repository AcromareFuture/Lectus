package com.heng.logistics;

import com.heng.biz.OrderService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

/** Read-only parcel tracking. Registered events never change trade/receipt state. */
@Service
public class ParcelTrackingService {
    private final OrderService orders;
    private final JdbcTemplate jdbc;
    private final AmapProperties config;
    public ParcelTrackingService(OrderService orders, JdbcTemplate jdbc, AmapProperties config) {
        this.orders = orders; this.jdbc = jdbc; this.config = config;
    }
    public Map<String, Object> tracking(Long member, Long id) {
        // Ownership is checked before reading any logistics or recipient data.
        orders.requireOrder(member, id);
        var order = orders.detail(member, id);
        var events = jdbc.query("SELECT e.event_id,e.event_time,e.content,e.source,l.phase,l.city,l.longitude,l.latitude,l.courier_name,l.courier_phone,l.estimated_delivery FROM v_logistics_event e LEFT JOIN v_logistics_event_location l ON l.event_id=e.event_id WHERE e.order_id=? ORDER BY e.event_time DESC,e.event_id DESC LIMIT 100",
                (rs, n) -> {
                    var event = event(String.valueOf(rs.getLong("event_id")), rs.getTimestamp("event_time").toLocalDateTime().toString(), rs.getString("content"), rs.getString("source"));
                    String phase = rs.getString("phase");
                    if (phase != null && Set.of("SHIPPED","PICKED_UP","IN_TRANSIT","OUT_FOR_DELIVERY","DELIVERED","EXCEPTION").contains(phase)) event.put("phase",phase);
                    event.put("city",rs.getString("city"));
                    if (rs.getBigDecimal("longitude") != null && rs.getBigDecimal("latitude") != null) event.put("point",List.of(rs.getDouble("longitude"),rs.getDouble("latitude")));
                    event.put("courierName",rs.getString("courier_name")); event.put("courierPhone",rs.getString("courier_phone"));
                    event.put("estimatedDelivery",rs.getTimestamp("estimated_delivery") == null ? null : rs.getTimestamp("estimated_delivery").toLocalDateTime().toString());
                    return event;
                }, id);
        boolean shipped = order.shipTime() != null || order.shipNo() != null && !order.shipNo().isBlank();
        String state = shipped ? "SHIPPED" : "NOT_SHIPPED";
        for (var event : events) {
            String phase = (String) event.get("phase");
            if (!"UNKNOWN".equals(phase)) { state = phase; break; }
        }
        if (shipped && events.stream().noneMatch(e -> "SHIPPED".equals(e.get("phase"))) && order.shipTime() != null) {
            // A shipment record confirms dispatch, not carrier pickup or delivery.
            events.add(event("shipment", order.shipTime(), "商家已发货，等待承运商更新物流", "SHIPMENT_RECORD"));
            events.sort(Comparator.comparing(e -> String.valueOf(e.get("time")), Comparator.reverseOrder()));
        }
        var result = base(false, state, events);
        result.put("orderId", id); result.put("orderNo", order.orderNo());
        result.put("carrier", order.shipCompany()); result.put("trackingNo", order.shipNo());
        result.put("recipient", order.contactName()); result.put("phone", order.contactPhone());
        result.put("address", order.shippingAddress()); result.put("items", order.items());
        result.put("orderStatus", order.status()); result.put("receiptConfirmedAt", order.receiveTime());
        result.put("dataSource", "REGISTERED_EVENTS");
        var nodes = new ArrayList<Map<String,Object>>();
        for (int i=events.size()-1; i>=0; i--) {
            var event = events.get(i);
            if (event.get("point") != null) nodes.add(event);
        }
        result.put("mapNodes",nodes); result.put("mapAvailable", !nodes.isEmpty());
        // Use metadata only from the latest event, so old courier/ETA details do not persist after delivery.
        if (!events.isEmpty()) {
            var latest = events.get(0);
            result.put("courierName",latest.get("courierName")); result.put("courierPhone",latest.get("courierPhone"));
            if (!"DELIVERED".equals(state)) result.put("estimatedDelivery",latest.get("estimatedDelivery"));
        }
        return result;
    }
    public Map<String, Object> preview() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        var events = new ArrayList<Map<String, Object>>();
        String[] content = {"快件已签收，包裹已送达保定市", "快件已交由派件员，正在派送，请留意电话", "快件到达保定配送站", "快件已从济南分拨中心发出，运输中", "快件已揽收，发货地无锡市"};
        for (int i = 0; i < content.length; i++) events.add(event("preview-" + i, now.minusHours(i * 4L).toString(), content[i], "DEMO"));
        var result = base(true, "DELIVERED", events);
        result.put("carrier", "示例快递"); result.put("trackingNo", "DEMO20261014001");
        result.put("recipient", "示例收件人"); result.put("phone", "138****0000");
        result.put("address", "河北省保定市 · 示例收货地址"); result.put("items", List.of());
        result.put("dataSource", "DEMO");
        result.put("mapAvailable",true);
        result.put("mapNodes",List.of(
            Map.of("city","无锡市","cityEn","Wuxi","point",List.of(120.31191,31.49117)),
            Map.of("city","济南市","cityEn","Jinan","point",List.of(117.12010,36.65120)),
            Map.of("city","天津市","cityEn","Tianjin","point",List.of(117.20098,39.08416)),
            Map.of("city","保定市","cityEn","Baoding","point",List.of(115.46459,38.87389))));
        return result;
    }
    private Map<String, Object> base(boolean demo, String status, List<Map<String, Object>> events) {
        var result = new LinkedHashMap<String, Object>();
        result.put("simulation", demo); result.put("status", status); result.put("events", events);
        result.put("estimatedDelivery", null); // No carrier ETA is available; never fabricate one.
        result.put("updatedAt", events.isEmpty() ? null : events.get(0).get("time"));
        result.put("mapAvailable", false); // Registered text has no verified coordinates.
        result.put("mapReady",config.isJsProxyEnabled() && !config.getJsKey().isBlank());
        result.put("jsKey",config.isJsProxyEnabled() ? config.getJsKey() : "");
        result.put("serviceHost","/_AMapService"); result.put("coordinateSystem","GCJ-02");
        return result;
    }
    private Map<String, Object> event(String id, String time, String content, String source) {
        var event = new LinkedHashMap<String, Object>();
        event.put("id", id); event.put("time", time.replace(' ','T')); event.put("content", content);
        event.put("source", source); event.put("phase", phase(content));
        return event;
    }
    public static String phase(String content) {
        if (content == null) return "UNKNOWN";
        // Only explicit milestones count. Negative/conditional mentions do not confirm delivery.
        if (content.matches("(?s).*(异常|拒收|退回|滞留|丢失|派送失败|签收失败|未签收|无法签收|未能签收|签收不成功).*")) return "EXCEPTION";
        if (content.matches("(?s).*(等待签收|待签收|预计|尚未|还未|未揽收|未派送).*")) return "UNKNOWN";
        if (content.matches("(?s).*(已签收|签收成功).*")) return "DELIVERED";
        if (content.matches("(?s).*(正在派送|派送中|开始派送|派件员|配送员).*")) return "OUT_FOR_DELIVERY";
        if (content.matches("(?s).*(运输中|已到达|到达.*(站|中心)|已从.*发出|离开.*(站|中心)).*")) return "IN_TRANSIT";
        if (content.matches("(?s).*(已揽收|已收件|揽收成功).*")) return "PICKED_UP";
        if (content.contains("已发货")) return "SHIPPED";
        return "UNKNOWN";
    }
}
