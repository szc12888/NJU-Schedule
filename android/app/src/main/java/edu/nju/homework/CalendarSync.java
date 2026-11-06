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
 private boolean exists(long id){try(Cursor c=activity.getContentResolver().query(event(id),new String[]{"_id"},null,null,null)){return c!=null&&c.moveToFirst();}}
 public void sync(){if(!permission()||settings()==null||!settings().has("calendarId"))return;
  ContentResolver cr=activity.getContentResolver();long calendarId=settings().optLong("calendarId");
  try{
   JSONArray orphans=store.data.optJSONArray("orphanCalendarEvents");if(orphans!=null){while(orphans.length()>0){long id=orphans.optLong(0,-1);if(id>=0)cr.delete(event(id),null,null);orphans.remove(0);}}
   for(int i=0;i<store.tasks().length();i++){
    JSONObject t=store.tasks().optJSONObject(i);long due=store.due(t),id=t.optLong("calendarEventId",-1);boolean complete=store.complete(t),eligible=due>System.currentTimeMillis()&&store.visible(t)&&!complete&&!store.removed(t);
    if(id>=0&&!exists(id)){t.remove("calendarEventId");id=-1;}if(id<0&&!eligible)continue;
    if(due==0){if(id>=0){ContentValues disabled=new ContentValues();disabled.put(CalendarContract.Events.HAS_ALARM,0);disabled.put(CalendarContract.Events.TITLE,(store.removed(t)?"[平台已移除] ":complete?"[已完成] ":"")+t.optString("title"));ArrayList<ContentProviderOperation> ops=new ArrayList<>();ops.add(ContentProviderOperation.newUpdate(event(id)).withValues(disabled).build());ops.add(ContentProviderOperation.newDelete(CalendarContract.Reminders.CONTENT_URI).withSelection("event_id = ?",new String[]{""+id}).build());cr.applyBatch(CalendarContract.AUTHORITY,ops);}continue;}
    ContentValues v=new ContentValues();v.put(CalendarContract.Events.TITLE,(complete?"[已完成] ":store.removed(t)?"[平台已移除] ":"")+t.optString("title"));JSONObject course=store.course(t.optString("course"));v.put(CalendarContract.Events.DESCRIPTION,"NJU作业 · "+(course==null?"":course.optString("name")));v.put(CalendarContract.Events.DTSTART,due);v.put(CalendarContract.Events.DTEND,due+60000);v.put(CalendarContract.Events.EVENT_TIMEZONE,TimeZone.getDefault().getID());v.put(CalendarContract.Events.HAS_ALARM,eligible?1:0);
    v.put(CalendarContract.Events.CALENDAR_ID,calendarId);ArrayList<ContentProviderOperation> ops=new ArrayList<>();boolean create=id<0;
    ops.add(create?ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI).withValues(v).build():ContentProviderOperation.newUpdate(event(id)).withValues(v).build());
    if(!create)ops.add(ContentProviderOperation.newDelete(CalendarContract.Reminders.CONTENT_URI).withSelection("event_id = ?",new String[]{""+id}).build());
    if(eligible){ContentValues r=new ContentValues();r.put(CalendarContract.Reminders.MINUTES,settings().optInt("reminderMinutes",1440));r.put(CalendarContract.Reminders.METHOD,CalendarContract.Reminders.METHOD_ALERT);ContentProviderOperation.Builder reminder=ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI).withValues(r);if(create)reminder.withValueBackReference(CalendarContract.Reminders.EVENT_ID,0);else reminder.withValue(CalendarContract.Reminders.EVENT_ID,id);ops.add(reminder.build());}
    ContentProviderResult[] results=cr.applyBatch(CalendarContract.AUTHORITY,ops);if(create){if(results[0].uri==null)throw new IllegalStateException();MainActivity.put(t,"calendarEventId",ContentUris.parseId(results[0].uri));}
   }store.save();
  }catch(Exception e){store.save();toast("日历同步未完成；请检查日历账号及权限后重试");}
 }
 public void deleteTask(JSONObject t){long id=t.optLong("calendarEventId",-1);if(id<0)return;if(permission()){try{activity.getContentResolver().delete(event(id),null,null);t.remove("calendarEventId");return;}catch(Exception ignored){}}
  JSONArray orphan=store.data.optJSONArray("orphanCalendarEvents");if(orphan==null){orphan=new JSONArray();MainActivity.put(store.data,"orphanCalendarEvents",orphan);}orphan.put(id);toast("日历权限不可用，恢复权限后将清理此作业日程");
 }
}
