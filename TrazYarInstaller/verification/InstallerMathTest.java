package com.trazyar.level;
public final class InstallerMathTest {
  static int count=0;
  static void eq(String label,double expected,double actual,double tolerance){
    if(!Double.isFinite(actual)||Math.abs(expected-actual)>tolerance)
      throw new AssertionError(label+": expected="+expected+" actual="+actual);
    count++;
  }
  static void check(String label,boolean ok){if(!ok)throw new AssertionError(label);count++;}
  static void fail(String label,Runnable action){
    try{action.run();throw new AssertionError(label+": not rejected");}
    catch(IllegalArgumentException expected){count++;}
  }
  public static void main(String[] args){
    InstallerMath.Result flat=InstallerMath.compute(60,80,0,0);
    for(double v:flat.raiseMm)eq("flat surface has no correction",0,v,1e-8);
    double delta=600*Math.tan(Math.toRadians(1));
    InstallerMath.Result rightLow=InstallerMath.compute(60,80,1,0);
    eq("right lower back right",delta,rightLow.raiseMm[InstallerMath.BACK_RIGHT],1e-7);
    eq("right lower front right",delta,rightLow.raiseMm[InstallerMath.FRONT_RIGHT],1e-7);
    eq("left side reference",0,rightLow.raiseMm[InstallerMath.BACK_LEFT],1e-7);
    InstallerMath.Result backLow=InstallerMath.compute(60,80,0,1);
    double depthDrop=800*Math.tan(Math.toRadians(1));
    eq("rear right lower",depthDrop,backLow.raiseMm[InstallerMath.BACK_RIGHT],1e-7);
    eq("rear left lower",depthDrop,backLow.raiseMm[InstallerMath.BACK_LEFT],1e-7);
    eq("front left reference",0,backLow.raiseMm[InstallerMath.FRONT_LEFT],1e-7);
    InstallerMath.Result mixed=InstallerMath.compute(60,80,1,-2);
    eq("each planar rectangle correction identity",
      mixed.raiseMm[InstallerMath.BACK_RIGHT]+mixed.raiseMm[InstallerMath.FRONT_LEFT],
      mixed.raiseMm[InstallerMath.FRONT_RIGHT]+mixed.raiseMm[InstallerMath.BACK_LEFT],1e-7);
    eq("highest corner needs zero",0,Math.min(Math.min(mixed.raiseMm[0],mixed.raiseMm[1]),Math.min(mixed.raiseMm[2],mixed.raiseMm[3])),1e-7);
    eq("persian digits",60.5,InstallerMath.parseCentimeters("۶۰٫۵"),1e-8);
    eq("arabic digits",80.5,InstallerMath.parseCentimeters("٨٠,٥"),1e-8);
    fail("zero size rejected",()->InstallerMath.compute(0,60,0,0));
    fail("no arbitrary steep slopes",()->InstallerMath.compute(60,60,89,0));
    fail("not-a-number rejected",()->InstallerMath.parseCentimeters("NaN"));
    fail("non-number rejected",()->InstallerMath.parseCentimeters("نامعلوم"));
    fail("huge size rejected",()->InstallerMath.parseCentimeters("2000"));
    InstallerMath.Result after=InstallerMath.compute(60,80,.2,.15);
    String text=InstallerReport.build("کابینت مشتری",1728000000000L,mixed,1728003000000L,after);
    check("report before",text.contains("قبل از تنظیم"));
    check("report after",text.contains("بعد از تنظیم"));
    check("report project",text.contains("کابینت مشتری"));
    check("report corners",text.contains("عقب راست")&&text.contains("جلو چپ"));
    check("report improvement",text.contains("کاهش بیشترین اختلاف"));
    check("report caveat",text.contains("تخمینی")&&text.contains("تراز مرجع"));
    String incomplete=InstallerReport.build("پروژه",1728000000000L,mixed,0,null);
    check("before only report",incomplete.contains("هنوز ثبت نشده"));
    System.out.println("TrazYar installer math/report tests passed: "+count);
  }
}
