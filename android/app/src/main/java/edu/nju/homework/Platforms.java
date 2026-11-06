package edu.nju.homework;

public final class Platforms {
 private Platforms() {}
 private static PlatformAdapter adapter(String id,String url,String host,String reader,boolean available){
  return new PlatformAdapter(){public String id(){return id;}public String entryUrl(){return url;}public String expectedHost(){return host;}public String readerAsset(){return reader;}public boolean available(){return available;}};
 }
 public static final PlatformAdapter[] ALL={
  adapter("moodle","https://selearning.nju.edu.cn/my/","selearning.nju.edu.cn","moodle.js",true),
  adapter("lms","https://lms.nju.edu.cn/login","lms.nju.edu.cn","lms.js",true),
  adapter("ehall","https://ehall.nju.edu.cn/","ehallapp.nju.edu.cn","grades.js",true),
  adapter("elite","http://elite.nju.edu.cn/exchangesystem/","elite.nju.edu.cn","grades.js",true),
  adapter("pe","https://ggtypt.nju.edu.cn/ggtypt/home","ggtypt.nju.edu.cn","grades.js",true),
  // SEEC stays disabled until its authentication and data paths are verified.
  adapter("seec","","","",false)
 };
 public static PlatformAdapter find(String id){for(PlatformAdapter p:ALL)if(p.id().equals(id))return p;throw new IllegalArgumentException("Unknown platform");}
}
