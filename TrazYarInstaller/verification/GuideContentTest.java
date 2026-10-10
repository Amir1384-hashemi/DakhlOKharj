package com.trazyar.level;
public final class GuideContentTest {
  static int checks=0;
  static void check(String message,boolean ok){
    if(!ok)throw new AssertionError(message);checks++;
  }
  public static void main(String[] args){
    check("12 help chapters",GuideContent.count()==12);
    check("chapter headings",GuideContent.TITLES.length==GuideContent.STEPS.length);
    check("chapter descriptions",GuideContent.LEADS.length==GuideContent.count());
    check("chapter tips",GuideContent.TIPS.length==GuideContent.count());
    check("chapter actions",GuideContent.ACTIONS.length==GuideContent.count());
    check("chapter icons",GuideContent.ICONS.length==GuideContent.count());
    for(int i=0;i<GuideContent.count();i++){
      check("chapter title "+i,GuideContent.TITLES[i].length()>3);
      check("chapter description "+i,GuideContent.LEADS[i].length()>20);
      check("chapter complete "+i,GuideContent.STEPS[i].length()>150);
      check("chapter tip "+i,GuideContent.TIPS[i].length()>15);
      check("chapter action range "+i,GuideContent.ACTIONS[i]>=-1&&GuideContent.ACTIONS[i]<=5);
    }
    check("about app",GuideContent.STEPS[GuideContent.INTRO].contains("تراز یار"));
    check("slope",GuideContent.STEPS[GuideContent.SLOPE].contains("درصد"));
    check("protractor",GuideContent.STEPS[GuideContent.ANGLE].contains("عمودی"));
    check("square",GuideContent.STEPS[GuideContent.SQUARE].contains("۹۰"));
    check("installer directions",GuideContent.STEPS[GuideContent.INSTALLER].contains("عقب"));
    check("installer before after",GuideContent.STEPS[GuideContent.INSTALLER].contains("قبل")&&GuideContent.STEPS[GuideContent.INSTALLER].contains("بعد"));
    check("beep only in level",GuideContent.STEPS[GuideContent.BEEP].contains("فقط")&&GuideContent.STEPS[GuideContent.BEEP].contains("تراز حبابی"));
    check("reports",GuideContent.STEPS[GuideContent.REPORT].contains("دفترچه"));
    check("calibration",GuideContent.STEPS[GuideContent.CALIBRATION].contains("۱۸۰"));
    check("intro action",GuideContent.ACTIONS[GuideContent.INTRO]==-1);
    check("start journal action",GuideContent.ACTIONS[GuideContent.JOURNAL]==5);
    for(int i=0;i<4;i++){
      check("tool pages",GuideContent.forTool(i)==i+2);
      check("tool nav",GuideContent.ACTIONS[GuideContent.forTool(i)]==i);
    }
    check("unknown tool fallback",GuideContent.forTool(-1)==0);
    check("invalid chapter rejected",!GuideContent.valid(12)&&!GuideContent.valid(-1));
    System.out.println("TrazYar complete guide tests passed: "+checks);
  }
}
