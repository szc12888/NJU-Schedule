package edu.nju.homework;

import org.json.*;

public final class GoogleCalendarChecks {
 static void check(boolean value,String what){if(!value)throw new AssertionError(what);}
 public static void main(String[] args)throws Exception{
  Store store=new Store(new JSONObject());
  JSONObject task=new JSONObject().put("title","Synthetic homework").put("grade",95).put("content","Private submission").put("due",1900000000000L);
  JSONObject event=GoogleCalendarSync.event(store,task,store.due(task),true);
  JSONObject reminders=event.getJSONObject("reminders");
  check(event.length()==4&&!event.toString().contains("Private submission"),"Only event fields are uploaded");
  check(!reminders.getBoolean("useDefault")&&reminders.getJSONArray("overrides").getJSONObject(0).getInt("minutes")==1440,"Explicit reminder");
  task.put("manualDone",true);
  JSONObject completed=GoogleCalendarSync.event(store,task,store.due(task),false);
  check(completed.getString("summary").startsWith("[已完成]"),"Completed label");
  check(completed.getJSONObject("reminders").getJSONArray("overrides").length()==0,"Completed alarm cleared");
  check(GoogleCalendarSync.hash("task").matches("[0-9a-f]{64}"),"Stable Google-compatible event ID");
  check(!GoogleCalendarSync.hash(event.toString()).equals(GoogleCalendarSync.hash(completed.toString())),"Completion changes fingerprint");
  System.out.println("PASS: Google Calendar payload and identity checks");
 }
}
