package com.trazyar.level;
public final class BeepPatternTest {
  static int count=0;
  static void check(String m,boolean pass){if(!pass)throw new AssertionError(m);count++;}
  public static void main(String[] args){
    BeepPattern.Step far=BeepPattern.of(15,.5,false);
    BeepPattern.Step mid=BeepPattern.of(6,.5,false);
    BeepPattern.Step near=BeepPattern.of(.8,.5,false);
    BeepPattern.Step level=BeepPattern.of(.3,.5,true);
    check("far short",far.onMs<far.periodMs/3);
    check("near has longer beep",near.onMs>mid.onMs&&mid.onMs>far.onMs);
    check("louder near",near.amplitude>mid.amplitude&&mid.amplitude>far.amplitude);
    check("more frequent near",near.periodMs<mid.periodMs&&mid.periodMs<far.periodMs);
    check("continuous only if stable",level.continuous&&!near.continuous);
    check("continuous has no gap",level.periodMs==level.onMs);
    check("steady louder than near",level.amplitude>near.amplitude);
    check("zero tolerance safe",BeepPattern.of(1,0,false).onMs>0);
    check("NaN safe",BeepPattern.of(Double.NaN,.5,false).onMs>0);
    check("nonnegative amplitude",far.amplitude>=0);
    check("bubble level permits beep",BeepPattern.allowedMode(0));
    check("inclinometer must be silent",!BeepPattern.allowedMode(1));
    check("protractor must be silent",!BeepPattern.allowedMode(2));
    check("square must be silent",!BeepPattern.allowedMode(3));
    check("invalid mode must be silent",!BeepPattern.allowedMode(-1));
    System.out.println("TrazYar BeepPattern tests passed: "+count);
  }
}
