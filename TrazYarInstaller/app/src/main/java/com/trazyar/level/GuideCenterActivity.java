package com.trazyar.level;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Persistent dedicated Persian help centre; offline, with direct tool navigation. */
public final class GuideCenterActivity extends Activity {
  static final int BG=0xff081A13,PANEL=0xff112A20,GREEN=0xffA8ED65,
    GOLD=0xffE8C87D,WHITE=0xffF1F9E9,MUTED=0xffC0D3BA;
  int topic=GuideContent.INTRO;
  TextView sectionTitle,progress,lead,steps,tip,footnote;
  ScrollView contentScroll;
  Button previous,next,openTool;
  TutorialSketch illustration;

  int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
  GradientDrawable background(int fill,int border){
    GradientDrawable d=new GradientDrawable();
    d.setColor(fill);d.setCornerRadius(dp(14));
    if(border!=0)d.setStroke(dp(1),border);
    return d;
  }
  TextView text(String value,int sp,int color){
    TextView t=new TextView(this);
    t.setText(value);t.setTextSize(sp);t.setTextColor(color);
    t.setTextDirection(View.TEXT_DIRECTION_RTL);
    t.setGravity(Gravity.RIGHT);
    t.setPadding(dp(8),dp(5),dp(8),dp(5));
    return t;
  }
  Button button(String title,boolean primary){
    Button b=new Button(this);b.setText(title);b.setTextSize(14);b.setAllCaps(false);
    b.setMinWidth(0);b.setPadding(dp(2),0,dp(2),0);
    b.setTextColor(primary?BG:WHITE);
    b.setBackground(background(primary?GREEN:0xff254934,primary?0:0xff4D7459));
    return b;
  }
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(BG);
    int selected=getIntent()==null?0:getIntent().getIntExtra("guide_topic",0);
    topic=GuideContent.valid(selected)?selected:0;
    drawUI();
    showTopic(topic);
  }
  void drawUI(){
    LinearLayout root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    root.setPadding(dp(13),dp(12),dp(13),dp(11));
    root.setBackgroundColor(BG);
    setContentView(root);

    Button back=button("‹ بازگشت به برنامه",false);
    root.addView(back,new LinearLayout.LayoutParams(-1,dp(44)));
    back.setOnClickListener(v->finish());
    TextView header=text("📚 مرکز آموزش تراز یار",23,GREEN);
    header.setGravity(Gravity.CENTER);header.setTypeface(null,Typeface.BOLD);
    root.addView(header);
    TextView tagline=text("آشنایی با برنامه، مراحل استفاده از ابزارها و راهنمای حل مشکل — کاملاً فارسی و بدون اینترنت",12,MUTED);
    tagline.setGravity(Gravity.CENTER);root.addView(tagline);

    Button selector=button("☷  فهرست ۱۲ موضوع آموزشی",true);
    LinearLayout.LayoutParams selectParams=new LinearLayout.LayoutParams(-1,dp(50));
    selectParams.topMargin=dp(7);
    root.addView(selector,selectParams);
    selector.setOnClickListener(v->new AlertDialog.Builder(this)
      .setTitle("یک بخش برای آموزش انتخاب کنید")
      .setItems(GuideContent.TITLES,(d,which)->showTopic(which))
      .setNegativeButton("بستن",null).show());

    LinearLayout fast=new LinearLayout(this);
    fast.setOrientation(LinearLayout.HORIZONTAL);
    fast.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    root.addView(fast,new LinearLayout.LayoutParams(-1,dp(45)));
    String[] labels={"زاویه‌سنج","گونیا","نصاب‌یار"};
    int[] fastTopics={GuideContent.ANGLE,GuideContent.SQUARE,GuideContent.INSTALLER};
    for(int i=0;i<labels.length;i++){
      int chapter=fastTopics[i];
      Button jump=button(labels[i],false);jump.setTextSize(12);
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);
      lp.setMargins(dp(3),0,dp(3),0);fast.addView(jump,lp);
      jump.setOnClickListener(v->showTopic(chapter));
    }

    contentScroll=new ScrollView(this);
    contentScroll.setFillViewport(false);contentScroll.setVerticalScrollBarEnabled(true);
    LinearLayout reading=new LinearLayout(this);
    reading.setOrientation(LinearLayout.VERTICAL);
    reading.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    reading.setPadding(dp(9),dp(10),dp(9),dp(20));
    reading.setBackground(background(PANEL,0xff416A50));
    contentScroll.addView(reading);
    LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,0,1);
    cp.topMargin=dp(9);root.addView(contentScroll,cp);

    progress=text("",12,GOLD);reading.addView(progress);
    sectionTitle=text("",21,GREEN);
    sectionTitle.setTypeface(null,Typeface.BOLD);reading.addView(sectionTitle);
    lead=text("",15,WHITE);lead.setLineSpacing(dp(2),1.08f);reading.addView(lead);
    illustration=new TutorialSketch();
    LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,dp(143));
    gp.topMargin=dp(7);gp.bottomMargin=dp(8);
    reading.addView(illustration,gp);
    TextView how=text("راهنمای مرحله‌به‌مرحله",16,GOLD);
    how.setTypeface(null,Typeface.BOLD);reading.addView(how);
    steps=text("",15,WHITE);steps.setLineSpacing(dp(3),1.15f);
    reading.addView(steps);
    tip=text("",13,GOLD);tip.setBackground(background(0xff1D4230,0xff51795B));
    LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);
    tp.topMargin=dp(10);reading.addView(tip,tp);

    openTool=button("بازکردن ابزار مربوط",true);
    LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(49));
    ap.topMargin=dp(8);root.addView(openTool,ap);
    openTool.setOnClickListener(v->launchTool());

    LinearLayout nav=new LinearLayout(this);
    nav.setOrientation(LinearLayout.HORIZONTAL);
    nav.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(49));
    np.topMargin=dp(7);root.addView(nav,np);
    previous=button("← قبلی",false);next=button("بعدی →",false);
    for(Button b:new Button[]{previous,next}){
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
      lp.setMargins(dp(3),0,dp(3),0);nav.addView(b,lp);
    }
    previous.setOnClickListener(v->showTopic(topic-1));
    next.setOnClickListener(v->showTopic(topic+1));
  }
  void showTopic(int index){
    if(!GuideContent.valid(index))return;
    topic=index;
    sectionTitle.setText(GuideContent.ICONS[index]+"  "+GuideContent.TITLES[index]);
    progress.setText("موضوع "+(index+1)+" از "+GuideContent.count());
    lead.setText(GuideContent.LEADS[index]);
    steps.setText(GuideContent.STEPS[index]);
    tip.setText(GuideContent.TIPS[index]);
    illustration.setTopic(index);
    previous.setEnabled(index>0);
    previous.setAlpha(index>0?1f:.4f);
    next.setEnabled(index<GuideContent.count()-1);
    next.setAlpha(index<GuideContent.count()-1?1f:.4f);
    int action=GuideContent.ACTIONS[index];
    if(action<0)openTool.setVisibility(View.GONE);
    else{
      openTool.setVisibility(View.VISIBLE);
      openTool.setText(action<4?"باز کردن "+new String[]{"تراز حبابی","شیب‌سنج","زاویه‌سنج","گونیا"}[action]:
        action==4?"ورود به نصاب‌یار حرفه‌ای":"بازکردن دفترچهٔ اندازه‌گیری");
    }
    contentScroll.post(()->contentScroll.scrollTo(0,0));
  }
  void launchTool(){
    int action=GuideContent.ACTIONS[topic];
    if(action<0)return;
    Intent intent;
    if(action==4)intent=new Intent(this,InstallerActivity.class);
    else{
      intent=new Intent(this,MainActivity.class);
      if(action==5)intent.putExtra("guide_journal",true);
      else intent.putExtra("guide_tool",action);
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    startActivity(intent);
    finish();
  }

  /** On-device vector sketches illustrating what each topic does. */
  final class TutorialSketch extends View{
    final Paint p=new Paint(3);int page=0;
    TutorialSketch(){super(GuideCenterActivity.this);setBackground(background(0xff1C3C2B,0));}
    void setTopic(int next){page=next;invalidate();}
    void line(Canvas c,float x,float y,float xx,float yy,int color,float weight){
      p.setColor(color);p.setStrokeWidth(weight);p.setStrokeCap(Paint.Cap.ROUND);
      p.setStyle(Paint.Style.STROKE);c.drawLine(x,y,xx,yy,p);
    }
    void circle(Canvas c,float x,float y,float rad,int color,boolean fill){
      p.setColor(color);p.setStyle(fill?Paint.Style.FILL:Paint.Style.STROKE);
      p.setStrokeWidth(2.4f);c.drawCircle(x,y,rad,p);
    }
    void label(Canvas c,String s,float x,float y,int color,float size){
      p.setColor(color);p.setStyle(Paint.Style.FILL);p.setTextSize(size);
      p.setTextAlign(Paint.Align.CENTER);c.drawText(s,x,y,p);
    }
    @Override protected void onDraw(Canvas canvas){
      super.onDraw(canvas);
      if(getWidth()==0||getHeight()==0)return;
      canvas.save();canvas.scale(getWidth()/330f,getHeight()/142f);
      int green=GREEN,gold=GOLD,white=WHITE;
      if(page==GuideContent.LEVEL||page==GuideContent.BEEP||
          page==GuideContent.CALIBRATION||page==GuideContent.START){
        circle(canvas,165,68,54,gold,false);
        circle(canvas,165,68,37,0xff668E68,false);
        line(canvas,111,68,219,68,0xff70916C,1);
        line(canvas,165,14,165,122,0xff70916C,1);
        circle(canvas,165,68,19,green,true);
        label(canvas,"X = ۰°     Y = ۰°",165,135,white,13);
      }else if(page==GuideContent.SLOPE){
        line(canvas,42,112,288,112,gold,3);
        line(canvas,58,112,267,35,green,7);
        line(canvas,267,35,267,112,gold,2);
        label(canvas,"شیب = زاویه و درصد",165,132,white,16);
      }else if(page==GuideContent.ANGLE||page==GuideContent.SQUARE){
        line(canvas,75,108,261,108,gold,4);
        line(canvas,75,108,page==GuideContent.SQUARE?75:221,20,green,6);
        circle(canvas,75,108,7,white,true);
        label(canvas,page==GuideContent.SQUARE?"۹۰° — بررسی قائمه":"∠ — نسبت به افق",183,130,white,16);
      }else if(page==GuideContent.INSTALLER||page==GuideContent.REPORT){
        for(int i=0;i<4;i++){
          float x=i%2==0?108:223,y=i<2?32:100;
          circle(canvas,x,y,21,gold,false);
          circle(canvas,x,y,9,green,true);
          label(canvas,i==0?"۱":i==1?"۲":i==2?"۳":"۴",x,y+4,BG,13);
        }
        line(canvas,108,32,223,32,white,2);line(canvas,108,100,223,100,white,2);
        line(canvas,108,32,108,100,white,2);line(canvas,223,32,223,100,white,2);
        label(canvas,"چهار پایه — قبل / بعد",167,139,white,14);
      }else if(page==GuideContent.JOURNAL){
        for(int i=0;i<3;i++){
          line(canvas,70,36+i*30,260,36+i*30,gold,3);
          circle(canvas,58,36+i*30,5,green,true);
        }
        label(canvas,"دفترچه و ثبت نتیجه",165,129,white,16);
      }else if(page==GuideContent.ISSUES){
        circle(canvas,165,63,46,gold,false);label(canvas,"؟",165,87,green,66);
      }else{
        circle(canvas,81,70,34,gold,false);circle(canvas,81,70,12,green,true);
        line(canvas,157,107,256,39,green,5);line(canvas,157,107,256,107,gold,3);
        label(canvas,"۴ ابزار + نصاب‌یار",165,134,white,16);
      }
      canvas.restore();
    }
  }
}
