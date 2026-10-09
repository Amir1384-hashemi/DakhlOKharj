package com.trazyar.level;
public final class TutorialContentTest {
  static int tests=0;
  static void check(String label,boolean pass){if(!pass)throw new AssertionError(label);tests++;}
  public static void main(String[] args){
    check("seven pages",TutorialContent.count()==7);
    check("titles match bodies",TutorialContent.TITLES.length==TutorialContent.DETAILS.length);
    check("first page intro",TutorialContent.toolForPage(0)==-1);
    for(int mode=0;mode<4;mode++){
      int page=TutorialContent.pageForTool(mode);
      check("current tool tutorial",page==mode+1);
      check("open requested tool",TutorialContent.toolForPage(page)==mode);
      check("nonempty help",TutorialContent.DETAILS[page].length()>80);
    }
    check("beep guide returns to level",TutorialContent.toolForPage(5)==0);
    check("calibration guide no tool",TutorialContent.toolForPage(6)==-1);
    check("unknown mode intro",TutorialContent.pageForTool(-1)==0);
    for(String body:TutorialContent.DETAILS){
      check("all tutorial bodies present",body!=null&&body.length()>80);
    }
    System.out.println("TrazYar tutorial tests passed: "+tests);
  }
}
