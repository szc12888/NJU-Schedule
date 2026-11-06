package edu.nju.homework;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure grade-ranking estimation helpers with no Android dependencies. */
public final class GradeSummary {
 private static final BigDecimal ZERO=BigDecimal.ZERO;
 private static final BigDecimal ONE_HUNDRED=BigDecimal.valueOf(100);
 private static final String INVALID="— / —";
 private GradeSummary() {}

 /**
  * Estimates a rank using the platform percentage scale from 0 through 100.
  * The result is approximate and is always constrained to the total population.
  */
 public static String estimate(String percent,String total) {
  Integer population=parsePopulation(total);
  if(population==null)return INVALID;
  String invalidWithPopulation="— / "+population;
  BigDecimal percentage=parsePercentage(percent);
  if(percentage==null)return invalidWithPopulation;

  try {
   BigDecimal rounded=percentage.multiply(BigDecimal.valueOf(population))
    .movePointLeft(2).setScale(0,RoundingMode.HALF_UP);
   int rank;
   if(rounded.compareTo(ZERO)<=0)rank=1;
   else if(rounded.compareTo(BigDecimal.valueOf(population))>0)rank=population;
   else rank=rounded.intValueExact();
   return "≈"+rank+" / "+population;
  }catch(ArithmeticException invalidPrecision) {
   return invalidWithPopulation;
  }
 }

 private static BigDecimal parsePercentage(String raw) {
  String value=stripSpace(raw);
  if(value==null||value.isEmpty())return null;
  if(value.endsWith("%")||value.endsWith("％")) {
   value=stripSpace(value.substring(0,value.length()-1));
   if(value.isEmpty())return null;
  }
  if(value.length()>64||!value.matches("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)"))return null;
  try {
   BigDecimal percentage=new BigDecimal(value);
   if(percentage.compareTo(ZERO)<0||percentage.compareTo(ONE_HUNDRED)>0)return null;
   return percentage;
  }catch(NumberFormatException invalidNumber) {
   return null;
  }
 }

 private static Integer parsePopulation(String raw) {
  String value=stripSpace(raw);
  if(value==null||value.isEmpty())return null;
  try {
   int population=Integer.parseInt(value);
   return population>0?population:null;
  }catch(NumberFormatException invalidNumber) {
   return null;
  }
 }

 private static String stripSpace(String raw) {
  if(raw==null)return null;
  int start=0;
  int end=raw.length();
  while(start<end&&isSpace(raw.charAt(start)))start++;
  while(end>start&&isSpace(raw.charAt(end-1)))end--;
  return raw.substring(start,end);
 }

 private static boolean isSpace(char value) {
  return Character.isWhitespace(value)||Character.isSpaceChar(value);
 }
}
