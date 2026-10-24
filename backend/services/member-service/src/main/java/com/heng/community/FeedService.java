package com.heng.community;

import com.heng.config.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class FeedService {
 public static final Set<String> TOPICS=Set.of("日常穿搭","球鞋日记","户外生活","数码好物","生活方式");
 private final JdbcTemplate db;
 public FeedService(JdbcTemplate db){this.db=db;}
 private long count(String sql,Object...args){return db.queryForObject(sql,Long.class,args);}
 private void lockMember(long me){if(db.queryForList("SELECT member_id FROM v_member WHERE member_id=? AND status=1 FOR UPDATE",me).isEmpty())throw new BizException("请重新登录后再试");}
 private static String text(String s,int max,String label){if(s==null||s.isBlank()||s.length()>max)throw new BizException(label+"不能为空且不能超过 "+max+" 字");return s.trim();}
 private static void key(String s){if(s==null||!s.matches("[a-f0-9-]{32,64}"))throw new BizException("提交信息已失效，请刷新后重试");}
 public Map<String,Object> feed(Long me,String tab,String topic,String kind,long before){
  StringBuilder where=new StringBuilder(" WHERE p.deleted=FALSE");var args=new ArrayList<Object>();
  if(before!=0){where.append(" AND p.id<?");args.add(before);}
  if(!topic.isBlank()){if(!TOPICS.contains(topic))throw new BizException("请选择有效话题");where.append(" AND p.topic=?");args.add(topic);}
  if(!kind.isBlank()){if(!Set.of("IMAGE","VIDEO").contains(kind))throw new BizException("请选择有效内容类型");where.append(" AND p.kind=?");args.add(kind);}
  if(!Set.of("recommend","following","mine","saved").contains(tab))throw new BizException("请选择有效栏目");
  if(!tab.equals("recommend")){
   if(me==null)return Map.of("items",List.of(),"hasMore",false,"nextCursor",0);
   if(tab.equals("mine")){where.append(" AND p.author_id=?");args.add(me);}
   if(tab.equals("following")){where.append(" AND EXISTS(SELECT 1 FROM lc_community_follow f WHERE f.member_id=? AND f.author_id=p.author_id)");args.add(me);}
   if(tab.equals("saved")){where.append(" AND EXISTS(SELECT 1 FROM lc_community_reaction r WHERE r.member_id=? AND r.post_id=p.id AND r.kind='SAVE')");args.add(me);}
  }
  var rows=db.queryForList("SELECT p.* FROM lc_community_post p"+where+" ORDER BY p.id DESC LIMIT 13",args.toArray());
  boolean more=rows.size()>12;if(more)rows=rows.subList(0,12);
  var items=rows.stream().map(p->decorate(p,me)).toList();
  return Map.of("items",items,"hasMore",more,"nextCursor",items.isEmpty()?0:items.getLast().get("id"));
 }
 private Map<String,Object> author(Object id,boolean official){
  if(official)return Map.of("id",0,"name","莱择编辑部","avatarUrl","","official",true);
  var rows=db.queryForList("SELECT member_id,nickname,avatar_url FROM v_member WHERE member_id=?",id);
  if(rows.isEmpty())return Map.of("id",id,"name","莱择会员","avatarUrl","","official",false);
  var r=rows.getFirst();String name=Objects.toString(r.get("nickname"),"");String avatar=Objects.toString(r.get("avatar_url"),"");
  return Map.of("id",id,"name",name.isBlank()?"莱择会员":name,"avatarUrl",avatar.matches("/api/member/avatar/[a-f0-9]{32}\\.png")?avatar:"","official",false);
 }
 private Map<String,Object> decorate(Map<String,Object> p,Long me){
  long id=((Number)p.get("id")).longValue();var out=new LinkedHashMap<String,Object>();
  for(String k:List.of("id","title","body","topic","kind"))out.put(k,p.get(k));
  out.put("createdAt",((Timestamp)p.get("created_at")).toInstant().toString());
  out.put("author",author(p.get("author_id"),Boolean.TRUE.equals(p.get("official"))));
  out.put("owned",me!=null&&Objects.equals(me,p.get("author_id")));
  out.put("following",me!=null&&count("SELECT COUNT(*) FROM lc_community_follow WHERE member_id=? AND author_id=?",me,p.get("author_id"))>0);
  out.put("likes",count("SELECT COUNT(*) FROM lc_community_reaction WHERE post_id=? AND kind='LIKE'",id));
  out.put("saves",count("SELECT COUNT(*) FROM lc_community_reaction WHERE post_id=? AND kind='SAVE'",id));
  out.put("comments",count("SELECT COUNT(*) FROM lc_community_comment WHERE post_id=?",id));
  for(String kind:List.of("LIKE","SAVE"))out.put(kind.equals("LIKE")?"liked":"saved",me!=null&&count("SELECT COUNT(*) FROM lc_community_reaction WHERE post_id=? AND member_id=? AND kind=?",id,me,kind)>0);
  out.put("media",db.queryForList("SELECT * FROM lc_community_media WHERE post_id=? ORDER BY sort_order",id).stream().map(m->{
   var item=new LinkedHashMap<String,Object>();for(String k:List.of("id","kind","width","height"))item.put(k,m.get(k));item.put("duration",m.get("duration_seconds"));
   item.put("url",m.get("external_url")!=null?m.get("external_url"):"/api/community/public/media/"+m.get("id"));return item;
  }).toList());return out;
 }
 public Map<String,Object> detail(long id,Long me){var rows=db.queryForList("SELECT * FROM lc_community_post WHERE id=? AND deleted=FALSE",id);if(rows.isEmpty())throw new BizException("内容已删除或暂时无法查看");return decorate(rows.getFirst(),me);}
 public record Publication(String title,String body,String topic,String kind,List<String> mediaIds,String requestKey){}
 @Transactional public Map<String,Object> publish(long me,Publication f){
  lockMember(me);key(f.requestKey());var old=db.queryForList("SELECT id FROM lc_community_post WHERE author_id=? AND request_key=?",me,f.requestKey());if(!old.isEmpty())return detail(((Number)old.getFirst().get("id")).longValue(),me);
  String title=text(f.title(),80,"标题");String body=Objects.toString(f.body(),"").trim();if(body.length()>2000||f.topic()==null||!TOPICS.contains(f.topic())||f.kind()==null||!Set.of("IMAGE","VIDEO").contains(f.kind()))throw new BizException("请检查正文、话题和内容类型");
  if(f.mediaIds()==null||f.mediaIds().isEmpty()||f.mediaIds().size()>9||new HashSet<>(f.mediaIds()).size()!=f.mediaIds().size())throw new BizException("请上传 1 至 9 张图片，或 1 段视频及封面");
  if(count("SELECT COUNT(*) FROM lc_community_post WHERE author_id=? AND created_at>?",me,Timestamp.from(Instant.now().minusSeconds(86400)))>=30)throw new BizException("今天发布的内容较多，请明天再来分享");
  var media=new ArrayList<Map<String,Object>>();for(String mid:f.mediaIds()){
   var rows=db.queryForList("SELECT * FROM lc_community_media WHERE id=? AND owner_id=? AND post_id IS NULL FOR UPDATE",mid,me);if(rows.isEmpty())throw new BizException("图片或视频不可用，请重新上传");media.add(rows.getFirst());
  }
  long videos=media.stream().filter(m->m.get("kind").equals("VIDEO")).count();
  if(f.kind().equals("IMAGE")&&videos!=0||f.kind().equals("VIDEO")&&(videos!=1||media.size()!=2||!media.getFirst().get("kind").equals("VIDEO")))throw new BizException("视频需包含一段视频和一张封面，图文只能包含图片");
  var generated=new org.springframework.jdbc.support.GeneratedKeyHolder();
  db.update(connection->{var ps=connection.prepareStatement("INSERT INTO lc_community_post(author_id,title,body,topic,kind,request_key) VALUES(?,?,?,?,?,?)",new String[]{"id"});ps.setLong(1,me);ps.setString(2,title);ps.setString(3,body);ps.setString(4,f.topic());ps.setString(5,f.kind());ps.setString(6,f.requestKey());return ps;},generated);
  long id=Objects.requireNonNull(generated.getKey()).longValue();
  for(int i=0;i<media.size();i++)db.update("UPDATE lc_community_media SET post_id=?,sort_order=? WHERE id=?",id,i,media.get(i).get("id"));return detail(id,me);
 }
 @Transactional public Map<String,Object> react(long me,long id,String kind,boolean enabled){lockMember(me);detail(id,me);if(!Set.of("LIKE","SAVE").contains(kind))throw new BizException("无效操作");
  if(enabled){if(count("SELECT COUNT(*) FROM lc_community_reaction WHERE member_id=? AND post_id=? AND kind=?",me,id,kind)==0)db.update("INSERT INTO lc_community_reaction(member_id,post_id,kind) VALUES(?,?,?)",me,id,kind);}else db.update("DELETE FROM lc_community_reaction WHERE member_id=? AND post_id=? AND kind=?",me,id,kind);return detail(id,me);
 }
 @Transactional public void follow(long me,long author,boolean enabled){lockMember(me);if(me==author||count("SELECT COUNT(*) FROM v_member WHERE member_id=? AND status=1",author)==0)throw new BizException("无法关注该用户");if(enabled){if(count("SELECT COUNT(*) FROM lc_community_follow WHERE member_id=? AND author_id=?",me,author)==0)db.update("INSERT INTO lc_community_follow VALUES(?,?)",me,author);}else db.update("DELETE FROM lc_community_follow WHERE member_id=? AND author_id=?",me,author);}
 public Map<String,Object> comments(long id,long after){detail(id,null);var rows=db.queryForList("SELECT * FROM lc_community_comment WHERE post_id=? AND id>? ORDER BY id LIMIT 31",id,after);boolean more=rows.size()>30;if(more)rows=rows.subList(0,30);var items=rows.stream().map(c->Map.of("id",c.get("id"),"body",c.get("body"),"createdAt",((Timestamp)c.get("created_at")).toInstant().toString(),"author",author(c.get("author_id"),false))).toList();return Map.of("items",items,"hasMore",more,"nextCursor",items.isEmpty()?0:items.getLast().get("id"));}
 @Transactional public void comment(long me,long id,String body,String requestKey){lockMember(me);detail(id,me);key(requestKey);body=text(body,500,"评论");var old=db.queryForList("SELECT post_id,body FROM lc_community_comment WHERE author_id=? AND request_key=?",me,requestKey);if(!old.isEmpty()){if(!Objects.equals(old.getFirst().get("post_id"),id)||!old.getFirst().get("body").equals(body))throw new BizException("请重新提交评论");return;}
  if(count("SELECT COUNT(*) FROM lc_community_comment WHERE author_id=? AND created_at>?",me,Timestamp.from(Instant.now().minusSeconds(60)))>=10)throw new BizException("评论太快了，请稍后再试");db.update("INSERT INTO lc_community_comment(post_id,author_id,body,request_key) VALUES(?,?,?,?)",id,me,body,requestKey);}
 @Transactional public void remove(long me,long id){if(db.update("UPDATE lc_community_post SET deleted=TRUE WHERE id=? AND author_id=? AND official=FALSE",id,me)!=1)throw new BizException("只能删除自己发布的内容");}
}
