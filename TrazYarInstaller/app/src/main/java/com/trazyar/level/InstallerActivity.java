package com.trazyar.level;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.InputType;
import java.util.Locale;

/**
 * Dedicated guided install-assistant screen. Only reports mathematical
 * estimates for a rigid planar top. Before and after captures are independent.
 * Physical actuator motion cannot be guaranteed from the sensor reading.
 */
public final class InstallerActivity extends Activity implements SensorEventListener {
  static final int BG=0xff081A13,PANEL=0xff112A20,ACCENT=0xffB3EF76,
    GOLD=0xffE8C87D,WHITE=0xffEFF8EB,MUTED=0xffBED3B9;
  static final String PREFS="trazyar";
  static final String PREFIX="installerV29_";
  SensorManager manager;
  Sensor sensor;
  SharedPreferences prefs;
  boolean observed=false,ready=false,smoothing=false;
  final float[] gravity=new float[3];
  double rawX=0,rawY=0;
  long lastSample=0,lastMovement=0,firstSample=0;
  InstallerMath.Result before,after,preview;
  long beforeAt,afterAt;
  EditText nameEdit,widthEdit,depthEdit;
  TextView liveLabel,stepLabel,suggestionText,beforeText,afterText,reportSummary;
  TextView[] cornerLabels=new TextView[4];
  Button beforeBtn,afterBtn,shareBtn;
  final String[] labels=InstallerMath.LABELS;

