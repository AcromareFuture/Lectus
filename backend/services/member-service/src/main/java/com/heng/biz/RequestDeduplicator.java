package com.heng.biz;
import com.heng.config.BizException;
import com.heng.mapper.AtomicWriteMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.function.Supplier;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
@Service
public class RequestDeduplicator {
 private final JdbcTemplate jdbc; private final AtomicWriteMapper atomic;
 public RequestDeduplicator(JdbcTemplate jdbc, AtomicWriteMapper atomic) {this.jdbc=jdbc;this.atomic=atomic;}
 @Transactional(rollbackFor=Exception.class)
 public Long execute(Long memberId,String scope,String key,String payload,Supplier<Long> action) {
  if(key==null || !key.matches("[A-Za-z0-9_-]{16,80}")) throw new BizException("请提供有效请求编号");
  var member=atomic.lockMember(memberId);
  if(member==null || !Integer.valueOf(1).equals(member.getStatus())) throw new BizException("会员不存在或已停用");
  String fingerprint=hash(payload);
  var rows=jdbc.queryForList("SELECT payload_hash,result_id FROM v_request_result WHERE member_id=? AND scope=? AND request_key=? FOR UPDATE",memberId,scope,key);
  if(!rows.isEmpty()) {
   if(!fingerprint.equals(rows.get(0).get("payload_hash"))) throw new BizException("同一请求编号不能用于不同内容");
   return ((Number)rows.get(0).get("result_id")).longValue();
  }
  Long id=action.get();
  jdbc.update("INSERT INTO v_request_result(member_id,scope,request_key,payload_hash,result_id) VALUES(?,?,?,?,?)",memberId,scope,key,fingerprint,id);
  return id;
 }
 private String hash(String value) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
