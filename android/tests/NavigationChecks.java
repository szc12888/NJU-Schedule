package edu.nju.homework;
public final class NavigationChecks {
 private static int checks;
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  String callback="http://lms.nju.edu.cn/login?ticket=ST-synthetic%2Bencoded&service=http%253A%252F%252Flms.nju.edu.cn%252Flogin";
  check(NavigationPolicy.upgrade(callback).equals("https"+callback.substring(4)),"upgrade preserves raw callback query without double encoding");
  check(NavigationPolicy.allowed(NavigationPolicy.upgrade(callback)),"upgraded callback is allowed");
  String nested="https://lms-identity.nju.edu.cn/auth/realms/nju/protocol/cas/login?service=http%3A//lms.nju.edu.cn/login";
  check(NavigationPolicy.upgrade(nested).equals(nested),"nested CAS service must stay unchanged");
  check(NavigationPolicy.allowed(nested),"LMS identity is school domain");
  check(NavigationPolicy.allowed("https://authserver.nju.edu.cn:443/authserver/login"),"authserver explicit HTTPS port");
  check(!NavigationPolicy.allowed("https://nju.edu.cn.evil.test/login"),"suffix lookalike refused");
  check(!NavigationPolicy.allowed("https://nju.edu.cn@evil.test/login"),"userinfo lookalike refused");
  check(!NavigationPolicy.allowed("http://lms.nju.edu.cn/login"),"never send raw cleartext LMS request");
  check(!NavigationPolicy.allowed("https://evil.test/login"),"external site refused");
  check(NavigationPolicy.allowed("http://elite.nju.edu.cn/exchangesystem/index/create?pid=4"),"explicitly authorized elite HTTP retained");
  check(!NavigationPolicy.safeLocation(callback).contains("ticket"),"diagnostic never includes query");
  System.out.println("PASS: "+checks+" navigation checks");
 }
}
