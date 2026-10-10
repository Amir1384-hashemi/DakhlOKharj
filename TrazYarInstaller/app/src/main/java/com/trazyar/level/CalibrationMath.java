package com.trazyar.level;

/** Two-position level bias calculation, pure-Java and host-testable. */
public final class CalibrationMath {
  private CalibrationMath(){}

  /** Opposite orientations on the SAME nearly horizontal surface yield a
    sensor fixed-axis bias as their mean: r1 = slope+bias, r2=-slope+bias. */
  public static double zeroBias(double first,double opposite){
    if(!Double.isFinite(first)||!Double.isFinite(opposite))return Double.NaN;
    return .5*first+.5*opposite;
  }

  public static double corrected(double reading,double bias){
    return reading-bias;
  }
}
