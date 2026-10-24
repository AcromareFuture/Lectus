package com.heng.community;

import com.heng.auth.MemberTokenService;
import com.heng.controller.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import java.nio.file.Files;
import java.util.*;

@RestController @RequestMapping("/api/community")
public class FeedController {
 private final FeedService feed;private final FeedMedia media;private final MemberTokenService tokens;private final JdbcTemplate db;
 public FeedController(FeedService feed,FeedMedia media,MemberTokenService tokens,JdbcTemplate db){this.feed=feed;this.media=media;this.tokens=tokens;this.db=db;}
 private Long viewer(String header){Long me=header!=null&&header.startsWith("Bearer ")?tokens.resolve(header.substring(7)):null;return me!=null&&db.queryForObject("SELECT COUNT(*) FROM v_member WHERE member_id=? AND status=1",Long.class,me)>0?me:null;}
 @GetMapping("/public/feed") public ApiResponse<?> list(@RequestHeader(value="Authorization",required=false) String auth,@RequestParam(defaultValue="recommend") String tab,@RequestParam(defaultValue="") String topic,@RequestParam(defaultValue="") String kind,@RequestParam(defaultValue="0") long before){return ApiResponse.ok(feed.feed(viewer(auth),tab,topic,kind,before));}
 @GetMapping("/public/posts/{id}") public ApiResponse<?> detail(@PathVariable long id,@RequestHeader(value="Authorization",required=false) String auth){return ApiResponse.ok(feed.detail(id,viewer(auth)));}
 @GetMapping("/public/posts/{id}/comments") public ApiResponse<?> comments(@PathVariable long id,@RequestParam(defaultValue="0") long after){return ApiResponse.ok(feed.comments(id,after));}
 @GetMapping("/public/media/{id:[a-f0-9]{32}}") public ResponseEntity<?> resource(@PathVariable String id){
  var rows=db.queryForList("SELECT m.* FROM lc_community_media m JOIN lc_community_post p ON p.id=m.post_id WHERE m.id=? AND p.deleted=FALSE",id);if(rows.isEmpty())return ResponseEntity.notFound().build();var m=rows.getFirst();String file=Objects.toString(m.get("filename"),"");if(!file.matches("[a-f0-9]{32}\\.(jpg|mp4)"))return ResponseEntity.notFound().build();var target=media.directory.resolve(file);if(!Files.isRegularFile(target))return ResponseEntity.notFound().build();
  // FileSystemResource lets Spring serve Range/206 for video seeking.
  return ResponseEntity.ok().contentType(MediaType.parseMediaType(m.get("content_type").toString())).cacheControl(CacheControl.noCache()).header("X-Content-Type-Options","nosniff").header("Content-Disposition","inline").body(new FileSystemResource(target));
 }
 @PostMapping("/media") public ApiResponse<?> upload(@RequestAttribute("viridac.memberId") long me,@RequestParam("file") MultipartFile file,@RequestParam String kind)throws Exception{return ApiResponse.ok(media.upload(me,file,kind));}
 @PostMapping("/posts") public ApiResponse<?> publish(@RequestAttribute("viridac.memberId") long me,@RequestBody FeedService.Publication form){return ApiResponse.ok(feed.publish(me,form));}
 public record Reaction(boolean enabled){}
 @PutMapping("/posts/{id}/reactions/{kind}") public ApiResponse<?> react(@RequestAttribute("viridac.memberId") long me,@PathVariable long id,@PathVariable String kind,@RequestBody Reaction form){return ApiResponse.ok(feed.react(me,id,kind,form.enabled()));}
 @PutMapping("/authors/{id}/follow") public ApiResponse<?> follow(@RequestAttribute("viridac.memberId") long me,@PathVariable long id,@RequestBody Reaction form){feed.follow(me,id,form.enabled());return ApiResponse.ok(null);}
 public record Comment(String body,String requestKey){}
 @PostMapping("/posts/{id}/comments") public ApiResponse<?> comment(@RequestAttribute("viridac.memberId") long me,@PathVariable long id,@RequestBody Comment form){feed.comment(me,id,form.body(),form.requestKey());return ApiResponse.ok(null);}
 @DeleteMapping("/posts/{id}") public ApiResponse<?> remove(@RequestAttribute("viridac.memberId") long me,@PathVariable long id){feed.remove(me,id);return ApiResponse.ok(null);}
}
