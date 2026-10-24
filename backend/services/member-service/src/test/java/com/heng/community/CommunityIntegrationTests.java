package com.heng.community;
import com.heng.auth.MemberTokenService;
import com.heng.config.BizException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test")
class CommunityIntegrationTests {
 @Autowired JdbcTemplate db; @Autowired SocialService social; @Autowired StoreCouponController stores;
 @Autowired WebApplicationContext context; @Autowired MemberTokenService tokens;
 MockMvc mvc;
 @BeforeEach void init(){
  mvc=MockMvcBuilders.webAppContextSetup(context).build();
  for(String table:List.of("v_social_call","v_social_event","v_chat_message","v_chat_media","v_conversation_member","v_conversation","v_friend","v_friend_request","v_social_block","v_member_store","v_store","v_order_store","v_coupon_publication","v_member_coupon","v_coupon","v_ai_usage","v_ai_history","v_member_session","v_member"))db.update("DELETE FROM "+table);
  db.update("INSERT INTO v_member(member_id,username,nickname) VALUES(101,'community-one','用户一'),(102,'community-two','用户二'),(103,'community-three','用户三')");
 }
 void friends(long a,long b){long r=social.request(a,b,"hi");social.decide(b,r,true);}
 long chat(){friends(101,102);return social.privateChat(101,102);}
 long n(String sql,Object...args){return db.queryForObject(sql,Long.class,args);}
 @Test void anonymousAndNonmemberCannotReadOrSend()throws Exception{
  long c=chat();mvc.perform(get("/api/social/friends")).andExpect(status().isUnauthorized());
  String h="Bearer "+tokens.issue(103L);
  mvc.perform(get("/api/social/conversations/"+c+"/messages").header("Authorization",h)).andExpect(jsonPath("$.success").value(false));
  mvc.perform(post("/api/social/conversations/"+c+"/messages").header("Authorization",h).contentType("application/json").content("{\"kind\":\"TEXT\",\"content\":\"intrude\",\"requestKey\":\"unauth-key\"}")).andExpect(jsonPath("$.success").value(false));
  assertEquals(0,n("SELECT COUNT(*) FROM v_chat_message"));
 }
 @Test void onlyReceiverDecidesAndDuplicateRequestIsStable(){long r=social.request(101,102,"hello");assertEquals(r,social.request(101,102,"hello"));assertThrows(BizException.class,()->social.decide(103,r,true));assertThrows(BizException.class,()->social.decide(101,r,true));social.decide(102,r,true);assertEquals(2,n("SELECT COUNT(*) FROM v_friend"));assertThrows(BizException.class,()->social.decide(102,r,true));}
 @Test void authenticatedEventStreamStartsAndRtcStaysPrivate()throws Exception{String h="Bearer "+tokens.issue(101L);mvc.perform(get("/api/social/rtc-config")).andExpect(status().isUnauthorized());mvc.perform(get("/api/social/rtc-config").header("Authorization",h)).andExpect(jsonPath("$.success").value(true));MvcResult stream=mvc.perform(get("/api/social/events").header("Authorization",h)).andExpect(request().asyncStarted()).andReturn();assertTrue(stream.getResponse().getContentAsString().contains("event:ready"));stream.getRequest().getAsyncContext().complete();}
 @Test void searchHidesContactDataAndEscapesWildcards(){db.update("UPDATE v_member SET phone='18600000000',email='private@example.com' WHERE member_id=102");assertTrue(social.search(101,"18600000000").isEmpty());var found=social.search(101,"community-two");assertEquals(1,found.size());assertFalse(found.get(0).containsKey("phone"));assertTrue(social.search(101,"__").isEmpty());}
 @Test void blockRemovesBothFriendLinksAndStopsMessages(){long c=chat();social.block(102,101,true);assertEquals(0,n("SELECT COUNT(*) FROM v_friend"));assertThrows(BizException.class,()->social.send(101,c,"TEXT","x",null,null,"block-key"));assertThrows(BizException.class,()->social.request(101,102,"x"));social.block(102,101,false);assertThrows(BizException.class,()->social.privateChat(101,102));}
 @Test void parallelPrivateChatHasOneConversation()throws Exception{friends(101,102);var ids=race(6,()->social.privateChat(101,102));assertEquals(1,new HashSet<>(ids).size());assertEquals(1,n("SELECT COUNT(*) FROM v_conversation"));assertEquals(2,n("SELECT COUNT(*) FROM v_conversation_member"));}
 @Test void parallelMessageReplayHasOneMessage()throws Exception{long c=chat();var ids=race(6,()->social.send(101,c,"TEXT","hello",null,null,"replay-key"));assertEquals(1,new HashSet<>(ids).size());assertEquals(1,n("SELECT COUNT(*) FROM v_chat_message"));assertThrows(BizException.class,()->social.send(101,c,"TEXT","other",null,null,"replay-key"));}
 @Test void imageOwnershipAndConversationAreEnforced()throws Exception{long c=chat();String image="a".repeat(32);db.update("INSERT INTO v_chat_media(media_id,conversation_id,owner_id,filename) VALUES(?,?,?,?)",image,c,101,image+".png");assertThrows(BizException.class,()->social.send(102,c,"IMAGE","",null,image,"foreign-image"));mvc.perform(get("/api/social/images/"+image).header("Authorization","Bearer "+tokens.issue(103L))).andExpect(jsonPath("$.success").value(false));social.send(101,c,"IMAGE","",null,image,"own-image");mvc.perform(get("/api/social/images/"+image).header("Authorization","Bearer "+tokens.issue(101L))).andExpect(status().isNotFound());}
 @Test void rolesAndJoinBoundaryProtectHistory(){friends(101,102);friends(101,103);long c=social.group(101,"group",List.of(102L));social.send(101,c,"TEXT","old secret",null,null,"old-message");assertThrows(BizException.class,()->social.invite(102,c,List.of(103L)));social.invite(101,c,List.of(103L));assertTrue(social.messages(103,c,0).isEmpty());assertNull(social.conversations(103).get(0).get("lastMessage"));social.send(101,c,"TEXT","new message",null,null,"new-message");assertEquals(1,social.messages(103,c,0).size());assertThrows(BizException.class,()->social.memberAction(101,c,101,"REMOVE"));social.memberAction(101,c,102,"OWNER");social.memberAction(101,c,101,"REMOVE");assertThrows(BizException.class,()->social.messages(101,c,0));assertEquals("OWNER",social.detail(102,c).get("role"));}
 @Test void removedMemberCannotReadImageHistory()throws Exception{friends(101,102);long c=social.group(101,"group",List.of(102L));String image="b".repeat(32);db.update("INSERT INTO v_chat_media(media_id,conversation_id,owner_id,filename) VALUES(?,?,?,?)",image,c,101,image+".png");social.send(101,c,"IMAGE","",null,image,"group-image");social.memberAction(101,c,102,"REMOVE");mvc.perform(get("/api/social/images/"+image).header("Authorization","Bearer "+tokens.issue(102L))).andExpect(jsonPath("$.success").value(false));}
 @Test void readCursorIsClampedAndNoDuplicateReadEvents(){long c=chat(),m=social.send(101,c,"TEXT","hi",null,null,"read-message");social.read(102,c,Long.MAX_VALUE);long count=n("SELECT COUNT(*) FROM v_social_event");social.read(102,c,Long.MAX_VALUE);assertEquals(count,n("SELECT COUNT(*) FROM v_social_event"));assertEquals(m,n("SELECT last_read_id FROM v_conversation_member WHERE conversation_id=? AND member_id=102",c));}
 @Test void callsEnforceParticipantDirectionAndState(){long c=chat();String id=social.startCall(101,c,"VIDEO").get("callId").toString();assertThrows(BizException.class,()->social.callAction(103,id,"ACCEPT",null));assertThrows(BizException.class,()->social.callAction(101,id,"OFFER",Map.of("type","offer")));assertThrows(BizException.class,()->social.callAction(101,id,"ACCEPT",null));social.callAction(102,id,"ACCEPT",null);assertThrows(BizException.class,()->social.callAction(102,id,"OFFER",Map.of("type","offer")));social.callAction(101,id,"OFFER",Map.of("type","offer","sdp","test"));assertThrows(BizException.class,()->social.startCall(102,c,"AUDIO"));social.callAction(101,id,"END",null);assertThrows(BizException.class,()->social.callAction(102,id,"ANSWER",Map.of("type","answer")));assertTrue(social.activeCalls(101).isEmpty());}
 @Test void staleRingingCallsExpire(){long c=chat();String id=social.startCall(101,c,"AUDIO").get("callId").toString();db.update("UPDATE v_social_call SET create_time=?,update_time=? WHERE call_id=?",SocialService.ago(100),SocialService.ago(100),id);social.expireCalls();assertEquals("ENDED",db.queryForObject("SELECT state FROM v_social_call WHERE call_id=?",String.class,id));}
 @Test void onlyPublishedStoresSelectableAndOrderSnapshotsStayPrivate()throws Exception{db.update("INSERT INTO v_store(store_id,name,address,hours,status) VALUES(1,'store','address','9-18',0)");assertThrows(BizException.class,()->stores.select(101,new StoreCouponController.Selection(1L)));db.update("UPDATE v_store SET status=1");stores.select(101,new StoreCouponController.Selection(1L));db.update("DELETE FROM v_order WHERE order_id=9991");db.update("INSERT INTO v_order(order_id,order_no,member_id) VALUES(9991,'STORE-TEST-ORDER',101)");stores.attachOrder(101,9991,1L);db.update("UPDATE v_store SET name='new name',status=0");assertEquals("store",((Map<?,?>)stores.orderStore(101,9991).data()).get("name"));assertNull(stores.selected(101).data());assertThrows(BizException.class,()->stores.orderStore(102,9991));}
 void coupon(int total,int limit){db.update("INSERT INTO v_coupon(coupon_id,name,status,valid_from,valid_to,total_count,remain_count,per_member_limit) VALUES(1,'coupon',1,?,?,?, ?,?)",SocialService.ago(86400),java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(86400)),total,total,limit);}
 @Test void couponPublicationMemberLimitAndStockAreEnforced(){coupon(2,1);assertThrows(BizException.class,()->stores.claim(101,1));db.update("INSERT INTO v_coupon_publication(coupon_id,enabled) VALUES(1,1)");stores.claim(101,1);assertThrows(BizException.class,()->stores.claim(101,1));stores.claim(102,1);assertThrows(BizException.class,()->stores.claim(103,1));assertEquals(0,n("SELECT remain_count FROM v_coupon WHERE coupon_id=1"));assertEquals(2,n("SELECT COUNT(*) FROM v_member_coupon"));}
 @Test void concurrentCouponClaimsCannotOversell()throws Exception{coupon(1,1);db.update("INSERT INTO v_coupon_publication(coupon_id,enabled) VALUES(1,1)");var results=race(6,()->{try{stores.claim(Thread.currentThread().getId()%2==0?101:102,1);return 1L;}catch(BizException e){return 0L;}});assertEquals(1,results.stream().mapToLong(Long::longValue).sum());assertEquals(0,n("SELECT remain_count FROM v_coupon WHERE coupon_id=1"));}
 static List<Long> race(int count,Supplier<Long> action)throws Exception{var pool=Executors.newFixedThreadPool(count);var start=new CountDownLatch(1);try{var futures=new ArrayList<Future<Long>>();for(int i=0;i<count;i++)futures.add(pool.submit(()->{start.await();return action.get();}));start.countDown();var out=new ArrayList<Long>();for(var f:futures)out.add(f.get(15,TimeUnit.SECONDS));return out;}finally{pool.shutdownNow();}}
}
