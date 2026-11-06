package edu.nju.homework;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.graphics.Color;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.*;
import java.util.*;

/** Requests stay in the authenticated school page; no credentials cross a JavaScript bridge. */
public class MainActivity extends Activity {
 private Store store; private CalendarSync calendar;
 private static final int GOOGLE_CALENDAR_AUTH=71;
 private boolean googleAuthorizing;
 private LinearLayout body, browserBin; private TextView notice; private WebView web;
 private Ui ui; private boolean interactive;
 private final Map<String,WebView> sessions=new HashMap<>();
 private final Map<String,String> connectionResults=new LinkedHashMap<>();
 private final Set<String> authReopened=new HashSet<>();
 private final Set<String> loginRequired=new LinkedHashSet<>();
 private final Handler handler=new Handler(Looper.getMainLooper());
 private int generation,tab; private boolean reading,monthMode,dark,filtersOpen,unifiedConnecting,moreLogins,signingOut;
 private String platform="",filterPlatform="全部",filterCourse="全部",filterState="全部",filterDue="全部";
 private LocalDate selected=LocalDate.now(); private YearMonth month=YearMonth.now();
 private final ArrayDeque<String> queue=new ArrayDeque<>(); private final ArrayList<WebView> popups=new ArrayList<>();
 private static final String[] PLATFORMS={"moodle","lms","ehall","elite","pe"};
 private static final String[] LABELS={"SElearning Moodle","智汇南雍 LMS","教务课程成绩","GPA 与排名百分比","体育教学成绩"};
 @Override public void onCreate(Bundle state){super.onCreate(state);store=new Store(this);calendar=new CalendarSync(this,store);calendar.sync();render();}
 @Override protected void onStart(){super.onStart();GoogleCalendarJob.schedule(this,true);if(web==null&&queue.isEmpty())autoRefresh();}
 static void put(JSONObject o,String k,Object v){try{o.put(k,v);}catch(JSONException e){throw new IllegalStateException(e);}}
 private JSONObject settings(){JSONObject s=store.data.optJSONObject("settings");if(s==null){s=new JSONObject();put(store.data,"settings",s);}return s;}
 private int fg(){return dark?0xffeee8f1:0xff28212d;}
 private TextView text(String value,int size){return ui.text(value,size);}
 private Button button(String label,Runnable action){return ui.button(label,action);}
 private void add(String value){body.addView(text(value,16));}
 private void action(String label,Runnable r){body.addView(button(label,r));}
 private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 private void changed(){store.save();calendar.sync();GoogleCalendarJob.schedule(this,false);}
 private void render(){
  String theme=settings().optString("theme","system");dark=theme.equals("dark")||theme.equals("system")&&(getResources().getConfiguration().uiMode&48)==32;
  setTheme(dark?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);ui=new Ui(this,dark);for(WebView cached:sessions.values())detach(cached);for(WebView popup:popups)detach(popup);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(ui.background());root.setPadding(16,24,16,12);setContentView(root);
  root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(20),insets.getSystemWindowInsetTop()+dp(8),dp(20),insets.getSystemWindowInsetBottom()+dp(8));return insets;});
  TextView brand=ui.label("NJU作业");root.addView(brand);
  notice=ui.label("");notice.setVisibility(View.GONE);notice.setPadding(0,dp(4),0,dp(8));root.addView(notice);notice.setOnClickListener(v->{if(!loginRequired.isEmpty())open(loginRequired.iterator().next(),false);else{tab=3;render();}});
  browserBin=new LinearLayout(this);browserBin.setVisibility(View.INVISIBLE);root.addView(browserBin,new LinearLayout.LayoutParams(1,1));for(WebView cached:sessions.values())park(cached);for(WebView popup:popups)park(popup);ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  root.addView(ui.navigation(tab,n->{if(interactive||unifiedConnecting){unifiedConnecting=false;queue.clear();closeWeb();}if(n!=1)filtersOpen=false;tab=n;render();}));
  if(tab==0)home();else if(tab==1)assignments();else if(tab==2)grades();else preferences();updateNotice();
 }
 private String date(long ms){return ms<=0?"无截止时间":DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(ms));}
 private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
 private String courseName(JSONObject t){JSONObject c=store.course(t.optString("course"));if(c==null)return "未知课程";JSONObject canonical=store.course(c.optString("group"));return (canonical==null?c:canonical).optString("name");}
 private ArrayList<JSONObject> visibleTasks(){ArrayList<JSONObject>a=new ArrayList<>();for(int i=0;i<store.tasks().length();i++){JSONObject t=store.tasks().optJSONObject(i);if(t!=null&&store.visible(t))a.add(t);}a.sort((a1,b)->{long x=store.due(a1),y=store.due(b),now=System.currentTimeMillis();int p=x==0?1:x<now?2:0,q=y==0?1:y<now?2:0;return p!=q?Integer.compare(p,q):p==2?Long.compare(y,x):Long.compare(x,y);});return a;}
 private boolean badDue(JSONObject t){JSONArray a=store.sources(t);for(int i=0;i<a.length();i++)if(a.optJSONObject(i).optBoolean("dueParseFailed"))return true;return false;}
 private void card(JSONObject t){
  long due=store.due(t);boolean done=store.complete(t);String badge=done?"已完成":store.removed(t)?"平台已移除":due>0&&due<System.currentTimeMillis()?"已截止":due>0&&due-System.currentTimeMillis()<86400000?"即将截止":"待完成";
  String deadline=due>0?"截止  "+date(due):badDue(t)?"截止时间待解析":"未设置截止时间";
  body.addView(ui.card(t.optString("title"),courseName(t),deadline,badge,done,()->detail(t)));
 }
 private void tools(String[] labels,Runnable[] actions){LinearLayout row=new LinearLayout(this);for(int i=0;i<labels.length;i++){Button b=button(labels[i],actions[i]);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(46),1);lp.setMargins(i==0?0:dp(6),dp(4),0,dp(10));row.addView(b,lp);}body.addView(row);}
 private void home(){
  body.addView(ui.section(monthMode?"作业日历":"今天的待办",LocalDate.now().toString()));int pendingCount=0,urgent=0;for(JSONObject t:visibleTasks())if(!store.complete(t)&&!store.removed(t)){pendingCount++;long d=store.due(t);if(d>System.currentTimeMillis()&&d-System.currentTimeMillis()<86400000)urgent++;}if(!monthMode)body.addView(ui.summary(pendingCount,urgent));tools(new String[]{monthMode?"卡片视图":"月历视图","刷新作业"},new Runnable[]{()->{monthMode=!monthMode;render();},()->refresh(false)});
  if(!monthMode){int count=0;for(JSONObject t:visibleTasks())if(!store.complete(t)&&!store.removed(t)){card(t);count++;}if(count==0)body.addView(ui.card("暂无待办","选择要显示的课程","管理课程","",false,()->{tab=1;render();courses();}));return;}
  LinearLayout bar=new LinearLayout(this);bar.setGravity(android.view.Gravity.CENTER_VERTICAL);bar.addView(button("‹",()->{month=month.minusMonths(1);render();}),new LinearLayout.LayoutParams(dp(46),dp(46)));TextView monthTitle=text(month.toString(),20);monthTitle.setGravity(android.view.Gravity.CENTER);bar.addView(monthTitle,new LinearLayout.LayoutParams(0,-2,1));bar.addView(button("›",()->{month=month.plusMonths(1);render();}),new LinearLayout.LayoutParams(dp(46),dp(46)));body.addView(bar);
  LinearLayout week=new LinearLayout(this);for(String name:new String[]{"一","二","三","四","五","六","日"}){TextView day=text(name,12);day.setGravity(android.view.Gravity.CENTER);day.setTextColor(ui.muted());week.addView(day,new LinearLayout.LayoutParams(0,dp(34),1));}body.addView(week);
  LinearLayout row=null;int offset=month.atDay(1).getDayOfWeek().getValue()-1;
  for(int cell=0;cell<((offset+month.lengthOfMonth()+6)/7)*7;cell++){
   if(cell%7==0){row=new LinearLayout(this);body.addView(row);}int day=cell-offset+1;
   if(day<1||day>month.lengthOfMonth()){row.addView(text("",12),new LinearLayout.LayoutParams(0,48,1));continue;}
   LocalDate d=month.atDay(day);boolean any=false,pending=false;
   for(JSONObject t:visibleTasks())if(onDay(t,d)){any=true;pending|=!store.complete(t);}
   Button b=button(String.valueOf(day),()->{selected=d;render();});b.setContentDescription(d.toString()+(any?pending?"，有未完成作业":"，作业已完成":"，无到期作业"));b.setTextSize(13);b.setPadding(0,0,0,0);b.setMinWidth(0);b.setMinimumWidth(0);b.setTextColor(any?(pending?0xff9c55b5:Color.GRAY):fg());android.graphics.drawable.GradientDrawable circle=new android.graphics.drawable.GradientDrawable();circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);circle.setColor(d.equals(selected)?(dark?0xff51405e:0xffe5c8ed):Color.TRANSPARENT);if(any)circle.setStroke(dp(2),pending?0xff9c55b5:Color.GRAY);b.setBackground(circle);row.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));
  }
  add(selected.toString());int shown=0,unparsed=0;for(JSONObject t:visibleTasks()){if(onDay(t,selected)){card(t);shown++;}if(badDue(t)&&store.due(t)==0)unparsed++;}if(shown==0)body.addView(ui.label("这一天没有到期作业"));if(unparsed>0)body.addView(ui.label("另有 "+unparsed+" 项截止时间待识别，可在卡片视图查看原文"));
 }
 private boolean onDay(JSONObject t,LocalDate d){return store.due(t)>0&&Instant.ofEpochMilli(store.due(t)).atZone(ZoneId.systemDefault()).toLocalDate().equals(d);}
 private void picker(String title,String[] options,java.util.function.Consumer<Integer> accept){new AlertDialog.Builder(this).setTitle(title).setItems(options,(d,w)->accept.accept(w)).show();}
 private void assignments(){
  body.addView(ui.section("作业",""));
  tools(new String[]{"课程管理","添加作业","更多"},new Runnable[]{this::courses,()->edit(null),()->picker("作业工具",new String[]{"刷新作业","导入 CSV"},i->{if(i==0)refresh(false);else{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("*/*");intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,40);}})});
  filterControls();
  int displayed=0;
  for(JSONObject t:visibleTasks()){
   boolean match=filterPlatform.equals("全部")||filterPlatform.equals("manual")&&store.sources(t).length()==0;for(int i=0;i<store.sources(t).length();i++)match|=filterPlatform.equals(store.sources(t).optJSONObject(i).optString("platform"));long due=store.due(t);
   if(!match||!filterCourse.equals("全部")&&!filterCourse.equals(courseName(t))||filterState.equals("已完成")&&!store.complete(t)||filterState.equals("未完成")&&store.complete(t)||filterDue.equals("无截止")&&due!=0||filterDue.equals("已截止")&&(due==0||due>=System.currentTimeMillis())||filterDue.equals("未截止")&&(due==0||due<System.currentTimeMillis()))continue;card(t);displayed++;
  }
  if(displayed==0)body.addView(ui.label("暂无符合条件的作业"));
 }
 private String platformFilterName(){return filterPlatform.equals("manual")?"手动":filterPlatform.equals("moodle")?"Moodle":filterPlatform.equals("lms")?"LMS":"全部";}
 private void filterControls(){
  ArrayList<String> chosen=new ArrayList<>();if(!filterPlatform.equals("全部"))chosen.add(platformFilterName());if(!filterCourse.equals("全部"))chosen.add(filterCourse);if(!filterState.equals("全部"))chosen.add(filterState);if(!filterDue.equals("全部"))chosen.add(filterDue);
  body.addView(ui.filterBar(chosen.isEmpty()?"全部作业":String.join(" · ",chosen),chosen.size(),filtersOpen,()->{filtersOpen=!filtersOpen;render();}));
  if(!filtersOpen)return;LinearLayout panel=new LinearLayout(this);panel.setTag("assignment-filters-panel");panel.setOrientation(LinearLayout.VERTICAL);
  String[] platforms={"全部","Moodle","LMS","手动"},keys={"全部","moodle","lms","manual"},states={"全部","已完成","未完成"},dues={"全部","未截止","已截止","无截止"};
  View[] choices={ui.filterChoice("平台",platformFilterName(),()->picker("平台",platforms,i->{filterPlatform=keys[i];render();})),ui.filterChoice("课程",filterCourse,()->{ArrayList<String> names=new ArrayList<>();names.add("全部");for(int i=0;i<store.courses().length();i++){JSONObject c=store.courses().optJSONObject(i),canonical=store.course(c.optString("group"));String name=(canonical==null?c:canonical).optString("name");if(!names.contains(name))names.add(name);}picker("课程",names.toArray(new String[0]),i->{filterCourse=names.get(i);render();});}),ui.filterChoice("完成状态",filterState,()->picker("完成状态",states,i->{filterState=states[i];render();})),ui.filterChoice("截止状态",filterDue,()->picker("截止状态",dues,i->{filterDue=dues[i];render();}))};
  for(int r=0;r<2;r++){LinearLayout line=new LinearLayout(this);for(int c=0;c<2;c++){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(c==0?0:dp(8),0,0,dp(8));line.addView(choices[r*2+c],lp);}panel.addView(line);}
  LinearLayout footer=new LinearLayout(this);String[] titles={"重置","完成"};Runnable[] actions={()->{filterPlatform=filterCourse=filterState=filterDue="全部";render();},()->{filtersOpen=false;render();}};for(int i=0;i<2;i++){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);lp.setMargins(i==0?0:dp(8),0,0,dp(10));footer.addView(button(titles[i],actions[i]),lp);}panel.addView(footer);body.addView(panel);
 }
 private String fallback(String s,String f){return s.isEmpty()||s.equals("null")||s.equals("[]")?f:s;}
 private void detail(JSONObject t){
  body.removeAllViews();body.post(()->{if(body.getParent() instanceof ScrollView)((ScrollView)body.getParent()).scrollTo(0,0);});body.addView(ui.section(t.optString("title"),courseName(t)));JSONArray sources=store.sources(t);
  LinearLayout overview=ui.detailPanel("作业概览");ui.detailField(overview,"截止时间",date(store.due(t)));ui.detailField(overview,"完成状态",store.complete(t)?"已完成":"未完成");body.addView(overview);
  java.util.HashSet<String> shown=new java.util.HashSet<>();String content=t.optString("content").trim();
  if(!content.isEmpty()){LinearLayout description=ui.detailPanel("作业内容");ui.detailField(description,"",content);body.addView(description);shown.add(content.replaceAll("\\s+"," "));}
  for(int i=0;i<sources.length();i++){
   JSONObject source=sources.optJSONObject(i);final JSONObject s=source;LinearLayout panel=ui.detailPanel(label(s.optString("platform")));
   ui.detailField(panel,"截止时间",s.optBoolean("dueParseFailed")?"待解析 · "+s.optString("dueRaw"):date(s.optLong("due")));
   ui.detailField(panel,"提交状态",s.optBoolean("removed")?"平台已移除":s.isNull("submitted")?"状态未知":s.optBoolean("submitted")?"已提交":"未提交");
   String sourceContent=s.optString("content").trim();if(!sourceContent.isEmpty()&&shown.add(sourceContent.replaceAll("\\s+"," ")))ui.detailField(panel,"平台作业内容",sourceContent);
   ui.detailField(panel,"教师评分",fallback(s.optString("grade"),s.optBoolean("detailUnavailable")?"暂未读取到评分":"暂无评分"));ui.detailField(panel,"教师评语",fallback(s.optString("comment"),s.optBoolean("detailUnavailable")?"暂未读取到评语":"暂无评语"));
   JSONArray attachments=s.optJSONArray("attachments");String names=s.optString("attachments");if(attachments!=null){java.util.ArrayList<String> items=new java.util.ArrayList<>();for(int j=0;j<attachments.length();j++)items.add(attachments.optString(j));names=String.join("\n",items);}ui.detailField(panel,"附件",fallback(names,"暂无附件信息"));
   if(s.optBoolean("detailUnavailable"))panel.addView(ui.label("部分详情暂未更新"));
   if(t.optBoolean("manualDone")&&!s.optBoolean("submitted"))panel.addView(ui.label("已手动完成，平台仍未提交"));
   if(s.optBoolean("missing")&&!s.optBoolean("removed")){panel.addView(ui.label("本次未找到该作业，尚未确认移除"));panel.addView(ui.button("确认平台已移除",()->new AlertDialog.Builder(this).setMessage("确认学校平台已删除或撤回此作业？").setPositiveButton("确认",(d,w)->{store.confirmRemoved(t,s);changed();detail(t);}).setNegativeButton("取消",null).show()));}body.addView(panel);
  }
  action(t.optBoolean("manualDone")?"取消手动完成":"手动标记完成",()->{put(t,"manualDone",!t.optBoolean("manualDone"));changed();detail(t);});action("本地编辑",()->edit(t));
  action("与另一作业合并",()->{ArrayList<JSONObject> a=new ArrayList<>();for(JSONObject x:visibleTasks())if(x!=t)a.add(x);picker("选择同一份作业",a.stream().map(x->courseName(x)+" · "+x.optString("title")).toArray(String[]::new),i->{store.mergeTasks(t.optString("id"),a.get(i).optString("id"));changed();render();});});
  if(sources.length()>1)action("拆分来源作业",()->{store.splitTask(t.optString("id"));changed();render();});
  boolean manual=sources.length()==0||sources.length()==1&&"manual".equals(sources.optJSONObject(0).optString("platform"));
  if(manual)action("删除手动作业",()->new AlertDialog.Builder(this).setMessage("删除作业及已同步日程？").setPositiveButton("删除",(d,w)->{calendar.deleteTask(t);for(int i=store.tasks().length()-1;i>=0;i--)if(store.tasks().optJSONObject(i)==t)store.tasks().remove(i);changed();render();}).setNegativeButton("取消",null).show());action("返回",this::render);
 }
 private EditText input(LinearLayout p,String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setTextColor(fg());p.addView(e);return e;}
 private long parseDate(String raw){if(raw.trim().isEmpty())return 0;return LocalDateTime.parse(raw.trim(),DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();}
 private void edit(JSONObject old){
  if(store.courses().length()==0){newCourse();return;}LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);Spinner course=new Spinner(this);ArrayList<String> ids=new ArrayList<>(),names=new ArrayList<>();for(int i=0;i<store.courses().length();i++){JSONObject c=store.courses().optJSONObject(i);ids.add(c.optString("id"));names.add(c.optString("name"));}course.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));form.addView(course);if(old!=null)course.setSelection(Math.max(0,ids.indexOf(old.optString("course"))));
  EditText title=input(form,"作业标题（必填）",old==null?"":old.optString("title")),content=input(form,"作业内容",old==null?"":old.optString("content")),due=input(form,"截止 YYYY-MM-DD HH:MM，可留空",old==null||store.due(old)==0?"":date(store.due(old)));
  AlertDialog d=new AlertDialog.Builder(this).setTitle(old==null?"添加作业":"本地编辑：平台更新后覆盖").setView(form).setPositiveButton("保存",null).setNegativeButton("取消",null).create();
  d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{try{if(title.getText().toString().trim().isEmpty())throw new Exception();long time=parseDate(due.getText().toString());JSONObject t=old==null?new JSONObject():old;put(t,"id",t.optString("id",UUID.randomUUID().toString()));put(t,"course",ids.get(course.getSelectedItemPosition()));put(t,"title",title.getText().toString().trim());put(t,"content",content.getText().toString());put(t,"due",time);put(t,"localEdited",old!=null);if(old==null){put(t,"sources",new JSONArray());store.tasks().put(t);}changed();d.dismiss();render();}catch(Exception e){toast("请填写标题并使用 YYYY-MM-DD HH:MM 时间格式");}}));d.show();
 }
 private void courses(){body.removeAllViews();add("课程管理");action("创建手动课程",this::newCourse);for(int i=0;i<store.courses().length();i++){JSONObject c=store.courses().optJSONObject(i);CheckBox check=new CheckBox(this);check.setText(c.optString("name"));check.setTextColor(fg());check.setChecked(c.optBoolean("visible"));check.setOnCheckedChangeListener((b,on)->{String group=c.optString("group",c.optString("id"));for(int j=0;j<store.courses().length();j++){JSONObject related=store.courses().optJSONObject(j);if(related.optString("group",related.optString("id")).equals(group))put(related,"visible",on);}changed();});body.addView(check);action("调整合并关系："+c.optString("name"),()->{ArrayList<JSONObject> a=new ArrayList<>();for(int j=0;j<store.courses().length();j++)if(store.courses().optJSONObject(j)!=c)a.add(store.courses().optJSONObject(j));new AlertDialog.Builder(this).setTitle("选择同一门课程").setItems(a.stream().map(x->x.optString("name")).toArray(String[]::new),(d,w)->{store.mergeCourses(c.optString("id"),a.get(w).optString("id"));changed();courses();}).setNeutralButton("解除合并",(d,w)->{store.splitCourse(c.optString("id"));changed();courses();}).show();});}action("返回",this::render);}
 private JSONObject createCourse(String name){JSONObject c=new JSONObject();String id="manual:"+UUID.randomUUID();put(c,"id",id);put(c,"group",id);put(c,"name",name);put(c,"visible",true);store.courses().put(c);return c;}
 private void newCourse(){EditText e=new EditText(this);new AlertDialog.Builder(this).setTitle("手动创建课程").setView(e).setPositiveButton("创建",(d,w)->{if(e.getText().toString().trim().isEmpty())return;createCourse(e.getText().toString().trim());changed();courses();}).setNegativeButton("取消",null).show();}
 private void grades(){
  body.addView(ui.section("成绩",""));JSONObject grades=store.data.optJSONObject("grades");JSONObject elite=grades==null?null:grades.optJSONObject("elite"),summary=elite==null?null:elite.optJSONObject("summary");String gpa=summary==null?"":summary.optString("gpa"),percent=summary==null?"":summary.optString("percent"),total=summary==null?"":summary.optString("total");
  String percentage=percent.trim();if(!percentage.isEmpty()&&!percentage.endsWith("%")&&!percentage.endsWith("％"))percentage+="%";
  body.addView(ui.gradeStats(fallback(gpa,"—"),fallback(percentage,"—"),GradeSummary.estimate(percent,total)));action("刷新成绩",()->refresh(true));if(grades==null){body.addView(ui.label("连接成绩平台后显示"));return;}
  ArrayList<JSONObject> records=new ArrayList<>();for(String p:new String[]{"ehall","pe"}){JSONObject g=grades.optJSONObject(p);if(g==null)continue;JSONArray a=g.optJSONArray("records");if(a!=null)for(int i=0;i<a.length();i++){JSONObject r=a.optJSONObject(i);if(r!=null){put(r,"_source",p);records.add(r);}}}records.sort((a,b)->b.optString("termKey",b.optString("term")).compareTo(a.optString("termKey",a.optString("term"))));String term="";
  boolean hasAcademic=records.stream().anyMatch(r->r.optString("_source").equals("ehall"));
  for(JSONObject r:records){if(hasAcademic&&r.optString("_source").equals("pe"))continue;if(!term.equals(r.optString("term"))){term=r.optString("term");TextView heading=text(term,14);heading.setTextColor(ui.muted());heading.setPadding(0,dp(14),0,dp(10));body.addView(heading);}body.addView(ui.courseGrade(r.optString("course"),r.optString("score"),()->{String detail=r.optString("detail","");JSONObject links=settings().optJSONObject("peLinks");for(JSONObject pe:records)if(pe.optString("_source").equals("pe")&&(pe.optString("course").equals(r.optString("course"))&&pe.optString("term").equals(r.optString("term"))||links!=null&&r.optString("id").equals(links.optString(pe.optString("id")))))detail=pe.optString("detail","");new AlertDialog.Builder(this).setTitle(r.optString("course")).setMessage(fallback(detail,"暂无明细")).setPositiveButton("关闭",null).show();}));}
  if(hasAcademic&&records.stream().anyMatch(r->r.optString("_source").equals("pe")))action("关联体育明细",()->{ArrayList<JSONObject> pe=new ArrayList<>(),academic=new ArrayList<>();for(JSONObject r:records)if(r.optString("_source").equals("pe"))pe.add(r);else academic.add(r);picker("选择体育平台课程",pe.stream().map(r->r.optString("term")+" · "+r.optString("course")).toArray(String[]::new),i->picker("选择对应教务课程",academic.stream().map(r->r.optString("term")+" · "+r.optString("course")).toArray(String[]::new),j->{JSONObject links=settings().optJSONObject("peLinks");if(links==null){links=new JSONObject();put(settings(),"peLinks",links);}put(links,pe.get(i).optString("id"),academic.get(j).optString("id"));store.save();toast("已关联");}));});
 }
 private void preferences(){
  body.addView(ui.section("设置",""));
  action("统一身份登录",this::unifiedLogin);int connected=0;for(String p:PLATFORMS)if(settings().optBoolean("connected_"+p)&&settings().optString("error_"+p).isEmpty())connected++;body.addView(ui.label(connected==0?"建立登录会话，数据按刷新间隔更新":"已连接 "+connected+" / "+PLATFORMS.length+" 个平台"));
  action(moreLogins?"收起更多登录选项":"更多登录选项",()->{moreLogins=!moreLogins;render();});
  if(moreLogins)for(int i=0;i<PLATFORMS.length;i++){final int n=i;action("连接 "+LABELS[i],()->{unifiedConnecting=false;queue.clear();open(PLATFORMS[n],false);});long time=settings().optLong("success_"+PLATFORMS[i]);String error=settings().optString("error_"+PLATFORMS[i]);body.addView(ui.label((!settings().optBoolean("connected_"+PLATFORMS[i],time>0)?"未登录 · 保留本地缓存":time==0?"未连接":"更新 "+date(time))+(error.isEmpty()?"":error.contains("部分详情")?" · 部分详情未更新":" · 更新失败")));}
  action("退出全部登录（保留缓存）",this::signOut);
  String[] themes={"system","light","dark"},themeNames={"跟随系统","浅色","深色"};String current=settings().optString("theme","system");int index=Arrays.asList(themes).indexOf(current);action("外观："+themeNames[Math.max(0,index)],()->picker("外观",themeNames,i->{put(settings(),"theme",themes[i]);store.save();render();}));
  numberSetting("作业刷新间隔（分钟）","taskInterval",30,3);numberSetting("成绩刷新间隔（分钟）","gradeInterval",10080,3);numberSetting("提前提醒（分钟）","reminderMinutes",1440,0);action("选择系统日历",calendar::chooseCalendar);action("同步日历",calendar::syncNow);
  SharedPreferences google=GoogleCalendarSync.prefs(this);
  boolean enabled=google.getBoolean("enabled",false);
  action(enabled?"同步 Google 日历":"连接 Google 日历",()->{
   if(enabled){authorizeGoogleCalendar();return;}
   new AlertDialog.Builder(this).setTitle("连接 Google 日历")
    .setMessage("将创建 NJU Homework 日历，并同步可见作业的标题、截止时间和提醒。")
    .setPositiveButton("继续授权",(d,w)->authorizeGoogleCalendar()).setNegativeButton("取消",null).show();
  });
  if(enabled){
   add("Google 日历 · "+google.getString("status","等待同步"));
   action("停止 Google 日历同步",()->{
    google.edit().putBoolean("enabled",false).putInt("connection",google.getInt("connection",0)+1).apply();
    android.app.job.JobScheduler scheduler=getSystemService(android.app.job.JobScheduler.class);
    scheduler.cancel(GoogleCalendarSync.PERIODIC);scheduler.cancel(GoogleCalendarSync.IMMEDIATE);
    toast("已停止同步，Google 日历中的日程保留");render();
   });
  }

 }
 private void numberSetting(String title,String key,int fallback,int min){action(title+"："+settings().optInt(key,fallback),()->{EditText e=new EditText(this);e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);e.setText(""+settings().optInt(key,fallback));AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setView(e).setPositiveButton("保存",null).setNegativeButton("取消",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{try{int n=Integer.parseInt(e.getText().toString());if(n<min||n>5256000)throw new Exception();put(settings(),key,n);changed();d.dismiss();render();}catch(Exception ex){toast("请输入 "+min+" 至 5256000 的整数");}}));d.show();});}
 private boolean hasCachedData(String p){
  if(p.equals("moodle")||p.equals("lms"))return settings().optLong("success_"+p)>0;
  JSONObject grades=store.data.optJSONObject("grades"),cached=grades==null?null:grades.optJSONObject(p);
  if(cached==null||!(cached.opt("records") instanceof JSONArray))return false;
  if(!p.equals("elite"))return true;
  JSONObject summary=cached.optJSONObject("summary");return summary!=null&&!summary.optString("gpa").isEmpty()&&!summary.optString("total").isEmpty()&&!summary.optString("percent").isEmpty();
 }
 private void autoRefresh(){
  if(reading||web!=null||unifiedConnecting||signingOut||!queue.isEmpty())return;
  // Small summaries first: a first login must not wait behind every assignment detail.
  for(String p:new String[]{"elite","ehall","lms","moodle","pe"}){boolean task=p.equals("moodle")||p.equals("lms");long interval=settings().optLong(task?"taskInterval":"gradeInterval",task?30:10080)*60000;
   long success=settings().optLong("success_"+p),attempt=settings().optLong("checked_"+p,success);
   if(RefreshPolicy.shouldRefresh(settings().optBoolean("connected_"+p,success>0),hasCachedData(p),success,attempt,System.currentTimeMillis(),interval,settings().optBoolean("retryAfterAuth_"+p)))queue.add(p);
  }next();
 }
 private String label(String p){if(p.equals("manual"))return "手动";for(int i=0;i<PLATFORMS.length;i++)if(PLATFORMS[i].equals(p))return LABELS[i];return p;}
 private void updateNotice(){if(notice==null)return;notice.setVisibility(View.VISIBLE);if(reading){notice.setText("正在后台更新 · "+label(platform));return;}if(!loginRequired.isEmpty()){notice.setText(label(loginRequired.iterator().next())+" 需要登录 · 点击继续");return;}for(String p:PLATFORMS)if(!settings().optString("error_"+p).isEmpty()){notice.setText(label(p)+(settings().optString("error_"+p).contains("部分详情")?" 部分详情未更新":" 更新失败 · 显示缓存"));return;}notice.setText("");notice.setVisibility(View.GONE);}
 private void refresh(boolean grades){if(reading){toast("正在刷新，请稍候");return;}unifiedConnecting=false;closeWeb();queue.clear();for(String p:grades?new String[]{"ehall","elite","pe"}:new String[]{"moodle","lms"})queue.add(p);next();}
 private void unifiedLogin(){
  if(unifiedConnecting||signingOut){toast("正在处理，请稍候");return;}queue.clear();closeWeb();connectionResults.clear();authReopened.clear();for(String p:PLATFORMS)connectionResults.put(p,"pending");unifiedConnecting=true;for(String p:PLATFORMS)queue.add(p);open(queue.remove(),false);
 }
 private void signOut(){
  if(signingOut)return;signingOut=true;unifiedConnecting=false;queue.clear();closeWeb();java.util.HashSet<WebView> views=new java.util.HashSet<>(sessions.values());views.addAll(popups);sessions.clear();popups.clear();for(WebView view:views){detach(view);view.destroy();}loginRequired.clear();
  for(String p:PLATFORMS){put(settings(),"connected_"+p,false);put(settings(),"retryAfterAuth_"+p,false);put(settings(),"reauthPending_"+p,false);put(settings(),"error_"+p,"");}store.save();
  CookieManager.getInstance().removeAllCookies(ok->{CookieManager.getInstance().flush();WebStorage.getInstance().deleteAllData();signingOut=false;if(!isFinishing()&&!isDestroyed()){render();toast("已退出登录，缓存保留");}});
 }
 private void next(){if(!queue.isEmpty())open(queue.remove(),true);else{boolean finished=unifiedConnecting;unifiedConnecting=false;closeWeb();render();if(finished){int success=0;for(String state:connectionResults.values())if(state.equals("success"))success++;toast("连接完成："+success+" 个成功，"+(PLATFORMS.length-success)+" 个待处理");handler.post(this::autoRefresh);}}}
 private void detach(WebView v){if(v.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)v.getParent()).removeView(v);}
 private void park(WebView v){detach(v);if(browserBin!=null){v.setVisibility(View.INVISIBLE);browserBin.addView(v,new LinearLayout.LayoutParams(Math.max(1,getResources().getDisplayMetrics().widthPixels-dp(40)),Math.max(1,getResources().getDisplayMetrics().heightPixels-dp(160))));}}
 private void closeWeb(){generation++;reading=false;if(web!=null){CookieManager.getInstance().flush();park(web);}web=null;interactive=false;}
 private void rememberSession(String p,WebView current){WebView previous=sessions.put(p,current);popups.remove(current);if(previous!=null&&previous!=current&&!popups.contains(previous))popups.add(previous);}
 private String host(String p){return Platforms.find(p).expectedHost();}
 private boolean ready(String p,String raw){Uri u=Uri.parse(raw==null?"":raw);String path=String.valueOf(u.getPath());if(!host(p).equals(u.getHost()))return false;if(path.contains("/login")||path.endsWith("/loginto"))return false;return !p.equals("ehall")||path.equals("/jwapp/sys/cjcx")||path.startsWith("/jwapp/sys/cjcx/")||path.equals("/jwmobile")||path.startsWith("/jwmobile/");}
 private void showBrowser(String p,WebView view){
  interactive=true;
  LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(ui.background());page.setPadding(dp(12),dp(8),dp(12),0);
  page.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(12),insets.getSystemWindowInsetTop()+dp(6),dp(12),insets.getSystemWindowInsetBottom());return insets;});
  TextView title=ui.text(label(p),18);title.setTypeface(null,android.graphics.Typeface.BOLD);page.addView(title);
  LinearLayout controls=new LinearLayout(this);String[] titles={unifiedConnecting?"取消连接":"返回",unifiedConnecting?"重试连接":"重试获取","更多"};Runnable[] actions={()->{unifiedConnecting=false;queue.clear();closeWeb();render();},()->{if(web!=null)advance(p,web,0,generation);},()->picker("网页操作",new String[]{"刷新当前页面","重新打开平台入口","建立体育教学会话","跳过此平台"},i->{if(web==null)return;if(i==3){if(unifiedConnecting)connectionResults.put(p,"skipped");closeWeb();next();return;}if(i==0)web.reload();else if(i==1)web.loadUrl(Platforms.find(p).entryUrl());else if(p.equals("pe"))web.loadUrl("https://ggtypt.nju.edu.cn/ptm/loginto");})};
  for(int i=0;i<titles.length;i++){Button b=button(titles[i],actions[i]);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);lp.setMargins(i==0?0:dp(6),0,0,dp(4));controls.addView(b,lp);}page.addView(controls);
  notice=ui.label(unifiedConnecting?"完成认证后继续连接":"完成认证后自动获取");page.addView(notice);
  detach(view);view.setVisibility(View.VISIBLE);page.addView(view,new LinearLayout.LayoutParams(-1,0,1));setContentView(page);
 }
 private void open(String p,boolean automatic){
  if(signingOut)return;closeWeb();platform=p;interactive=!automatic;
  if(automatic&&unifiedConnecting){render();body.removeAllViews();body.addView(ui.section("正在连接平台",label(p)));body.addView(ui.label("第 "+(PLATFORMS.length-queue.size())+" / "+PLATFORMS.length+" 个"));action("取消连接",()->{unifiedConnecting=false;queue.clear();closeWeb();render();});}
  web=sessions.get(p);if(web==null){web=new WebView(this);sessions.put(p,web);configure(p,web,null);}
  if(automatic)park(web);else showBrowser(p,web);
  String current=web.getUrl();
  if(current!=null&&ready(p,current)){
   if(p.equals("ehall")){final WebView existing=web;final int stamp=generation;existing.evaluateJavascript("delete window.__njuEhallIdentity",ignored->{if(web==existing&&generation==stamp)advance(p,existing,0,stamp);});}
   else advance(p,web,0,generation);return;
  }
  if(!automatic&&current!=null&&!current.equals("about:blank")){advance(p,web,0,generation);return;}
  final WebView target=web;final int token=generation;
  String entry=p.equals("pe")?"https://ggtypt.nju.edu.cn/ptm/loginto":Platforms.find(p).entryUrl();
  target.loadUrl(entry);notice.setText(automatic?"正在连接 "+label(p)+"…":"请在学校页面完成认证");
  handler.postDelayed(()->{if(token==generation&&web==target&&!reading&&!interactive&&!ready(p,target.getUrl()))failed(p,"学校登录尚未完成",true);},15000);
 }
 private void configure(String p,WebView view,WebView opener){
  WebSettings config=view.getSettings();config.setJavaScriptEnabled(true);config.setJavaScriptCanOpenWindowsAutomatically(true);config.setDomStorageEnabled(true);config.setAllowFileAccess(false);config.setAllowContentAccess(false);config.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);config.setSupportMultipleWindows(true);
  config.setUseWideViewPort(true);config.setLoadWithOverviewMode(true);config.setSupportZoom(true);config.setBuiltInZoomControls(true);config.setDisplayZoomControls(false);
  WebView.setWebContentsDebuggingEnabled(false);CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(view,true);
  view.setWebChromeClient(new WebChromeClient(){
   @Override public boolean onConsoleMessage(ConsoleMessage message){return true;}
   @Override public boolean onCreateWindow(WebView parent,boolean dialog,boolean gesture,Message message){WebView popup=new WebView(MainActivity.this);popups.add(popup);configure(p,popup,parent);((WebView.WebViewTransport)message.obj).setWebView(popup);message.sendToTarget();if(web==parent){web=popup;if(interactive)showBrowser(p,popup);else park(popup);}return true;}
   @Override public void onCloseWindow(WebView window){if(opener!=null){if(sessions.get(p)==window){sessions.put(p,opener);popups.remove(opener);}detach(window);popups.remove(window);window.destroy();if(web==window){web=opener;if(interactive)showBrowser(p,opener);else park(opener);}}}
  });
  view.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){String raw=request.getUrl().toString(),upgrade=NavigationPolicy.upgrade(raw);if(!upgrade.equals(raw)){v.loadUrl(upgrade);return true;}if(NavigationPolicy.allowed(raw))return false;if(web==v)notice.setText("无法打开 "+NavigationPolicy.safeLocation(raw)+"：此地址不在允许的学校访问范围内");return true;}
   @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){if(v==web){generation++;reading=false;}}
   @Override public void onPageFinished(WebView v,String url){
    CookieManager.getInstance().flush();if(v!=web)return;
    final int token=generation;handler.postDelayed(()->advance(p,v,0,token),600);
   }
   @Override public void onReceivedError(WebView v,WebResourceRequest request,WebResourceError error){if(!request.isForMainFrame()||v!=web)return;String raw=request.getUrl().toString(),upgrade=NavigationPolicy.upgrade(raw);if(!upgrade.equals(raw)){v.loadUrl(upgrade);return;}failed(p,"页面网络错误（"+error.getErrorCode()+"），位置："+NavigationPolicy.safeLocation(raw),false);}
   @Override public void onReceivedHttpError(WebView v,WebResourceRequest request,WebResourceResponse response){if(request.isForMainFrame()&&v==web&&response.getStatusCode()>=400)failed(p,"学校页面返回 HTTP "+response.getStatusCode(),response.getStatusCode()==401);}
  });
 }
 private void advance(String p,WebView view,int attempts,int token){
  if(web!=view||reading||generation!=token)return;
  if(attempts>=(p.equals("ehall")?120:30)){failed(p,p.equals("ehall")?"成绩页面未就绪，请重试":"页面加载未完成，请重试",false);return;}
  try(InputStream in=getAssets().open("entry.js");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);String script="window.__njuSource="+JSONObject.quote(p)+";"+new String(out.toByteArray(),StandardCharsets.UTF_8);
   view.evaluateJavascript(script,value->{if(web!=view||generation!=token)return;try{String state=new JSONObject(value).optString("state");if((state.equals("read")||state.equals("ready"))&&ready(p,view.getUrl())){if(unifiedConnecting)authenticated(p,view);else read();return;}if(state.equals("auth")){if(interactive){notice.setText(unifiedConnecting?"完成认证后继续连接":"完成认证后自动获取");handler.postDelayed(()->advance(p,view,0,token),1500);}else failed(p,"学校需要验证身份，点击提示即可登录",true);return;}handler.postDelayed(()->advance(p,view,attempts+1,token),500);}catch(Exception e){handler.postDelayed(()->advance(p,view,attempts+1,token),500);}});
  }catch(Exception e){failed(p,"无法打开平台，请重试",false);}
 }
 private void authenticated(String p,WebView view){
  rememberSession(p,view);loginRequired.remove(p);put(settings(),"connected_"+p,true);put(settings(),"authenticated_"+p,System.currentTimeMillis());if(settings().optBoolean("reauthPending_"+p)){put(settings(),"retryAfterAuth_"+p,true);put(settings(),"reauthPending_"+p,false);}put(settings(),"error_"+p,"");connectionResults.put(p,"success");store.save();closeWeb();next();
 }
 private void read(){
  if(web==null||reading)return;String current=web.getUrl();if(!ready(platform,current)||!NavigationPolicy.allowed(current)){if(interactive)notice.setText(unifiedConnecting?"完成认证后继续连接":"完成认证后自动获取");else failed(platform,"请完成此平台认证",true);return;}
  boolean grade=!platform.equals("moodle")&&!platform.equals("lms");final String active=platform;
  try(InputStream in=getAssets().open(Platforms.find(active).readerAsset());ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);String script=new String(out.toByteArray(),StandardCharsets.UTF_8);int id=++generation;reading=true;put(settings(),"retryAfterAuth_"+active,false);put(settings(),"checked_"+active,System.currentTimeMillis());updateNotice();web.evaluateJavascript(script,v->poll(id,active,grade,0));}catch(Exception e){failed(active,"读取失败，请重试",false);}
 }
 private void poll(int id,String p,boolean grade,int n){
  if(id!=generation||web==null)return;if(n>=180){failed(p,"读取超时，请检查校园网络",false);return;}
  web.evaluateJavascript("JSON.stringify(window."+(grade?"__njuGradeResult":"__njuHomeworkResult")+")",value->{if(id!=generation)return;try{
   Object decoded=new JSONTokener(value==null?"null":value).nextValue();if(!(decoded instanceof String)||decoded.equals("null")){handler.postDelayed(()->poll(id,p,grade,n+1),1000);return;}
   JSONObject result=new JSONObject((String)decoded);if(result.has("error")){String reason=result.optString("error"),kind=result.optString("errorKind");boolean auth=kind.equals("auth")||kind.isEmpty()&&(reason.contains("登录已失效")||reason.contains("尚未检测到已登录")||reason.contains("没有提供 Moodle 会话"));String message=auth?"需要认证，请在当前网页完成登录":reason;if(grade&&result.optString("errorCode").equals("elite_fields_missing"))message="暂未获取到完整成绩";failed(p,message,auth);return;}
   if(grade){if(result.optBoolean("partial")||!(result.opt("records") instanceof JSONArray)){failed(p,"成绩结果不完整，保留旧缓存",false);return;}JSONObject g=store.data.optJSONObject("grades");if(g==null){g=new JSONObject();put(store.data,"grades",g);}put(g,p,result);}else store.ingest(p,result);
   put(settings(),"connected_"+p,true);put(settings(),"reauthPending_"+p,false);put(settings(),"retryAfterAuth_"+p,false);rememberSession(p,web);loginRequired.remove(p);boolean partial=false,details=false;JSONArray cs=result.optJSONArray("courses");if(cs!=null)for(int i=0;i<cs.length();i++){partial|=cs.optJSONObject(i).has("error");details|=cs.optJSONObject(i).has("detailsPartial");}
   if(!partial)put(settings(),"success_"+p,System.currentTimeMillis());put(settings(),"error_"+p,partial?"部分课程读取失败，缓存保留":details?"部分详情暂不可读，作业索引已更新":"");changed();reading=false;if(unifiedConnecting)connectionResults.put(p,partial?"partial":"success");if(grade&&interactive&&!unifiedConnecting)tab=2;closeWeb();if(!queue.isEmpty()||unifiedConnecting)next();else{render();if(!partial)toast("数据已更新");}
  }catch(Exception e){failed(p,"数据格式无法识别，缓存保留",false);}});
 }
 private void failed(String p,String reason){failed(p,reason,false);}
 private void failed(String p,String reason,boolean auth){
  generation++;reading=false;if(unifiedConnecting)connectionResults.put(p,auth?"auth":"failed");put(settings(),"error_"+p,reason);if(!unifiedConnecting){put(settings(),"checked_"+p,System.currentTimeMillis());if(auth)put(settings(),"reauthPending_"+p,true);}if(auth)loginRequired.add(p);store.save();
  if(unifiedConnecting&&auth&&!interactive&&web!=null){showBrowser(p,web);notice.setText("请完成学校认证，随后自动连接其他平台");if(ready(p,web.getUrl())&&authReopened.add(p))web.loadUrl(p.equals("pe")?"https://ggtypt.nju.edu.cn/ptm/loginto":Platforms.find(p).entryUrl());return;}if(interactive){notice.setText(reason);return;}closeWeb();if(!queue.isEmpty()||unifiedConnecting)next();else{render();notice.setText(label(p)+"："+reason+(auth?" · 点击登录":""));}
 }
 private void authorizeGoogleCalendar(){
  if(googleAuthorizing)return;
  googleAuthorizing=true;
  com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient(this)
   .authorize(GoogleCalendarJob.authorization())
   .addOnSuccessListener(this,result->{
    if(result.hasResolution()){
     try{startIntentSenderForResult(result.getPendingIntent().getIntentSender(),GOOGLE_CALENDAR_AUTH,null,0,0,0);}
     catch(IntentSender.SendIntentException e){googleAuthorizing=false;toast("无法打开 Google 授权，请重试");}
    }else finishGoogleAuthorization(result);
   }).addOnFailureListener(this,e->{googleAuthorizing=false;toast("Google 授权失败，请检查 Play 服务和 OAuth 配置");});
 }
 private void finishGoogleAuthorization(com.google.android.gms.auth.api.identity.AuthorizationResult result){
  googleAuthorizing=false;
  String token=result.getAccessToken();
  if(token==null||!result.getGrantedScopes().contains(GoogleCalendarSync.SCOPE)){toast("未获得 Google 日历权限");return;}
  SharedPreferences prefs=GoogleCalendarSync.prefs(this);
  prefs.edit().putBoolean("enabled",true).putString("status","正在同步").apply();
  GoogleCalendarJob.schedule(this,true);render();
  Context app=getApplicationContext();
  int connection=prefs.getInt("connection",0);
  GoogleCalendarJob.EXECUTOR.execute(()->{
   if(prefs.getInt("connection",0)!=connection)return;
   boolean retry=GoogleCalendarJob.run(app,token);
   if(retry)GoogleCalendarJob.schedule(app,false);
   runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()&&tab==3&&web==null){render();toast(prefs.getString("status","同步未完成"));}});
  });
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==GOOGLE_CALENDAR_AUTH){
  googleAuthorizing=false;
  if(result==RESULT_OK&&data!=null)try{finishGoogleAuthorization(com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data));}
  catch(com.google.android.gms.common.api.ApiException e){toast("Google 日历授权失败");}
  return;
 }if(request==40&&result==RESULT_OK&&data!=null){try(InputStream in=getContentResolver().openInputStream(data.getData())){if(in==null)throw new IOException();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>2*1024*1024)throw new IOException();out.write(b,0,n);}importCsv(new String(out.toByteArray(),StandardCharsets.UTF_8));}catch(Exception e){toast("CSV 无法读取、超过 2MB 或格式不正确");}}}
 private void importCsv(String csv){try{List<List<String>> rows=Csv.parse(csv);if(rows.isEmpty()||!rows.get(0).equals(Arrays.asList("课程名称","作业标题","作业内容","截止时间")))throw new Exception();for(int i=1;i<rows.size();i++){List<String> r=rows.get(i);if(r.size()!=4||r.get(0).trim().isEmpty()||r.get(1).trim().isEmpty())throw new Exception();parseDate(r.get(3));}new AlertDialog.Builder(this).setMessage("已校验 "+(rows.size()-1)+" 行。逐项确认课程与重复处理后导入？").setPositiveButton("导入",(d,w)->importRow(rows,1)).setNegativeButton("取消",null).show();}catch(Exception e){toast("CSV 表头应为：课程名称,作业标题,作业内容,截止时间；日期 YYYY-MM-DD HH:MM");}}
 private void importRow(List<List<String>> rows,int index){
  if(index>=rows.size()){changed();render();return;}List<String> r=rows.get(index);JSONObject course=null;for(int i=0;i<store.courses().length();i++){JSONObject c=store.courses().optJSONObject(i);if(c.optString("name").equals(r.get(0)))course=c;}
  if(course!=null){importInto(rows,index,course);return;}ArrayList<JSONObject> cs=new ArrayList<>();for(int i=0;i<store.courses().length();i++)cs.add(store.courses().optJSONObject(i));new AlertDialog.Builder(this).setTitle("课程不存在："+r.get(0)).setItems(cs.stream().map(c->c.optString("name")).toArray(String[]::new),(d,w)->importInto(rows,index,cs.get(w))).setPositiveButton("手动创建此课程",(d,w)->importInto(rows,index,createCourse(r.get(0)))).setNegativeButton("跳过此行",(d,w)->importRow(rows,index+1)).show();
 }
 private void importInto(List<List<String>> rows,int i,JSONObject c){
  List<String> r=rows.get(i);JSONObject duplicate=null;for(int j=0;j<store.tasks().length();j++){JSONObject t=store.tasks().optJSONObject(j);if(t.optString("course").equals(c.optString("id"))&&t.optString("title").equals(r.get(1)))duplicate=t;}final JSONObject existing=duplicate;
  Runnable apply=()->{JSONObject t=existing==null?new JSONObject():existing;put(t,"id",t.optString("id",UUID.randomUUID().toString()));put(t,"course",c.optString("id"));put(t,"title",r.get(1));put(t,"content",r.get(2));put(t,"due",parseDate(r.get(3)));if(existing==null){put(t,"sources",new JSONArray());store.tasks().put(t);}store.save();handler.post(()->importRow(rows,i+1));};if(existing==null)apply.run();else new AlertDialog.Builder(this).setTitle("重复作业："+r.get(1)).setMessage("覆盖本地标题、内容与截止时间？平台刷新后仍以平台为准。").setPositiveButton("覆盖",(d,w)->apply.run()).setNegativeButton("跳过",(d,w)->importRow(rows,i+1)).show();
 }
 @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==70&&g.length==2&&g[0]==0&&g[1]==0)calendar.chooseCalendar();else toast("未授权日历；作业缓存可正常使用");}
 @Override public void onBackPressed(){if(unifiedConnecting){unifiedConnecting=false;queue.clear();closeWeb();render();return;}if(web!=null&&web.canGoBack()){web.goBack();return;}queue.clear();closeWeb();render();}
 @Override protected void onDestroy(){closeWeb();for(WebView cached:sessions.values()){detach(cached);cached.destroy();}sessions.clear();for(WebView popup:popups){detach(popup);popup.destroy();}popups.clear();handler.removeCallbacksAndMessages(null);super.onDestroy();}
 @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);if(!interactive)render();}
}
