package edu.nju.homework;

import android.app.job.*;
import android.content.*;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import com.google.android.gms.auth.api.identity.*;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Tasks;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.*;

public final class GoogleCalendarJob extends JobService {
 static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor();
 private final java.util.Map<Integer,JobParameters> running=new java.util.HashMap<>();
 private final java.util.Map<Integer,Future<?>> jobs=new java.util.HashMap<>();
 static AuthorizationRequest authorization(){
  return AuthorizationRequest.builder().setRequestedScopes(Collections.singletonList(new Scope(GoogleCalendarSync.SCOPE))).build();
 }
 static void schedule(Context c,boolean periodic){
  if(!GoogleCalendarSync.prefs(c).getBoolean("enabled",false))return;
  JobScheduler scheduler=c.getSystemService(JobScheduler.class);
  int id=periodic?GoogleCalendarSync.PERIODIC:GoogleCalendarSync.IMMEDIATE;
  if(scheduler.getPendingJob(id)!=null)return;
  JobInfo.Builder job=new JobInfo.Builder(id,new ComponentName(c,GoogleCalendarJob.class))
    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true);
  if(periodic)job.setPeriodic(30*60000L);
  else job.setMinimumLatency(3000);
  if(scheduler.schedule(job.build())!=JobScheduler.RESULT_SUCCESS)GoogleCalendarSync.prefs(c).edit().putString("status","无法安排后台同步，可手动同步").apply();
 }
 static boolean run(Context c,String token){
  SharedPreferences prefs=GoogleCalendarSync.prefs(c);
  if(!prefs.getBoolean("enabled",false))return false;
  int connection=prefs.getInt("connection",0);
  try{
   if(token==null){
    AuthorizationResult auth=Tasks.await(Identity.getAuthorizationClient(c).authorize(authorization()),30,TimeUnit.SECONDS);
    token=auth.getAccessToken();
    if(auth.hasResolution()||token==null||!auth.getGrantedScopes().contains(GoogleCalendarSync.SCOPE)){status(prefs,connection,"需要重新授权");return false;}
   }
   if(!prefs.getBoolean("enabled",false)||prefs.getInt("connection",0)!=connection)return false;
   new GoogleCalendarSync(c,token).sync(new Store(c));
   return false;
  }catch(GoogleCalendarSync.ApiError e){
   if(e.status==401&&token!=null)Identity.getAuthorizationClient(c).clearToken(ClearTokenRequest.builder().setToken(token).build());
   status(prefs,connection,e.getMessage());
   return e.status==401||e.status==429||e.status>=500;
  }catch(GoogleCalendarSync.CreationUncertain e){
   status(prefs,connection,e.getMessage());return false;
  }catch(InterruptedException e){Thread.currentThread().interrupt();return true;}
  catch(Exception e){
   status(prefs,connection,e instanceof IOException?"同步未完成，请检查网络或在设置中重试":"无法同步，请在设置中检查 Google 授权");
   return e instanceof SocketTimeoutException||e instanceof UnknownHostException;
  }
 }
 private static void status(SharedPreferences p,int connection,String value){
  if(p.getBoolean("enabled",false)&&p.getInt("connection",0)==connection)p.edit().putString("status",value).apply();
 }
 @Override public boolean onStartJob(JobParameters p){
  running.put(p.getJobId(),p);
  jobs.put(p.getJobId(),EXECUTOR.submit(()->{
   boolean retry=run(getApplicationContext(),null);
   new android.os.Handler(getMainLooper()).post(()->{
    if(running.get(p.getJobId())==p){running.remove(p.getJobId());jobs.remove(p.getJobId());jobFinished(p,retry);}
   });
  }));
  return true;
 }
 @Override public boolean onStopJob(JobParameters p){
  running.remove(p.getJobId());
  Future<?> work=jobs.remove(p.getJobId());if(work!=null)work.cancel(true);
  return GoogleCalendarSync.prefs(this).getBoolean("enabled",false);
 }
}
