package com.trazyar.level;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.app.AlertDialog;
import android.text.InputType;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;
import java.util.Locale;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends Activity implements SensorEventListener {
  static final int BG=0xff081A13, PANEL=0xff112A20, GREEN=0xffA8ED65, GOLD=0xffE8C87D, TEXT=0xffF1F9E9, MUTED=0xffA7BCAE;
  SensorManager manager; Sensor sensor; Vibrator vibrator;
  final BeepEngine beeper=new BeepEngine();
  boolean measuring=false,resumed=false,observed=false,lowpassInit=false;
  boolean haptic=true,beepEnabled=true,nearArmed=true,tutorialOpen=false,readingFrozen=false,calibrationComplete=false;
  int calibrationStage=0;
  double firstCalX=0,firstCalY=0;
  long firstCalAt=0;
  float beepVolume=.70f;
  long previewUntil=0;
  double previewError=12;
  boolean previewSteady=false;
  int measurementMode=0;
  double tiltDegrees=0, slopePercent=0, edgeAngle=0, referenceAngle=Double.NaN, squareAngle=0;
  boolean edgeAngleValid=false;
  final float[] gv=new float[3];
  double zeroX=0,zeroY=0,rawX=0,rawY=0,x=0,y=0,tolerance=.5;
  int state=-1, candidate=-1;
  long candidateAt=0,lastReading=0,lastSensorUi=0;
  SharedPreferences prefs;
  TextView status, angles, directions, resultValue, resultDescription, modeHint, sensorStatus, beepStatus, calibrationStatus;
  Button start, calibrate, captureReference, holdButton, saveButton, historyButton;
  final Button[] toolButtons=new Button[4];
  LevelDrawing drawing; MeasurementGauge meter;
  final Handler handler=new Handler(Looper.getMainLooper());
  int px(float dp){return Math.round(dp*getResources().getDisplayMetrics().density);}
  GradientDrawable background(int fill,int border,int r) { GradientDrawable g=new GradientDrawable(); g.setColor(fill);g.setCornerRadius(px(r));if(border!=0)g.setStroke(px(1),border);return g; }
  TextView text(String s,int sp,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setGravity(Gravity.CENTER);return t;}
  Button button(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(16);b.setTextColor(primary?BG:TEXT);b.setBackground(background(primary?GREEN:0xff284936,primary?0:0xff557D59,14));return b;}
  void gap(LinearLayout l,int dp){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,px(dp)));}
  @Override public void onCreate(Bundle b) {
    super.onCreate(b); getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
    prefs=getSharedPreferences("trazyar",MODE_PRIVATE);
    tolerance=prefs.getFloat("tol",.5f);
    measurementMode=Math.max(0,Math.min(3,prefs.getInt("measurementMode",0)));
    haptic=prefs.getBoolean("haptic",true);
    beepEnabled=prefs.getBoolean("beepEnabled",true);
    beepVolume=prefs.getFloat("beepVolume",.70f);
    calibrationComplete=prefs.getBoolean("calibrationCompleteV28",false);
    zeroX=calibrationComplete?prefs.getFloat("calibrationZeroX",0):0;
    zeroY=calibrationComplete?prefs.getFloat("calibrationZeroY",0):0;
    manager=(SensorManager)getSystemService(Context.SENSOR_SERVICE);
    vibrator=(Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
    if(manager!=null){sensor=manager.getDefaultSensor(Sensor.TYPE_GRAVITY);if(sensor==null)sensor=manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);}
    buildUI();
    if(sensor==null){start.setEnabled(false);status.setText("حسگر مناسب پیدا نشد");sensorStatus.setText("این گوشی حسگر شتاب/گرانش مورد نیاز را ندارد.");}
    else startMeasure(); // Begin sensor measurement automatically; tool tabs remain one-tap.
    if(getIntent()!=null){
      int fromGuide=getIntent().getIntExtra("guide_tool",-1);
      if(fromGuide>=0&&fromGuide<4)selectMode(fromGuide);
      else if(getIntent().getBooleanExtra("guide_journal",false))
        handler.postDelayed(()->showHistory(),350);
    }
    // The app introduction is shown once after this release, not at every launch.
    if(!prefs.getBoolean("guideIntroSeenV30",false)){
      prefs.edit().putBoolean("guideIntroSeenV30",true).apply();
      handler.postDelayed(()->{
        if(!isFinishing()&&!isDestroyed())
          startActivity(new Intent(this,GuideCenterActivity.class).putExtra("guide_topic",GuideContent.INTRO));
      },850);
    }
    handler.post(new Runnable(){public void run() {
      long now=SystemClock.elapsedRealtime();
      if(resumed&&measuring&&!readingFrozen&&observed&&now-lastReading>3000){
        observed=false;state=-1;status.setText("ارتباط حسگر قطع شده");
        sensorStatus.setText("حسگر: دادهٔ جدیدی دریافت نمی‌شود");refreshBeep();
      }
      handler.postDelayed(this,500);
    }});
  }
  @Override protected void onNewIntent(Intent incoming){
    super.onNewIntent(incoming);
    setIntent(incoming);
    int tool=incoming.getIntExtra("guide_tool",-1);
    if(tool>=0&&tool<4)selectMode(tool);
    else if(incoming.getBooleanExtra("guide_journal",false))
      handler.postDelayed(()->showHistory(),300);
  }
  void buildUI(){
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(px(16),px(18),px(16),px(30));root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);scroll.addView(root);setContentView(scroll);
    TextView brand=text("◉   تراز یار",30,GREEN);brand.setTypeface(null,1);root.addView(brand);
    root.addView(text("همیشه در سطح درست  •  TRAZYAR",12,GOLD));gap(root,8);
    TextView appIntro=text("تراز یار، دستیار فارسی و آفلاینِ ترازکردن، سنجش شیب و زاویه و راهنمای تنظیم پایه‌هاست. با چند لمس، از اندازه‌گیری به نتیجه و گزارش برسید.",13,TEXT);
    appIntro.setPadding(px(9),px(12),px(9),px(12));
    appIntro.setBackground(background(PANEL,0xff376D50,13));
    root.addView(appIntro);
    gap(root,9);
    Button guideEntry=button("📚 معرفی برنامه و مرکز آموزش کامل",false);
    guideEntry.setTextSize(16);
    root.addView(guideEntry,new LinearLayout.LayoutParams(-1,px(56)));
    guideEntry.setOnClickListener(v->startActivity(new Intent(this,GuideCenterActivity.class)));
    gap(root,9);
    Button projectCoach=button("🎯 از نوع کارتان شروع کنید • راهنمای پروژه‌محور",true);
    projectCoach.setTextSize(15);
    root.addView(projectCoach,new LinearLayout.LayoutParams(-1,px(56)));
    projectCoach.setOnClickListener(v->startActivity(new Intent(this,ProjectCoachActivity.class)));
    gap(root,12);
    Button installAssistant=button("▣ نصاب‌یار حرفه‌ای  •  تنظیم چهار پایه و گزارش",true);
    installAssistant.setTextSize(15);
    root.addView(installAssistant,new LinearLayout.LayoutParams(-1,px(64)));
    installAssistant.setOnClickListener(v->startActivity(new Intent(this,InstallerActivity.class)));
    TextView installerHint=text("اندازهٔ دقیق وسیله را بدهید؛ چهار گوشه، راهنمای نصب و گزارش قبل/بعد بگیرید.",12,MUTED);
    root.addView(installerHint);gap(root,12);
    Button helpButton=button("📖 فهرست آموزش همهٔ بخش‌های برنامه",false);
    root.addView(helpButton,new LinearLayout.LayoutParams(-1,px(52)));
    helpButton.setOnClickListener(v->startActivity(new Intent(this,GuideCenterActivity.class)));
    gap(root,9);
    TextView quickHelpTitle=text("آموزش مستقیم ابزارها",16,GOLD);
    quickHelpTitle.setTypeface(null,1);
    root.addView(quickHelpTitle);
    // Always-visible, one-tap help shortcuts; no need to find the next page in a long dialog.
    String[] helpNames={"آموزش تراز حبابی","آموزش شیب‌سنج","آموزش زاویه‌سنج","آموزش گونیا"};
    for(int helpRow=0;helpRow<2;helpRow++){
      LinearLayout shortcutRow=new LinearLayout(this);
      shortcutRow.setOrientation(LinearLayout.HORIZONTAL);
      shortcutRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
      root.addView(shortcutRow,new LinearLayout.LayoutParams(-1,px(50)));
      for(int col=0;col<2;col++){
        final int tool=helpRow*2+col;
        Button shortcut=button("؟ "+helpNames[tool],false);
        shortcut.setTextSize(13);
        shortcut.setMinWidth(0);
        shortcut.setPadding(px(2),0,px(2),0);
        LinearLayout.LayoutParams hparams=new LinearLayout.LayoutParams(0,px(45),1);
        hparams.setMargins(px(3),px(2),px(3),px(2));
        shortcutRow.addView(shortcut,hparams);
        shortcut.setOnClickListener(v->startActivity(
          new Intent(this,GuideCenterActivity.class).putExtra("guide_topic",GuideContent.forTool(tool))));
      }
    }
    gap(root,13);
    LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(px(11),px(16),px(11),px(17));panel.setBackground(background(PANEL,0xff406E50,23));root.addView(panel);
    TextView toolTitle=text("ابزار را انتخاب کنید",17,GOLD);toolTitle.setTypeface(null,1);panel.addView(toolTitle);
    String[] shortNames={"◉ تراز حبابی","↗ شیب‌سنج","∠ زاویه‌سنج","□ گونیا"};
    for(int rowNum=0;rowNum<2;rowNum++){
      LinearLayout tabRow=new LinearLayout(this);
      tabRow.setOrientation(LinearLayout.HORIZONTAL);
      tabRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
      panel.addView(tabRow,new LinearLayout.LayoutParams(-1,px(58)));
      for(int col=0;col<2;col++){
        int mode=rowNum*2+col;
        Button tab=button(shortNames[mode],false);
        tab.setTextSize(15);
        tab.setMinWidth(0);
        tab.setPadding(px(2),0,px(2),0);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,px(53),1);
        lp.setMargins(px(3),px(2),px(3),px(2));
        tabRow.addView(tab,lp);
        toolButtons[mode]=tab;
        tab.setOnClickListener(v->selectMode(mode));
      }
    }
    modeHint=text("",13,MUTED);
    panel.addView(modeHint);
    gap(panel,7);
    status=text("آمادهٔ اندازه‌گیری",19,GOLD);status.setTypeface(null,1);panel.addView(status);
    sensorStatus=text("حسگر: در انتظار دریافت داده",12,MUTED);
    panel.addView(sensorStatus);
    beepStatus=text("بوق راهنما: در انتظار سنجش",13,GOLD);
    panel.addView(beepStatus);

    LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
    row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    panel.addView(row,new LinearLayout.LayoutParams(-1,px(56)));
    start=button("▶ شروع سنجش",true);
    LinearLayout.LayoutParams sparams=new LinearLayout.LayoutParams(0,-1,1);sparams.setMargins(px(3),0,px(3),0);
    row.addView(start,sparams);
    calibrate=button("⊕ کالیبراسیون",false);calibrate.setEnabled(false);calibrate.setTextSize(13);
    LinearLayout.LayoutParams cparams=new LinearLayout.LayoutParams(0,-1,1);cparams.setMargins(px(3),0,px(3),0);
    row.addView(calibrate,cparams);
    start.setOnClickListener(v->{if(measuring)stopMeasure();else startMeasure();});
    calibrate.setOnClickListener(v->calibrateStep());
    gap(panel,7);
    calibrationStatus=text("",12,GOLD);
    panel.addView(calibrationStatus);
    updateCalibrationLabel();
    gap(panel,9);

    gap(panel,12);
    drawing=new LevelDrawing(this);panel.addView(drawing,new LinearLayout.LayoutParams(-1,px(410)));
    meter=new MeasurementGauge(this);panel.addView(meter,new LinearLayout.LayoutParams(-1,px(290)));
    resultValue=text("—",31,GREEN);resultValue.setTypeface(null,1);panel.addView(resultValue);
    resultDescription=text("",14,MUTED);panel.addView(resultDescription);
    captureReference=button("📐 ثبت ضلع اول به‌عنوان مرجع",false);
    panel.addView(captureReference,new LinearLayout.LayoutParams(-1,px(56)));
    captureReference.setOnClickListener(v->{
      if(!measuring||!observed||!edgeAngleValid){Toast.makeText(this,"گوشی را عمودی نگه دارید و سنجش را شروع کنید",Toast.LENGTH_LONG).show();return;}
      referenceAngle=edgeAngle;state=-1;candidate=-1;candidateAt=0;nearArmed=true;
      Toast.makeText(this,"مرجع ثبت شد؛ لبهٔ گوشی را روی ضلع دوم در همان صفحه قرار دهید.",Toast.LENGTH_LONG).show();
      refreshMeasureUI();
    });
    angles=text("X:  --.-°       Y:  --.-°",18,TEXT);panel.addView(angles);
    directions=text("گوشی را با صفحهٔ رو به بالا روی سطح بگذارید.",13,MUTED);
    panel.addView(directions);gap(panel,10);

    TextView historyTitle=text("مدیریت اندازه‌گیری‌ها",15,GOLD);
    historyTitle.setTypeface(null,1);panel.addView(historyTitle);
    LinearLayout workRow=new LinearLayout(this);
    workRow.setOrientation(LinearLayout.HORIZONTAL);
    workRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    panel.addView(workRow,new LinearLayout.LayoutParams(-1,px(54)));
    String[] workLabels={"❚❚ قفل عدد","＋ ذخیره نتیجه","▤ دفترچه"};
    for(int i=0;i<3;i++){
      Button b=button(workLabels[i],false);
      b.setTextSize(12);b.setMinWidth(0);b.setPadding(px(1),0,px(1),0);
      LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,px(50),1);
      bp.setMargins(px(2),0,px(2),0);workRow.addView(b,bp);
      if(i==0)holdButton=b;
      if(i==1)saveButton=b;
      if(i==2)historyButton=b;
    }
    holdButton.setOnClickListener(v->toggleFreeze());
    saveButton.setOnClickListener(v->saveCurrentMeasurement());
    historyButton.setOnClickListener(v->showHistory());
    panel.addView(text("عدد را قفل کنید، با نام پروژه ذخیره کنید و گزارش را به اشتراک بگذارید.",12,MUTED));
    gap(panel,13);
    updateModeUI();

    gap(root,16);
    LinearLayout opt=new LinearLayout(this);
    opt.setOrientation(1);opt.setPadding(px(16),px(13),px(16),px(16));
    opt.setBackground(background(PANEL,0xff3A6849,20));root.addView(opt);
    TextView beepTitle=text("🔔 راهنمای صوتی بوقی",19,GREEN);
    beepTitle.setTypeface(null,1);opt.addView(beepTitle);
    opt.addView(text("فقط تراز حبابی: دور از مرکز بوق کوتاه، نزدیک مرکز سریع‌تر و بلندتر، در مرکز بوق ممتد",13,MUTED));
    Switch toneToggle=new Switch(this);
    toneToggle.setText("صدای بوق روشن باشد");toneToggle.setTextSize(16);
    toneToggle.setTextColor(TEXT);toneToggle.setChecked(beepEnabled);opt.addView(toneToggle);
    toneToggle.setOnCheckedChangeListener((sw,on)->{
      beepEnabled=on;prefs.edit().putBoolean("beepEnabled",on).apply();refreshBeep();
    });
    TextView volTitle=text("بلندی صدای بوق: "+Math.round(beepVolume*100)+"٪",15,GOLD);opt.addView(volTitle);
    SeekBar volSeek=new SeekBar(this);volSeek.setMax(100);volSeek.setProgress(Math.round(beepVolume*100));opt.addView(volSeek);
    volSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar sb,int progress,boolean fromUser){
        beepVolume=progress/100f;volTitle.setText("بلندی صدای بوق: "+progress+"٪");
        prefs.edit().putFloat("beepVolume",beepVolume).apply();refreshBeep();
      }
      public void onStartTrackingTouch(SeekBar sb){}
      public void onStopTrackingTouch(SeekBar sb){}
    });
    LinearLayout previews=new LinearLayout(this);previews.setOrientation(LinearLayout.HORIZONTAL);
    previews.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    opt.addView(previews,new LinearLayout.LayoutParams(-1,px(50)));
    String[] labels={"دور","نزدیک","ممتد"};
    for(int i=0;i<3;i++){
      final int sample=i;
      Button b=button(labels[i],false);b.setTextSize(13);
      LinearLayout.LayoutParams pa=new LinearLayout.LayoutParams(0,-1,1);
      pa.setMargins(px(2),0,px(2),0);previews.addView(b,pa);
      b.setOnClickListener(v->previewTone(sample));
    }
    opt.addView(text("صدای رسانهٔ گوشی را روشن کنید. بوق فقط در تراز حبابی فعال است؛ شیب‌سنج، زاویه‌سنج و گونیا کاملاً بی‌صدا هستند.",12,MUTED));
    Switch hapticToggle=new Switch(this);
    hapticToggle.setText("لرزش نزدیک تراز و هنگام تراز کامل");hapticToggle.setTextSize(15);
    hapticToggle.setTextColor(TEXT);hapticToggle.setChecked(haptic);opt.addView(hapticToggle);
    hapticToggle.setOnCheckedChangeListener((sw,on)->{
      haptic=on;prefs.edit().putBoolean("haptic",on).apply();
    });
    gap(root,14);
    LinearLayout settings=new LinearLayout(this);settings.setOrientation(1);settings.setPadding(px(16),px(13),px(16),px(14));settings.setBackground(background(PANEL,0xff3A6849,20));root.addView(settings);
    TextView sens=text(String.format(Locale.US,"حساسیت: ±%.1f°",tolerance),17,GREEN);settings.addView(sens);
    SeekBar seek=new SeekBar(this);seek.setMax(18);seek.setProgress((int)Math.round((tolerance-.2)*10));settings.addView(seek);
    settings.addView(text("دقیق‌تر ۰٫۲ درجه             آسان‌تر ۲ درجه",12,MUTED));
    seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar bar,int n,boolean fromUser){tolerance=.2+n*.1;sens.setText(String.format(Locale.US,"حساسیت: ±%.1f°",tolerance));prefs.edit().putFloat("tol",(float)tolerance).apply();candidate=-1;candidateAt=0;}
      public void onStartTrackingTouch(SeekBar bar){} public void onStopTrackingTouch(SeekBar bar){}
    });
    gap(settings,10);
    Button resetCal=button("↺ پاک کردن کالیبراسیون ذخیره‌شده",false);
    resetCal.setTextSize(13);settings.addView(resetCal);
    resetCal.setOnClickListener(v->new AlertDialog.Builder(this)
      .setTitle("بازنشانی کالیبراسیون")
      .setMessage("مقادیر تصحیح دو محوره پاک شوند؟ اندازه‌گیری‌های دفترچه حذف نمی‌شوند.")
      .setNegativeButton("انصراف",null)
      .setPositiveButton("بازنشانی",(d,which)->{
        calibrationStage=0;calibrationComplete=false;zeroX=0;zeroY=0;
        prefs.edit().remove("calibrationZeroX").remove("calibrationZeroY").putBoolean("calibrationCompleteV28",false).apply();
        updateCalibrationLabel();
        if(measurementMode==0&&observed)updateLevel();
        refreshBeep();
      }).show());
    gap(root,12);root.addView(text("زاویه‌سنج و گونیا: گوشی را با صفحهٔ قائم نگه دارید. در گونیا، ضلع اول را ثبت کنید و برای ضلع دوم، گوشی را در همان صفحه بچرخانید. چرخش روی میز افقی با حسگر گرانش اندازه‌گیری نمی‌شود.",12,MUTED));
    gap(root,12);root.addView(text("تراز یار • نسخهٔ ۳٫۱ • آموزش پروژه‌محور + معرفی کامل",11,GOLD));
  }
  void openTutorial(int firstPage){
    if(tutorialOpen)return;
    tutorialOpen=true;
    previewUntil=0;
    beeper.update(false,20,tolerance,false,beepVolume);
    HelpDialog.show(this,firstPage,
      mode->{
        tutorialOpen=false;
        selectMode(mode);
      },
      ()->{
        tutorialOpen=false;
        refreshBeep();
      }
    );
  }
  void updateCalibrationLabel(){
    if(calibrationStatus==null)return;
    if(calibrationStage==1){
      calibrationStatus.setText("مرحله ۲ از ۲: گوشی را روی همان سطح، ۱۸۰° در جهت صفحه بچرخانید؛ دوباره «کالیبراسیون» را بزنید.");
      if(calibrate!=null)calibrate.setText("ثبت وضعیت دوم");
    }else{
      calibrationStatus.setText(calibrationComplete?
        "✓ تصحیح دوحالته ذخیره شده و پس از بستن برنامه نیز حفظ می‌شود":
        "کالیبراسیون دوحالته: روی یک سطح تقریباً افقی، دو جهت مخالف گوشی را ثبت کنید.");
      if(calibrate!=null)calibrate.setText("⊕ کالیبراسیون");
    }
  }
  void calibrateStep(){
    if(measurementMode!=0||!measuring||!observed||readingFrozen)return;
    // The two observations must be taken with the DISPLAY FACING UP, in opposite
    // compass orientations. Gravity alone cannot verify a yaw rotation.
    if(Math.max(Math.abs(rawX),Math.abs(rawY))>12){
      Toast.makeText(this,"برای کالیبراسیون، گوشی را روی سطح تقریباً افقی قرار دهید.",Toast.LENGTH_LONG).show();
      return;
    }
    if(calibrationStage==0){
      firstCalX=rawX;firstCalY=rawY;
      firstCalAt=SystemClock.elapsedRealtime();calibrationStage=1;updateCalibrationLabel();
      new AlertDialog.Builder(this)
        .setTitle("مرحله اول ثبت شد")
        .setMessage("گوشی را بدون پشت‌ورو کردن، روی همان سطح به اندازهٔ ۱۸۰ درجه بچرخانید تا بالای گوشی جای پایین آن قرار بگیرد. سپس «ثبت وضعیت دوم» را لمس کنید. برنامه چرخش واقعی را با شتاب‌سنج به‌تنهایی تشخیص نمی‌دهد.")
        .setPositiveButton("متوجه شدم",null).show();
      return;
    }
    if(SystemClock.elapsedRealtime()-firstCalAt<1200){
      Toast.makeText(this,"پس از چرخش ۱۸۰ درجه و ثابت شدن گوشی، وضعیت دوم را ثبت کنید.",Toast.LENGTH_LONG).show();
      return;
    }
    double newX=CalibrationMath.zeroBias(firstCalX,rawX);
    double newY=CalibrationMath.zeroBias(firstCalY,rawY);
    if(!Double.isFinite(newX)||!Double.isFinite(newY)){
      calibrationStage=0;updateCalibrationLabel();return;
    }
    zeroX=newX;zeroY=newY;calibrationComplete=true;calibrationStage=0;
    prefs.edit()
      .putFloat("calibrationZeroX",(float)zeroX)
      .putFloat("calibrationZeroY",(float)zeroY)
      .putBoolean("calibrationCompleteV28",true).apply();
    state=-1;candidate=-1;candidateAt=0;
    updateCalibrationLabel();
    updateLevel();refreshBeep();
    new AlertDialog.Builder(this)
      .setTitle("کالیبراسیون ذخیره شد")
      .setMessage("تصحیح هر دو محور ثبت شد و در اجرای بعدی هم باقی می‌ماند. برای اطمینان از دقت، روی یک سطح مرجع واقعی امتحان کنید.")
      .setPositiveButton("تأیید",null).show();
  }
  void toggleFreeze(){
    if(!measuring||!observed){
      Toast.makeText(this,"ابتدا سنجش را شروع کنید و منتظر دادهٔ حسگر بمانید.",Toast.LENGTH_SHORT).show();return;
    }
    readingFrozen=!readingFrozen;
    if(holdButton!=null)holdButton.setText(readingFrozen?"▶ ادامهٔ زنده":"❚❚ قفل عدد");
    if(readingFrozen){
      // Freeze must silence even a previously continuous lock tone.
      beeper.update(false,20,tolerance,false,beepVolume);
      status.setText("❚❚ اندازه‌گیری قفل شد");
      status.setTextColor(GOLD);
    }else{
      lowpassInit=false;lastReading=SystemClock.elapsedRealtime();
      status.setText("● سنجش زنده فعال شد");
    }
    if(calibrate!=null)calibrate.setEnabled(measurementMode==0&&measuring&&observed&&!readingFrozen);
    refreshBeep();
  }
  String[] currentReading(){
    String tool="",value="",extra="";
    switch(measurementMode){
      case 0:
        tool="تراز حبابی";
        value=String.format(Locale.US,"X=%+.2f° | Y=%+.2f°",x,y);
        extra= (Math.max(Math.abs(x),Math.abs(y))<=tolerance?"در محدودهٔ تراز":"خارج از محدودهٔ تراز")
          +" | تلرانس "+String.format(Locale.US,"±%.1f°",tolerance)
          +" | "+(calibrationComplete?"کالیبراسیون ذخیره‌شده":"کالیبراسیون ثبت نشده");
        break;
      case 1:
        tool="شیب‌سنج";
        value=String.format(Locale.US,"%.2f°",tiltDegrees);
        extra=Double.isInfinite(slopePercent)?"درصد شیب: نزدیک عمودی":
          String.format(Locale.US,"درصد شیب: %.2f%%",slopePercent);
        break;
      case 2:
        if(!edgeAngleValid)return null;
        tool="زاویه‌سنج";value=String.format(Locale.US,"%.2f°",edgeAngle);
        extra="لبهٔ بلند گوشی نسبت به افق در صفحهٔ عمودی";
        break;
      case 3:
        if(!edgeAngleValid||Double.isNaN(referenceAngle))return null;
        tool="گونیا";value=String.format(Locale.US,"%.2f°",squareAngle);
        extra=String.format(Locale.US,"اختلاف با زاویهٔ قائمه: %.2f°",Math.abs(90-squareAngle));
        break;
      default:return null;
    }
    return new String[]{tool,value,extra};
  }
  void saveCurrentMeasurement(){
    if(!measuring||!observed){
      Toast.makeText(this,"برای ذخیره، ابتدا سنجش را فعال کنید.",Toast.LENGTH_SHORT).show();return;
    }
    String[] reading=currentReading();
    if(reading==null){
      Toast.makeText(this,"برای این ابزار ابتدا گوشی را درست قرار دهید و در گونیا ضلع مرجع را ثبت کنید.",Toast.LENGTH_LONG).show();
      return;
    }
    final android.widget.EditText note=new android.widget.EditText(this);
    note.setSingleLine(true);note.setTextSize(16);
    note.setHint("مثلاً: نصب کابینت آشپزخانه");
    note.setText(prefs.getString("lastProjectName",""));
    LinearLayout wrapper=new LinearLayout(this);wrapper.setPadding(px(18),0,px(18),0);
    wrapper.addView(note,new LinearLayout.LayoutParams(-1,-2));
    new AlertDialog.Builder(this)
      .setTitle("ذخیرهٔ اندازه‌گیری")
      .setMessage(reading[0]+" — "+reading[1]+"\n"+reading[2]+"\nنام پروژه/یادداشت (اختیاری):")
      .setView(wrapper)
      .setNegativeButton("انصراف",null)
      .setPositiveButton("ذخیره",(d,which)->{
        String name=note.getText().toString().trim();
        if(name.length()>75)name=name.substring(0,75);
        prefs.edit().putString("lastProjectName",name).apply();
        MeasurementLog.save(this,name,reading[0],reading[1],reading[2]);
        Toast.makeText(this,"نتیجه در دفترچه ذخیره شد",Toast.LENGTH_SHORT).show();
      }).show();
  }
  void showHistory(){
    final java.util.List<MeasurementLog.Entry> items=MeasurementLog.list(this);
    if(items.isEmpty()){
      new AlertDialog.Builder(this)
        .setTitle("دفترچه اندازه‌گیری‌ها")
        .setMessage("هنوز نتیجه‌ای ذخیره نکرده‌اید. ابتدا عدد را در یکی از ابزارها ثبت کنید.")
        .setPositiveButton("متوجه شدم",null).show();
      return;
    }
    String[] names=new String[items.size()];
    for(int i=0;i<items.size();i++){
      MeasurementLog.Entry e=items.get(i);
      names[i]=(i+1)+". "+e.tool+"  "+e.value+"\n"+e.name+"  •  "+MeasurementLog.dateText(e.time);
    }
    new AlertDialog.Builder(this)
      .setTitle("دفترچه اندازه‌گیری‌ها ("+items.size()+")")
      .setItems(names,(d,which)->showRecord(items.get(which)))
      .setPositiveButton("اشتراک کل گزارش",(d,which)->shareText(MeasurementLog.exportText(items)))
      .setNeutralButton("پاک‌کردن همه",(d,which)->{
        new AlertDialog.Builder(this)
          .setTitle("حذف دفترچه")
          .setMessage("تمام اندازه‌گیری‌های ذخیره‌شده پاک شوند؟")
          .setNegativeButton("انصراف",null)
          .setPositiveButton("حذف دائمی",(ignore,which2)->{
            MeasurementLog.clear(this);
            Toast.makeText(this,"دفترچه پاک شد",Toast.LENGTH_SHORT).show();
          }).show();
      })
      .setNegativeButton("بستن",null).show();
  }
  void showRecord(MeasurementLog.Entry entry){
    new AlertDialog.Builder(this)
      .setTitle(entry.tool+" — "+entry.value)
      .setMessage(MeasurementLog.text(entry))
      .setPositiveButton("اشتراک‌گذاری",(d,w)->shareText(MeasurementLog.text(entry)))
      .setNeutralButton("کپی",(d,w)->{
        ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(clipboard!=null)clipboard.setPrimaryClip(ClipData.newPlainText("اندازه‌گیری تراز یار",MeasurementLog.text(entry)));
        Toast.makeText(this,"نتیجه کپی شد",Toast.LENGTH_SHORT).show();
      })
      .setNegativeButton("بستن",null).show();
  }
  void shareText(String report){
    Intent i=new Intent(Intent.ACTION_SEND);
    i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,report);
    try{startActivity(Intent.createChooser(i,"ارسال گزارش تراز یار"));}
    catch(Exception ex){Toast.makeText(this,"برنامه‌ای برای اشتراک‌گذاری یافت نشد",Toast.LENGTH_SHORT).show();}
  }

  void startMeasure() {
    if(sensor==null)return;
    measuring=true;observed=false;lowpassInit=false;readingFrozen=false;calibrationStage=0;state=-1;candidate=-1;candidateAt=0;referenceAngle=Double.NaN;nearArmed=true;
    if(holdButton!=null)holdButton.setText("❚❚ قفل عدد");
    updateCalibrationLabel();
    status.setText("در حال خواندن حسگر...");start.setText("■ توقف سنجش");calibrate.setEnabled(false);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    if(resumed)manager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_UI);
    refreshMeasureUI();refreshBeep();
  }
  void stopMeasure() {
    measuring=false;observed=false;readingFrozen=false;calibrationStage=0;state=-1;candidate=-1;
    if(holdButton!=null)holdButton.setText("❚❚ قفل عدد");
    updateCalibrationLabel();
    if(manager!=null)manager.unregisterListener(this);
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    start.setText("▶ شروع سنجش");calibrate.setEnabled(false);status.setText("سنجش متوقف شد");angles.setText("X: --.-°       Y: --.-°");drawing.setTilt(0,0,false);resultValue.setText("—");refreshBeep();refreshMeasureUI();
  }
  // Calibrated axes: with the display facing up, positive gravity X means
  // the phone's right edge is lower and should be raised; positive Y means
  // the phone's top edge is lower and should be raised.
  int correctionDirection(){
    if(Math.abs(x)>=Math.abs(y))return x>0?0:1;
    return y>0?2:3;
  }
  String correctionText(){
    String[] labels={"سمت راست گوشی","سمت چپ گوشی","بالای گوشی","پایین گوشی"};
    return labels[correctionDirection()]+" را کمی بالاتر بیاورید.";
  }
  void vibrateNear(){
    if(!haptic||vibrator==null||!vibrator.hasVibrator())return;
    try{vibrator.vibrate(VibrationEffect.createOneShot(28,VibrationEffect.DEFAULT_AMPLITUDE));}
    catch(Exception ignored){}
  }
  void vibrateSuccess(){
    if(!haptic||vibrator==null||!vibrator.hasVibrator())return;
    try{vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,45,80,75},-1));}
    catch(Exception ignored){}
  }
  void selectMode(int mode){
    if(mode<0||mode>3)return;
    if(measurementMode!=mode){
      // Silence immediately on leaving the bubble level, including tone previews.
      previewUntil=0;
      beeper.update(false,20,tolerance,false,beepVolume);
      measurementMode=mode;
      prefs.edit().putInt("measurementMode",mode).apply();
      state=-1;candidate=-1;candidateAt=0;
      // A reference from another visit is invalid; each square session captures its own.
      if(mode==3)referenceAngle=Double.NaN;
      readingFrozen=false;
      if(holdButton!=null)holdButton.setText("❚❚ قفل عدد");
      calibrationStage=0;
      updateCalibrationLabel();
    }
    if(sensor!=null&&!measuring)startMeasure();
    updateModeUI();refreshBeep();
    Toast.makeText(this,new String[]{"تراز حبابی","شیب‌سنج فعال شد","زاویه‌سنج فعال شد","گونیا فعال شد"}[mode],Toast.LENGTH_SHORT).show();
  }
  void updateModeUI(){
    if(drawing==null||meter==null||captureReference==null)return;
    boolean bubble=(measurementMode==0);
    String[] hints={
      "سطح را زیر گوشی بگذارید؛ حباب باید وسط باشد.",
      "گوشی را روی سطح قرار دهید؛ زاویه و درصد شیب زنده نمایش داده می‌شود.",
      "گوشی را قائم نگه دارید؛ زاویهٔ لبهٔ آن نسبت به افق نمایش داده می‌شود.",
      "در یک صفحهٔ عمودی: ضلع اول را ثبت کنید، سپس روی ضلع دوم قرار دهید."
    };
    if(modeHint!=null)modeHint.setText(hints[measurementMode]);
    for(int i=0;i<toolButtons.length;i++){
      if(toolButtons[i]==null)continue;
      boolean selected=(i==measurementMode);
      toolButtons[i].setTextColor(selected?BG:TEXT);
      toolButtons[i].setBackground(background(selected?GREEN:0xff234331,selected?GOLD:0xff53765D,14));
    }
    drawing.setVisibility(bubble?View.VISIBLE:View.GONE);
    meter.setVisibility(bubble?View.GONE:View.VISIBLE);
    resultValue.setVisibility(bubble?View.GONE:View.VISIBLE);
    resultDescription.setVisibility(bubble?View.GONE:View.VISIBLE);
    captureReference.setVisibility(measurementMode==3?View.VISIBLE:View.GONE);
    if(calibrate!=null)calibrate.setEnabled(measuring&&observed&&bubble&&!readingFrozen);
    if(calibrationStatus!=null)calibrationStatus.setVisibility(bubble?View.VISIBLE:View.GONE);
    if(meter!=null){meter.setMode(measurementMode);meter.setReading(0,0,false);}
    if(!bubble) {
      angles.setVisibility(View.GONE);
      directions.setVisibility(View.GONE);
    } else {
      angles.setVisibility(View.VISIBLE);
      directions.setVisibility(View.VISIBLE);
      status.setText(measuring?"◉ تراز حبابی فعال":"◉ تراز حبابی آماده");
    }
    refreshMeasureUI();refreshBeep();
  }
  double angleDifference180(double a,double b){return LevelMath.angleBetweenLines(a,b);}
  void refreshMeasureUI(){
    if(meter==null||resultValue==null||resultDescription==null)return;
    if(measurementMode==0)return;
    if(!measuring || !observed){
      resultValue.setText("—");
      resultDescription.setText(measurementMode==3?"گوشی را قائم نگه دارید و ضلع اول را ثبت کنید.":"برای نمایش زاویه یا شیب، شروع سنجش را بزنید.");
      return;
    }
    if(measurementMode==1){
      resultValue.setText(String.format(Locale.US,"%.1f°",tiltDegrees));
      resultDescription.setText(slopePercent>9999?"شیب نزدیک به عمودی؛ درصد شیب بسیار زیاد است":String.format(Locale.US,"شیب: %.1f درصد | صفر درجه = افقی",slopePercent));
      meter.setReading(tiltDegrees,0,true);
      status.setText("◡  شیب‌سنج");
    }else if(measurementMode==2){
      if(!edgeAngleValid){
        resultValue.setText("—");
        resultDescription.setText("برای زاویه‌سنج، گوشی را تقریباً قائم بگیرید؛ در حالت تخت حسگر زاویهٔ این صفحه را نمی‌خواند.");
        status.setText("زاویهٔ نامعتبر در حالت تخت");
        meter.setReading(0,0,false);
      }else{
        resultValue.setText(String.format(Locale.US,"%.1f°",edgeAngle));
        resultDescription.setText("زاویهٔ لبهٔ بلند گوشی نسبت به افق، در صفحهٔ عمودی");
        status.setText("∠  زاویه‌سنج فعال");
        meter.setReading(edgeAngle,0,true);
      }
    }else if(measurementMode==3){
      if(!edgeAngleValid){
        resultValue.setText("—");
        resultDescription.setText("برای گونیا، گوشی را تقریباً قائم بگیرید. حالت تخت مناسب نیست.");
        meter.setReading(0,90,false);
        status.setText("□  گونیا: گوشی را قائم کنید");
      }else if(Double.isNaN(referenceAngle)){
        resultValue.setText("مرجع ثبت نشده");
        resultDescription.setText("لبهٔ گوشی را روی ضلع اول قرار داده و «ثبت ضلع اول» را بزنید.");
        status.setText("□  آمادهٔ ثبت ضلع اول");
        meter.setReading(0,90,false);
      }else{
        squareAngle=angleDifference180(edgeAngle,referenceAngle);
        double error=Math.abs(90.0-squareAngle);
        resultValue.setText(String.format(Locale.US,"%.1f° / 90°",squareAngle));
        resultDescription.setText(String.format(Locale.US,"اختلاف با زاویهٔ قائمه: %.1f درجه | اندازه‌گیری در یک صفحه",error));
        meter.setReading(squareAngle,90,true);
        status.setText(state==1?"✓  زاویهٔ قائمه برقرار است":state==0?"□  نزدیک به ۹۰ درجه تنظیم کنید":"□  سنجش گونیا");
        status.setTextColor(state==1?GREEN:GOLD);
      }
    }
  }
  void updateSquare(){
    if(!edgeAngleValid || Double.isNaN(referenceAngle)){
      state=-1;candidate=-1;refreshMeasureUI();refreshBeep();return;
    }
    squareAngle=angleDifference180(edgeAngle,referenceAngle);
    double error=Math.abs(90-squareAngle);
    long now=SystemClock.elapsedRealtime();
    double nearLimit=Math.max(4.0,tolerance*3);
    if(error>nearLimit+1)nearArmed=true;
    if(error<=nearLimit&&nearArmed&&error>tolerance){
      nearArmed=false;vibrateNear();
    }
    int wanted=error<=Math.max(tolerance,.6)?1:0;
    if(wanted!=candidate){candidate=wanted;candidateAt=now;}
    if(now-candidateAt>=750&&wanted!=state){
      state=wanted;
      if(state==1){vibrateSuccess();meter.celebrate();}
    }
    refreshMeasureUI();
  }
  void updateLevel(){
    x=rawX-zeroX; y=rawY-zeroY;
    double distance=Math.max(Math.abs(x),Math.abs(y));
    boolean leveled=distance<=tolerance;
    long now=SystemClock.elapsedRealtime();
    double nearLimit=Math.max(2.5,tolerance*3.0);
    if(distance>nearLimit+0.8)nearArmed=true;
    if(!leveled && distance<=nearLimit && nearArmed){
      nearArmed=false;
      vibrateNear();
    }
    int wanted=leveled?1:0;
    if(wanted!=candidate){candidate=wanted;candidateAt=now;}
    if(now-candidateAt>=750 && wanted!=state) {
      state=wanted;
      if(state==1){vibrateSuccess();drawing.celebrate();}
    }
    status.setText(state==1?"✓  تراز است":state==0?"●  در حال تنظیم تراز":"در حال تثبیت حباب...");
    status.setTextColor(state==1?GREEN:state==0?0xffffc29a:GOLD);
    angles.setText(String.format(Locale.US,"X: %+.1f°      Y: %+.1f°",x,y));
    directions.setText(leveled?"آفرین! هر دو محور در محدودهٔ تراز هستند.":
      distance<=nearLimit?"نزدیک شدی! فقط یک تنظیم کوچولو باقی مانده.":correctionText());
    drawing.setTilt(x,y,leveled);
  }
  @Override public void onSensorChanged(SensorEvent e){
    if(!measuring || e.sensor!=sensor || readingFrozen)return;
    for(int i=0;i<3;i++){if(!lowpassInit)gv[i]=e.values[i];else gv[i]=.84f*gv[i]+.16f*e.values[i];}
    lowpassInit=true;
    double gz=Math.max(.0001,Math.abs(gv[2]));
    rawX=Math.toDegrees(Math.atan2(gv[0],gz));rawY=Math.toDegrees(Math.atan2(gv[1],gz));
    tiltDegrees=LevelMath.inclineDegrees(gv[0],gv[1],gv[2]);
    slopePercent=LevelMath.slopePercent(gv[0],gv[1],gv[2]);
    edgeAngleValid=LevelMath.canMeasureEdge(gv[0],gv[1],gv[2]);
    if(edgeAngleValid)edgeAngle=LevelMath.edgeFromHorizontal(gv[0],gv[1]);
    observed=true;lastReading=SystemClock.elapsedRealtime();
    if(lastReading-lastSensorUi>600){
      lastSensorUi=lastReading;
      sensorStatus.setText("● حسگر فعال  |  "+(sensor.getType()==Sensor.TYPE_GRAVITY?"گرانش":"شتاب‌سنج")+"  |  "+String.format(Locale.US,"%.1f°",tiltDegrees));
      sensorStatus.setTextColor(GREEN);
    }
    calibrate.setEnabled(measurementMode==0);
    if(measurementMode==0)updateLevel();
    else if(measurementMode==3)updateSquare();
    else refreshMeasureUI();
    refreshBeep();
  }
  @Override public void onAccuracyChanged(Sensor s,int accuracy){}
  void previewTone(int sample){
    if(!BeepPattern.allowedMode(measurementMode)){
      previewUntil=0;beeper.update(false,20,tolerance,false,beepVolume);
      Toast.makeText(this,"بوق فقط در ابزار «تراز حبابی» فعال است",Toast.LENGTH_SHORT).show();
      return;
    }
    previewError=sample==0?12:sample==1?1.1:0;
    previewSteady=sample==2;
    previewUntil=SystemClock.elapsedRealtime()+2000;
    beeper.start();refreshBeep();
    handler.postDelayed(()->refreshBeep(),2050);
  }
  void refreshBeep(){
    if(tutorialOpen || readingFrozen){
      beeper.update(false,20,tolerance,false,beepVolume);
      return;
    }
    // Enforce silence for inclinometer, angle meter, and carpenter's square
    // BEFORE preview handling. No other tool may emit a beep.
    if(!BeepPattern.allowedMode(measurementMode)){
      previewUntil=0;
      beeper.update(false,20,tolerance,false,beepVolume);
      if(beepStatus!=null)beepStatus.setText("🔕 بوق فقط در تراز حبابی فعال است");
      return;
    }
    long now=SystemClock.elapsedRealtime();
    if(now<previewUntil){
      beeper.update(true,previewError,tolerance,previewSteady,beepVolume);
      if(beepStatus!=null)beepStatus.setText("🔔 آزمایش: "+(previewSteady?"ممتد":previewError>5?"بوق کوتاه":"بوق نزدیک"));
      return;
    }
    previewUntil=0;
    boolean valid=resumed&&beepEnabled&&beepVolume>0&&measuring&&observed&&(now-lastReading<2500)&&BeepPattern.allowedMode(measurementMode);
    double error=20;boolean locked=false;
    if(valid){
      error=Math.max(Math.abs(x),Math.abs(y));
      locked=state==1;
    }
    beeper.update(valid,error,tolerance,locked,beepVolume);
    if(beepStatus!=null){
      String label=!beepEnabled?"بوق خاموش است":
      !valid?"در انتظار حسگر و سنجش":
      locked?"تراز کامل: بوق ممتد":
      error>7?"دور از تراز: کوتاه و آرام":
      error>2?"در حال نزدیک‌شدن: سریع‌تر":"خیلی نزدیک: بلند و تقریباً پیوسته";
      beepStatus.setText("🔔 "+label);
    }
  }
  @Override protected void onResume(){
    super.onResume();resumed=true;beeper.start();
    if(measuring&&sensor!=null)manager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_UI);
    refreshBeep();
  }
  @Override protected void onPause(){
    resumed=false;previewUntil=0;
    if(manager!=null)manager.unregisterListener(this);
    beeper.stop();super.onPause();
  }
  @Override protected void onDestroy(){
    handler.removeCallbacksAndMessages(null);
    beeper.stop();super.onDestroy();
  }

  // Glass-and-liquid inspired two-axis bubble level, drawn at device resolution.
  static class LevelDrawing extends View {
    final Paint p=new Paint(3);
    double dx=0,dy=0;
    float bubbleX=Float.NaN,bubbleY=Float.NaN;
    boolean centered=false;
    long winAt=0;
    LevelDrawing(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    void setTilt(double x,double y,boolean ok){dx=x;dy=y;centered=ok;postInvalidateOnAnimation();}
    void celebrate(){winAt=SystemClock.uptimeMillis();postInvalidateOnAnimation();}
    void fill(Canvas c,int color,float cx,float cy,float r) {
      p.reset();p.setAntiAlias(true);p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawCircle(cx,cy,r,p);
    }
    void strokeCircle(Canvas c,int color,float width,float cx,float cy,float r) {
      p.reset();p.setAntiAlias(true);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setColor(color);c.drawCircle(cx,cy,r,p);
    }
    void line(Canvas c,int color,float width,float x1,float y1,float x2,float y2) {
      p.reset();p.setAntiAlias(true);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setColor(color);c.drawLine(x1,y1,x2,y2,p);
    }
    void tube(Canvas c,float l,float r,float mid,float value,String label) {
      float top=mid-13,bottom=mid+13;
      p.reset();p.setAntiAlias(true);
      p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(l,top,l,bottom,new int[]{0xff5b8050,0xff0b2819,0xff214c2f,0xff739263},null,Shader.TileMode.CLAMP));
      c.drawRoundRect(l,top,r,bottom,13,13,p);p.setShader(null);
      p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(0xffD9C481);
      c.drawRoundRect(l,top,r,bottom,13,13,p);
      float center=(l+r)/2f;
      for(int i=-5;i<=5;i++){
        float mark=center+i*(r-l-40)/10f;
        line(c,i==0?0xffDFF5AB:0xffA0BB7F,i==0?2:1,mark,top+5,mark,bottom-5);
      }
      float target=center+(float)Math.max(-1,Math.min(1,value/7.0))*(r-l-42)/2f;
      p.reset();p.setAntiAlias(true);p.setShader(new RadialGradient(target-3,mid-3,14,new int[]{0xffFFFFFF,0xffCCFF83,0xff4CA650},null,Shader.TileMode.CLAMP));
      c.drawCircle(target,mid,11,p);p.setShader(null);
      p.setStyle(Paint.Style.FILL);p.setColor(0xffF4FFCC);p.setTextSize(11*getResources().getDisplayMetrics().scaledDensity);
      c.drawText(label,l+2,top-5,p);
    }
    @Override protected void onDraw(Canvas c){
      super.onDraw(c);
      float w=getWidth(),h=getHeight(),scale=Math.min(w*.405f,h*.31f),cx=w/2f,cy=scale+22;
      p.reset();p.setAntiAlias(true);p.setStyle(Paint.Style.FILL);p.setShadowLayer(14,0,7,0xaa000000);
      p.setColor(0xff0a120c);c.drawCircle(cx,cy,scale+11,p);p.clearShadowLayer();
      p.setShader(new LinearGradient(cx-scale,cy-scale,cx+scale,cy+scale,new int[]{0xffF0DDA0,0xff8F6F37,0xffE3C987,0xff755832},null,Shader.TileMode.CLAMP));
      c.drawCircle(cx,cy,scale+10,p);p.setShader(null);
      fill(c,0xff07170E,cx,cy,scale+4);
      p.setShader(new RadialGradient(cx-scale*.24f,cy-scale*.25f,scale*1.36f,new int[]{0xff32683F,0xff153C27,0xff071810},null,Shader.TileMode.CLAMP));
      c.drawCircle(cx,cy,scale-3,p);p.setShader(null);
      for(int i=1;i<=5;i++) strokeCircle(c,i==5?0xff82956B:0xff3B7044,1.2f,cx,cy,scale*i/6f);
      for(int a=0;a<360;a+=5){
        double rad=Math.toRadians(a);
        float inner=scale-(a%30==0?24:a%10==0?18:13);
        float outer=scale-7;
        float x1=cx+(float)Math.cos(rad)*inner,y1=cy+(float)Math.sin(rad)*inner;
        float x2=cx+(float)Math.cos(rad)*outer,y2=cy+(float)Math.sin(rad)*outer;
        line(c,a%30==0?0xffF4DD90:0xff829C7A,a%30==0?2.2f:1,x1,y1,x2,y2);
      }
      line(c,0x88779C78,1.5f,cx-scale+29,cy,cx+scale-29,cy);
      line(c,0x88779C78,1.5f,cx,cy-scale+29,cx,cy+scale-29);
      float rBubble=scale*.19f;
      float desiredX=cx+(float)Math.max(-1,Math.min(1,dx/9.0))*scale*.78f;
      float desiredY=cy+(float)Math.max(-1,Math.min(1,dy/9.0))*scale*.78f;
      float dist=(float)Math.hypot(desiredX-cx,desiredY-cy),limit=scale-rBubble-10;
      if(dist>limit){desiredX=cx+(desiredX-cx)*limit/dist;desiredY=cy+(desiredY-cy)*limit/dist;}
      if(Float.isNaN(bubbleX)){bubbleX=desiredX;bubbleY=desiredY;}
      bubbleX+=(desiredX-bubbleX)*.21f;
      bubbleY+=(desiredY-bubbleY)*.21f;
      p.reset();p.setAntiAlias(true);p.setShadowLayer(9,2,4,0xaa000000);p.setColor(0xbb000000);
      c.drawCircle(bubbleX,bubbleY,rBubble+4,p);p.clearShadowLayer();
      p.setShader(new RadialGradient(bubbleX-rBubble*.30f,bubbleY-rBubble*.39f,rBubble*1.7f,new int[]{0xffF9FFD5,centered?0xffC8FF78:0xffB6F66D,0xff409D37,0xff124524},null,Shader.TileMode.CLAMP));
      c.drawCircle(bubbleX,bubbleY,rBubble,p);p.setShader(null);
      fill(c,0xAAFFFFFF,bubbleX-rBubble*.28f,bubbleY-rBubble*.32f,rBubble*.17f);
      strokeCircle(c,centered?0xffFFEAA6:0xffC8B76F,centered?3.5f:2.2f,cx,cy,rBubble+6);
      p.reset();p.setAntiAlias(true);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);
      p.setColor(0x77FFFFFF);
      c.drawArc(cx-scale+7,cy-scale+7,cx+scale-7,cy+scale-7,210,105,false,p);
      long ms=SystemClock.uptimeMillis()-winAt;
      if(winAt>0&&ms>=0&&ms<1100){
        float progress=ms/1100f;
        p.setColor((Math.max(0,Math.min(255,(int)(200*(1-progress))))<<24)|0x00C9FF80);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4f*(1-progress)+1f);
        c.drawCircle(cx,cy,rBubble+13+progress*36,p);
        postInvalidateOnAnimation();
      }
      // Two calibrated graduated liquid tubes below the round spirit level.
      float left=27,right=w-27;
      tube(c,left,right,h-72,(float)dx,"X");
      tube(c,left,right,h-26,(float)dy,"Y");
      if(Math.abs(bubbleX-desiredX)>0.3||Math.abs(bubbleY-desiredY)>0.3)postInvalidateOnAnimation();
    }
  }
  static class MeasurementGauge extends View {
    final Paint p=new Paint(3);
    float angle=0,target=0;
    boolean valid=false;
    int mode=1;
    long winAt=0;
    MeasurementGauge(Context context){super(context);}
    void setMode(int m){mode=m;invalidate();}
    void setReading(double reading,double goal,boolean ok){angle=(float)reading;target=(float)goal;valid=ok;postInvalidateOnAnimation();}
    void celebrate(){winAt=SystemClock.uptimeMillis();postInvalidateOnAnimation();}
    @Override protected void onDraw(Canvas c){
      super.onDraw(c);
      float w=getWidth(),h=getHeight();
      float cx=w/2,cy=h-40;
      float r=Math.min(w*.41f,h*.78f);
      p.setAntiAlias(true);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(13);
      p.setColor(0xff22462F);
      c.drawArc(cx-r,cy-r,cx+r,cy+r,180,180,false,p);
      p.setColor(0xffD2B878);p.setStrokeWidth(3);
      c.drawArc(cx-r,cy-r,cx+r,cy+r,180,180,false,p);
      p.setTextAlign(Paint.Align.CENTER);p.setStyle(Paint.Style.FILL);
      for(int i=0;i<=18;i++){
        double theta=Math.PI*(1.0+i/18.0);
        float co=(float)Math.cos(theta),si=(float)Math.sin(theta);
        float x1=cx+co*(r-15),y1=cy+si*(r-15),x2=cx+co*(r-((i%3==0)?34:25)),y2=cy+si*(r-((i%3==0)?34:25));
        p.setColor(i%3==0?0xffE2D194:0xff739779);p.setStrokeWidth(i%3==0?2.5f:1.3f);
        c.drawLine(x1,y1,x2,y2,p);
        if(i%3==0){
          p.setColor(0xffC3D5BD);p.setTextSize(13*getResources().getDisplayMetrics().scaledDensity);
          c.drawText(String.valueOf(mode==2?i*10:i*5),cx+co*(r-53),cy+si*(r-53)+5,p);
        }
      }
      float dialMax=mode==1?90:180;
      if(mode==3)dialMax=90;
      float a=valid?Math.max(0,Math.min(dialMax,angle)):0;
      double theta=Math.PI+(Math.PI*a/dialMax);
      p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
      p.setStrokeWidth(5);
      p.setColor(valid?0xffB9F579:0xff667E67);
      c.drawLine(cx,cy,cx+(float)Math.cos(theta)*(r-38),cy+(float)Math.sin(theta)*(r-38),p);
      p.setStyle(Paint.Style.FILL);p.setColor(0xffDCCB83);c.drawCircle(cx,cy,8,p);
      if(mode==3){
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setColor(0xffBBDD8D);
        float size=47;
        c.drawLine(cx+60,cy-20,cx+60,cy-20-size,p);
        c.drawLine(cx+60,cy-20,cx+60+size,cy-20,p);
      }
      if(winAt>0){
        long time=SystemClock.uptimeMillis()-winAt;
        if(time<800){
          p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);p.setColor((int)((180*(1-time/800f)))<<24|0x00A8ED65);
          c.drawCircle(cx,cy,25+time*.045f,p);
          postInvalidateOnAnimation();
        }
      }
    }
  }

}
