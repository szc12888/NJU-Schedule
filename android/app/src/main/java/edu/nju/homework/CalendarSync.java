package edu.nju.homework;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.widget.Toast;
import org.json.*;
import java.util.*;

/** Only touches events whose identifiers were recorded by this app. */
public final class CalendarSync {
 private final Activity activity;private final Store store;
 public CalendarSync(Activity a,Store s){activity=a;store=s;}
 private JSONObject settings(){return store.data.optJSONObject("settings");}
 private boolean permission(){return activity.checkSelfPermission(Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED&&activity.checkSelfPermission(Manifest.permission.WRITE_CALENDAR)==PackageManager.PERMISSION_GRANTED;}
 private void toast(String s){Toast.makeText(activity,s,Toast.LENGTH_LONG).show();}
 public void chooseCalendar(){if(!permission()){activity.requestPermissions(new String[]{Manifest.permission.READ_CALENDAR,Manifest.permission.WRITE_CALENDAR},70);return;}
  ArrayList<Long> ids=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();ArrayList<Long> primary=new ArrayList<>();
  try(Cursor c=activity.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,null,"calendar_access_level >= ? AND visible = 1",new String[]{"500"},null)){if(c!=null)while(c.moveToNext()){long id=c.getLong(c.getColumnIndexOrThrow(CalendarContract.Calendars._ID));ids.add(id);labels.add(c.getString(c.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME))+" · "+c.getString(c.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)));int index=c.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY);if(index>=0&&c.getInt(index)==1)primary.add(id);}}
  catch(Exception e){toast("无法读取系统日历，请检查权限");return;}if(ids.isEmpty()){toast("未发现可写日历，请先在系统日历添加账号");return;}
  if(ids.size()==1){select(ids.get(0));return;}if(!settings().has("calendarId")&&primary.size()==1){select(primary.get(0));return;}new AlertDialog.Builder(activity).setTitle("选择作业提醒日历").setItems(labels.toArray(new String[0]),(d,w)->select(ids.get(w))).setNegativeButton("取消",null).show();
 }
 private void select(long id){MainActivity.put(settings(),"calendarId",id);store.save();toast("已保存日历选择");sync();}
 private Uri event(long id){return ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,id);}
 private boolean exists(long id){try(Cursor c=activity.getContentResolver().query(event(id),new String[]{CalendarContract.Events.DELETED},null,null,null)){if(c==null)throw new IllegalStateException("Calendar query unavailable");return c.moveToFirst()&&c.getInt(0)==0;}}
 private String description(JSONObject task){
  JSONObject course=store.course(task.optString("course"));
  return "NJU作业 · "+(course==null?"":course.optString("name"));
 }
 private String eventTitle(JSONObject task){
  return (store.complete(task)?"[已完成] ":store.removed(task)?"[平台已移除] ":"")+task.optString("title");
 }
 private List<Object> signature(String title,String description,long due,long end){
  return Arrays.asList(title,description,due,end);
 }
 Map<JSONObject,Long> findLegacyEvents(){
  Map<List<Object>,JSONObject> tasks=new HashMap<>();
  Set<List<Object>> ambiguous=new HashSet<>();
  Set<Long> known=new HashSet<>();
  JSONArray orphans=store.data.optJSONArray("orphanCalendarEvents");
  if(orphans!=null)for(int i=0;i<orphans.length();i++)known.add(orphans.optLong(i,-1));
  for(int i=0;i<store.tasks().length();i++){
   JSONObject task=store.tasks().optJSONObject(i);
   if(task==null)continue;
   known.add(task.optLong("calendarEventId",-1));
   long due=store.due(task);
   if(due<=0)continue;
   for(String title:new HashSet<>(Arrays.asList(task.optString("title"),eventTitle(task)))){
    List<Object> key=signature(title,description(task),due,due+60000);
    if(tasks.putIfAbsent(key,task)!=null)ambiguous.add(key);
   }
  }
  Map<JSONObject,Long> matches=new LinkedHashMap<>();
  Set<JSONObject> multiple=new HashSet<>();
  try(Cursor c=activity.getContentResolver().query(CalendarContract.Events.CONTENT_URI,
    new String[]{CalendarContract.Events._ID,CalendarContract.Events.TITLE,CalendarContract.Events.DESCRIPTION,CalendarContract.Events.DTSTART,CalendarContract.Events.DTEND},
    CalendarContract.Events.DELETED+"=0 AND "+CalendarContract.Events.DESCRIPTION+" LIKE ?",new String[]{"NJU作业 · %"},null)){
   if(c==null)throw new IllegalStateException("Calendar query unavailable");
   while(c.moveToNext()){
    long id=c.getLong(0);
    if(known.contains(id))continue;
    List<Object> key=signature(c.getString(1),c.getString(2),c.getLong(3),c.getLong(4));
    JSONObject task=tasks.get(key);
    if(task!=null&&!ambiguous.contains(key)&&matches.putIfAbsent(task,id)!=null)multiple.add(task);
   }
  }
  for(JSONObject task:multiple)matches.remove(task);
  return matches;
 }
 public void syncNow(){
  if(!permission()||!settings().has("calendarId")){chooseCalendar();return;}
  try{
   long target=settings().optLong("calendarId");
   Map<JSONObject,Long> plan=findLegacyEvents();
   if(plan.isEmpty()){sync();return;}
   int reconnect=0;
   Map<JSONObject,Long> links=repairLinks(plan);
   for(long id:links.values())if(id<0)reconnect++;
   new AlertDialog.Builder(activity).setTitle("整理旧日程")
    .setMessage("发现 "+plan.size()+" 条可匹配的旧日程。将重新关联 "+reconnect+" 条，移除 "+(plan.size()-reconnect)+" 条已有对应日程的重复项。")
    .setPositiveButton("整理并同步",(d,w)->{
     try{
      if(settings().optLong("calendarId")!=target||!plan.equals(findLegacyEvents())||!links.equals(repairLinks(plan))){toast("日程已变化，请重新同步");return;}
      repairLegacyEvents(plan,target);
     }catch(Exception e){toast("旧日程未完全整理，请检查日历权限后重试");}
    }).setNegativeButton("取消",null).show();
  }catch(Exception e){toast("无法核对旧日程，请检查日历权限");}
 }
 private Map<JSONObject,Long> repairLinks(Map<JSONObject,Long> plan){
  Map<JSONObject,Long> links=new HashMap<>();
  for(JSONObject task:plan.keySet()){
   long id=task.optLong("calendarEventId",-1);
   links.put(task,id>=0&&exists(id)?id:-1L);
  }
  return links;
 }
 void repairLegacyEvents(Map<JSONObject,Long> plan,long target){
  Map<JSONObject,Long> duplicates=new LinkedHashMap<>();
  for(Map.Entry<JSONObject,Long> entry:plan.entrySet()){
   JSONObject task=entry.getKey();
   long current=task.optLong("calendarEventId",-1);
   if(current<0||!exists(current))MainActivity.put(task,"calendarEventId",entry.getValue());
   else duplicates.put(task,entry.getValue());
  }
  sync();
  int failed=0;
  for(Map.Entry<JSONObject,Long> entry:duplicates.entrySet()){
   JSONObject task=entry.getKey();
   try(Cursor c=activity.getContentResolver().query(event(task.optLong("calendarEventId")),
     new String[]{CalendarContract.Events.CALENDAR_ID,CalendarContract.Events.DTSTART,CalendarContract.Events.TITLE,CalendarContract.Events.DESCRIPTION,CalendarContract.Events.DELETED},
     null,null,null)){
    if(c==null||!c.moveToFirst()||c.getInt(4)!=0||c.getLong(0)!=target||c.getLong(1)!=store.due(task)||!eventTitle(task).equals(c.getString(2))||!description(task).equals(c.getString(3))){failed++;continue;}
    if(activity.getContentResolver().delete(event(entry.getValue()),null,null)!=1)failed++;
   }catch(Exception e){failed++;}
  }
  if(failed>0)toast("有 "+failed+" 条重复日程暂未移除");
 }
 public void sync(){if(!permission()||settings()==null||!settings().has("calendarId"))return;
  ContentResolver cr=activity.getContentResolver();long calendarId=settings().optLong("calendarId");
  int failed=0;
  JSONArray orphans=store.data.optJSONArray("orphanCalendarEvents");if(orphans!=null){for(int i=orphans.length()-1;i>=0;i--){try{long id=orphans.optLong(i,-1);if(id>=0)cr.delete(event(id),null,null);orphans.remove(i);}catch(Exception e){failed++;}}}
   for(int i=0;i<store.tasks().length();i++){
    try{
    JSONObject t=store.tasks().optJSONObject(i);long due=store.due(t),id=t.optLong("calendarEventId",-1);boolean complete=store.complete(t),eligible=due>System.currentTimeMillis()&&store.visible(t)&&!complete&&!store.removed(t);
    if(id>=0&&!exists(id)){t.remove("calendarEventId");id=-1;}if(id<0&&!eligible)continue;
    if(due==0){if(id>=0){ContentValues disabled=new ContentValues();disabled.put(CalendarContract.Events.HAS_ALARM,0);disabled.put(CalendarContract.Events.TITLE,(store.removed(t)?"[平台已移除] ":complete?"[已完成] ":"")+t.optString("title"));ArrayList<ContentProviderOperation> ops=new ArrayList<>();ops.add(ContentProviderOperation.newUpdate(event(id)).withValues(disabled).withExpectedCount(1).build());ops.add(ContentProviderOperation.newDelete(CalendarContract.Reminders.CONTENT_URI).withSelection("event_id = ?",new String[]{""+id}).build());cr.applyBatch(CalendarContract.AUTHORITY,ops);}continue;}
    ContentValues v=new ContentValues();v.put(CalendarContract.Events.TITLE,eventTitle(t));v.put(CalendarContract.Events.DESCRIPTION,description(t));v.put(CalendarContract.Events.DTSTART,due);v.put(CalendarContract.Events.DTEND,due+60000);v.put(CalendarContract.Events.EVENT_TIMEZONE,TimeZone.getDefault().getID());v.put(CalendarContract.Events.HAS_ALARM,eligible?1:0);
    v.put(CalendarContract.Events.CALENDAR_ID,calendarId);ArrayList<ContentProviderOperation> ops=new ArrayList<>();boolean create=id<0;
    ops.add(create?ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI).withValues(v).build():ContentProviderOperation.newUpdate(event(id)).withValues(v).withExpectedCount(1).build());
    if(!create)ops.add(ContentProviderOperation.newDelete(CalendarContract.Reminders.CONTENT_URI).withSelection("event_id = ?",new String[]{""+id}).build());
    if(eligible){ContentValues r=new ContentValues();r.put(CalendarContract.Reminders.MINUTES,settings().optInt("reminderMinutes",1440));r.put(CalendarContract.Reminders.METHOD,CalendarContract.Reminders.METHOD_ALERT);ContentProviderOperation.Builder reminder=ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI).withValues(r);if(create)reminder.withValueBackReference(CalendarContract.Reminders.EVENT_ID,0);else reminder.withValue(CalendarContract.Reminders.EVENT_ID,id);ops.add(reminder.build());}
    ContentProviderResult[] results=cr.applyBatch(CalendarContract.AUTHORITY,ops);if(create){if(results[0].uri==null)throw new IllegalStateException();MainActivity.put(t,"calendarEventId",ContentUris.parseId(results[0].uri));}
    }catch(Exception e){failed++;}
   }
   store.save();
   if(failed>0)toast("有 "+failed+" 条日程未同步；请检查日历账号及权限后重试");
 }
 public void deleteTask(JSONObject t){long id=t.optLong("calendarEventId",-1);if(id<0)return;if(permission()){try{activity.getContentResolver().delete(event(id),null,null);t.remove("calendarEventId");return;}catch(Exception ignored){}}
  JSONArray orphan=store.data.optJSONArray("orphanCalendarEvents");if(orphan==null){orphan=new JSONArray();MainActivity.put(store.data,"orphanCalendarEvents",orphan);}orphan.put(id);toast("日历权限不可用，恢复权限后将清理此作业日程");
 }
}
