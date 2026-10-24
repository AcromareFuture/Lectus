package com.heng.community;
import com.heng.biz.CatalogService;
import com.heng.config.BizException;
import com.heng.vo.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest @ActiveProfiles("test")
class AiShoppingTests {
 @Autowired JdbcTemplate db; CatalogService catalog; AiShoppingController ai;
 @BeforeEach void init(){db.update("DELETE FROM v_ai_usage");db.update("DELETE FROM v_ai_history");catalog=mock(CatalogService.class);ai=spy(new AiShoppingController(db,catalog,"private-test-key","https://provider.example/v1","qwen-max",true,2));var card=new ProductCardVO(88L,1L,"fresh","real product","","/real.png",List.of(),500L,450L,500L,1,10,false,false,null,0,List.of(),0,1L,"");when(catalog.page(null,null,"sales",1,30,101L)).thenReturn(new PageResult<>(List.of(card),1,1,30,false));}
 AiShoppingController.Request question(){return new AiShoppingController.Request("Recommend dinner",null,10000,2,"");}
 @Test void noKeyOrDisabledServiceNeverMakesARequest(){assertFalse(new AiShoppingController(db,catalog,"","https://provider.example","qwen-max",true,2).ready());assertThrows(BizException.class,()->new AiShoppingController(db,catalog,"secret","https://provider.example","qwen-max",false,2).shopping(101,question()));verifyNoInteractions(catalog);}
 @Test void rejectsInventedIdsAndKeepsPrivateHistory()throws Exception{doReturn("{\"answer\":\"Buy real product\",\"productIds\":[88,999,88]}").when(ai).completion(anyString());var result=(Map<?,?>)ai.shopping(101,question()).data();assertEquals(1,((List<?>)result.get("products")).size());assertEquals(1,((List<?>)ai.history(101).data()).size());assertTrue(((List<?>)ai.history(102).data()).isEmpty());assertFalse(result.toString().contains("private-test-key"));ai.clear(102);assertEquals(1,((List<?>)ai.history(101).data()).size());}
 @Test void invalidResponseRefundsQuota()throws Exception{doReturn("not json").when(ai).completion(anyString());assertThrows(BizException.class,()->ai.shopping(101,question()));assertEquals(0,db.queryForObject("SELECT requests FROM v_ai_usage WHERE member_id=101",Integer.class));assertTrue(((List<?>)ai.history(101).data()).isEmpty());}
 @Test void upstreamFailureRefundsQuotaWithoutLeakingDetails()throws Exception{doThrow(new IllegalStateException("private-test-key provider internals")).when(ai).completion(anyString());var e=assertThrows(BizException.class,()->ai.shopping(101,question()));assertFalse(e.getMessage().contains("private-test-key"));assertEquals(0,db.queryForObject("SELECT requests FROM v_ai_usage WHERE member_id=101",Integer.class));}
 @Test void dailyQuotaIsEnforced()throws Exception{doReturn("{\"answer\":\"ok\",\"productIds\":[88]}").when(ai).completion(anyString());ai.shopping(101,question());ai.shopping(101,question());assertThrows(BizException.class,()->ai.shopping(101,question()));assertEquals(2,db.queryForObject("SELECT requests FROM v_ai_usage WHERE member_id=101",Integer.class));}
}
