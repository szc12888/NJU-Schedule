package edu.nju.homework;
import org.json.*;
import java.time.Instant;
import java.util.*;

/** Synthetic records only. Run by tools/test.ps1; no school access or personal data. */
public final class StoreChecks {
 private static int checks;
 private static void check(boolean b,String name){checks++;if(!b)throw new AssertionError(name);}
 private static long epoch(String iso){return Instant.parse(iso).toEpochMilli();}
 private static void checkDue(String raw,String iso,String name){check(Store.parseDue(raw)==epoch(iso),name);}
 private static JSONObject source(Store store,String key)throws Exception{for(int i=0;i<store.tasks().length();i++){JSONObject task=store.tasks().getJSONObject(i);for(int j=0;j<store.sources(task).length();j++){JSONObject source=store.sources(task).getJSONObject(j);if(key.equals(source.optString("key")))return source;}}throw new AssertionError("synthetic source missing: "+key);}
 private static JSONObject course(String id,JSONObject...tasks)throws Exception{return new JSONObject().put("id",id).put("name","Synthetic course").put("assignments",new JSONArray(Arrays.asList(tasks)));}
 private static JSONObject assignment(String id,boolean submitted)throws Exception{return assignment(id,submitted,"2030-12-01T08:00:00Z");}
 private static JSONObject assignment(String id,Object submitted,String due)throws Exception{return new JSONObject().put("id",id).put("title","Synthetic task").put("content","Synthetic content").put("due",due).put("submitted",submitted);}
 private static JSONObject manualTask(String id,String due,boolean done)throws Exception{return new JSONObject().put("id",id).put("course","manual:course").put("title","Synthetic manual task").put("content","Synthetic manual content").put("due",Store.parseDue(due)).put("manualDone",done);}
 private static JSONObject snapshot(JSONObject c)throws Exception{return new JSONObject().put("courses",new JSONArray().put(c));}
 public static void main(String[] args)throws Exception{
  Store s=new Store(new JSONObject());s.ingest("moodle",snapshot(course("1",assignment("10",true))));
  JSONObject t=s.tasks().getJSONObject(0);check(!s.visible(t),"new course hidden");check(s.complete(t),"platform complete");
  s.course("moodle:1").put("visible",true);check(s.visible(t),"course enabled");
  s.ingest("lms",snapshot(course("2",assignment("20",false))));check(s.tasks().length()==1,"conservative exact cross-platform merge");check(s.sources(t).length()==2,"sources retained");check(!s.complete(t),"any pending source means pending");
  t.put("manualDone",true);s.ingest("lms",snapshot(course("2",assignment("20",false))));check(s.complete(t),"manual completion survives refresh");t.put("manualDone",false);check(!s.complete(t),"unmark restores platform state");
  JSONObject partial=course("2").put("error","synthetic unavailable");s.ingest("lms",snapshot(partial));check(s.sources(t).length()==2,"partial retains source");check(!s.sources(t).getJSONObject(1).optBoolean("missing"),"partial never marks missing");
  s.ingest("lms",snapshot(course("2")));check(s.sources(t).getJSONObject(1).optBoolean("missing"),"complete absence marked unconfirmed");check(!s.sources(t).getJSONObject(1).optBoolean("removed"),"absence not deletion");
  s.ingest("lms",snapshot(course("2",assignment("20",false))));check(!s.sources(t).getJSONObject(1).optBoolean("missing"),"reappearance restores source");
  t.put("title","local edit");t.put("localEdited",true);s.ingest("moodle",snapshot(course("1",assignment("10",true))));check(t.getString("title").equals("local edit"),"unchanged source preserves local edit");
  s.ingest("moodle",snapshot(course("1",assignment("10",true).put("content","Updated source"))));check(t.getString("title").equals("Synthetic task"),"source update replaces local edit");
  String before=s.data.toString();try{s.ingest("lms",snapshot(course("2",new JSONObject().put("title","invalid"))));throw new AssertionError("invalid accepted");}catch(IllegalArgumentException expected){}check(before.equals(s.data.toString()),"invalid snapshot transactional rollback");
  t=s.tasks().getJSONObject(0);String id=t.getString("id");t.put("calendarEventId",101);s.splitTask(id);check(s.tasks().length()==2,"split restores sources");check(s.data.getJSONArray("orphanCalendarEvents").getInt(0)==101,"split queues calendar cleanup");
  s.ingest("lms",snapshot(course("2",assignment("20",false))));check(s.tasks().length()==2,"explicit split resists auto merge");
  JSONObject first=s.tasks().getJSONObject(0),second=s.tasks().getJSONObject(1);s.mergeTasks(first.getString("id"),second.getString("id"));check(s.tasks().length()==1,"manual merge");
  first=s.tasks().getJSONObject(0);s.confirmRemoved(first,s.sources(first).getJSONObject(1));check(s.complete(first),"removed pending source no longer blocks completion");

  Store unknownState=new Store(new JSONObject());unknownState.ingest("moodle",snapshot(course("unknown",assignment("unknown",JSONObject.NULL,"2030-12-01T08:00:00Z"))));
  JSONObject unknownTask=unknownState.tasks().getJSONObject(0);check(unknownTask.getJSONArray("sources").getJSONObject(0).isNull("submitted"),"unknown submission state retained as unknown");check(!unknownState.complete(unknownTask),"unknown submission state never completes task");

  Store splitCourseStore=new Store(new JSONObject());splitCourseStore.ingest("moodle",snapshot(course("course-a",assignment("a",true))));splitCourseStore.ingest("lms",snapshot(course("course-b",assignment("b",false))));
  check(splitCourseStore.tasks().length()==1,"course split setup starts merged");check(splitCourseStore.course("moodle:course-a").getString("group").equals(splitCourseStore.course("lms:course-b").getString("group")),"synthetic courses initially share group");
  splitCourseStore.splitCourse("moodle:course-a");check(!splitCourseStore.course("moodle:course-a").getString("group").equals(splitCourseStore.course("lms:course-b").getString("group")),"course split separates groups");check(splitCourseStore.tasks().length()==2,"course split restores source-specific tasks");
  for(int i=0;i<splitCourseStore.tasks().length();i++){JSONObject split=splitCourseStore.tasks().getJSONObject(i);check(splitCourseStore.sources(split).length()==1,"split task keeps one source");check(split.optBoolean("noAutoMerge"),"split task resists automatic remerge");}
  splitCourseStore.ingest("lms",snapshot(course("course-b",assignment("b",false))));check(splitCourseStore.tasks().length()==2,"course split remains separate after refresh");

  Store mergeStore=new Store(new JSONObject());mergeStore.ingest("moodle",snapshot(course("late",assignment("late",true,"2030-12-02T08:00:00Z"))));mergeStore.ingest("lms",snapshot(course("early",assignment("early",false,"2030-12-01T08:00:00Z"))));
  JSONObject late=mergeStore.tasks().getJSONObject(0),early=mergeStore.tasks().getJSONObject(1);mergeStore.mergeTasks(late.getString("id"),early.getString("id"));
  JSONObject merged=mergeStore.tasks().getJSONObject(0);check(mergeStore.tasks().length()==1,"different deadlines can be manually merged");check(mergeStore.sources(merged).length()==2,"different-deadline merge retains both sources");check(mergeStore.due(merged)==Store.parseDue("2030-12-01T08:00:00Z"),"manual merge chooses earlier deadline");
  check(!mergeStore.complete(merged),"different-deadline merge remains pending when one source is pending");
  mergeStore.ingest("lms",snapshot(course("early")));JSONObject earlySource=mergeStore.sources(merged).getJSONObject(1);check(earlySource.optBoolean("missing"),"missing platform source is marked missing");check(!earlySource.optBoolean("removed"),"missing platform source is not automatically removed");check(!mergeStore.complete(merged),"missing pending source still blocks completion");
  mergeStore.confirmRemoved(merged,earlySource);check(earlySource.optBoolean("removed"),"confirmed platform deletion marks source removed");check(mergeStore.due(merged)==Store.parseDue("2030-12-02T08:00:00Z"),"confirmed removal chooses remaining deadline");check(mergeStore.complete(merged),"confirmed removal stops pending source blocking completion");check(!mergeStore.removed(merged),"partly removed task remains active");
  mergeStore.confirmRemoved(merged,mergeStore.sources(merged).getJSONObject(0));check(mergeStore.removed(merged),"all confirmed sources make task removed");check(mergeStore.due(merged)==0,"task with all sources removed has no active deadline");

  JSONObject manualData=new JSONObject().put("tasks",new JSONArray().put(manualTask("manual-late","2030-12-05T08:00:00Z",false).put("calendarEventId",202)).put(manualTask("manual-early","2030-12-03T08:00:00Z",true).put("calendarEventId",303)));
  Store manualStore=new Store(manualData);manualStore.mergeTasks("manual-late","manual-early");JSONObject manual=manualStore.tasks().getJSONObject(0);
  check(manualStore.tasks().length()==1,"manual tasks merge into one");check(manualStore.sources(manual).length()==2,"manual merge creates both manual sources");check(manualStore.due(manual)==Store.parseDue("2030-12-03T08:00:00Z"),"manual task merge chooses earlier deadline");check(manual.optBoolean("manualDone"),"manual merge preserves completion from either task");check(manualStore.complete(manual),"manually merged completed task stays complete");
  check(manualStore.data.getJSONArray("orphanCalendarEvents").getInt(0)==303,"manual merge queues removed task calendar cleanup");

  mergeStore.ingest("lms",snapshot(course("early",assignment("early",false,"2030-12-01T08:00:00Z"))));
  check(!mergeStore.removed(merged),"reappearing confirmed source restores task");check(mergeStore.due(merged)==Store.parseDue("2030-12-01T08:00:00Z"),"reappearing source restores deadline and reminder eligibility");
  Store detailStore=new Store(new JSONObject());detailStore.ingest("moodle",snapshot(course("detail",assignment("detail",false).put("content","Cached synthetic detail"))));
  detailStore.ingest("moodle",snapshot(course("detail",assignment("detail",false).put("content","").put("detailUnavailable",true))));
  check(detailStore.tasks().getJSONObject(0).getString("content").equals("Cached synthetic detail"),"optional detail failure preserves cached content");
  Store lmsDetailStore=new Store(new JSONObject());
  lmsDetailStore.ingest("lms",snapshot(course("detail-cache",assignment("detail-cache",false).put("grade","SYNTHETIC_GRADE_CACHED").put("comment","SYNTHETIC_COMMENT_CACHED").put("attachments",new JSONArray().put("synthetic-cached-attachment.txt")))));
  lmsDetailStore.ingest("lms",snapshot(course("detail-cache",assignment("detail-cache",false).put("detailUnavailable",true))));
  JSONObject cachedDetail=source(lmsDetailStore,"lms:detail-cache");
  check(cachedDetail.optBoolean("detailUnavailable"),"LMS detail failure is recorded");
  check(cachedDetail.optString("grade").equals("SYNTHETIC_GRADE_CACHED"),"LMS detail failure preserves cached grade");
  check(cachedDetail.optString("comment").equals("SYNTHETIC_COMMENT_CACHED"),"LMS detail failure preserves cached comment");
  check(cachedDetail.optJSONArray("attachments")!=null&&cachedDetail.optJSONArray("attachments").optString(0).equals("synthetic-cached-attachment.txt"),"LMS detail failure preserves cached attachments");
  lmsDetailStore.ingest("lms",snapshot(course("detail-cache",assignment("detail-cache",false).put("grade","SYNTHETIC_GRADE_UPDATED").put("comment","SYNTHETIC_COMMENT_UPDATED").put("attachments",new JSONArray().put("synthetic-updated-attachment.txt")))));
  JSONObject updatedDetail=source(lmsDetailStore,"lms:detail-cache");
  check(!updatedDetail.optBoolean("detailUnavailable"),"LMS detail recovery clears unavailable flag");
  check(updatedDetail.optString("grade").equals("SYNTHETIC_GRADE_UPDATED"),"LMS detail recovery updates grade");
  check(updatedDetail.optString("comment").equals("SYNTHETIC_COMMENT_UPDATED"),"LMS detail recovery updates comment");
  check(updatedDetail.optJSONArray("attachments")!=null&&updatedDetail.optJSONArray("attachments").optString(0).equals("synthetic-updated-attachment.txt"),"LMS detail recovery updates attachments");
  checkDue("2030年12月1日 星期日 16:00","2030-12-01T08:00:00Z","Chinese Moodle date");
  checkDue("2030年12月1日，星期日，16:00","2030-12-01T08:00:00Z","Chinese comma Moodle date");
  checkDue("2030年12月1日（星期日）下午4:00","2030-12-01T08:00:00Z","Chinese weekday and PM date");
  checkDue("2030年12月01日 16:00","2030-12-01T08:00:00Z","Moodle yyyy year padded date");
  checkDue("2030年12月01日, 16:00","2030-12-01T08:00:00Z","Moodle yyyy year date with ASCII comma");
  checkDue("2030年12月01日 星期一 16:00","2030-12-01T08:00:00Z","Moodle yyyy year date with Chinese weekday");
  checkDue("Sunday,1 December 2030,16:00","2030-12-01T08:00:00Z","English weekday without comma spaces");
  checkDue("Monday, 2 December 2030, 09:30","2030-12-02T01:30:00Z","English weekday and d Month yyyy");
  checkDue("Monday, 02 December 2030, 16:00","2030-12-02T08:00:00Z","English 24-hour comma date with padded day");
  checkDue("1 December 2030 09:30","2030-12-01T01:30:00Z","English d Month yyyy without weekday");
  checkDue("2030-12-01T08:00:00.123Z","2030-12-01T08:00:00.123Z","ISO milliseconds Z");
  checkDue("2030-12-01T16:00:00.123+0800","2030-12-01T08:00:00.123Z","LMS ISO basic offset");
  checkDue("2030-12-01 16:00:00","2030-12-01T08:00:00Z","SQL local date time");
  checkDue("2030-12-01T16:00:00.123456","2030-12-01T08:00:00.123456Z","local fractional seconds");
  long expected=epoch("2030-12-01T08:00:00Z");
  check(Store.parseDue(Long.toString(expected/1000))==expected,"numeric string epoch seconds");
  check(Store.parseDue(Long.toString(expected+321))==expected+321,"numeric string epoch milliseconds");
  check(Store.parseDue("2030-02-30 16:00:00")==0,"invalid calendar date rejected");
  check(Store.parseDue("unknown")==0,"unknown due remains unknown");

  Store dueFields=new Store(new JSONObject());dueFields.ingest("moodle",snapshot(course("due-fields",assignment("valid",false,"2030年12月1日，星期日，16:00"),assignment("unknown",false,"not-a-date"),assignment("none",false,"").put("due",JSONObject.NULL))));
  JSONObject validSource=source(dueFields,"moodle:valid"),unknownSource=source(dueFields,"moodle:unknown"),noneSource=source(dueFields,"moodle:none");
  check(validSource.getString("dueRaw").equals("2030年12月1日，星期日，16:00")&&!validSource.optBoolean("dueParseFailed"),"valid source keeps raw due and parse flag");
  check(unknownSource.getString("dueRaw").equals("not-a-date")&&unknownSource.optBoolean("dueParseFailed")&&unknownSource.optLong("due")==0,"unknown source records parse failure");
  check(noneSource.optString("dueRaw").isEmpty()&&!noneSource.optBoolean("dueParseFailed")&&noneSource.optLong("due")==0,"missing due is distinct from parse failure");

  Store dueRefresh=new Store(new JSONObject());dueRefresh.ingest("lms",snapshot(course("refresh",assignment("deadline",false,"2030-12-01T16:00:00.250+0800"))));
  JSONObject refreshSource=source(dueRefresh,"lms:deadline");long validDeadline=refreshSource.getLong("due");
  dueRefresh.ingest("lms",snapshot(course("refresh",assignment("deadline",false,"unreadable synthetic due"))));
  refreshSource=source(dueRefresh,"lms:deadline");check(refreshSource.optBoolean("dueParseFailed"),"invalid refresh marks parse failure");check(refreshSource.getLong("due")==validDeadline,"invalid refresh preserves cached valid deadline");
  check(dueRefresh.due(dueRefresh.tasks().getJSONObject(0))==validDeadline,"task deadline survives invalid refresh");

  JSONObject reparsedSource=new JSONObject().put("key","moodle:reparse").put("platform","moodle").put("dueRaw","2030-12-01，星期日，16:00").put("due",0).put("dueParseFailed",true);
  JSONObject preservedSource=new JSONObject().put("key","lms:preserve").put("platform","lms").put("dueRaw","unreadable cached due").put("due",epoch("2030-12-02T08:00:00Z")).put("dueParseFailed",false);
  JSONObject reparsedTask=new JSONObject().put("id","cached-reparse").put("sources",new JSONArray().put(reparsedSource));
  JSONObject preservedTask=new JSONObject().put("id","cached-preserve").put("sources",new JSONArray().put(preservedSource));
  Store repairedCache=new Store(new JSONObject().put("tasks",new JSONArray().put(reparsedTask).put(preservedTask)));
  check(repairedCache.due(reparsedTask)==epoch("2030-12-01T08:00:00Z"),"cached raw due is reparsed");
  check(!reparsedSource.optBoolean("dueParseFailed"),"reparsed cache failure flag clears");
  check(preservedSource.getLong("due")==epoch("2030-12-02T08:00:00Z")&&preservedSource.optBoolean("dueParseFailed"),"cached failed raw preserves valid deadline");
  check(Csv.parse("\ufeff课程名称,作业标题,作业内容,截止时间\r\nA,\"B,C\",\"one\n\"\"two\"\"\",\r\n").get(1).get(2).equals("one\n\"two\""),"CSV quoted commas multiline BOM CRLF");
  check(Csv.parse("a,b,\n").get(0).size()==3,"CSV trailing empty column");
  for(String bad:new String[]{"\"unfinished","a\"b,c","\"a\"junk,b"}){boolean rejected=false;try{Csv.parse(bad);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"malformed CSV rejected");}
  Store restartEdit=new Store(new JSONObject());restartEdit.ingest("moodle",snapshot(course("restart",assignment("restart",false))));
  JSONObject edited=restartEdit.tasks().getJSONObject(0);edited.put("title","Local synthetic title").put("localEdited",true).put("due",Store.parseDue("2030-12-09 16:00"));
  Store reopened=new Store(new JSONObject(restartEdit.data.toString()));check(reopened.tasks().getJSONObject(0).getString("title").equals("Local synthetic title"),"cache repair preserves local title edits");check(reopened.tasks().getJSONObject(0).getLong("due")==Store.parseDue("2030-12-09 16:00"),"cache repair preserves local deadline edits");
  check(Store.parseDue("2026年11月9日 Monday 18:00")==Store.parseDue("2026-11-09T10:00:00Z"),"actual Moodle mixed Chinese date and English weekday");
  check(Store.parseDue("2030年12月2日 Monday 16:00")==Store.parseDue("2030-12-02T08:00:00Z"),"mixed-language weekday stripped before strict date parse");
  check(Store.parseDue("2030年12月2日Monday16:00")==Store.parseDue("2030-12-02T08:00:00Z"),"mixed-language weekday without spaces");
  Store noDue=new Store(new JSONObject());noDue.ingest("moodle",snapshot(course("no-due",assignment("no-due",false).put("due","-"))));check(!noDue.sources(noDue.tasks().getJSONObject(0)).getJSONObject(0).optBoolean("dueParseFailed"),"actual Moodle dash means no deadline rather than parse failure");
  System.out.println("PASS: "+checks+" synthetic Store/CSV checks");
 }
}
