package edu.nju.homework;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.*;
import java.util.*;

/** Private local cache. Failed/partial snapshots never imply deletion. */
public final class Store {
 public JSONObject data; private final AtomicFile file;
 private static final ZoneId DUE_ZONE=ZoneId.of("Asia/Shanghai");
 public Store(Context context){file=new AtomicFile(new File(context.getFilesDir(),"homework.json"));try(InputStream in=file.openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);data=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));}catch(Exception e){data=new JSONObject();}if(!data.has("courses"))p(data,"courses",new JSONArray());if(!data.has("tasks"))p(data,"tasks",new JSONArray());if(!data.has("settings"))p(data,"settings",new JSONObject());repairCachedDueFields();}
 public JSONArray courses(){return data.optJSONArray("courses");} public JSONArray tasks(){return data.optJSONArray("tasks");}
 Store(JSONObject initial){file=null;data=initial;if(!data.has("courses"))p(data,"courses",new JSONArray());if(!data.has("tasks"))p(data,"tasks",new JSONArray());if(!data.has("settings"))p(data,"settings",new JSONObject());repairCachedDueFields();}
 public void save(){if(file==null)return;FileOutputStream out=null;try{out=file.startWrite();out.write(data.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw new IllegalStateException("无法保存本地缓存",e);}}
 private static void p(JSONObject o,String k,Object v){try{o.put(k,v);}catch(JSONException e){throw new IllegalStateException(e);}}
 private static JSONObject copy(JSONObject o){try{return new JSONObject(o.toString());}catch(Exception e){throw new IllegalStateException(e);}}
 public JSONObject course(String id){for(int i=0;i<courses().length();i++){JSONObject c=courses().optJSONObject(i);if(c!=null&&id.equals(c.optString("id")))return c;}return null;}
 public JSONArray sources(JSONObject t){JSONArray s=t.optJSONArray("sources");return s==null?new JSONArray():s;}
 public long due(JSONObject t){return t.optLong("due");}
 public boolean visible(JSONObject t){JSONObject c=course(t.optString("course"));if(c==null)return false;String g=c.optString("group",c.optString("id"));for(int i=0;i<courses().length();i++){JSONObject x=courses().optJSONObject(i);if(x.optString("group",x.optString("id")).equals(g)&&x.optBoolean("visible"))return true;}return false;}
 public boolean complete(JSONObject t){if(t.optBoolean("manualDone"))return true;JSONArray ss=sources(t);boolean active=false;for(int i=0;i<ss.length();i++){JSONObject s=ss.optJSONObject(i);if(s.optBoolean("removed"))continue;active=true;if(!Boolean.TRUE.equals(s.opt("submitted")))return false;}return active;}
 public boolean removed(JSONObject t){JSONArray ss=sources(t);if(ss.length()==0)return false;for(int i=0;i<ss.length();i++)if(!ss.optJSONObject(i).optBoolean("removed"))return false;return true;}
 public void confirmRemoved(JSONObject t,JSONObject s){p(s,"removed",true);recompute(t);}
 private String normalized(String s){return s.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}，。·（）【】]","");}
 private String group(String courseId){JSONObject c=course(courseId);return c==null?courseId:c.optString("group",courseId);}
 private void orphan(JSONObject t){long id=t.optLong("calendarEventId",-1);if(id<0)return;JSONArray a=data.optJSONArray("orphanCalendarEvents");if(a==null){a=new JSONArray();p(data,"orphanCalendarEvents",a);}a.put(id);t.remove("calendarEventId");}
 private static final class DueParse{
  final String raw; final long millis; final boolean failed;
  DueParse(String raw,long millis,boolean failed){this.raw=raw;this.millis=millis;this.failed=failed;}
 }
 private static DueParse parseDueValue(Object raw){
  String rawText=raw==null||raw==JSONObject.NULL?"":String.valueOf(raw);String text=rawText.trim();
  if(text.isEmpty())return new DueParse(rawText,0,false);
  if(text.matches("(?i)^(?:[-–—]+|无|无截止时间|未设置截止时间|没有截止日期|no due date|not set)$"))return new DueParse(rawText,0,false);
  Long numeric=numericDue(text);if(numeric!=null)return new DueParse(rawText,numeric,false);
  if(raw instanceof Number)return new DueParse(rawText,0,true);
  Long parsed=parseOffsetDue(text);
  if(parsed!=null)return new DueParse(rawText,parsed,false);
  String local=text.replace('\uFF1A',':').replace("，"," ").replace("、"," ")
   .replaceAll("(\\d{4})年(\\d{1,2})月(\\d{1,2})日","$1-$2-$3")
   .replaceAll("(?:星期|周)[一二三四五六日天]"," ")
   .replaceAll("(?i)(?<![a-z])(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday|Mon|Tue|Wed|Thu|Fri|Sat|Sun)(?![a-z])"," ")
   .replace("下午"," PM ").replace("晚上"," PM ").replace("中午"," PM ")
   .replace("上午"," AM ").replace("凌晨"," AM ").replace("（"," ").replace("）"," ").replace("("," ").replace(")"," ")
   .replaceAll("\\s*,\\s*"," ").replaceAll("\\s+"," ").trim();
  parsed=parseLocalDue(local);
  if(parsed==null)parsed=parseEnglishDue(local);
  return parsed==null?new DueParse(rawText,0,true):new DueParse(rawText,parsed,false);
 }
 public static long parseDue(Object raw){return parseDueValue(raw).millis;}
 private static Long numericDue(String text){
  try{
   BigDecimal value=new BigDecimal(text).stripTrailingZeros();
   if(value.signum()==0)return 0L;if(value.signum()<0||value.scale()>0)return null;
   long n=value.longValueExact();
   return n<100000000000L?Math.multiplyExact(n,1000L):n;
  }catch(Exception notNumericOrInvalid){
   return null;
  }
 }
 private static Long parseOffsetDue(String text){
  String iso=text.replaceFirst("([+-]\\d{2})(\\d{2})$","$1:$2");
  try{return Instant.parse(iso).toEpochMilli();}catch(Exception ignored){}
  try{return OffsetDateTime.parse(iso,DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli();}catch(Exception ignored){}
  return null;
 }
 private static Long parseLocalDue(String text){
  for(String pattern:new String[]{"uuuu-M-d H:mm:ss","uuuu-M-d H:mm","uuuu-M-d'T'HH:mm:ss","uuuu-M-d'T'HH:mm","uuuu-M-d h:mm:ss a","uuuu-M-d h:mm a","uuuu-M-d a h:mm:ss","uuuu-M-d a h:mm"}){
   try{return LocalDateTime.parse(text,strictFormatter(pattern,Locale.ROOT)).atZone(DUE_ZONE).toInstant().toEpochMilli();}catch(Exception ignored){}
  }
  return null;
 }
 private static Long parseEnglishDue(String text){
  for(String pattern:new String[]{"EEEE, d MMMM uuuu, HH:mm:ss","EEEE, d MMMM uuuu, HH:mm","EEEE d MMMM uuuu HH:mm:ss","EEEE d MMMM uuuu HH:mm","d MMMM uuuu, HH:mm:ss","d MMMM uuuu, HH:mm","d MMMM uuuu HH:mm:ss","d MMMM uuuu HH:mm","EEEE, d MMMM uuuu, h:mm:ss a","EEEE, d MMMM uuuu, h:mm a","d MMMM uuuu, h:mm:ss a","d MMMM uuuu, h:mm a"}){
   try{return LocalDateTime.parse(text,strictFormatter(pattern,Locale.ENGLISH)).atZone(DUE_ZONE).toInstant().toEpochMilli();}catch(Exception ignored){}
  }
  return null;
 }
 private static DateTimeFormatter strictFormatter(String pattern,Locale locale){
  return new DateTimeFormatterBuilder().appendPattern(pattern)
   .optionalStart().appendFraction(java.time.temporal.ChronoField.NANO_OF_SECOND,0,9,true).optionalEnd()
   .toFormatter(locale).withResolverStyle(ResolverStyle.STRICT);
 }
 private void repairCachedDueFields(){
  JSONArray ts=tasks();for(int i=0;i<ts.length();i++){
   JSONObject task=ts.optJSONObject(i);if(task==null)continue;JSONArray ss=sources(task);boolean sourced=ss.length()>0;
   for(int j=0;j<ss.length();j++){
    JSONObject source=ss.optJSONObject(j);if(source==null||!source.has("dueRaw"))continue;
    DueParse parsed=parseDueValue(source.opt("dueRaw"));p(source,"dueParseFailed",parsed.failed);
    if(!parsed.failed)p(source,"due",parsed.millis);
    else if(source.optLong("due")<0)p(source,"due",0);
   }
   if(sourced&&!task.optBoolean("localEdited"))recompute(task);
  }
 }
 public void ingest(String platform,JSONObject result)throws Exception{
  JSONArray incoming=result.optJSONArray("courses");if(incoming==null||result.has("error"))throw new IllegalArgumentException("invalid snapshot");
  // Validate before touching live cache, then roll back on an unexpected parse failure.
  for(int i=0;i<incoming.length();i++){JSONObject c=incoming.getJSONObject(i);if(!c.has("id")||!c.has("name")||!c.has("error")&&!(c.opt("assignments") instanceof JSONArray))throw new IllegalArgumentException("invalid course");}
  JSONObject previous=copy(data);try{
   for(int i=0;i<incoming.length();i++){
    JSONObject incomingCourse=incoming.getJSONObject(i);String cid=platform+":"+incomingCourse.get("id");JSONObject c=course(cid);
    if(c==null){c=new JSONObject();p(c,"id",cid);p(c,"platform",platform);p(c,"name",incomingCourse.optString("name"));p(c,"visible",false);p(c,"group",cid);
     for(int j=0;j<courses().length();j++){JSONObject other=courses().optJSONObject(j);if(!platform.equals(other.optString("platform"))&&normalized(other.optString("name")).equals(normalized(c.optString("name")))&&!normalized(c.optString("name")).isEmpty()){p(c,"group",group(other.optString("id")));break;}}
     courses().put(c);
    }else p(c,"name",incomingCourse.optString("name"));
    p(c,"lastError",incomingCourse.has("error")?"课程读取不完整":"");
    p(c,"detailsIncomplete",incomingCourse.has("detailsPartial"));
    if(incomingCourse.has("error"))continue; // Never apply a partial course snapshot.
    Set<String> seen=new HashSet<>();JSONArray assignments=incomingCourse.getJSONArray("assignments");
    for(int j=0;j<assignments.length();j++){
     JSONObject a=assignments.getJSONObject(j);String nativeId=a.optString("id",a.optString("url"));if(nativeId.isEmpty())throw new IllegalArgumentException("unstable assignment id");String sid=platform+":"+nativeId;seen.add(sid);
     JSONObject found=null,oldSource=null;for(int k=0;k<tasks().length();k++){JSONObject t=tasks().optJSONObject(k);JSONArray ss=sources(t);for(int z=0;z<ss.length();z++){JSONObject s=ss.optJSONObject(z);if(sid.equals(s.optString("key"))){found=t;oldSource=s;break;}}if(found!=null)break;}
     DueParse due=parseDueValue(a.has("due")?a.opt("due"):a.opt("deadline"));long dueMillis=due.millis;
     if(due.failed&&oldSource!=null&&oldSource.optLong("due")>0)dueMillis=oldSource.optLong("due");
     JSONObject source=new JSONObject();p(source,"key",sid);p(source,"platform",platform);p(source,"id",nativeId);p(source,"course",cid);p(source,"title",a.optString("title",a.optString("name","未命名作业")));p(source,"content",a.optString("content"));p(source,"dueRaw",due.raw);p(source,"dueParseFailed",due.failed);p(source,"due",dueMillis);Object submitted=a.opt("submitted");p(source,"submitted",submitted instanceof Boolean?submitted:JSONObject.NULL);for(String key:new String[]{"grade","comment","attachments"})p(source,key,a.opt(key)==null?(key.equals("attachments")?new JSONArray():""):a.opt(key));p(source,"removed",false);p(source,"missing",false);
     p(source,"detailUnavailable",a.optBoolean("detailUnavailable"));
     if(oldSource!=null){if(a.optBoolean("detailUnavailable")){for(String k:new String[]{"content","comment","attachments"})if(oldSource.has(k))p(source,k,oldSource.opt(k));if(source.optString("grade").isEmpty()&&oldSource.has("grade"))p(source,"grade",oldSource.opt("grade"));}boolean changed=!samePlatformContent(oldSource,source)||oldSource.optBoolean("removed");JSONArray ss=sources(found);for(int z=0;z<ss.length();z++)if(ss.optJSONObject(z)==oldSource)ss.put(z,source);if(changed){found.remove("localEdited");recompute(found);}}
     else{
      // Only exact title+course+deadline across different platforms. Never merge within a platform.
      for(int k=0;k<tasks().length();k++){JSONObject t=tasks().optJSONObject(k);if(t.optBoolean("noAutoMerge")||!group(t.optString("course")).equals(group(cid))||!normalized(t.optString("title")).equals(normalized(source.optString("title")))||source.optLong("due")==0||due(t)!=source.optLong("due"))continue;boolean conflict=sources(t).length()==0;for(int z=0;z<sources(t).length();z++)conflict|=platform.equals(sources(t).optJSONObject(z).optString("platform"));if(!conflict){found=t;break;}}
      if(found==null){found=new JSONObject();p(found,"id",UUID.randomUUID().toString());p(found,"course",cid);p(found,"sources",new JSONArray());tasks().put(found);}sources(found).put(source);recompute(found);
     }
    }
    for(int k=0;k<tasks().length();k++){JSONArray ss=sources(tasks().optJSONObject(k));for(int z=0;z<ss.length();z++){JSONObject s=ss.optJSONObject(z);if(cid.equals(s.optString("course"))&&!seen.contains(s.optString("key")))p(s,"missing",true);}}
   }
   save();
  }catch(Exception e){data=previous;throw e;}
 }
 private boolean samePlatformContent(JSONObject a,JSONObject b){for(String k:new String[]{"title","content","due"})if(!String.valueOf(a.opt(k)).equals(String.valueOf(b.opt(k))))return false;return true;}
 private void recompute(JSONObject t){JSONArray ss=sources(t);if(ss.length()==0)return;JSONObject first=ss.optJSONObject(0);p(t,"title",first.optString("title"));p(t,"content",first.optString("content"));long min=0;for(int i=0;i<ss.length();i++){JSONObject s=ss.optJSONObject(i);long d=s.optLong("due");if(!s.optBoolean("removed")&&d>0&&(min==0||d<min))min=d;}p(t,"due",min);}
 private void ensureManualSource(JSONObject t){if(sources(t).length()>0)return;JSONObject s=new JSONObject();for(String k:new String[]{"title","content","due","course"})p(s,k,t.opt(k));p(s,"dueRaw",t.optLong("due")>0?Long.toString(t.optLong("due")):"");p(s,"dueParseFailed",false);p(s,"id",t.optString("id"));p(s,"key","manual:"+t.optString("id"));p(s,"platform","manual");p(s,"submitted",false);p(t,"sources",new JSONArray().put(s));}
 public void mergeTasks(String a,String b){JSONObject x=task(a),y=task(b);if(x==null||y==null||x==y)return;ensureManualSource(x);ensureManualSource(y);for(int i=0;i<sources(y).length();i++)sources(x).put(sources(y).optJSONObject(i));p(x,"manualDone",x.optBoolean("manualDone")||y.optBoolean("manualDone"));orphan(y);for(int i=tasks().length()-1;i>=0;i--)if(tasks().optJSONObject(i)==y)tasks().remove(i);recompute(x);}
 public void splitTask(String id){JSONObject t=task(id);if(t==null||sources(t).length()<2)return;JSONArray ss=sources(t);orphan(t);for(int i=0;i<ss.length();i++){JSONObject s=copy(ss.optJSONObject(i)),next=new JSONObject();p(next,"id",UUID.randomUUID().toString());p(next,"course",s.optString("course"));p(next,"manualDone",t.optBoolean("manualDone"));p(next,"noAutoMerge",true);p(next,"sources",new JSONArray().put(s));recompute(next);tasks().put(next);}for(int i=tasks().length()-1;i>=0;i--)if(tasks().optJSONObject(i)==t)tasks().remove(i);}
 public void mergeCourses(String a,String b){JSONObject x=course(a),y=course(b);if(x==null||y==null)return;String old=group(b),target=group(a);for(int i=0;i<courses().length();i++){JSONObject c=courses().optJSONObject(i);if(group(c.optString("id")).equals(old))p(c,"group",target);}}
 public void splitCourse(String id){JSONObject selected=course(id);if(selected==null)return;String old=group(id),replacement=null;for(int i=0;i<courses().length();i++){JSONObject c=courses().optJSONObject(i);if(!id.equals(c.optString("id"))&&old.equals(group(c.optString("id")))){replacement=c.optString("id");break;}}if(replacement==null)return;for(int i=0;i<courses().length();i++){JSONObject c=courses().optJSONObject(i);if(old.equals(group(c.optString("id"))))p(c,"group",c==selected?id:replacement);}ArrayList<String> split=new ArrayList<>();for(int i=0;i<tasks().length();i++){JSONObject t=tasks().optJSONObject(i);if(sources(t).length()<2)continue;boolean belongs=false;for(int j=0;j<sources(t).length();j++)belongs|=id.equals(sources(t).optJSONObject(j).optString("course"));if(belongs)split.add(t.optString("id"));}for(String taskId:split)splitTask(taskId);}
 private JSONObject task(String id){for(int i=0;i<tasks().length();i++){JSONObject t=tasks().optJSONObject(i);if(t.optString("id").equals(id))return t;}return null;}
}
