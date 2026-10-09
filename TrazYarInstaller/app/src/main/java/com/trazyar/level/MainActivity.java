package com.trazyar.level;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.speech.tts.TextToSpeech;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;
import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener, TextToSpeech.OnInitListener {
  static final int BG=0xff081A13, PANEL=0xff112A20, GREEN=0xffA8ED65, GOLD=0xffE8C87D, TEXT=0xffF1F9E9, MUTED=0xffA7BCAE;
  SensorManager manager; Sensor sensor; TextToSpeech tts;
  boolean ttsReady=false, speaking=false, measuring=false, resumed=false, voice=true, observed=false, lowpassInit=false;
  final float[] gv=new float[3];
  double zeroX=0,zeroY=0,rawX=0,rawY=0,x=0,y=0,tolerance=.5;
  int state=-1, candidate=-1, interval=5000;
  long candidateAt=0,lastVoice=0,lastReading=0;
  SharedPreferences prefs;
  TextView status, angles, directions; Button start, calibrate; LevelDrawing drawing;
  final Handler handler=new Handler(Looper.getMainLooper());
  int px(float dp){return Math.round(dp*getResources().getDisplayMetrics().density);}
  GradientDrawable background(int fill,int border,int r) { GradientDrawable g=new GradientDrawable(); g.setColor(fill);g.setCornerRadius(px(r));if(border!=0)g.setStroke(px(1),border);return g; }
  TextView text(String s,int sp,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);t.setGravity(Gravity.CENTER);return t;}
  Button button(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(16);b.setTextColor(primary?BG:TEXT);b.setBackground(background(primary?GREEN:0xff284936,primary?0:0xff557D59,14));return b;}
  void gap(LinearLayout l,int dp){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,px(dp)));}
  @Override public void onCreate(Bundle b) {
    super.onCreate(b); getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
    prefs=getSharedPreferences("trazyar",MODE_PRIVATE);
    tolerance=prefs.getFloat("tol",.5f);voice=prefs.getBoolean("voice",true);interval=prefs.getInt("repeat",5000);
    manager=(SensorManager)getSystemService(Context.SENSOR_SERVICE);
    if(manager!=null){sensor=manager.getDefaultSensor(Sensor.TYPE_GRAVITY);if(sensor==null)sensor=manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);}
    buildUI();
    tts=new TextToSpeech(this,this);
    if(sensor==null){start.setEnabled(false);status.setText("حسگر مناسب پیدا نشد");}
    handler.post(new Runnable(){public void run() {
      long now=SystemClock.elapsedRealtime();
      if(resumed&&measuring) {
        if(observed&&now-lastReading>3000){observed=false;state=-1;status.setText("ارتباط حسگر قطع شده");stopVoice();}
        else if(state==0&&voice&&!speaking&&now-lastVoice>=interval)announce("تراز نیست، سطح را تنظیم کنید");
      }
      handler.postDelayed(this,250);
    }});
  }
  void buildUI(){
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(px(16),px(18),px(16),px(30));root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);scroll.addView(root);setContentView(scroll);
    TextView brand=text("◉   تراز یار",30,GREEN);brand.setTypeface(null,1);root.addView(brand);
    root.addView(text("همیشه در سطح درست  •  TRAZYAR",12,GOLD));gap(root,18);
    LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(px(11),px(16),px(11),px(17));panel.setBackground(background(PANEL,0xff406E50,23));root.addView(panel);
    status=text("آمادهٔ اندازه‌گیری",19,GOLD);status.setTypeface(null,1);panel.addView(status);
    gap(panel,12);drawing=new LevelDrawing(this);panel.addView(drawing,new LinearLayout.LayoutParams(-1,px(320)));
    angles=text("X:  --.-°       Y:  --.-°",18,TEXT);panel.addView(angles);
    directions=text("گوشی را با صفحهٔ رو به بالا روی سطح بگذارید.",13,MUTED);panel.addView(directions);gap(panel,13);
    LinearLayout row=new LinearLayout(this);row.setOrientation(0);panel.addView(row,new LinearLayout.LayoutParams(-1,px(55)));
    start=button("▶ شروع سنجش",true);row.addView(start,new LinearLayout.LayoutParams(0,-1,1));
    calibrate=button("⊕ کالیبره",false);calibrate.setEnabled(false);row.addView(calibrate,new LinearLayout.LayoutParams(0,-1,1));
    start.setOnClickListener(v->{if(measuring)stopMeasure();else startMeasure();});
    calibrate.setOnClickListener(v->{if(observed){zeroX=rawX;zeroY=rawY;state=-1;candidate=-1;candidateAt=0;updateLevel();Toast.makeText(this,"کالیبره شد",Toast.LENGTH_SHORT).show();}});
    gap(root,16);
    LinearLayout opt=new LinearLayout(this);opt.setOrientation(1);opt.setPadding(px(16),px(13),px(16),px(16));opt.setBackground(background(PANEL,0xff3A6849,20));root.addView(opt);
    TextView voiceTitle=text("اعلان صوتی فارسی",19,TEXT);opt.addView(voiceTitle);gap(opt,10);
    Switch voiceToggle=new Switch(this);voiceToggle.setText("اعلان صوتی روشن باشد");voiceToggle.setTextSize(15);voiceToggle.setTextColor(TEXT);voiceToggle.setChecked(voice);opt.addView(voiceToggle);
    voiceToggle.setOnCheckedChangeListener((sw,on)->{voice=on;prefs.edit().putBoolean("voice",on).apply();if(!on)stopVoice();});
    TextView intervalLabel=text("فاصلهٔ تکرار پیام «تراز نیست»",14,MUTED);opt.addView(intervalLabel);
    Spinner options=new Spinner(this);
    String[] repeats={"هر ۳ ثانیه","هر ۵ ثانیه","هر ۸ ثانیه","هر ۱۲ ثانیه"};
    int[] repeatsMs={3000,5000,8000,12000};
    ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,repeats); options.setAdapter(adapter);
    options.setSelection(interval==3000?0:interval==8000?2:interval==12000?3:1);opt.addView(options);
    options.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
      public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){interval=repeatsMs[position];prefs.edit().putInt("repeat",interval).apply();}
      public void onNothingSelected(android.widget.AdapterView<?> p){}
    });
    Button test=button("🔊 آزمایش پیام صوتی",false);opt.addView(test,new LinearLayout.LayoutParams(-1,px(48)));test.setOnClickListener(v->announce("تراز است"));
    gap(root,14);
    LinearLayout settings=new LinearLayout(this);settings.setOrientation(1);settings.setPadding(px(16),px(13),px(16),px(14));settings.setBackground(background(PANEL,0xff3A6849,20));root.addView(settings);
    TextView sens=text(String.format(Locale.US,"حساسیت: ±%.1f°",tolerance),17,GREEN);settings.addView(sens);
    SeekBar seek=new SeekBar(this);seek.setMax(18);seek.setProgress((int)Math.round((tolerance-.2)*10));settings.addView(seek);
    settings.addView(text("دقیق‌تر ۰٫۲ درجه             آسان‌تر ۲ درجه",12,MUTED));
    seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar bar,int n,boolean fromUser){tolerance=.2+n*.1;sens.setText(String.format(Locale.US,"حساسیت: ±%.1f°",tolerance));prefs.edit().putFloat("tol",(float)tolerance).apply();candidate=-1;candidateAt=0;}
      public void onStartTrackingTouch(SeekBar bar){} public void onStopTrackingTouch(SeekBar bar){}
    });
    gap(root,12);root.addView(text("برای کالیبراسیون از سطح مرجع واقعاً تراز استفاده کنید. دقت به حسگر گوشی وابسته است.",12,MUTED));
    gap(root,12);root.addView(text("تراز یار • نسخهٔ ۱٫۳ • بدون نیاز به اینترنت",11,GOLD));
  }
  void startMeasure() {
    if(sensor==null)return;
    measuring=true;observed=false;lowpassInit=false;zeroX=0;zeroY=0;state=-1;candidate=-1;candidateAt=0;
    status.setText("در حال خواندن حسگر...");start.setText("■ توقف سنجش");calibrate.setEnabled(false);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    if(resumed)manager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_UI);
  }
  void stopMeasure() {
    measuring=false;observed=false;state=-1;candidate=-1;
    if(manager!=null)manager.unregisterListener(this);
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    start.setText("▶ شروع سنجش");calibrate.setEnabled(false);status.setText("سنجش متوقف شد");angles.setText("X: --.-°       Y: --.-°");drawing.setTilt(0,0,false);stopVoice();
  }
  void updateLevel(){
    x=rawX-zeroX;y=rawY-zeroY;
    boolean leveled=Math.abs(x)<=tolerance&&Math.abs(y)<=tolerance;
    long now=SystemClock.elapsedRealtime();
    int wanted=leveled?1:0;
    if(wanted!=candidate){candidate=wanted;candidateAt=now;}
    if(now-candidateAt>=650 && wanted!=state) {
      state=wanted;stopVoice();
      if(voice)announce(state==1?"تراز است":"تراز نیست، سطح را تنظیم کنید");
    }
    status.setText(state==1?"✓  تراز است":state==0?"●  تراز نیست":"در حال تثبیت...");
    status.setTextColor(state==1?GREEN:state==0?0xffffb69f:GOLD);
    angles.setText(String.format(Locale.US,"X: %+.1f°      Y: %+.1f°",x,y));
    directions.setText(leveled?"حباب در مرکز قرار دارد.":"حباب را با تنظیم سطح به مرکز برسانید.");
    drawing.setTilt(x,y,leveled);
  }
  @Override public void onSensorChanged(SensorEvent e){
    if(!measuring || e.sensor!=sensor)return;
    for(int i=0;i<3;i++){if(!lowpassInit)gv[i]=e.values[i];else gv[i]=.84f*gv[i]+.16f*e.values[i];}
    lowpassInit=true;
    double gz=Math.max(.0001,Math.abs(gv[2]));
    rawX=Math.toDegrees(Math.atan2(gv[0],gz));rawY=Math.toDegrees(Math.atan2(gv[1],gz));
    observed=true;lastReading=SystemClock.elapsedRealtime();calibrate.setEnabled(true);updateLevel();
  }
  @Override public void onAccuracyChanged(Sensor s,int accuracy){}
  void announce(String message){
    if(!resumed||!voice && !message.equals("تراز است"))return;
    if(ttsReady&&tts!=null){
      speaking=true;
      String id="level-"+SystemClock.elapsedRealtime();
      tts.speak(message,TextToSpeech.QUEUE_FLUSH,null,id);
      lastVoice=SystemClock.elapsedRealtime();
    } else {
      if(!ttsReady)Toast.makeText(this,"برای صدای فارسی، موتور گفتار فارسی را در تنظیمات گوشی فعال کنید.",Toast.LENGTH_LONG).show();
      lastVoice=SystemClock.elapsedRealtime();
    }
  }
  void stopVoice(){speaking=false;if(tts!=null)tts.stop();}
  @Override public void onInit(int result){
    if(result==TextToSpeech.SUCCESS&&tts!=null){
      int code=tts.setLanguage(new Locale("fa","IR"));
      ttsReady=code!=TextToSpeech.LANG_MISSING_DATA&&code!=TextToSpeech.LANG_NOT_SUPPORTED;
      tts.setSpeechRate(.92f);
      tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener(){
        @Override public void onStart(String s){speaking=true;}
        @Override public void onDone(String s){speaking=false;}
        @Override public void onError(String s){speaking=false;}
      });
    }
  }
  @Override protected void onResume(){super.onResume();resumed=true;if(measuring&&sensor!=null)manager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_UI);}
  @Override protected void onPause(){resumed=false;if(manager!=null)manager.unregisterListener(this);stopVoice();super.onPause();}
  @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);stopVoice();if(tts!=null)tts.shutdown();super.onDestroy();}

  static class LevelDrawing extends View {
    final Paint p=new Paint(3);double dx=0,dy=0;boolean centered=false,valid=false;
    LevelDrawing(Context c){super(c);}
    void setTilt(double x,double y,boolean ok){dx=x;dy=y;centered=ok;valid=true;invalidate();}
    void fill(Canvas c,int color,float cx,float cy,float radius){p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawCircle(cx,cy,radius,p);}
    void line(Canvas c,int color,float width,float x1,float y1,float x2,float y2){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setColor(color);c.drawLine(x1,y1,x2,y2,p);p.setStyle(Paint.Style.FILL);}
    @Override protected void onDraw(Canvas c){
      super.onDraw(c);float w=getWidth(),h=getHeight(),scale=Math.min(w,h)*.40f,cx=w/2,cy=h/2;
      fill(c,0xff06140E,cx,cy,scale+11);fill(c,0xffC1A45B,cx,cy,scale+9);fill(c,0xff1B4A2B,cx,cy,scale+5);fill(c,0xff132B1C,cx,cy,scale-2);
      p.setColor(0xff528250);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.3f);
      for(int a=1;a<=4;a++)c.drawCircle(cx,cy,scale*a/5,p);
      p.setStyle(Paint.Style.FILL);
      for(int a=0;a<360;a+=10){
        double rad=Math.toRadians(a);float x1=cx+(float)Math.cos(rad)*(scale-13),y1=cy+(float)Math.sin(rad)*(scale-13);
        float x2=cx+(float)Math.cos(rad)*(scale-(a%30==0?24:18)),y2=cy+(float)Math.sin(rad)*(scale-(a%30==0?24:18));
        line(c,a%30==0?0xffDCC884:0xff61865D,a%30==0?2.3f:1.4f,x1,y1,x2,y2);
      }
      line(c,0xff719069,1.6f,cx-scale+28,cy,cx+scale-28,cy);
      line(c,0xff719069,1.6f,cx,cy-scale+28,cx,cy+scale-28);
      float bubbleRadius=scale*.21f,offset=scale*.83f;
      float bx=cx+(float)(Math.max(-1,Math.min(1,dx/9))*offset),by=cy+(float)(Math.max(-1,Math.min(1,dy/9))*offset);
      float dis=(float)Math.hypot(bx-cx,by-cy),max=scale-bubbleRadius-4;if(dis>max){bx=cx+(bx-cx)*max/dis;by=cy+(by-cy)*max/dis;}
      p.setStyle(Paint.Style.FILL);p.setColor(0x55000000);c.drawCircle(bx+2,by+4,bubbleRadius+2,p);
      fill(c,centered?0xffC0FF76:0xffA5EA58,bx,by,bubbleRadius);
      fill(c,0xffE8FFC4,bx-bubbleRadius*.31f,by-bubbleRadius*.35f,bubbleRadius*.25f);
      p.setStyle(Paint.Style.STROKE);p.setColor(centered?0xffFFF6AB:0xffD0BA66);p.setStrokeWidth(2);c.drawCircle(cx,cy,bubbleRadius+4,p);
      p.setStyle(Paint.Style.FILL);
    }
  }
}
