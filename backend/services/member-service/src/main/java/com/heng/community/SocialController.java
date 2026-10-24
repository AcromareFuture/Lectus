package com.heng.community;

import com.heng.controller.ApiResponse;
import com.heng.auth.MemberTokenService;
import com.heng.config.BizException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.FileSystemResource;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController @RequestMapping("/api/social")
public class SocialController {
 private final org.springframework.jdbc.core.JdbcTemplate db;private final SocialService service;private final MemberTokenService tokens;private final Path directory;
 private final String stun,turn,user,password,turnSecret;private final CopyOnWriteArrayList<Stream> streams=new CopyOnWriteArrayList<>();
 private static class Stream{long me,cursor;String token;SseEmitter emitter;Stream(long me,long cursor,String token,SseEmitter emitter){this.me=me;this.cursor=cursor;this.token=token;this.emitter=emitter;}}
 public SocialController(org.springframework.jdbc.core.JdbcTemplate db,SocialService service,MemberTokenService tokens,@Value("${viridac.upload-directory:/srv/viridac/uploads}") String directory,@Value("${VIRIDAC_RTC_STUN_URL:stun:stun.l.google.com:19302}") String stun,@Value("${VIRIDAC_RTC_TURN_URL:}") String turn,@Value("${VIRIDAC_RTC_TURN_USERNAME:}") String user,@Value("${VIRIDAC_RTC_TURN_PASSWORD:}") String password,@Value("${VIRIDAC_RTC_TURN_SECRET:}") String turnSecret){this.db=db;this.service=service;this.tokens=tokens;this.directory=Path.of(directory).resolve("chat");this.stun=stun;this.turn=turn;this.user=user;this.password=password;this.turnSecret=turnSecret;}
 @GetMapping("/members") public ApiResponse<?> search(@RequestAttribute("viridac.memberId") long me,@RequestParam String q){return ApiResponse.ok(service.search(me,q));}
 @GetMapping("/friends") public ApiResponse<?> friends(@RequestAttribute("viridac.memberId") long me){return ApiResponse.ok(service.friendList(me));}
 public record FriendRequest(long memberId,String note){}
 @PostMapping("/friend-requests") public ApiResponse<?> request(@RequestAttribute("viridac.memberId") long me,@RequestBody FriendRequest q){return ApiResponse.ok(Map.of("requestId",service.request(me,q.memberId(),q.note())));}
 public record Decision(boolean accept){}
 @PostMapping("/friend-requests/{id}") public ApiResponse<?> decide(@RequestAttribute("viridac.memberId") long me,@PathVariable long id,@RequestBody Decision q){service.decide(me,id,q.accept());return ApiResponse.ok(null);}
 @DeleteMapping("/friends/{id}") public ApiResponse<?> remove(@RequestAttribute("viridac.memberId") long me,@PathVariable long id){service.remove(me,id);return ApiResponse.ok(null);}
 public record Remark(String remark){}
 @PutMapping("/friends/{id}") public ApiResponse<?> remark(@RequestAttribute("viridac.memberId") long me,@PathVariable long id,@RequestBody Remark q){service.remark(me,id,q.remark());return ApiResponse.ok(null);}
 public record Block(boolean enabled){}
 @PutMapping("/blocks/{id}") public ApiResponse<?> block(@RequestAttribute("viridac.memberId") long me,@PathVariable long id,@RequestBody Block q){service.block(me,id,q.enabled());return ApiResponse.ok(null);}
 @GetMapping("/conversations") public ApiResponse<?> conversations(@RequestAttribute("viridac.memberId") long me){return ApiResponse.ok(service.conversations(me));}
 @PostMapping("/conversations/private") public ApiResponse<?> privateChat(@RequestAttribute("viridac.memberId") long me,@RequestBody FriendRequest q){return ApiResponse.ok(Map.of("conversationId",service.privateChat(me,q.memberId())));}
 public record Group(String name,List<Long> memberIds){}
 @PostMapping("/conversations/group") public ApiResponse<?> group(@RequestAttribute("viridac.memberId") long me,@RequestBody Group q){return ApiResponse.ok(Map.of("conversationId",service.group(me,q.name(),q.memberIds())));}
 @GetMapping("/conversations/{id}") public ApiResponse<?> detail(@RequestAttribute("viridac.memberId") long me,@PathVariable long id){return ApiResponse.ok(service.detail(me,id));}
 @PutMapping("/conversations/{id}") public ApiResponse<?> rename(@RequestAttribute("viridac.memberId") long me,@PathVariable long id,@RequestBody Group q){service.rename(me,id,q.name());return ApiResponse.ok(null);}
 @PostMapping("/conversations/{id}/members") public ApiResponse<?> invite(@RequestAttribute("viridac.memberId") long me,@PathVariable long id,@RequestBody Group q){service.invite(me,id,q.memberIds());return ApiResponse.ok(null);}
 public record MemberAction(long memberId,String action){}
 @PutMapping("/conversations/{id}/members") public ApiResponse<?> memberAction(@RequestAttribute("viridac.memberId") long me,@PathVariable long id,@RequestBody MemberAction q){service.memberAction(me,id,q.memberId(),q.action());return ApiResponse.ok(null);}
 @GetMapping("/conversations/{id}/messages") public ApiResponse<?> messages(@RequestAttribute("viridac.memberId") long me,@PathVariable long id,@RequestParam(defaultValue="0") long before){return ApiResponse.ok(service.messages(me,id,Math.max(0,before)));}
 public record Message(String kind,String content,Long productId,String mediaId,String requestKey){}
 @PostMapping("/conversations/{id}/messages") public ApiResponse<?> send(@RequestAttribute("viridac.memberId") long me,@PathVariable long id,@RequestBody Message q){return ApiResponse.ok(Map.of("messageId",service.send(me,id,q.kind(),q.content(),q.productId(),q.mediaId(),q.requestKey())));}
 public record Read(long messageId){}
 @PutMapping("/conversations/{id}/read") public ApiResponse<?> read(@RequestAttribute("viridac.memberId") long me,@PathVariable long id,@RequestBody Read q){service.read(me,id,q.messageId());return ApiResponse.ok(null);}
 public record Call(long conversationId,String media){}
 @PostMapping("/calls") public ApiResponse<?> call(@RequestAttribute("viridac.memberId") long me,@RequestBody Call q){return ApiResponse.ok(service.startCall(me,q.conversationId(),q.media()));}
 @GetMapping("/calls") public ApiResponse<?> activeCalls(@RequestAttribute("viridac.memberId") long me){return ApiResponse.ok(service.activeCalls(me));}
 public record Signal(String action,Object signal){}
 @PostMapping("/calls/{id}") public ApiResponse<?> signal(@RequestAttribute("viridac.memberId") long me,@PathVariable String id,@RequestBody Signal q){service.callAction(me,id,q.action(),q.signal());return ApiResponse.ok(null);}
 @GetMapping("/rtc-config") public ApiResponse<?> rtc(@RequestAttribute("viridac.memberId") long me){
  var servers=new ArrayList<Map<String,Object>>();if(!stun.isBlank())servers.add(Map.of("urls",stun));boolean relay=false;
  if(!turn.isBlank()&&!turnSecret.isBlank())try{
   String username=(java.time.Instant.now().getEpochSecond()+3600)+":"+me;
   var mac=javax.crypto.Mac.getInstance("HmacSHA1");mac.init(new javax.crypto.spec.SecretKeySpec(turnSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA1"));
   String credential=Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
   servers.add(Map.of("urls",turn.split(","),"username",username,"credential",credential));relay=true;
  }catch(Exception e){throw new BizException("通话网络配置暂不可用");}
  else if(!turn.isBlank()&&!user.isBlank()&&!password.isBlank()){servers.add(Map.of("urls",turn.split(","),"username",user,"credential",password));relay=true;}
  return ApiResponse.ok(Map.of("iceServers",servers,"relayConfigured",relay));
 }
 @GetMapping(value="/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE) public synchronized SseEmitter events(@RequestAttribute("viridac.memberId") long me,@RequestHeader("Authorization") String header,@RequestParam(defaultValue="0") long after){if(streams.stream().filter(s->s.me==me).count()>=3)throw new BizException("实时连接已达上限，请关闭其他聊天窗口");long max=db.queryForObject("SELECT COALESCE(MAX(event_id),0) FROM v_social_event WHERE member_id=?",Long.class,me);long cursor=after<=0?max:Math.min(max,after);var emitter=new SseEmitter(300000L);var stream=new Stream(me,cursor,header.substring(7),emitter);emitter.onCompletion(()->streams.remove(stream));emitter.onTimeout(()->{streams.remove(stream);emitter.complete();});emitter.onError(e->streams.remove(stream));streams.add(stream);try{emitter.send(SseEmitter.event().name("ready").id(String.valueOf(cursor)).data("{}"));}catch(Exception e){streams.remove(stream);}return emitter;}
 @Scheduled(fixedDelay=1000) public void pump(){for(var stream:streams){try{Long resolved=tokens.resolve(stream.token);if(resolved==null||resolved!=stream.me||db.queryForObject("SELECT COUNT(*) FROM v_member WHERE member_id=? AND status=1",Long.class,stream.me)!=1){stream.emitter.complete();streams.remove(stream);continue;}var events=db.queryForList("SELECT event_id,payload FROM v_social_event WHERE member_id=? AND event_id>? ORDER BY event_id LIMIT 100",stream.me,stream.cursor);for(var event:events){long id=((Number)event.get("event_id")).longValue();stream.emitter.send(SseEmitter.event().name("update").id(String.valueOf(id)).data(event.get("payload")));stream.cursor=id;}if(events.isEmpty())stream.emitter.send(SseEmitter.event().comment("keepalive"));}catch(Exception e){streams.remove(stream);stream.emitter.complete();}}}
 @Scheduled(fixedDelay=30000,initialDelay=30000) public void expire(){service.expireCalls();db.update("DELETE FROM v_social_event WHERE create_time<?",SocialService.ago(86400));}
 @PostMapping("/conversations/{id}/images") public ApiResponse<?> upload(@RequestAttribute("viridac.memberId") long me,@PathVariable long id,@RequestParam("file") MultipartFile file)throws Exception{service.membership(me,id);if(file.isEmpty()||file.getSize()>2*1024*1024)throw new BizException("图片应小于 2 MB");if(db.queryForObject("SELECT COUNT(*) FROM v_chat_media WHERE owner_id=? AND create_time>?",Long.class,me,SocialService.ago(86400))>=100)throw new BizException("今日图片上传次数已达上限");java.awt.image.BufferedImage image;try(var input=ImageIO.createImageInputStream(file.getInputStream())){var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new BizException("请上传 PNG 或 JPEG 图片");var reader=readers.next();try{String format=reader.getFormatName();if(!Set.of("png","jpeg","jpg").contains(format.toLowerCase()))throw new BizException("请上传 PNG 或 JPEG 图片");reader.setInput(input);if(reader.getWidth(0)>4096||reader.getHeight(0)>4096)throw new BizException("图片边长不能超过 4096 像素");image=reader.read(0);}finally{reader.dispose();}}String media=UUID.randomUUID().toString().replace("-","");Files.createDirectories(directory);Path target=directory.resolve(media+".png");ImageIO.write(image,"png",target.toFile());try{service.membership(me,id);db.update("INSERT INTO v_chat_media(media_id,conversation_id,owner_id,filename) VALUES(?,?,?,?)",media,id,me,media+".png");}catch(Exception e){Files.deleteIfExists(target);throw e;}return ApiResponse.ok(Map.of("mediaId",media));}
 @GetMapping("/images/{media:[a-f0-9]{32}}") public ResponseEntity<?> image(@RequestAttribute("viridac.memberId") long me,@PathVariable String media){var rows=db.queryForList("SELECT * FROM v_chat_media WHERE media_id=?",media);if(rows.isEmpty())return ResponseEntity.notFound().build();var row=rows.get(0);var member=service.membership(me,service.number(row,"conversation_id"));if(service.number(row,"owner_id")!=me&&db.queryForObject("SELECT COUNT(*) FROM v_chat_message WHERE media_id=? AND message_id>?",Long.class,media,service.number(member,"joined_message_id"))==0)throw new BizException("图片不存在或无访问权限");Path path=directory.resolve(row.get("filename").toString());if(!Files.isRegularFile(path))return ResponseEntity.notFound().build();return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(new FileSystemResource(path));}
}
