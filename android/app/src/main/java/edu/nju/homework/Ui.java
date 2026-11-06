package edu.nju.homework;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.function.IntConsumer;

/** Native visual system: calm surfaces, strong typography, restrained NJU purple. */
public final class Ui {
 private final Activity activity; private final boolean dark;
 public Ui(Activity activity,boolean dark){this.activity=activity;this.dark=dark;barContrast();}
 private void barContrast(){
  // Window.getInsetsController() can dereference an uncreated DecorView during onCreate.
  // Create the decor first, then wait until it is attached before styling system bars.
  View decor=activity.getWindow().getDecorView();
  decor.post(()->{
   if(activity.isFinishing()||activity.isDestroyed())return;
   if(android.os.Build.VERSION.SDK_INT>=30){android.view.WindowInsetsController controller=decor.getWindowInsetsController();if(controller!=null){int mask=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;controller.setSystemBarsAppearance(dark?0:mask,mask);}}
   else{int mask=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;decor.setSystemUiVisibility((decor.getSystemUiVisibility()&~mask)|(dark?0:mask));}
  });
 }
 public int dp(int value){return Math.round(activity.getResources().getDisplayMetrics().density*value);}
 public int background(){return dark?0xff15131b:0xfff6f5f9;}
 public int foreground(){return dark?0xfff1edf6:0xff252130;}
 public int muted(){return dark?0xffa69fb3:0xff80788e;}
 public int accent(){return dark?0xffd0a5ed:0xff74458d;}
 public int panel(){return dark?0xff211e2a:Color.WHITE;}
 public int blue(){return dark?0xff9dc5ff:0xff315fa2;}
 public int teal(){return dark?0xff8ed8c6:0xff21745f;}
 public int warm(){return dark?0xfff2bd8b:0xffa95b24;}
 private int tint(int index){return dark?new int[]{0xff30243d,0xff1e3044,0xff1e3530,0xff3b2d24}[index]:new int[]{0xfff1e9f7,0xffeaf1fd,0xffe7f4ee,0xfffff0e2}[index];}
 public GradientDrawable rounded(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 public TextView text(String value,int size){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(foreground());t.setFontFeatureSettings("kern");t.setLineSpacing(dp(3),1f);t.setPadding(0,dp(4),0,dp(4));return t;}
 public TextView label(String value){TextView t=text(value,12);t.setTextColor(muted());return t;}
 public Button button(String label,Runnable click){
  Button b=new Button(activity);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(accent());b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setMinHeight(dp(46));b.setMinimumHeight(dp(46));b.setPadding(dp(16),dp(8),dp(16),dp(8));
  StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_pressed},rounded(dark?0xff403047:0xffe5daed,14));states.addState(new int[]{},rounded(dark?0xff2c2535:0xffeee7f4,14));b.setBackground(states);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(4),0,dp(4));b.setLayoutParams(lp);b.setOnClickListener(v->click.run());return b;
 }
 public LinearLayout section(String title,String subtitle){LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(0,dp(12),0,dp(10));TextView h=text(title,25);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);box.addView(h);if(!subtitle.isEmpty())box.addView(label(subtitle));return box;}
 public LinearLayout detailPanel(String title){
  LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(16),dp(18),dp(16));box.setBackground(rounded(panel(),18));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(14));box.setLayoutParams(lp);TextView heading=text(title,15);heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);heading.setTextColor(title.equals("作业内容")?blue():title.equals("作业概览")?accent():teal());heading.setPadding(0,0,0,dp(12));box.addView(heading);return box;
 }
 public void detailField(LinearLayout box,String title,String value){
  if(!title.isEmpty()){TextView label=label(title);label.setPadding(0,dp(8),0,dp(4));box.addView(label);}TextView content=text(value,15);content.setTextIsSelectable(true);content.setLineSpacing(dp(5),1.08f);content.setPadding(0,0,0,dp(10));box.addView(content);
 }
 public View gradeStats(String gpa,String percent,String rank){
  LinearLayout row=new LinearLayout(activity);row.setTag("grade-summary");
  String[] titles={"GPA","排名百分比","估算名次 / 人数"},values={gpa,percent,rank},tags={"grade-gpa","grade-percent","grade-rank"};
  for(int i=0;i<3;i++){
   LinearLayout box=new LinearLayout(activity);box.setTag(tags[i]);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(12),dp(12),dp(12),dp(12));
   GradientDrawable surface=rounded(tint(i),17);box.setBackground(surface);
   TextView label=label(titles[i]);label.setTextSize(i==2?10:11);label.setSingleLine(true);box.addView(label);
   TextView value=text(values[i],i==2?21:27);value.setTypeface(Typeface.DEFAULT,Typeface.BOLD);value.setTextColor(i==0?accent():i==1?blue():teal());value.setGravity(Gravity.BOTTOM);value.setMaxLines(1);value.setAutoSizeTextTypeUniformWithConfiguration(12,i==2?22:28,1,android.util.TypedValue.COMPLEX_UNIT_SP);box.addView(value,new LinearLayout.LayoutParams(-1,0,1));
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(100),1);lp.setMargins(i==0?0:dp(8),dp(2),0,dp(12));row.addView(box,lp);
  }return row;
 }
 public View filterBar(String summary,int active,boolean open,Runnable toggle){
  LinearLayout row=new LinearLayout(activity);row.setTag("assignment-filter-toggle");row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(14),0,dp(14),0);row.setBackground(rounded(panel(),14));
  TextView title=text(active==0?"筛选":"筛选 · "+active,14);title.setTextColor(accent());title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);row.addView(title);
  TextView state=label(summary);state.setSingleLine(true);state.setEllipsize(android.text.TextUtils.TruncateAt.END);state.setGravity(Gravity.END);LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(0,-2,1);slp.setMargins(dp(12),0,dp(10),0);row.addView(state,slp);TextView arrow=text(open?"⌃":"⌄",18);arrow.setTextColor(muted());row.addView(arrow);
  row.setContentDescription(open?"收起筛选":"展开筛选");row.setClickable(true);row.setFocusable(true);row.setOnClickListener(v->toggle.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(44));lp.setMargins(0,dp(2),0,dp(8));row.setLayoutParams(lp);return row;
 }
 public View filterChoice(String title,String value,Runnable choose){
  LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(12),dp(8),dp(12),dp(8));box.setBackground(rounded(dark?0xff2a2533:0xffeeeaf3,12));TextView small=label(title);small.setTextSize(10);box.addView(small);TextView selected=text(value+"  ›",13);selected.setSingleLine(true);selected.setEllipsize(android.text.TextUtils.TruncateAt.END);selected.setTypeface(Typeface.DEFAULT,Typeface.BOLD);box.addView(selected);box.setClickable(true);box.setFocusable(true);box.setOnClickListener(v->choose.run());return box;
 }
 public View courseGrade(String course,String score,Runnable click){
  LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(14),dp(13),dp(14),dp(13));row.setBackground(rounded(panel(),13));TextView title=text(course,15);title.setMaxLines(2);LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,-2,1);titleLp.setMargins(0,0,dp(14),0);row.addView(title,titleLp);TextView value=text(score,17);value.setTextColor(accent());value.setTypeface(Typeface.DEFAULT,Typeface.BOLD);row.addView(value);row.setClickable(true);row.setFocusable(true);row.setOnClickListener(v->click.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(6));row.setLayoutParams(lp);return row;
 }
 public LinearLayout card(String title,String subtitle,String due,String badge,boolean complete,Runnable click){
  LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(16),dp(18),dp(16));GradientDrawable surface=rounded(panel(),20);surface.setStroke(dp(1),dark?0xff302b3c:0xffedeaf2);box.setBackground(surface);box.setElevation(dp(1));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(7));box.setLayoutParams(lp);
  LinearLayout top=new LinearLayout(activity);top.setGravity(Gravity.CENTER_VERTICAL);TextView course=label(subtitle);course.setSingleLine(true);course.setEllipsize(android.text.TextUtils.TruncateAt.END);top.addView(course,new LinearLayout.LayoutParams(0,-2,1));
  if(!badge.isEmpty()){TextView tag=text(badge,11);int tone=complete?2:badge.contains("截止")?3:0;tag.setTextColor(complete?teal():tone==3?warm():accent());tag.setPadding(dp(9),dp(4),dp(9),dp(4));tag.setBackground(rounded(tint(tone),8));top.addView(tag);}box.addView(top);
  TextView heading=text(title,18);heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);heading.setPadding(0,dp(10),0,dp(10));if(complete){heading.setTextColor(muted());heading.setPaintFlags(heading.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);}box.addView(heading);
  LinearLayout bottom=new LinearLayout(activity);bottom.setGravity(Gravity.CENTER_VERTICAL);TextView deadline=text(due,13);deadline.setTextColor(!complete&&badge.contains("截止")?warm():muted());bottom.addView(deadline,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=text("›",24);arrow.setTextColor(accent());bottom.addView(arrow);box.addView(bottom);
  box.setClickable(true);box.setFocusable(true);box.setOnClickListener(v->click.run());return box;
 }
 public View navigation(int selected,IntConsumer choose){
  LinearLayout bar=new LinearLayout(activity);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(4),dp(8),dp(4),dp(2));bar.setBackground(rounded(panel(),24));
  String[] icons={"⌂","▤","▥","⚙"},names={"首页","作业","成绩","设置"};
  for(int i=0;i<4;i++){final int index=i;LinearLayout item=new LinearLayout(activity);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);item.setPadding(0,dp(5),0,dp(7));TextView icon=text(icons[i],23);icon.setGravity(Gravity.CENTER);icon.setTextColor(selected==i?accent():muted());icon.setMinWidth(dp(48));icon.setBackground(rounded(selected==i?(dark?0xff3c2b48:0xffeadef2):Color.TRANSPARENT,15));item.addView(icon);TextView name=text(names[i],11);name.setGravity(Gravity.CENTER);name.setTextColor(selected==i?accent():muted());name.setTypeface(Typeface.DEFAULT,selected==i?Typeface.BOLD:Typeface.NORMAL);item.addView(name);item.setContentDescription(names[i]);item.setClickable(true);item.setFocusable(true);item.setOnClickListener(v->choose.accept(index));bar.addView(item,new LinearLayout.LayoutParams(0,dp(72),1));}return bar;
 }
 public View summary(int count,int urgent){
  LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(18),dp(20),dp(18));GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{dark?0xff493357:0xff71538e,dark?0xff24434a:0xff4d8e99});gradient.setCornerRadius(dp(24));box.setBackground(gradient);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(6),0,dp(16));box.setLayoutParams(lp);
  LinearLayout numbers=new LinearLayout(activity);numbers.setGravity(Gravity.BOTTOM);TextView value=text(String.valueOf(count),40);value.setTypeface(Typeface.DEFAULT,Typeface.BOLD);value.setTextColor(Color.WHITE);numbers.addView(value);TextView caption=text("  项待完成",14);caption.setTextColor(Color.WHITE);caption.setPadding(dp(4),0,0,dp(10));numbers.addView(caption);box.addView(numbers);TextView hint=text(urgent>0?"其中 "+urgent+" 项将在 24 小时内截止":"暂无 24 小时内截止的作业",12);hint.setTextColor(0xffefe4f4);box.addView(hint);return box;
 }
}
