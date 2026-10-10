package com.trazyar.level;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Offline use-case wizard; progress persists between app sessions. */
public final class ProjectCoachActivity extends Activity {
  static final int BG=0xff081A13,PANEL=0xff112A20,GREEN=0xffA8ED65,
    GOLD=0xffE8C87D,WHITE=0xffF1F9E9,MUTED=0xffBDD0B7;
  static final String PREF_FILE="trazyar",PREF_PROJECT="coachProjectV31",PREF_STEP="coachStepV31";
  SharedPreferences prefs;
  int project,step;
  TextView heading,stepText,subheading,note,progress,stepPreview;
  Button backward,forward,launch;
  ScrollView stageScroll;
  Button[] selectors=new Button[ProjectCoachContent.count()];
  int px(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
  GradientDrawable background(int fill,int outline){
    GradientDrawable g=new GradientDrawable();
    g.setColor(fill);g.setCornerRadius(px(13));
    if(outline!=0)g.setStroke(px(1),outline);
    return g;
  }
  TextView text(String s,int font,int color){
    TextView t=new TextView(this);
    t.setText(s);t.setTextSize(font);t.setTextColor(color);
    t.setGravity(Gravity.RIGHT);t.setTextDirection(View.TEXT_DIRECTION_RTL);
    t.setPadding(px(8),px(7),px(8),px(7));
    return t;
  }
  Button button(String s,boolean primary){
    Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);
    b.setMinWidth(0);b.setPadding(px(2),0,px(2),0);
    b.setTextColor(primary?BG:WHITE);
    b.setBackground(background(primary?GREEN:0xff294935,primary?0:0xff5A8267));
    return b;
  }
  @Override public void onCreate(Bundle saved){
    super.onCreate(saved);
    getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
    prefs=getSharedPreferences(PREF_FILE,MODE_PRIVATE);
    int chosen=getIntent()!=null?getIntent().getIntExtra("coach_case",-1):-1;
    project=ProjectCoachContent.valid(chosen)?chosen:prefs.getInt(PREF_PROJECT,0);
    if(!ProjectCoachContent.valid(project))project=0;
    step=ProjectCoachContent.valid(chosen)?0:
      ProjectCoachContent.clampStep(project,prefs.getInt(PREF_STEP,0));
    makeUI();redraw();
  }
  void makeUI(){
    LinearLayout root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    root.setPadding(px(13),px(11),px(13),px(12));
    root.setBackgroundColor(BG);setContentView(root);

    Button back=button("‹ بازگشت",false);
    root.addView(back,new LinearLayout.LayoutParams(-1,px(43)));
    back.setOnClickListener(v->finish());
    TextView appTitle=text("🎯 راهنمای انجام کار با تراز یار",22,GREEN);
    appTitle.setGravity(Gravity.CENTER);appTitle.setTypeface(null,Typeface.BOLD);
    root.addView(appTitle);
    TextView caption=text("اول نوع کار را انتخاب کنید؛ مراحل را انجام دهید و مستقیم به ابزار مناسب بروید.",12,MUTED);
    caption.setGravity(Gravity.CENTER);root.addView(caption);

    TextView choose=text("چه کاری می‌خواهید انجام دهید؟",16,GOLD);
    choose.setTypeface(null,Typeface.BOLD);root.addView(choose);
    for(int row=0;row<3;row++){
      LinearLayout group=new LinearLayout(this);
      group.setOrientation(LinearLayout.HORIZONTAL);group.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
      root.addView(group,new LinearLayout.LayoutParams(-1,px(48)));
      for(int col=0;col<2;col++){
        final int id=row*2+col;
        Button pick=button(ProjectCoachContent.ICONS[id]+" "+ProjectCoachContent.TITLES[id],false);
        pick.setTextSize(12);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,px(44),1);
        cp.setMargins(px(2),0,px(2),0);
        group.addView(pick,cp);selectors[id]=pick;
        pick.setOnClickListener(v->chooseProject(id));
      }
    }

