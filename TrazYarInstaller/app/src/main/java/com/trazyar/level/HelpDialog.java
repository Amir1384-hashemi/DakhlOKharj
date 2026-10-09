package com.trazyar.level;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** Seven illustrated, offline Persian training screens with previous/next and launch-tool buttons. */
public final class HelpDialog {
  private HelpDialog(){}
  static final int BG=0xff0E251A, PANEL=0xff183628, GOLD=0xffE8C87D,
    GREEN=0xffB2F27C, WHITE=0xffF3F9EB, MUTED=0xffC4D7BF;

  private static int dp(Activity a,float x){return (int)(x*a.getResources().getDisplayMetrics().density+.5f);}
  private static GradientDrawable rounded(int color,int radius){
    GradientDrawable drawable=new GradientDrawable();
    drawable.setColor(color);drawable.setCornerRadius(radius);
    return drawable;
  }
  private static TextView label(Activity a,int sp,int color){
    TextView t=new TextView(a);
    t.setTextColor(color);t.setTextSize(sp);
    t.setGravity(Gravity.RIGHT);
    t.setTextDirection(View.TEXT_DIRECTION_RTL);
    t.setPadding(dp(a,5),dp(a,3),dp(a,5),dp(a,3));
    return t;
  }
  private static Button makeButton(Activity a,String name,boolean primary){
    Button b=new Button(a);
    b.setAllCaps(false);b.setText(name);b.setTextSize(14);
    b.setMinHeight(dp(a,45));b.setMinWidth(0);
    b.setPadding(dp(a,4),0,dp(a,4),0);
    b.setTextColor(primary?BG:WHITE);
    b.setBackground(rounded(primary?GREEN:0xff315341,dp(a,12)));
    return b;
  }

  public static void show(Activity activity,int startingPage,IntConsumer selectTool,Runnable onClosed){
    final int[] page={Math.max(0,Math.min(TutorialContent.count()-1,startingPage))};
    LinearLayout layout=new LinearLayout(activity);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    layout.setPadding(dp(activity,18),dp(activity,18),dp(activity,18),dp(activity,18));
    layout.setBackground(rounded(BG,dp(activity,22)));

    TextView heading=label(activity,21,GREEN);
    heading.setTypeface(null,1);
    layout.addView(heading);
    TextView counter=label(activity,12,GOLD);
    layout.addView(counter);

    Illustration graphic=new Illustration(activity);
    LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,dp(activity,165));
    gp.topMargin=dp(activity,9);gp.bottomMargin=dp(activity,9);
    layout.addView(graphic,gp);

    TextView instructions=label(activity,15,WHITE);
    instructions.setLineSpacing(dp(activity,3),1.05f);
    layout.addView(instructions);

    Button launch=makeButton(activity,"باز کردن این ابزار",true);
    LinearLayout.LayoutParams launchParams=new LinearLayout.LayoutParams(-1,dp(activity,48));
    launchParams.topMargin=dp(activity,15);
    layout.addView(launch,launchParams);

