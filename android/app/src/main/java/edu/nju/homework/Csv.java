package edu.nju.homework;
import java.util.*;
/** RFC4180 quoted fields, escaped quotes and embedded line breaks. */
public final class Csv {
 public static List<List<String>> parse(String input){
  if(input.startsWith("\ufeff"))input=input.substring(1);
  List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder field=new StringBuilder();boolean quoted=false,closed=false;
  for(int i=0;i<input.length();i++){char c=input.charAt(i);
   if(quoted){if(c=='"'){if(i+1<input.length()&&input.charAt(i+1)=='"'){field.append('"');i++;}else{quoted=false;closed=true;}}else field.append(c);continue;}
   if(c=='"'){if(field.length()!=0||closed)throw new IllegalArgumentException("quote");quoted=true;}
   else if(c==','||c=='\n'||c=='\r'){row.add(field.toString());field.setLength(0);closed=false;if(c!=','){if(c=='\r'&&i+1<input.length()&&input.charAt(i+1)=='\n')i++;rows.add(row);row=new ArrayList<>();}}
   else{if(closed)throw new IllegalArgumentException("trailing text");field.append(c);}
  }
  if(quoted)throw new IllegalArgumentException("unclosed quote");if(field.length()>0||!row.isEmpty()||closed){row.add(field.toString());rows.add(row);}return rows;
 }
}
