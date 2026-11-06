package edu.nju.homework;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** One Google calendar per local connection; OAuth tokens are used in memory only. */
final class GoogleCalendarSync {
 static final String SCOPE="https://www.googleapis.com/auth/calendar.app.created";
 private static final String ROOT="https://www.googleapis.com/calendar/v3";
 static final int PERIODIC=701, IMMEDIATE=702;
 private final SharedPreferences prefs;
 private final String token;
 private final int connection;
 GoogleCalendarSync(Context c,String token){
  this.prefs=prefs(c);this.token=token;this.connection=prefs.getInt("connection",0);
 }
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("google_calendar",Context.MODE_PRIVATE);}
 static final class CreationUncertain extends IOException{
  CreationUncertain(){super("创建结果未确认，请先在 Google 日历查看 NJU Homework 是否已出现");}
 }
 static final class ApiError extends IOException{
  final int status;
  ApiError(int status){super("Google 日历请求失败（HTTP "+status+"）");this.status=status;}
 }
 private void active() throws InterruptedIOException{
  if(Thread.currentThread().isInterrupted()||!prefs.getBoolean("enabled",false)||connection!=prefs.getInt("connection",0))
   throw new InterruptedIOException();
 }
 private JSONObject request(String method,String path,JSONObject body)throws Exception{
  active();
  HttpURLConnection conn=(HttpURLConnection)new URL(ROOT+path).openConnection();
  try{
   conn.setInstanceFollowRedirects(false);
   conn.setConnectTimeout(15000);conn.setReadTimeout(20000);
   conn.setRequestMethod(method);conn.setRequestProperty("Authorization","Bearer "+token);
   if(body!=null){
    byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
    conn.setDoOutput(true);conn.setFixedLengthStreamingMode(bytes.length);
    conn.setRequestProperty("Content-Type","application/json; charset=utf-8");
    try(OutputStream out=conn.getOutputStream()){out.write(bytes);}
   }
   int status=conn.getResponseCode();
   if(status<200||status>=300)throw new ApiError(status);
   if(status==204)return new JSONObject();
   try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
    byte[] buffer=new byte[4096];int n;
    while((n=in.read(buffer))!=-1){
     if(out.size()+n>1024*1024)throw new IOException("Response too large");
     out.write(buffer,0,n);
    }
    return new JSONObject(out.toString("UTF-8"));
   }
  }finally{conn.disconnect();}
 }
 static String hash(String value)throws Exception{
  byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
  StringBuilder id=new StringBuilder();
  for(byte b:bytes){int v=b&255;id.append(Character.forDigit(v>>4,16)).append(Character.forDigit(v&15,16));}
  return id.toString();
 }
 static JSONObject event(Store store,JSONObject task,long due,boolean remind)throws JSONException{
  String title=(store.complete(task)?"[已完成] ":store.removed(task)?"[平台已移除] ":"")+task.optString("title");
  JSONArray reminders=new JSONArray();
  if(remind){
   int minutes=store.data.getJSONObject("settings").optInt("reminderMinutes",1440);
   reminders.put(new JSONObject().put("method","popup").put("minutes",Math.max(0,Math.min(40320,minutes))));
  }
  return new JSONObject().put("summary",title)
    .put("start",new JSONObject().put("dateTime",Instant.ofEpochMilli(due).toString()))
    .put("end",new JSONObject().put("dateTime",Instant.ofEpochMilli(due+60000).toString()))
    .put("reminders",new JSONObject().put("useDefault",false).put("overrides",reminders));
 }
 private void checkpoint(SharedPreferences.Editor edit)throws IOException{
  if(!edit.commit())throw new IOException("无法保存日历同步记录");
 }
 void sync(Store store)throws Exception{
  active();
  String calendar=prefs.getString("calendar","");
  if(calendar.isEmpty()){
   // A failed or ambiguous create must not be repeated automatically.
   if(prefs.getBoolean("creating",false))throw new CreationUncertain();
   checkpoint(prefs.edit().putBoolean("creating",true));
   try{
    calendar=request("POST","/calendars",new JSONObject().put("summary","NJU Homework").put("timeZone","Asia/Shanghai")).getString("id");
   }catch(ApiError e){if(e.status<500)checkpoint(prefs.edit().remove("creating"));throw e;}
   checkpoint(prefs.edit().putString("calendar",calendar).remove("creating"));
  }
  String base="/calendars/"+Uri.encode(calendar);
  request("GET",base,null); // No creation or fallback if this account cannot access the saved calendar.
  String events=base+"/events";
  Set<String> retained=new HashSet<>();
  long now=System.currentTimeMillis();
  for(int i=0;i<store.tasks().length();i++){
   JSONObject task=store.tasks().optJSONObject(i);
   if(task==null||task.optString("id").isEmpty())continue;
   String id="nju"+hash(task.optString("id")),key="event_"+id;
   long due=store.due(task);
   boolean remind=due>now&&store.visible(task)&&!store.complete(task)&&!store.removed(task);
   boolean known=prefs.contains(key);
   if(!known&&!remind)continue;
   String path=events+"/"+id+"?sendUpdates=none";
   if(due<=0){
    retained.add(key);
    try{
     JSONObject existing=request("GET",events+"/"+id,null);
     JSONObject body=event(store,task,now,false).put("start",existing.getJSONObject("start")).put("end",existing.getJSONObject("end"));
     String fingerprint=hash(body.toString());
     if(!fingerprint.equals(prefs.getString(key,""))){request("PUT",path,body);checkpoint(prefs.edit().putString(key,fingerprint));}
    }catch(ApiError e){if(e.status!=404&&e.status!=410)throw e;checkpoint(prefs.edit().remove(key));}
    continue;
   }
   retained.add(key);
   JSONObject body=event(store,task,due,remind);
   String fingerprint=hash(body.toString());
   if(fingerprint.equals(prefs.getString(key,"")))continue;
   boolean insert=!known;
   if(known){
    try{request("PUT",path,body);}catch(ApiError e){if(e.status!=404&&e.status!=410)throw e;insert=remind;if(!insert){checkpoint(prefs.edit().remove(key));continue;}}
   }
   if(insert){
    try{request("POST",events+"?sendUpdates=none",body.put("id",id));}
    catch(ApiError e){if(e.status!=409)throw e;body.remove("id");request("PUT",path,body);}
   }
   checkpoint(prefs.edit().putString(key,fingerprint));
  }
  // Reconcile only IDs written by this connection, and only from a valid disk snapshot.
  if(store.loadedFromDisk())for(String key:prefs.getAll().keySet()){
   if(!key.matches("event_nju[0-9a-f]{64}")||retained.contains(key))continue;
   try{request("DELETE",events+"/"+key.substring(6)+"?sendUpdates=none",null);}
   catch(ApiError e){if(e.status!=404&&e.status!=410)throw e;}
   checkpoint(prefs.edit().remove(key));
  }
  active();
  checkpoint(prefs.edit().putLong("lastSuccess",System.currentTimeMillis()).putString("status","同步成功"));
 }
}
