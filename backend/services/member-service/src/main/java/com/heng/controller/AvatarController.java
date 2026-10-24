package com.heng.controller;
import com.heng.config.BizException;
import com.heng.biz.MemberCenterService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.core.io.FileSystemResource;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

@RestController
@RequestMapping("/api/member/avatar")
public class AvatarController {
 private final Path directory;
 private final MemberCenterService center;
 public AvatarController(@Value("${viridac.upload-directory:/srv/viridac/uploads}") String directory,MemberCenterService center) { this.directory=Path.of(directory).resolve("avatars");this.center=center; }
 @PostMapping public ApiResponse<Map<String,Object>> upload(@RequestAttribute("viridac.memberId") Long memberId,@RequestParam("file") MultipartFile file) throws Exception {
  if(file.isEmpty() || file.getSize()>10*1024*1024) throw new BizException("头像文件应小于 10 MB");
  BufferedImage image;
  try(var input=ImageIO.createImageInputStream(file.getInputStream())) {
   var readers=ImageIO.getImageReaders(input);
   if(!readers.hasNext()) throw new BizException("请上传 PNG 或 JPEG 图片");
   var reader=readers.next();
   try {
    String format=reader.getFormatName();
    if(!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg")) throw new BizException("请上传 PNG 或 JPEG 图片");
    reader.setInput(input);
    if(reader.getWidth(0)>4096 || reader.getHeight(0)>4096) throw new BizException("图片边长不能超过 4096 像素");
    image=reader.read(0);
   } finally {reader.dispose();}
  }
  int side=Math.min(image.getWidth(),image.getHeight());
  BufferedImage resized=new BufferedImage(256,256,BufferedImage.TYPE_INT_RGB);
  var g=resized.createGraphics();
  g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
  g.drawImage(image,0,0,256,256,(image.getWidth()-side)/2,(image.getHeight()-side)/2,(image.getWidth()+side)/2,(image.getHeight()+side)/2,null);g.dispose();
  Files.createDirectories(directory);
  String name=UUID.randomUUID().toString().replace("-","")+".png";
  Path target=directory.resolve(name);
  ImageIO.write(resized,"png",target.toFile());
  String url="/api/member/avatar/"+name;
  try {center.setAvatar(memberId,url);} catch(Exception e) {Files.deleteIfExists(target);throw e;}
  return ApiResponse.ok(Map.of("avatarUrl",url));
 }
 @org.springframework.scheduling.annotation.Scheduled(fixedDelay=86400000,initialDelay=3600000)
 public void cleanupUnusedAvatars() throws java.io.IOException {
  if(!Files.isDirectory(directory)) return;
  try(var paths=Files.list(directory)) {
   for(Path path:paths.filter(Files::isRegularFile).toList()) {
    String name=path.getFileName().toString();
    if(name.matches("[a-f0-9]{32}\\.png") && Files.getLastModifiedTime(path).toInstant().isBefore(java.time.Instant.now().minusSeconds(86400)) && !center.avatarReferenced("/api/member/avatar/"+name)) Files.deleteIfExists(path);
   }
  }
 }
 @GetMapping("/{name:[a-f0-9]{32}\\.png}") public ResponseEntity<?> image(@PathVariable String name) {
  Path path=directory.resolve(name);
  if(!Files.isRegularFile(path)) return ResponseEntity.notFound().build();
  return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30))).body(new FileSystemResource(path));
 }
}