    LinearLayout nav=new LinearLayout(activity);
    nav.setOrientation(LinearLayout.HORIZONTAL);
    nav.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(activity,48));
    np.topMargin=dp(activity,14);
    layout.addView(nav,np);
    Button previous=makeButton(activity,"قبلی",false);
    Button next=makeButton(activity,"بعدی",true);
    Button close=makeButton(activity,"بستن",false);
    LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-1,1);
    bp.setMargins(dp(activity,3),0,dp(activity,3),0);
    nav.addView(previous,new LinearLayout.LayoutParams(bp));
    nav.addView(next,new LinearLayout.LayoutParams(bp));
    nav.addView(close,new LinearLayout.LayoutParams(bp));

    ScrollView scroll=new ScrollView(activity);
    scroll.setFillViewport(false);
    scroll.setVerticalScrollBarEnabled(false);
    scroll.addView(layout);
    AlertDialog dialog=new AlertDialog.Builder(activity).setView(scroll).create();

    Runnable render=()->{
      int index=page[0];
      heading.setText(TutorialContent.TITLES[index]);
      counter.setText("آموزش "+(index+1)+" از "+TutorialContent.count());
      instructions.setText(TutorialContent.DETAILS[index]);
      graphic.setPage(index);
      previous.setEnabled(index>0);
      previous.setAlpha(index>0?1f:.4f);
      next.setText(index==TutorialContent.count()-1?"پایان":"بعدی");
      int tool=TutorialContent.toolForPage(index);
      launch.setVisibility(tool<0?View.GONE:View.VISIBLE);
      if(tool>=0){
        launch.setText("باز کردن "+new String[]{"تراز حبابی","شیب‌سنج","زاویه‌سنج","گونیا"}[tool]);
      }
    };
    previous.setOnClickListener(v->{if(page[0]>0){page[0]--;render.run();}});
    next.setOnClickListener(v->{if(page[0]<TutorialContent.count()-1){page[0]++;render.run();}else dialog.dismiss();});
    close.setOnClickListener(v->dialog.dismiss());
    launch.setOnClickListener(v->{
      int tool=TutorialContent.toolForPage(page[0]);
      dialog.dismiss();
      if(tool>=0&&selectTool!=null)selectTool.accept(tool);
    });
    dialog.setOnDismissListener(d->{if(onClosed!=null)onClosed.run();});
    render.run();
    dialog.show();
    if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
  }

  /** Small vector drawings — no downloaded images, internet, or font dependencies. */
  static final class Illustration extends View {
    final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    int page=0;
    Illustration(Activity a){super(a);setBackground(rounded(PANEL,18));}
    void setPage(int index){page=index;invalidate();}
    void line(Canvas c,float x,float y,float x2,float y2,int color,float width){
      p.reset();p.setAntiAlias(true);p.setColor(color);p.setStrokeWidth(width);
      p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
      c.drawLine(x,y,x2,y2,p);
    }
    void circle(Canvas c,float cx,float cy,float radius,int color,boolean fill){
      p.reset();p.setAntiAlias(true);p.setColor(color);
      p.setStyle(fill?Paint.Style.FILL:Paint.Style.STROKE);
      p.setStrokeWidth(3);c.drawCircle(cx,cy,radius,p);
    }
    void rect(Canvas c,float x,float y,float xx,float yy,int color,boolean fill){
      p.reset();p.setAntiAlias(true);p.setColor(color);
      p.setStyle(fill?Paint.Style.FILL:Paint.Style.STROKE);p.setStrokeWidth(3);
      c.drawRoundRect(x,y,xx,yy,6,6,p);
    }
    void title(Canvas c,String s,float x,float y,int color,int size){
      p.reset();p.setAntiAlias(true);p.setColor(color);p.setTextSize(size);
      p.setTextAlign(Paint.Align.CENTER);c.drawText(s,x,y,p);
    }
    void arrow(Canvas c,float x,float y,float xx,float yy){
      line(c,x,y,xx,yy,GREEN,4);
      line(c,xx,yy,xx-8,yy-8,GREEN,3);
      line(c,xx,yy,xx-8,yy+8,GREEN,3);
    }
    @Override protected void onDraw(Canvas raw){
      super.onDraw(raw);
      float w=getWidth(),h=getHeight();
      if(w==0||h==0)return;
      raw.save();
      raw.scale(w/360f,h/165f);
      Canvas c=raw;
      if(page==0){
        circle(c,83,78,40,GOLD,false);circle(c,83,78,17,GREEN,true);
        line(c,83,37,83,119,GOLD,1.6f);line(c,42,78,124,78,GOLD,1.6f);
        line(c,160,112,248,48,GREEN,5);
        line(c,160,112,248,112,GOLD,3);
        title(c,"۴ ابزار در یک برنامه",241,145,WHITE,16);
      }else if(page==1){
        circle(c,180,79,64,GOLD,false);
        circle(c,180,79,39,0xff578361,false);
        line(c,180,11,180,146,0xff8BAA80,1.5f);
        line(c,105,79,255,79,0xff8BAA80,1.5f);
        circle(c,180,79,24,GREEN,true);circle(c,173,70,7,WHITE,true);
        title(c,"مرکز حباب = سطح تراز",180,154,WHITE,15);
      }else if(page==2){
        line(c,34,130,325,130,MUTED,3);
        line(c,60,130,290,48,GREEN,7);
        line(c,290,48,290,130,GOLD,2);
        p.setColor(GOLD);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);
        c.drawArc(new RectF(66,73,170,176),-46,47,false,p);
        title(c,"۳۰°",146,118,WHITE,19);
      }else if(page==3){
        p.setColor(GOLD);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);
        c.drawArc(new RectF(92,13,268,145),180,180,false,p);
        line(c,92,145,268,145,MUTED,3);
        line(c,180,145,238,54,GREEN,7);
        circle(c,180,145,7,GREEN,true);
        title(c,"زاویه نسبت به افق",180,164,WHITE,14);
      }else if(page==4){
        line(c,90,40,90,135,GREEN,8);
        line(c,90,135,265,135,GOLD,8);
        line(c,90,112,114,112,WHITE,3);
        line(c,114,112,114,135,WHITE,3);
        title(c,"۹۰°",179,103,WHITE,24);
      }else if(page==5){
        for(int i=0;i<4;i++)rect(c,18+i*27,63,27+i*27,92,MUTED,true);
        for(int i=0;i<5;i++)rect(c,139+i*31,53,161+i*31,103,GREEN,true);
        rect(c,18,122,343,136,GOLD,true);
        title(c,"دور",70,41,WHITE,14);
        title(c,"نزدیک",209,41,WHITE,14);
        title(c,"تراز کامل",180,158,WHITE,13);
      }else{
        rect(c,106,19,253,132,GOLD,false);
        circle(c,180,75,25,0xff326547,false);
        circle(c,180,75,11,GREEN,true);
        line(c,180,40,180,110,GREEN,1.7f);
        line(c,147,75,213,75,GREEN,1.7f);
        title(c,"مرجع واقعاً تراز",180,159,WHITE,14);
      }
      raw.restore();
    }
  }
}
