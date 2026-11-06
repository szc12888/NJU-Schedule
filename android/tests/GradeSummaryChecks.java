package edu.nju.homework;

/** Synthetic host checks for GradeSummary; no platform or personal data is used. */
public final class GradeSummaryChecks {
 private static int checks;

 private static void expect(String actual,String expected,String name) {
  checks++;
  if(!expected.equals(actual))throw new AssertionError(name+" expected="+expected+" actual="+actual);
 }

 public static void main(String[] args) {
  expect(GradeSummary.estimate("0%","8"),"≈1 / 8","zero percent clamps to rank one");
  expect(GradeSummary.estimate("100%","8"),"≈8 / 8","one hundred percent uses all ranks");
  expect(GradeSummary.estimate(" 25 "," 8 "),"≈2 / 8","plain decimal percentage and surrounding spaces");
  expect(GradeSummary.estimate("25%","8"),"≈2 / 8","ASCII percent suffix");
  expect(GradeSummary.estimate("\u3000 25 ％ \u3000","8"),"≈2 / 8","full-width percent and spaces");
  expect(GradeSummary.estimate("12.5","8"),"≈1 / 8","fractional percentage rounds half up");
  expect(GradeSummary.estimate("18.75%","8"),"≈2 / 8","fractional result rounds half up");
  expect(GradeSummary.estimate("50%","7"),"≈4 / 7","exact half rounds away from zero");
  expect(GradeSummary.estimate("1%","2"),"≈1 / 2","positive rounding below one clamps to one");
  expect(GradeSummary.estimate("99%","1"),"≈1 / 1","result never exceeds population");
  expect(GradeSummary.estimate("50%","2147483647"),"≈1073741824 / 2147483647","integer population boundary stays exact");
  expect(GradeSummary.estimate("0.0000000000000000000001%","2147483647"),"≈1 / 2147483647","tiny decimal remains computable");
  expect(GradeSummary.estimate("10%","0008"),"≈1 / 8","population is rendered canonically");

  expect(GradeSummary.estimate(null,"8"),"— / 8","missing percentage shows known population");
  expect(GradeSummary.estimate(" ","8"),"— / 8","blank percentage shows known population");
  expect(GradeSummary.estimate("not-a-number","8"),"— / 8","nonnumeric percentage shows known population");
  expect(GradeSummary.estimate("NaN","8"),"— / 8","NaN percentage is invalid");
  expect(GradeSummary.estimate("Infinity","8"),"— / 8","positive Infinity percentage is invalid");
  expect(GradeSummary.estimate("-Infinity","8"),"— / 8","negative Infinity percentage is invalid");
  expect(GradeSummary.estimate("-0.0001%","8"),"— / 8","percentage below zero is invalid");
  expect(GradeSummary.estimate("100.0001%","8"),"— / 8","percentage above one hundred is invalid");
  expect(GradeSummary.estimate("10%%","8"),"— / 8","repeated percent suffix is invalid");
  expect(GradeSummary.estimate("1%2%","8"),"— / 8","embedded percent suffix is invalid");

  expect(GradeSummary.estimate("10%","0"),"— / —","zero population is invalid");
  expect(GradeSummary.estimate("10%","-2"),"— / —","negative population is invalid");
  expect(GradeSummary.estimate("10%","2.5"),"— / —","fractional population is invalid");
  expect(GradeSummary.estimate("10%",null),"— / —","missing population is invalid");
  expect(GradeSummary.estimate("10%",""),"— / —","empty population is invalid");
  expect(GradeSummary.estimate("10%"," \u3000 "),"— / —","blank population is invalid");
  expect(GradeSummary.estimate("10%","not-a-number"),"— / —","nonnumeric population is invalid");
  expect(GradeSummary.estimate("10%","NaN"),"— / —","NaN population is invalid");
  expect(GradeSummary.estimate("10%","Infinity"),"— / —","Infinity population is invalid");
  expect(GradeSummary.estimate("10%","2147483648"),"— / —","population above int range is invalid");
  expect(GradeSummary.estimate(null,"0"),"— / —","invalid percentage and population use full fallback");

  expect(GradeSummary.estimate("1e-999999999","8"),"— / 8","scientific exponents rejected before BigDecimal allocation");
  expect(GradeSummary.estimate("0."+"0".repeat(80)+"1","8"),"— / 8","unbounded precision rejected");
  System.out.println("PASS: synthetic GradeSummary checks ("+checks+" cases)");
 }
}
