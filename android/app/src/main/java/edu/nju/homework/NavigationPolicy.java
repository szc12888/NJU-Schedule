package edu.nju.homework;

import java.net.URI;
import java.util.Locale;

/** Rewrites only the outer LMS navigation; CAS service/ticket encoding stays untouched. */
public final class NavigationPolicy {
 private NavigationPolicy() {}
 public static String upgrade(String raw) {
  try { URI u=new URI(raw); if("http".equalsIgnoreCase(u.getScheme())&&"lms.nju.edu.cn".equalsIgnoreCase(u.getHost())&&(u.getPort()==-1||u.getPort()==80)&&u.getRawUserInfo()==null)
   return "https:"+raw.substring(raw.indexOf(':')+1).replaceFirst("(?i)^//lms\\.nju\\.edu\\.cn:80(?=/|$)","//lms.nju.edu.cn");
  } catch(Exception ignored) {} return raw;
 }
 public static boolean allowed(String raw) {
  try {URI u=new URI(raw);String host=u.getHost();if(host==null||u.getRawUserInfo()!=null)return false;host=host.toLowerCase(Locale.ROOT);
   boolean school=host.equals("nju.edu.cn")||host.endsWith(".nju.edu.cn");
   return school&&("https".equalsIgnoreCase(u.getScheme())&&(u.getPort()==-1||u.getPort()==443)||"http".equalsIgnoreCase(u.getScheme())&&host.equals("elite.nju.edu.cn")&&(u.getPort()==-1||u.getPort()==80));
  }catch(Exception ignored){return false;}
 }
 public static String safeLocation(String raw){try{URI u=new URI(raw);return u.getHost()+String.valueOf(u.getPath());}catch(Exception e){return "未知地址";}}
}
