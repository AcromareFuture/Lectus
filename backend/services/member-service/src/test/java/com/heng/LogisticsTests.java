package com.heng;
import com.heng.biz.OrderService;
import com.heng.config.BizException;
import com.heng.logistics.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
class LogisticsTests {
    String fixture="{\"status\":\"1\",\"route\":{\"paths\":[{\"distance\":\"6751\",\"cost\":{\"duration\":\"2187\"},\"steps\":[{\"instruction\":\"Go straight\",\"road\":\"Road\",\"polyline\":\"121.47,31.23;121.46,31.22\"},{\"polyline\":\"121.46,31.22;121.44,31.19\"}]}]}}";
    @Test void parsedRoadRouteDeduplicatesSharedStepPoints(){var r=new AmapGateway(new AmapProperties()).parse(fixture);assertEquals(6751L,r.get("distanceMetres"));assertEquals(3,((java.util.List<?>)r.get("points")).size());}
    @Test void invalidProviderGeometryOrFailureNeverLooksLikeSuccess(){var g=new AmapGateway(new AmapProperties());for(String s:new String[]{"{}",fixture.replace("121.47","NaN"),fixture.replace("121.47","900"),fixture.replace("6751","-1"),fixture.replace("\"1\"","\"0\"")})assertThrows(BizException.class,()->g.parse(s));}
    @Test void disabledProviderDoesNotCallNetworkOrExposeWebKey(){var p=new AmapProperties();p.setWebKey("private-web-key");var g=mock(AmapGateway.class);var s=new LogisticsService(p,g,mock(OrderService.class));var r=s.demo();assertEquals("CONFIG_REQUIRED",r.get("state"));assertFalse(r.toString().contains("private-web-key"));verifyNoInteractions(g);}
    @Test void sampleCacheAndFailureCooldownBoundCalls(){var p=new AmapProperties();p.setEnabled(true);p.setWebKey("private");var g=mock(AmapGateway.class);when(g.route()).thenReturn(Map.of("points",java.util.List.of()));var s=new LogisticsService(p,g,mock(OrderService.class));assertEquals("READY",s.demo().get("state"));s.demo();verify(g,times(1)).route();g=mock(AmapGateway.class);when(g.route()).thenThrow(new BizException("provider secret"));s=new LogisticsService(p,g,mock(OrderService.class));assertEquals("TEMPORARILY_UNAVAILABLE",s.demo().get("state"));s.demo();verify(g,times(1)).route();}
    @Test void orderOwnershipCheckedBeforeRouteAndNoStateMutation(){var orders=mock(OrderService.class);when(orders.requireOrder(2L,7L)).thenThrow(new BizException("订单不存在"));var g=mock(AmapGateway.class);var s=new LogisticsService(new AmapProperties(),g,orders);assertThrows(BizException.class,()->s.orderDemo(2L,7L));verifyNoInteractions(g);verify(orders).requireOrder(2L,7L);}
}
