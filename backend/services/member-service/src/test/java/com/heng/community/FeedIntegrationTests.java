package com.heng.community;

import com.heng.auth.MemberTokenService;
import com.heng.config.BizException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import java.nio.file.*;
import java.io.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="viridac.upload-directory=target/feed-test-uploads")
@ActiveProfiles("test") @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FeedIntegrationTests {
 @Autowired JdbcTemplate db; @Autowired FeedService feed; @Autowired FeedMedia media; @Autowired MemberTokenService tokens; @Autowired WebApplicationContext context;
 MockMvc mvc;
 @BeforeAll void schema()throws Exception{try(var c=db.getDataSource().getConnection()){ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/V2__community_feed.sql"));}mvc=MockMvcBuilders.webAppContextSetup(context).build();}
 @BeforeEach void reset(){for(String t:List.of("lc_community_comment","lc_community_reaction","lc_community_follow"))db.update("DELETE FROM "+t);db.update("DELETE FROM lc_community_media WHERE owner_id IS NOT NULL");db.update("DELETE FROM lc_community_post WHERE official=FALSE");db.update("DELETE FROM v_member WHERE member_id IN (9901,9902)");db.update("INSERT INTO v_member(member_id,username,nickname,status) VALUES(9901,'feed-one','穿搭用户',1),(9902,'feed-two','球鞋用户',1)");}
 String key(){return UUID.randomUUID().toString();}
 String image(long owner){String id=key().replace("-","");db.update("INSERT INTO lc_community_media(id,owner_id,kind,filename,content_type,width,height) VALUES(?,?,'IMAGE',?,'image/jpeg',600,800)",id,owner,id+".jpg");return id;}
 FeedService.Publication form(String media,String key){return new FeedService.Publication("今天的穿搭","舒服的一天","日常穿搭","IMAGE",List.of(media),key);}
 long createPost(long me){return ((Number)feed.publish(me,form(image(me),key())).get("id")).longValue();}
 @Test void publicReadsAndAuthenticatedWritesHaveDifferentBoundaries()throws Exception{mvc.perform(get("/api/community/public/feed")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(6));mvc.perform(post("/api/community/posts").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());mvc.perform(post("/api/community/media")).andExpect(status().isUnauthorized());}
 @Test void publishRejectsForeignMediaAndReusingPublishedUploads(){String mine=image(9901),other=image(9902);assertThrows(BizException.class,()->feed.publish(9901,form(other,key())));var publication=form(mine,key());long id=((Number)feed.publish(9901,publication).get("id")).longValue();assertEquals(id,feed.publish(9901,publication).get("id"));assertThrows(BizException.class,()->feed.publish(9901,form(mine,key())));assertThrows(BizException.class,()->feed.publish(9901,new FeedService.Publication("x","","bad","IMAGE",List.of(image(9901)),key())));}
 @Test void pendingAndRemovedMediaCannotBeReadPublicly()throws Exception{String id=image(9901);mvc.perform(get("/api/community/public/media/"+id)).andExpect(status().isNotFound());long p=((Number)feed.publish(9901,form(id,key())).get("id")).longValue();assertThrows(BizException.class,()->feed.remove(9902,p));feed.remove(9901,p);assertThrows(BizException.class,()->feed.detail(p,null));mvc.perform(get("/api/community/public/media/"+id)).andExpect(status().isNotFound());}
 @Test void feedCursorAndTopicDoNotRepeatPosts(){for(int i=0;i<15;i++)createPost(9901);var first=feed.feed(null,"recommend","日常穿搭","",0);assertEquals(12,((List<?>)first.get("items")).size());assertEquals(true,first.get("hasMore"));var second=feed.feed(null,"recommend","日常穿搭","",((Number)first.get("nextCursor")).longValue());assertEquals(5,((List<?>)second.get("items")).size());assertEquals(false,second.get("hasMore"));assertEquals(0,((List<?>)feed.feed(null,"following","","",0).get("items")).size());}
 @Test void reactionsAreIdempotentAndPrivatePreferencesArePerMember(){long p=createPost(9901);feed.react(9902,p,"LIKE",true);feed.react(9902,p,"LIKE",true);feed.react(9902,p,"SAVE",true);assertEquals(1L,feed.detail(p,null).get("likes"));assertEquals(false,feed.detail(p,null).get("liked"));assertEquals(true,feed.detail(p,9902L).get("liked"));assertEquals(1,((List<?>)feed.feed(9902L,"saved","","",0).get("items")).size());feed.react(9902,p,"LIKE",false);assertEquals(0L,feed.detail(p,null).get("likes"));}
 @Test void followingAndCommentsPersistWithoutExposingPrivateMemberData(){long p=createPost(9901);feed.follow(9902,9901,true);feed.follow(9902,9901,true);assertEquals(1,((List<?>)feed.feed(9902L,"following","","",0).get("items")).size());String k=key();feed.comment(9902,p,"鞋子配色很好看",k);feed.comment(9902,p,"鞋子配色很好看",k);assertEquals(1L,feed.detail(p,null).get("comments"));assertThrows(BizException.class,()->feed.comment(9902,p,"changed",k));String output=feed.comments(p,0).toString();assertTrue(output.contains("球鞋用户"));assertFalse(output.contains("phone"));assertFalse(output.contains("email"));feed.follow(9902,9901,false);assertEquals(0,((List<?>)feed.feed(9902L,"following","","",0).get("items")).size());}
 @Test void imageUploadStripsMetadataAndHasBoundedDimensions()throws Exception{var image=new java.awt.image.BufferedImage(20,30,java.awt.image.BufferedImage.TYPE_INT_RGB);var bytes=new ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);var result=media.upload(9901,new MockMultipartFile("file","photo.png","image/png",bytes.toByteArray()),"IMAGE");Path target=media.directory.resolve(result.get("id")+".jpg");assertTrue(Files.isRegularFile(target));assertEquals(20,result.get("width"));assertThrows(BizException.class,()->media.upload(9901,new MockMultipartFile("file","fake.png","image/png","not-image".getBytes()),"IMAGE"));}
 @Test void fakeMp4AndMixedMediaAreRejected()throws Exception{Path fake=Files.createTempFile("feed-fake-",".mp4");try{Files.writeString(fake,"not-a-movie");assertThrows(BizException.class,()->FeedMedia.mp4(fake));}finally{Files.deleteIfExists(fake);}String id=image(9901);assertThrows(BizException.class,()->feed.publish(9901,new FeedService.Publication("x","","球鞋日记","VIDEO",List.of(id),key())));}
 @Test void realAvcUploadPublishesAndSupportsRangeRequests()throws Exception{
  byte[] bytes=new ClassPathResource("community/avc.mp4").getInputStream().readAllBytes();
  var video=media.upload(9901,new MockMultipartFile("file","clip.mp4","video/mp4",bytes),"VIDEO");
  assertEquals(360,video.get("width"));assertEquals(640,video.get("height"));assertEquals(2,video.get("duration"));
  String mid=video.get("id").toString();
  feed.publish(9901,new FeedService.Publication("城市片刻","","生活方式","VIDEO",List.of(mid,image(9901)),key()));
  mvc.perform(get("/api/community/public/media/"+mid).header("Range","bytes=0-63"))
   .andExpect(status().isPartialContent()).andExpect(header().string("Content-Range","bytes 0-63/"+bytes.length)).andExpect(content().bytes(Arrays.copyOf(bytes,64)));
 }
 @Test void parallelReplayCreatesOnePost()throws Exception{var publication=form(image(9901),key());var pool=Executors.newFixedThreadPool(4);var start=new CountDownLatch(1);try{var futures=new ArrayList<Future<Object>>();for(int i=0;i<4;i++)futures.add(pool.submit(()->{start.await();return feed.publish(9901,publication).get("id");}));start.countDown();var ids=new HashSet<Object>();for(var f:futures)ids.add(f.get(15,TimeUnit.SECONDS));assertEquals(1,ids.size());}finally{pool.shutdownNow();}}
}
