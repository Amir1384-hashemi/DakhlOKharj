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
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;
import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener {
  static final int BG=0xff081A13, PANEL=0xff112A20, GREEN=0xffA8ED65, GOLD=0xffE8C87D, TEXT=0xffF1F9E9, MUTED=0xffA7BCAE;
  SensorManager manager; Sensor sensor; MediaPlayer player; Vibrator vibrator;
  boolean speaking=false, measuring=false, resumed=false, voice=true, observed=false, lowpassInit=false;
  boolean haptic=true, guided=true, nearArmed=true;
  int instructionCounter=0;
  double previousAnnouncementDistance=Double.NaN;
  int nearIdx=0, farIdx=0, successIdx=0;

  // Four selectable original voice profiles. None imitates a specific performer.
  final int[][] nearAudio = {
    {R.raw.kid_near_1,R.raw.kid_near_2},
    {R.raw.girl_near_1,R.raw.girl_near_2},
    {R.raw.woman_near_1,R.raw.woman_near_2},
    {R.raw.cinema_near_1,R.raw.cinema_near_2}
  };
  final int[][] farAudio = {
    {R.raw.kid_far_1,R.raw.kid_far_2},
    {R.raw.girl_far_1,R.raw.girl_far_2},
    {R.raw.woman_far_1,R.raw.woman_far_2},
    {R.raw.cinema_far_1,R.raw.cinema_far_2}
  };
  final int[][] successAudio = {
    {R.raw.kid_ok_1,R.raw.kid_win2},{R.raw.girl_ok_1,R.raw.girl_win2},{R.raw.woman_ok_1,R.raw.woman_win2},{R.raw.cinema_ok_1,R.raw.cinema_win2}
  };
  // Order: right, left, top, bottom. Applies when screen faces upward.
  final int[][] directionAudio={
    {R.raw.kid_right,R.raw.kid_left,R.raw.kid_top,R.raw.kid_bottom},
    {R.raw.girl_right,R.raw.girl_left,R.raw.girl_top,R.raw.girl_bottom},
    {R.raw.woman_right,R.raw.woman_left,R.raw.woman_top,R.raw.woman_bottom},
    {R.raw.cinema_right,R.raw.cinema_left,R.raw.cinema_top,R.raw.cinema_bottom}
  };
  final float[] voicePitch={1.25f,1.09f,1.0f,0.90f};
  final float[] voiceSpeed={1.06f,1.04f,1.0f,0.96f};
  int voiceProfile=0;
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
    tolerance=prefs.getFloat("tol",.5f);voice=prefs.getBoolean("voice",true);interval=prefs.getInt("repeat",5000);haptic=prefs.getBoolean("haptic",true);guided=prefs.getBoolean("guided",true);voiceProfile=Math.max(0,Math.min(3,prefs.getInt("voiceProfile",0)));
    manager=(SensorManager)getSystemService(Context.SENSOR_SERVICE);
    vibrator=(Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
    if(manager!=null){sensor=manager.getDefaultSensor(Sensor.TYPE_GRAVITY);if(sensor==null)sensor=manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);}
    buildUI();
    if(sensor==null){start.setEnabled(false);status.setText("حسگر مناسب پیدا نشد");}
    handler.post(new Runnable(){public void run() {
      long now=SystemClock.elapsedRealtime();
      if(resumed&&measuring) {
        if(observed&&now-lastReading>3000){observed=false;state=-1;status.setText("ارتباط حسگر قطع شده");stopVoice();}
        else if(state==0&&observed&&voice&&!speaking&&now-lastVoice>=interval)announceLevel();
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
    gap(panel,12);drawing=new LevelDrawing(this);panel.addView(drawing,new LinearLayout.LayoutParams(-1,px(410)));
    angles=text("X:  --.-°       Y:  --.-°",18,TEXT);panel.addView(angles);
    directions=text("گوشی را با صفحهٔ رو به بالا روی سطح بگذارید.",13,MUTED);panel.addView(directions);gap(panel,13);
    LinearLayout row=new LinearLayout(this);row.setOrientation(0);panel.addView(row,new LinearLayout.LayoutParams(-1,px(55)));
    start=button("▶ شروع سنجش",true);row.addView(start,new LinearLayout.LayoutParams(0,-1,1));
    calibrate=button("⊕ کالیبره",false);calibrate.setEnabled(false);row.addView(calibrate,new LinearLayout.LayoutParams(0,-1,1));
    start.setOnClickListener(v->{if(measuring)stopMeasure();else startMeasure();});
    calibrate.setOnClickListener(v->{if(observed){zeroX=rawX;zeroY=rawY;state=-1;candidate=-1;candidateAt=0;previousAnnouncementDistance=Double.NaN;updateLevel();Toast.makeText(this,"کالیبره شد",Toast.LENGTH_SHORT).show();}});
    gap(root,16);
    LinearLayout opt=new LinearLayout(this);opt.setOrientation(1);opt.setPadding(px(16),px(13),px(16),px(16));opt.setBackground(background(PANEL,0xff3A6849,20));root.addView(opt);
    TextView voiceTitle=text("🎙️ گویندهٔ طنز و تشویقی فارسی",19,TEXT);opt.addView(voiceTitle);gap(opt,10);
    Switch voiceToggle=new Switch(this);voiceToggle.setText("گویندهٔ بامزه روشن باشد");voiceToggle.setTextSize(15);voiceToggle.setTextColor(TEXT);voiceToggle.setChecked(voice);opt.addView(voiceToggle);
    voiceToggle.setOnCheckedChangeListener((sw,on)->{voice=on;prefs.edit().putBoolean("voice",on).apply();if(!on)stopVoice();});
    Switch guidedToggle=new Switch(this);
    guidedToggle.setText("راهنمای صوتی جهت‌دار (چپ، راست، بالا، پایین)");
    guidedToggle.setTextSize(14);guidedToggle.setTextColor(TEXT);guidedToggle.setChecked(guided);opt.addView(guidedToggle);
    guidedToggle.setOnCheckedChangeListener((sw,on)->{guided=on;prefs.edit().putBoolean("guided",on).apply();});
    Switch hapticToggle=new Switch(this);
    hapticToggle.setText("لرزش تشویقی و لرزش هنگام تراز کامل");
    hapticToggle.setTextSize(14);hapticToggle.setTextColor(TEXT);hapticToggle.setChecked(haptic);opt.addView(hapticToggle);
    hapticToggle.setOnCheckedChangeListener((sw,on)->{haptic=on;prefs.edit().putBoolean("haptic",on).apply();});

    gap(opt,8);
    TextView voiceSelectTitle=text("شخصیت گوینده (فارسی معیار)",15,GOLD);opt.addView(voiceSelectTitle);
    Spinner voices=new Spinner(this);
    String[] voiceNames={"بچهٔ شیطون و پرانرژی","دختر شیطون و بازیگوش","خانم شوخ‌طبع","مرد سینمایی با صدای بم"};
    ArrayAdapter<String> voiceAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,voiceNames);
    voices.setAdapter(voiceAdapter);
    voices.setSelection(voiceProfile);opt.addView(voices);
    voices.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
      public void onItemSelected(android.widget.AdapterView<?> parent,View item,int pos,long id){
        int profile=Math.max(0,Math.min(3,pos));
        if(profile!=voiceProfile){
          stopVoice();voiceProfile=profile;nearIdx=0;farIdx=0;successIdx=0;
          previousAnnouncementDistance=Double.NaN;
          prefs.edit().putInt("voiceProfile",profile).apply();
        }
      }
      public void onNothingSelected(android.widget.AdapterView<?> parent){}
    });
    opt.addView(text("لحن‌ها شخصیت‌پردازی‌شده‌اند؛ صدای مرد سینمایی شبیه‌سازی شخص واقعی نیست.",12,MUTED));

    TextView intervalLabel=text("فاصلهٔ تکرار جمله‌های طنز",14,MUTED);opt.addView(intervalLabel);
    Spinner options=new Spinner(this);
    String[] repeats={"هر ۳ ثانیه","هر ۵ ثانیه","هر ۸ ثانیه","هر ۱۲ ثانیه"};
    int[] repeatsMs={3000,5000,8000,12000};
    ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,repeats); options.setAdapter(adapter);
    options.setSelection(interval==3000?0:interval==8000?2:interval==12000?3:1);opt.addView(options);
    options.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
      public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){interval=repeatsMs[position];prefs.edit().putInt("repeat",interval).apply();}
      public void onNothingSelected(android.widget.AdapterView<?> p){}
    });
    
    Button testFar=button("😂 آزمایش شوخی وقتی دور است",false);opt.addView(testFar,new LinearLayout.LayoutParams(-1,px(48)));
    testFar.setOnClickListener(v->playClip(farAudio[voiceProfile][(farIdx++)%farAudio[voiceProfile].length],true));
    Button testNear=button("👏 آزمایش تشویق وقتی نزدیک است",false);opt.addView(testNear,new LinearLayout.LayoutParams(-1,px(48)));
    testNear.setOnClickListener(v->playClip(nearAudio[voiceProfile][(nearIdx++)%nearAudio[voiceProfile].length],true));
    Button testOk=button("🎉 آزمایش جشن تراز شدن",false);opt.addView(testOk,new LinearLayout.LayoutParams(-1,px(48)));
    testOk.setOnClickListener(v->playClip(successAudio[voiceProfile][(successIdx++)%successAudio[voiceProfile].length],true));
    opt.addView(text("صداها از قبل داخل برنامه‌اند و بدون اینترنت یا زبان فارسی گوشی پخش می‌شوند.",12,GOLD));
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
    gap(root,12);root.addView(text("تراز یار • نسخهٔ ۲٫۰ • حباب زنده، راهنمای صوتی و لرزش",11,GOLD));
  }
  void startMeasure() {
    if(sensor==null)return;
    measuring=true;observed=false;lowpassInit=false;zeroX=0;zeroY=0;state=-1;candidate=-1;candidateAt=0;previousAnnouncementDistance=Double.NaN;nearArmed=true;instructionCounter=0;
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
      stopVoice();
      if(state==1) {
        vibrateSuccess();
        drawing.celebrate();
      }
      if(voice)announceLevel();
    }
    status.setText(state==1?"✓  تراز است":state==0?"●  در حال تنظیم تراز":"در حال تثبیت حباب...");
    status.setTextColor(state==1?GREEN:state==0?0xffffc29a:GOLD);
    angles.setText(String.format(Locale.US,"X: %+.1f°      Y: %+.1f°",x,y));
    directions.setText(leveled?"آفرین! هر دو محور در محدودهٔ تراز هستند.":
      distance<=nearLimit?"نزدیک شدی! فقط یک تنظیم کوچولو باقی مانده.":correctionText());
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
  // Select the joke by calibrated two-axis error and movement trend.
  // The announcement interval prevents rapid chatter; success is immediate after stability.
  // Deliver a direction in the selected character's voice, alternating
  // with humour and encouragement; do not interrupt clips mid-sentence.
  void announceLevel(){
    if(!voice || !resumed || !observed)return;
    if(state==1){
      previousAnnouncementDistance=0;
      playClip(successAudio[voiceProfile][(successIdx++)%successAudio[voiceProfile].length],false);
      return;
    }
    if(state!=0)return;
    double distance=Math.max(Math.abs(x),Math.abs(y));
    double nearLimit=Math.max(2.5,tolerance*3.0);
    boolean movingCloser=Double.isFinite(previousAnnouncementDistance) && distance<previousAnnouncementDistance-.75;
    boolean movingFarther=Double.isFinite(previousAnnouncementDistance) && distance>previousAnnouncementDistance+.75;
    int id;
    int round=instructionCounter++;
    if(distance<=nearLimit || movingCloser){
      id=nearAudio[voiceProfile][(nearIdx++)%nearAudio[voiceProfile].length];
    }else if(movingFarther){
      id=farAudio[voiceProfile][(farIdx++)%farAudio[voiceProfile].length];
    }else if(guided && round%2==0){
      id=directionAudio[voiceProfile][correctionDirection()];
    }else{
      id=farAudio[voiceProfile][(farIdx++)%farAudio[voiceProfile].length];
    }
    previousAnnouncementDistance=distance;
    playClip(id,false);
  }
  void playClip(int resource,boolean preview){
    if(!resumed || (!voice && !preview))return;
    stopVoice();
    try{
      final MediaPlayer next=MediaPlayer.create(this,resource);
      if(next==null){Toast.makeText(this,"بارگذاری صدای طنز ممکن نشد",Toast.LENGTH_SHORT).show();return;}
      player=next;speaking=true;lastVoice=SystemClock.elapsedRealtime();
      next.setOnCompletionListener(mp->{if(player==mp){player=null;speaking=false;}mp.release();});
      next.setOnErrorListener((mp,what,extra)->{if(player==mp){player=null;speaking=false;}mp.release();return true;});
      next.start();
      try{next.setPlaybackParams(new PlaybackParams().setSpeed(voiceSpeed[voiceProfile]).setPitch(voicePitch[voiceProfile]));}catch(Exception ignored){} 
    }catch(Exception e){speaking=false;player=null;Toast.makeText(this,"پخش صدای طنز ممکن نشد",Toast.LENGTH_SHORT).show();}
  }
  void stopVoice(){
    speaking=false;MediaPlayer old=player;player=null;
    if(old!=null){try{old.stop();}catch(Exception ignored){}try{old.release();}catch(Exception ignored){}}
  }
  @Override protected void onResume(){super.onResume();resumed=true;if(measuring&&sensor!=null)manager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_UI);}
  @Override protected void onPause(){resumed=false;if(manager!=null)manager.unregisterListener(this);stopVoice();super.onPause();}
  @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);stopVoice();super.onDestroy();}

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
      tube(c,left,right,h-72,dx,"X");
      tube(c,left,right,h-26,dy,"Y");
      if(Math.abs(bubbleX-desiredX)>0.3||Math.abs(bubbleY-desiredY)>0.3)postInvalidateOnAnimation();
    }
  }
}