    stageScroll=new ScrollView(this);
    stageScroll.setFillViewport(false);
    stageScroll.setVerticalScrollBarEnabled(true);
    LinearLayout card=new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    card.setPadding(px(11),px(13),px(11),px(18));
    card.setBackground(background(PANEL,0xff4F7255));
    stageScroll.addView(card);
    LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1);
    sp.topMargin=px(8);root.addView(stageScroll,sp);

    heading=text("",18,GREEN);heading.setTypeface(null,Typeface.BOLD);card.addView(heading);
    subheading=text("",13,WHITE);card.addView(subheading);
    progress=text("",13,GOLD);card.addView(progress);
    stepText=text("",17,WHITE);stepText.setLineSpacing(px(5),1.12f);
    stepText.setBackground(background(0xff244230,0xff4D7860));
    LinearLayout.LayoutParams stp=new LinearLayout.LayoutParams(-1,-2);
    stp.topMargin=px(12);card.addView(stepText,stp);
    stepPreview=text("",14,GOLD);
    LinearLayout.LayoutParams prp=new LinearLayout.LayoutParams(-1,-2);
    prp.topMargin=px(9);card.addView(stepPreview,prp);
    note=text("",12,MUTED);
    LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);
    np.topMargin=px(12);card.addView(note,np);

    launch=button("بازکردن ابزار مربوط",true);
    LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,px(49));
    ap.topMargin=px(8);root.addView(launch,ap);
    launch.setOnClickListener(v->openTool());

    LinearLayout nav=new LinearLayout(this);
    nav.setOrientation(LinearLayout.HORIZONTAL);
    nav.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    LinearLayout.LayoutParams vnp=new LinearLayout.LayoutParams(-1,px(50));
    vnp.topMargin=px(7);root.addView(nav,vnp);
    backward=button("مرحله قبل",false);
    forward=button("انجام شد، بعدی",true);
    for(Button b:new Button[]{backward,forward}){
      LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);
      p.setMargins(px(3),0,px(3),0);nav.addView(b,p);
    }
    backward.setOnClickListener(v->{if(step>0){step--;saveStep();redraw();}});
    forward.setOnClickListener(v->{
      if(step<ProjectCoachContent.totalSteps(project)-1){
        step++;saveStep();redraw();
      }else{
        new AlertDialog.Builder(this)
          .setTitle("پایان راهنمای پروژه")
          .setMessage("مراحل آموزشی به پایان رسید. نتیجه را در ابزار مربوط کنترل و در صورت نیاز ذخیره کنید. می‌توانید این آموزش را از ابتدا تکرار کنید.")
          .setPositiveButton("شروع از اول",(d,x)->{step=0;saveStep();redraw();})
          .setNegativeButton("بستن",null).show();
      }
    });
  }
  void chooseProject(int id){
    if(!ProjectCoachContent.valid(id))return;
    if(id!=project){project=id;step=0;saveStep();}
    redraw();
  }
  void saveStep(){
    prefs.edit().putInt(PREF_PROJECT,project).putInt(PREF_STEP,step).apply();
  }
  void redraw(){
    heading.setText(ProjectCoachContent.TITLES[project]);
    subheading.setText(ProjectCoachContent.SUBTITLES[project]);
    progress.setText("مرحله "+(step+1)+" از "+ProjectCoachContent.totalSteps(project)
      +"  •  پیشرفت: "+(int)Math.round(100.0*(step+1)/ProjectCoachContent.totalSteps(project))+"٪");
    stepText.setText("گام "+(step+1)+"\n\n"+ProjectCoachContent.STEPS[project][step]);
    String next=step+1<ProjectCoachContent.totalSteps(project)?
      "گام بعد: "+ProjectCoachContent.STEPS[project][step+1]:
      "تمام مراحل آموزشی مرور شد؛ اکنون نتیجه را با ابزار مرجع هم بررسی کنید.";
    stepPreview.setText(next);
    note.setText("توجه: "+ProjectCoachContent.CAUTIONS[project]);
    backward.setEnabled(step>0);backward.setAlpha(step>0?1f:.4f);
    forward.setText(step==ProjectCoachContent.totalSteps(project)-1?"پایان راهنما":"انجام شد، بعدی");
    int action=ProjectCoachContent.ACTIONS[project];
    launch.setText(action==4?"▣ ورود مستقیم به نصاب‌یار حرفه‌ای":
      "◉ باز کردن "+new String[]{"تراز حبابی","شیب‌سنج","زاویه‌سنج","گونیا"}[action]);
    for(int i=0;i<selectors.length;i++){
      boolean active=i==project;
      selectors[i].setTextColor(active?BG:WHITE);
      selectors[i].setBackground(background(active?GREEN:0xff294935,active?0:0xff5A8267));
    }
    stageScroll.post(()->stageScroll.scrollTo(0,0));
  }
  void openTool(){
    int action=ProjectCoachContent.ACTIONS[project];
    Intent intent;
    if(action==4){
      intent=new Intent(this,InstallerActivity.class);
    }else{
      intent=new Intent(this,MainActivity.class);
      intent.putExtra("guide_tool",action);
    }
    // Intentionally do not CLEAR_TOP: Android Back returns to this step-by-step wizard.
    startActivity(intent);
  }
}