  int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
  GradientDrawable shape(int bg,int border){
    GradientDrawable d=new GradientDrawable();d.setColor(bg);d.setCornerRadius(dp(15));
    if(border!=0)d.setStroke(dp(1),border);
    return d;
  }
  TextView text(String str,int size,int color){
    TextView t=new TextView(this);t.setText(str);t.setTextColor(color);
    t.setTextSize(size);t.setTextDirection(View.TEXT_DIRECTION_RTL);
    t.setGravity(Gravity.CENTER);
    t.setPadding(dp(6),dp(7),dp(6),dp(7));return t;
  }
  Button button(String label,boolean primary){
    Button b=new Button(this);b.setText(label);b.setAllCaps(false);
    b.setTextSize(14);b.setTextColor(primary?BG:WHITE);
    b.setBackground(shape(primary?ACCENT:0xff274A35,primary?0:0xff4B7860));
    return b;
  }
  void gap(LinearLayout l,int value){
    View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(value)));
  }
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
    prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
    manager=(SensorManager)getSystemService(Context.SENSOR_SERVICE);
    if(manager!=null){
      sensor=manager.getDefaultSensor(Sensor.TYPE_GRAVITY);
      if(sensor==null)sensor=manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }
    loadSession();
    buildUI();
    updateUI();
  }
  void loadSession(){
    boolean hasBefore=prefs.getBoolean(PREFIX+"beforeValid",false);
    if(!hasBefore)return;
    try{
      double width=prefs.getFloat(PREFIX+"width",60f);
      double depth=prefs.getFloat(PREFIX+"depth",60f);
      before=InstallerMath.compute(width,depth,
        prefs.getFloat(PREFIX+"beforeX",0),prefs.getFloat(PREFIX+"beforeY",0));
      beforeAt=prefs.getLong(PREFIX+"beforeAt",0);
      if(prefs.getBoolean(PREFIX+"afterValid",false)){
        after=InstallerMath.compute(width,depth,
          prefs.getFloat(PREFIX+"afterX",0),prefs.getFloat(PREFIX+"afterY",0));
        afterAt=prefs.getLong(PREFIX+"afterAt",0);
      }
    }catch(IllegalArgumentException ex){
      before=null;after=null;
    }
  }
  void buildUI(){
    ScrollView scroll=new ScrollView(this);
    scroll.setBackgroundColor(BG);scroll.setFillViewport(true);
    LinearLayout root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    root.setPadding(dp(14),dp(16),dp(14),dp(30));
    scroll.addView(root);setContentView(scroll);

    Button back=button("‹ برگشت به تراز یار",false);
    root.addView(back,new LinearLayout.LayoutParams(-1,dp(48)));
    back.setOnClickListener(v->finish());
    gap(root,8);
    Button installGuide=button("📚 آموزش تصویری نصاب‌یار + راهنمای گزارش",false);
    installGuide.setTextSize(15);
    root.addView(installGuide,new LinearLayout.LayoutParams(-1,dp(53)));
    installGuide.setOnClickListener(v->startActivity(
      new Intent(this,GuideCenterActivity.class).putExtra("guide_topic",GuideContent.INSTALLER)));
    gap(root,8);
    Button projectExamples=button("🎯 راهنمای پروژه‌های واقعی (کابینت، لباس‌شویی و ...)",false);
    projectExamples.setTextSize(13);
    root.addView(projectExamples,new LinearLayout.LayoutParams(-1,dp(49)));
    projectExamples.setOnClickListener(v->startActivity(
      new Intent(this,ProjectCoachActivity.class)));
    gap(root,10);
    TextView heading=text("▣ نصاب‌یار حرفه‌ای",27,ACCENT);
    heading.setTypeface(null,Typeface.BOLD);root.addView(heading);
    root.addView(text("تنظیم تقریبی چهار پایه • راهنمای کار • گزارش قبل و بعد",13,GOLD));
    gap(root,12);

    LinearLayout intro=panel(root);
    intro.addView(text("مرحله ۱ • وسیله و ابعاد را معرفی کنید",18,GOLD));
    intro.addView(text("گوشی را با صفحه رو به بالا روی بخش محکم و صاف وسیله بگذارید. بالای گوشی به سمت عقب و لبهٔ راست گوشی به سمت راست وسیله باشد. ابعاد بین چهار پایه را وارد کنید، نه ابعاد بیرونی بدنه.",14,WHITE));
    nameEdit=input(intro,"نام پروژه / وسیله",prefs.getString(PREFIX+"name",""),false);
    widthEdit=input(intro,"فاصلهٔ پایه‌های چپ و راست (سانتی‌متر)",
      String.format(Locale.US,"%.1f",prefs.getFloat(PREFIX+"width",60f)),true);
    depthEdit=input(intro,"فاصلهٔ پایه‌های جلو و عقب (سانتی‌متر)",
      String.format(Locale.US,"%.1f",prefs.getFloat(PREFIX+"depth",60f)),true);
    liveLabel=text("حسگر: منتظر خواندن اطلاعات گوشی...",14,MUTED);intro.addView(liveLabel);
    stepLabel=text("",14,GOLD);intro.addView(stepLabel);
    gap(root,10);

    LinearLayout scan=panel(root);
    scan.addView(text("مرحله ۲ • تنظیم پایه‌ها را حساب کنید",18,GOLD));
    scan.addView(text("گوشی را حداقل یک ثانیه ثابت نگه دارید. با دکمهٔ زیر، موقعیت «قبل از تنظیم» ثبت می‌شود؛ برای دریافت مقادیر جدید، دوباره اندازه‌گیری کنید.",13,MUTED));
    beforeBtn=button("۱. ثبت وضعیت قبل و محاسبهٔ چهار پایه",true);
    scan.addView(beforeBtn,new LinearLayout.LayoutParams(-1,dp(55)));
    beforeBtn.setOnClickListener(v->capture(false));
    gap(scan,9);
    suggestionText=text("مقادیر زیر بعد از ثبت «قبل از تنظیم» نمایش داده می‌شوند.",14,WHITE);
    scan.addView(suggestionText);
    LinearLayout rear=new LinearLayout(this),front=new LinearLayout(this);
    rear.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    front.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    rear.setOrientation(LinearLayout.HORIZONTAL);
    front.setOrientation(LinearLayout.HORIZONTAL);
    scan.addView(text("عقب وسیله  ↑",13,GOLD));
    scan.addView(rear,new LinearLayout.LayoutParams(-1,dp(88)));
    scan.addView(text("جلو وسیله  ↓",13,GOLD));
    scan.addView(front,new LinearLayout.LayoutParams(-1,dp(88)));
    // UI is laid out right to left; first cell is always the right-side foot.
    addFoot(rear,0);addFoot(rear,1);
    addFoot(front,2);addFoot(front,3);
    scan.addView(text("معنا: پیشنهاد افزایش ارتفاع هر پایه نسبت به بالاترین گوشه، نه میزان چرخاندن پیچ پایه. اعداد را به تدریج اجرا کنید؛ سپس دوباره تراز را کنترل کنید.",13,MUTED));
    gap(root,10);

    LinearLayout finish=panel(root);
    finish.addView(text("مرحله ۳ • نتیجهٔ بعد از تنظیم را ثبت کنید",18,GOLD));
    finish.addView(text("پس از تنظیم پایه‌ها، گوشی را دقیقاً با جهت قبلی روی همان سطح بگذارید. اندازه‌گیری دوم را بگیرید تا اختلاف قبل/بعد مقایسه شود.",13,WHITE));
    afterBtn=button("۲. ثبت وضعیت بعد از تنظیم",true);
    finish.addView(afterBtn,new LinearLayout.LayoutParams(-1,dp(54)));
    afterBtn.setOnClickListener(v->capture(true));
    gap(finish,8);
    beforeText=text("قبل: ثبت نشده",14,WHITE);finish.addView(beforeText);
    afterText=text("بعد: ثبت نشده",14,WHITE);finish.addView(afterText);
    reportSummary=text("",14,GOLD);finish.addView(reportSummary);
    gap(root,12);

    LinearLayout sharePanel=panel(root);
    sharePanel.addView(text("مرحله ۴ • گزارش کار را تحویل دهید",18,GOLD));
    Button report=button("↗ اشتراک گزارش قبل و بعد",true);
    sharePanel.addView(report,new LinearLayout.LayoutParams(-1,dp(55)));
    shareBtn=report;
    shareBtn.setOnClickListener(v->shareReport());
    Button journal=button("▤ ثبت در دفترچهٔ تراز یار",false);
    sharePanel.addView(journal,new LinearLayout.LayoutParams(-1,dp(51)));
    journal.setOnClickListener(v->addToJournal());
    Button reset=button("↺ شروع پروژه / اندازه‌گیری تازه",false);
    sharePanel.addView(reset,new LinearLayout.LayoutParams(-1,dp(51)));
    reset.setOnClickListener(v->confirmReset());
    sharePanel.addView(text("این محاسبات فقط روی سطح صلب و تخت و با هم‌راستایی گوشی معتبرترند. تاب سطح، لق‌بودن بدنه و مقدار واقعی چرخش پیچ هر پایه با حسگر گوشی سنجیده نمی‌شوند؛ برای نصب حساس با تراز مرجع کنترل کنید.",12,MUTED));
    gap(root,18);
    root.addView(text("تراز یار ۳٫۱ • بدون اینترنت • بوق فقط در تراز حبابی",12,GOLD));
    if(sensor==null){
      liveLabel.setText("این گوشی حسگر گرانش/شتاب‌سنج قابل استفاده ندارد.");
      beforeBtn.setEnabled(false);afterBtn.setEnabled(false);
    }
  }
  LinearLayout panel(LinearLayout root){
    LinearLayout card=new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    card.setPadding(dp(12),dp(12),dp(12),dp(12));
    card.setBackground(shape(PANEL,0xff385E46));
    root.addView(card,new LinearLayout.LayoutParams(-1,-2));
    return card;
  }
  EditText input(LinearLayout parent,String hint,String original,boolean number){
    TextView label=text(hint,14,GOLD);
    label.setGravity(Gravity.RIGHT);parent.addView(label);
    EditText value=new EditText(this);
    value.setSingleLine(true);value.setText(original);
    value.setTextColor(WHITE);value.setHintTextColor(MUTED);
    value.setTextSize(16);
    value.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    if(number)value.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
    parent.addView(value,new LinearLayout.LayoutParams(-1,dp(49)));
    return value;
  }
  void addFoot(LinearLayout row,int idx){
    TextView v=text("",14,WHITE);cornerLabels[idx]=v;
    v.setBackground(shape(0xff24452F,0xff5B8965));
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);
    p.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(v,p);
  }
  @Override protected void onResume(){
    super.onResume();getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    ready=true;observed=false;smoothing=false;firstSample=0;
    if(manager!=null&&sensor!=null)manager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_UI);
  }
  @Override protected void onPause(){
    ready=false;
    if(manager!=null)manager.unregisterListener(this);
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    super.onPause();
  }
  @Override public void onSensorChanged(SensorEvent e){
    if(!ready||e.sensor!=sensor||e.values.length<3)return;
    long now=SystemClock.elapsedRealtime();
    if(!smoothing){
      for(int i=0;i<3;i++)gravity[i]=e.values[i];
      smoothing=true;firstSample=now;lastMovement=now;
    }else{
      for(int i=0;i<3;i++)gravity[i]=.82f*gravity[i]+.18f*e.values[i];
    }
    double denominator=Math.max(.0001,Math.abs(gravity[2]));
    double newX=Math.toDegrees(Math.atan2(gravity[0],denominator));
    double newY=Math.toDegrees(Math.atan2(gravity[1],denominator));
    if(observed&&(Math.abs(rawX-newX)>.23||Math.abs(rawY-newY)>.23))lastMovement=now;
    rawX=newX;rawY=newY;observed=true;lastSample=now;
    if(liveLabel!=null)liveLabel.setText(String.format(Locale.US,
      "● حسگر فعال | X: %+.2f°   Y: %+.2f°",adjustedX(),adjustedY()));
    if(stepLabel!=null){
      boolean stable=now-firstSample>=900&&now-lastMovement>=750;
      stepLabel.setText(stable?"✓ گوشی ثابت است؛ آمادهٔ ثبت":
        "گوشی را ثابت نگه دارید و حسگر را آرام کنید...");
    }
  }
  @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
  double adjustedX(){
    return rawX-(prefs.getBoolean("calibrationCompleteV28",false)?
      prefs.getFloat("calibrationZeroX",0):0);
  }
  double adjustedY(){
    return rawY-(prefs.getBoolean("calibrationCompleteV28",false)?
      prefs.getFloat("calibrationZeroY",0):0);
  }
  InstallerMath.Result read(){
    if(!observed||!ready||SystemClock.elapsedRealtime()-lastSample>1800)
      throw new IllegalArgumentException("اطلاعات حسگر تازه نیست. گوشی را روی وسیله قرار دهید.");
    if(SystemClock.elapsedRealtime()-firstSample<900||
       SystemClock.elapsedRealtime()-lastMovement<750)
      throw new IllegalArgumentException("گوشی هنوز حرکت می‌کند؛ آن را ثابت نگه دارید و دوباره تلاش کنید.");
    double width=InstallerMath.parseCentimeters(widthEdit.getText().toString());
    double depth=InstallerMath.parseCentimeters(depthEdit.getText().toString());
    return InstallerMath.compute(width,depth,adjustedX(),adjustedY());
  }
  void capture(boolean second){
    try{
      InstallerMath.Result r=read();
      if(second){
        if(before==null){
          ToastMsg("ابتدا وضعیت «قبل از تنظیم» را ثبت کنید.");return;
        }
        if(Math.abs(before.widthCm-r.widthCm)>.0001||Math.abs(before.depthCm-r.depthCm)>.0001)
          throw new IllegalArgumentException("ابعاد پروژه تغییر کرده‌اند؛ همان عرض و عمق مرحلهٔ قبل را وارد کنید.");
        after=r;afterAt=System.currentTimeMillis();
      }else{
        before=r;beforeAt=System.currentTimeMillis();
        after=null;afterAt=0;
      }
      persistSession();updateUI();
      if(second){
        new AlertDialog.Builder(this).setTitle("اندازه‌گیری بعد ثبت شد")
          .setMessage(String.format(Locale.US,
            "بیشترین اختلاف قبل: %.1f میلی‌متر\nبعد: %.1f میلی‌متر\nحالا گزارش قابل اشتراک‌گذاری است.",
            before.maxRaiseMm,after.maxRaiseMm))
          .setPositiveButton("بسیار خوب",null).show();
      }else{
        ToastMsg("مقادیر تقریبی هر چهار گوشه محاسبه شد.");
      }
    }catch(IllegalArgumentException ex){
      new AlertDialog.Builder(this).setTitle("ثبت ممکن نیست")
        .setMessage(ex.getMessage()!=null&&ex.getMessage().contains("invalid")?
          "ابعاد باید عددی مثبت و حداکثر ۱۰۰۰ سانتی‌متر باشند؛ سطح هم باید تقریباً افقی باشد (تا ۱۵ درجه).":
          ex.getMessage()).setPositiveButton("متوجه شدم",null).show();
    }
  }
  void persistSession(){
    SharedPreferences.Editor edit=prefs.edit()
      .putString(PREFIX+"name",nameEdit.getText().toString().trim())
      .putBoolean(PREFIX+"beforeValid",before!=null)
      .putBoolean(PREFIX+"afterValid",after!=null);
    if(before!=null){
      edit.putFloat(PREFIX+"width",(float)before.widthCm)
        .putFloat(PREFIX+"depth",(float)before.depthCm)
        .putFloat(PREFIX+"beforeX",(float)before.xDeg)
        .putFloat(PREFIX+"beforeY",(float)before.yDeg)
        .putLong(PREFIX+"beforeAt",beforeAt);
    }
    if(after!=null){
      edit.putFloat(PREFIX+"afterX",(float)after.xDeg)
        .putFloat(PREFIX+"afterY",(float)after.yDeg)
        .putLong(PREFIX+"afterAt",afterAt);
    }
    edit.apply();
  }
  void updateUI(){
    if(beforeBtn==null)return;
    beforeBtn.setText(before==null?"۱. ثبت وضعیت قبل و محاسبهٔ چهار پایه":
      "↺ اندازه‌گیری مجدد قبل از تنظیم");
    afterBtn.setEnabled(before!=null);
    shareBtn.setEnabled(before!=null);
    if(before==null){
      suggestionText.setText("ابتدا اندازه‌گیری «قبل» را ثبت کنید.");
      for(int i=0;i<4;i++)cornerLabels[i].setText(labels[i]+"\n—");
      beforeText.setText("قبل: ثبت نشده");
      afterText.setText("بعد: ثبت نشده");
      reportSummary.setText("");
      return;
    }
    suggestionText.setText(String.format(Locale.US,
      "پیشنهاد تنظیم هر پایه (افزایش ارتفاع) — از صفر تا %.1f میلی‌متر:",
      before.maxRaiseMm));
    for(int i=0;i<4;i++){
      cornerLabels[i].setText(labels[i]+"\n"+
        InstallerMath.millimeters(before.raiseMm[i])+" میلی‌متر"+
        (before.raiseMm[i]<.05?"\n(مرجع)":"\n↑ بالا"));
    }
    beforeText.setText(String.format(Locale.US,
      "قبل: X=%+.2f° | Y=%+.2f° | اختلاف گوشه‌ها=%.1f میلی‌متر",
      before.xDeg,before.yDeg,before.maxRaiseMm));
    if(after==null){
      afterText.setText("بعد: هنوز ثبت نشده");
      reportSummary.setText("قدم بعد: پایه‌ها را به‌آرامی تنظیم کنید، سپس وضعیت بعد را بگیرید.");
    }else{
      afterText.setText(String.format(Locale.US,
        "بعد: X=%+.2f° | Y=%+.2f° | اختلاف گوشه‌ها=%.1f میلی‌متر",
        after.xDeg,after.yDeg,after.maxRaiseMm));
      double gain=before.maxRaiseMm-after.maxRaiseMm;
      reportSummary.setText(String.format(Locale.US,
        "تغییر نسبت به قبل: %+.1f میلی‌متر در بیشترین اختلاف\n%s",
        -gain,gain>=0?"✓ اختلاف گوشه‌ها کمتر شده است":"● اختلاف بیشتر شده؛ تنظیم را بازبینی کنید."));
    }
  }
  void shareReport(){
    if(before==null){ToastMsg("ابتدا نتیجهٔ قبل را ثبت کنید.");return;}
    String report=InstallerReport.build(
      nameEdit.getText().toString().trim(),beforeAt,before,afterAt,after);
    Intent send=new Intent(Intent.ACTION_SEND);
    send.setType("text/plain");
    send.putExtra(Intent.EXTRA_TEXT,report);
    try{startActivity(Intent.createChooser(send,"ارسال گزارش نصاب‌یار"));}
    catch(Exception ex){ToastMsg("برنامه‌ای برای اشتراک‌گذاری پیدا نشد.");}
  }
  void addToJournal(){
    if(before==null){ToastMsg("ابتدا وضعیت قبل را ثبت کنید.");return;}
    String project=nameEdit.getText().toString().trim();
    String report=InstallerReport.build(project,beforeAt,before,afterAt,after);
    String summary=String.format(Locale.US,"قبل %.1f mm / بعد %s",
      before.maxRaiseMm,after==null?"ثبت نشده":String.format(Locale.US,"%.1f mm",after.maxRaiseMm));
    MeasurementLog.save(this,project,"نصاب‌یار چهارپایه",summary,report);
    ToastMsg("گزارش در دفترچهٔ تراز یار ثبت شد.");
  }
  void confirmReset(){
    new AlertDialog.Builder(this)
      .setTitle("پروژهٔ جدید")
      .setMessage("اندازه‌گیری قبل و بعد این پروژه پاک و آمادهٔ ثبت پروژهٔ جدید شود؟ گزارش‌های ذخیره‌شده در دفترچه باقی می‌مانند.")
      .setNegativeButton("انصراف",null)
      .setPositiveButton("شروع تازه",(dialog,which)->{
        before=null;after=null;beforeAt=0;afterAt=0;
        prefs.edit().remove(PREFIX+"beforeX").remove(PREFIX+"beforeY")
          .remove(PREFIX+"afterX").remove(PREFIX+"afterY")
          .remove(PREFIX+"beforeAt").remove(PREFIX+"afterAt")
          .putBoolean(PREFIX+"beforeValid",false)
          .putBoolean(PREFIX+"afterValid",false).apply();
        updateUI();
      }).show();
  }
  void ToastMsg(String msg){android.widget.Toast.makeText(this,msg,android.widget.Toast.LENGTH_LONG).show();}
}
