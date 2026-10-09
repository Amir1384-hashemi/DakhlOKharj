package com.trazyar.level;

/** Pure gravity-vector geometry, unit-testable without Android runtime. */
public final class LevelMath {
  private LevelMath() {}

  /** Angle between the phone's face and the horizontal surface, 0..90 degrees. */
  public static double inclineDegrees(double gx,double gy,double gz) {
    return Math.toDegrees(Math.atan2(Math.hypot(gx,gy),Math.abs(gz)));
  }

  /** Rise/run * 100. Becomes infinite for a vertical plane. */
  public static double slopePercent(double gx,double gy,double gz) {
    if(Math.abs(gz)<0.08) return Double.POSITIVE_INFINITY;
    return Math.hypot(gx,gy)/Math.abs(gz)*100.0;
  }

  /** Edge-angle measurements require a roughly vertical face. */
  public static boolean canMeasureEdge(double gx,double gy,double gz) {
    double horizontal=Math.hypot(gx,gy);
    double total=Math.hypot(horizontal,gz);
    return total>1 && horizontal/total >= 0.80;
  }

  /** The LONG edge of the phone relative to horizontal, 0..180 in a vertical plane.
      A portrait upright long edge gives 90 degrees; a horizontal long edge gives 0. */
  public static double edgeFromHorizontal(double gx,double gy) {
    double degrees=Math.toDegrees(Math.atan2(gy,gx));
    return ((degrees%180.0)+180.0)%180.0;
  }

  /** Smallest undirected angle between two lines; 0..90 degrees. */
  public static double angleBetweenLines(double a,double b) {
    double diff=Math.abs(a-b)%180.0;
    return Math.min(diff,180.0-diff);
  }
}
