package com.heng.community;

import com.heng.config.BizException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
public class FeedMedia {
 final Path directory;private final JdbcTemplate db;
 public FeedMedia(JdbcTemplate db,@Value("${viridac.upload-directory:/srv/viridac/uploads}") String root){this.db=db;directory=Path.of(root).resolve("community").toAbsolutePath().normalize();}
 public Map<String,Object> upload(long me,MultipartFile file,String kind)throws IOException{
  if(kind==null||!Set.of("IMAGE","VIDEO").contains(kind))throw new BizException("请选择图片或视频");
  if(file.isEmpty()||file.getSize()>(kind.equals("VIDEO")?50L:10L)*1024*1024)throw new BizException(kind.equals("VIDEO")?"视频不能超过 50 MB":"单张图片不能超过 10 MB");
  // Serialize quota reservation per account, including concurrent uploads.
  String id=UUID.randomUUID().toString().replace("-","");String ext=kind.equals("VIDEO")?".mp4":".jpg";Files.createDirectories(directory);Path target=directory.resolve(id+ext);
  int w,h,duration=0;
  try{
   if(kind.equals("IMAGE")){
    BufferedImage image;
    try(var input=ImageIO.createImageInputStream(file.getInputStream())){
     var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new BizException("请上传 JPG 或 PNG 图片");var reader=readers.next();
     try{reader.setInput(input);String format=reader.getFormatName();if(!Set.of("png","jpeg","jpg").contains(format.toLowerCase()))throw new BizException("请上传 JPG 或 PNG 图片");w=reader.getWidth(0);h=reader.getHeight(0);if(w<1||h<1||w>6000||h>6000||(long)w*h>20000000)throw new BizException("图片尺寸过大，请缩小后上传");image=reader.read(0);}finally{reader.dispose();}
    }
    double scale=Math.min(1,1920d/Math.max(w,h));w=Math.max(1,(int)(w*scale));h=Math.max(1,(int)(h*scale));
    var clean=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);var g=clean.createGraphics();g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,w,h);g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(image,0,0,w,h,null);g.dispose();ImageIO.write(clean,"jpeg",target.toFile());
   }else{
    file.transferTo(target);int[] metadata=mp4(target);w=metadata[0];h=metadata[1];duration=metadata[2];
   }
   final int width=w,height=h,seconds=duration;
   var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.support.JdbcTransactionManager(db.getDataSource()));
   tx.execute(status->{
    db.queryForList("SELECT member_id FROM v_member WHERE member_id=? FOR UPDATE",me);
    if(db.queryForObject("SELECT COUNT(*) FROM lc_community_media WHERE owner_id=? AND created_at>?",Long.class,me,java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(86400)))>=60)throw new BizException("今天上传的内容较多，请明天再来分享");
    db.update("INSERT INTO lc_community_media(id,owner_id,kind,filename,content_type,width,height,duration_seconds) VALUES(?,?,?,?,?,?,?,?)",id,me,kind,id+ext,kind.equals("VIDEO")?"video/mp4":"image/jpeg",width,height,seconds);return null;
   });return Map.of("id",id,"kind",kind,"width",w,"height",h,"duration",duration);
  }catch(Exception e){Files.deleteIfExists(target);if(e instanceof BizException b)throw b;if(e instanceof IOException io)throw io;throw new BizException("文件处理失败，请检查格式后重试");}
 }
 // Read bounded ISO-BMFF boxes, not filename/MIME claims. Only browser-compatible
 // AVC MP4 with a real video track, media payload and <= 2 minutes is accepted.
 static int[] mp4(Path path)throws IOException{
  try(var f=new RandomAccessFile(path.toFile(),"r")){
   var top=boxes(f,0,f.length());var ftyp=find(top,"ftyp");var moov=find(top,"moov");find(top,"mdat");
   if(ftyp[1]-ftyp[0]<8)throw new IOException("ftyp");// Brands are read as bounded fixed bytes.
   f.seek(ftyp[0]);byte[] bytes=new byte[(int)Math.min(64,ftyp[1]-ftyp[0])];f.readFully(bytes);String brands=new String(bytes,java.nio.charset.StandardCharsets.ISO_8859_1);
   if(!brands.matches("(?s).*(isom|iso2|mp41|mp42|avc1|M4V ).*"))throw new IOException("brand");
   var children=boxes(f,moov[0],moov[1]);int w=0,h=0,seconds=0;boolean avc=false;
   for(var box:children){if(!box.name.equals("trak"))continue;var tracks=boxes(f,box.start,box.end);var mdia=find(tracks,"mdia");var md=boxes(f,mdia[0],mdia[1]);var hdlr=find(md,"hdlr");f.seek(hdlr[0]+8);if(!four(f).equals("vide"))continue;
    var tk=find(tracks,"tkhd");f.seek(tk[1]-8);w=f.readInt()>>>16;h=f.readInt()>>>16;
    var duration=find(md,"mdhd");f.seek(duration[0]);int version=f.readUnsignedByte();f.skipBytes(version==1?19:11);long timescale=Integer.toUnsignedLong(f.readInt());long ticks=version==1?f.readLong():Integer.toUnsignedLong(f.readInt());if(timescale==0||ticks<0)throw new IOException("duration");seconds=(int)Math.ceil((double)ticks/timescale);
    var minf=find(md,"minf");var stbl=find(boxes(f,minf[0],minf[1]),"stbl");var stsd=find(boxes(f,stbl[0],stbl[1]),"stsd");for(var sample:boxes(f,stsd[0]+8,stsd[1]))if(sample.name.equals("avc1")||sample.name.equals("avc3"))avc=true;
   }
   if(!avc||w<1||h<1||w>4096||h>4096||seconds<1||seconds>120)throw new BizException("请上传 2 分钟以内、H.264 编码的 MP4 视频，分辨率不超过 4K");return new int[]{w,h,seconds};
  }catch(BizException e){throw e;}catch(Exception e){throw new BizException("视频文件无法读取，请上传完整的 MP4 视频");}
 }
 record Box(String name,long start,long end){}
 static String four(RandomAccessFile f)throws IOException{byte[] b=new byte[4];f.readFully(b);return new String(b,java.nio.charset.StandardCharsets.US_ASCII);}
 static List<Box> boxes(RandomAccessFile f,long start,long end)throws IOException{var out=new ArrayList<Box>();long pos=start;while(pos<end){if(out.size()>10000||end-pos<8)throw new IOException("box");f.seek(pos);long size=Integer.toUnsignedLong(f.readInt());String type=four(f);int header=8;if(size==1){size=f.readLong();header=16;}if(size==0)size=end-pos;if(size<header||size>end-pos)throw new IOException("size");out.add(new Box(type,pos+header,pos+size));pos+=size;}return out;}
 static long[] find(List<Box> boxes,String name)throws IOException{for(var b:boxes)if(b.name.equals(name))return new long[]{b.start,b.end};throw new IOException(name);}
}
