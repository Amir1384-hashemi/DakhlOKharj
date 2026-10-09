package com.trazyar.level;
public final class BeepPattern {
  /** The bubble level is the ONLY tool authorised to produce a beep. */
  public static boolean allowedMode(int mode){return mode==0;}

  private BeepPattern(){}
  public static final class Step {
    public final int periodMs,onMs;public final double amplitude;public final boolean continuous;
    Step(int periodMs,int onMs,double amplitude,boolean continuous){
      this.periodMs=periodMs;this.onMs=onMs;this.amplitude=amplitude;this.continuous=continuous;
    }
  }
  public static Step of(double errorDegrees,double toleranceDegrees,boolean stable){
    if(stable)return new Step(1000,1000,.88,true);
    double error=Math.max(0,Double.isFinite(errorDegrees)?errorDegrees:25);
    double tol=Math.max(.15,toleranceDegrees);
    double ratio=Math.max(0,Math.min(1,(12-error)/(12-tol)));
    int cycle=(int)Math.round(1120-670*ratio);
    double duty=.10+.82*Math.pow(ratio,1.2);
    int on=Math.min(cycle-18,Math.max(55,(int)Math.round(cycle*duty)));
    return new Step(cycle,on,.13+.68*ratio,false);
  }
}
