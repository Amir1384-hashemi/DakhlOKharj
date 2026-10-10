package com.trazyar.level;

import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;

/** Deterministic, shareable, Persian installer report without Android dependencies. */
public final class InstallerReport {
  private InstallerReport(){}
  private static String f(double a){return String.format(Locale.US,"%.2f",a);}
  public static String build(String project,long beforeAt,InstallerMath.Result before,
                             long afterAt,InstallerMath.Result after){
    if(before==null)throw new IllegalArgumentException("before measurement required");
    StringBuilder s=new StringBuilder();
    String safeName=project==null||project.trim().isEmpty()?"پروژه بدون نام":project.trim();
    s.append("گزارش نصاب‌یار حرفه‌ای — تراز یار\n")
      .append("پروژه: ").append(safeName).append("\n")
      .append("ابعاد سطح: عرض ").append(f(before.widthCm)).append(" سانتی‌متر، عمق ")
      .append(f(before.depthCm)).append(" سانتی‌متر\n")
      .append("روش: صفحه گوشی رو به بالا، بالای گوشی به سمت عقب وسیله\n");
    block(s,"قبل از تنظیم",beforeAt,before);
    if(after!=null){
      block(s,"بعد از تنظیم",afterAt,after);
      s.append("کاهش بیشترین اختلاف پیشنهادی گوشه‌ها: ")
        .append(f(before.maxRaiseMm-after.maxRaiseMm)).append(" میلی‌متر\n");
      s.append("کاهش بیشترین زاویهٔ محورها: ")
        .append(f(before.levelErrorDeg-after.levelErrorDeg)).append(" درجه\n");
      s.append(after.maxRaiseMm<=1.0?
        "ارزیابی: اختلاف سطح هندسی تخمینی حداکثر ۱ میلی‌متر است.\n":
        "ارزیابی: هنوز اختلاف تخمینی باقی مانده؛ اندازه‌گیری و تنظیم را تکرار کنید.\n");
    }else{
      s.append("اندازه‌گیری بعد: هنوز ثبت نشده است.\n");
    }
    s.append("\nتوجه: مقدار تنظیم هر گوشه تخمینی و نسبت به بالاترین گوشه است. ")
     .append("فقط برای سطح سخت، مسطح و هم‌راستا با گوشی؛ این روش تاب سطح، لق‌بودن، ")
     .append("بار واقعی هر پایه و چرخش مستقل پایه‌ها را تشخیص نمی‌دهد. ")
     .append("نتیجه را بعد از تنظیم با تراز مرجع کنترل کنید.");
    return s.toString();
  }
  private static void block(StringBuilder b,String label,long time,InstallerMath.Result r){
    b.append("\n--- ").append(label).append(" ---\n");
    if(time>0)b.append("زمان: ").append(new SimpleDateFormat("yyyy/MM/dd HH:mm",Locale.US).format(new Date(time))).append("\n");
    b.append("زاویه X: ").append(f(r.xDeg)).append("° | زاویه Y: ")
      .append(f(r.yDeg)).append("°\n");
    for(int i=0;i<4;i++)b.append(InstallerMath.LABELS[i]).append(": ")
      .append(InstallerMath.millimeters(r.raiseMm[i])).append(" میلی‌متر افزایش ارتفاع\n");
    b.append("بیشترین اختلاف ارتفاع تخمینی: ")
      .append(InstallerMath.millimeters(r.maxRaiseMm)).append(" میلی‌متر\n");
  }
}
