package com.trazyar.level;
public final class CalibrationMathTest {
  static int testCount=0;
  static void eq(String label,double expected,double actual){
    if(!Double.isFinite(actual)||Math.abs(expected-actual)>1e-8)
      throw new AssertionError(label+" expected "+expected+" got "+actual);
    testCount++;
  }
  static void check(String label,boolean ok){if(!ok)throw new AssertionError(label);testCount++;}
  public static void main(String[] args){
    eq("zero bias",0,CalibrationMath.zeroBias(1,-1));
    eq("fixed axis bias",.4,CalibrationMath.zeroBias(2.4,-1.6));
    eq("second axis bias",-.7,CalibrationMath.zeroBias(-2.7,1.3));
    eq("corrected level",0,CalibrationMath.corrected(.4,.4));
    eq("negative values",-.25,CalibrationMath.zeroBias(-1.5,1));
    check("reject NaN",Double.isNaN(CalibrationMath.zeroBias(Double.NaN,4)));
    check("reject infinity",Double.isNaN(CalibrationMath.zeroBias(1,Double.POSITIVE_INFINITY)));
    System.out.println("TrazYar calibration tests passed: "+testCount);
  }
}
