package com.heng.mall;

import com.heng.auth.PasswordLoginGuard;
import com.heng.config.BizException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class WorkspaceAuth {
 private final JdbcTemplate db;
 private final PasswordLoginGuard guard;
 private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();
 private final boolean local;
 private final String bootstrapPassword;
 public WorkspaceAuth(JdbcTemplate db, PasswordLoginGuard guard,@Value("${lectus.mall.local-enabled:false}") boolean local,@Value("${lectus.mall.bootstrap-password:}") String bootstrapPassword){this.db=db;this.guard=guard;this.local=local;this.bootstrapPassword=bootstrapPassword;}
 public record Staff(String username,String role,String merchantId) {}
 @PostConstruct void seed(){
  try { new ResourceDatabasePopulator(new ClassPathResource("mall-schema.sql")).execute(db.getDataSource()); }
  catch(Exception e){ throw new IllegalStateException("无法初始化商城联调表",e); }
  if(!local) return;
  if(db.queryForObject("SELECT COUNT(*) FROM lc_merchant",Long.class)==0){
   db.update("INSERT INTO lc_merchant VALUES('M-1001','莱择官方旗舰店','自营','综合零售','莱择电子商务有限公司（联调）','联调联系人','2026-10-23','营业中','')");
   db.update("INSERT INTO lc_merchant VALUES('M-1002','云野运动专营店','入驻','运动户外','云野体育用品有限公司（联调）','联调联系人','2026-10-23','营业中','')");
   db.update("INSERT INTO lc_merchant VALUES('M-1003','森屿生活馆','入驻','服饰生活','森屿商贸有限公司（联调）','联调联系人','2026-10-23','待审核','')");
  }
  seedAccount("platform","PLATFORM",null);seedAccount("merchant","MERCHANT","M-1001");seedAccount("yunye","MERCHANT","M-1002");
 }
 private void seedAccount(String name,String role,String merchant){
  if(db.queryForObject("SELECT COUNT(*) FROM lc_staff WHERE username=?",Long.class,name)==0){
   if(bootstrapPassword.length()<10)throw new IllegalStateException("商城测试账号初始化需要配置至少10位的 bootstrap-password");
   db.update("INSERT INTO lc_staff VALUES(?,?,?,?)",name,encoder.encode(bootstrapPassword),role,merchant);
  }
 }
 public boolean local(){return local;}
 public Map<String,Object> login(String username,String password,String ip){
  if(username==null||username.length()>60||password==null||password.length()>64) throw new BizException("账号或密码错误");
  guard.attempt("workspace:"+username,ip);
  var rows=db.queryForList("SELECT * FROM lc_staff WHERE username=?",username);
  if(rows.isEmpty()||!encoder.matches(password,(String)rows.getFirst().get("password_hash"))) throw new BizException("账号或密码错误");
  byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=HexFormat.of().formatHex(bytes);
  db.update("DELETE FROM lc_staff_session WHERE expires_at<?",Timestamp.from(Instant.now()));
  db.update("INSERT INTO lc_staff_session VALUES(?,?,?)",hash(token),username,Timestamp.from(Instant.now().plusSeconds(28800)));
  var row=rows.getFirst();return Map.of("token",token,"username",username,"role",row.get("role"),"merchantId",Objects.toString(row.get("merchant_id"),""));
 }
 public Staff require(String header,String role){
  String token=header!=null&&header.startsWith("Bearer ")?header.substring(7):"";
  var rows=db.query("SELECT a.username,a.role,a.merchant_id FROM lc_staff_session s JOIN lc_staff a ON a.username=s.username WHERE s.token_hash=? AND s.expires_at>?",(r,i)->new Staff(r.getString(1),r.getString(2),r.getString(3)),hash(token),Timestamp.from(Instant.now()));
  if(rows.isEmpty()) throw new WorkspaceException(401,"请登录后台账号");
  var staff=rows.getFirst();if(role!=null&&!role.equals(staff.role())) throw new WorkspaceException(403,"当前账号没有此操作权限");return staff;
 }
 public void logout(String header){if(header!=null&&header.startsWith("Bearer "))db.update("DELETE FROM lc_staff_session WHERE token_hash=?",hash(header.substring(7)));}
 private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}

