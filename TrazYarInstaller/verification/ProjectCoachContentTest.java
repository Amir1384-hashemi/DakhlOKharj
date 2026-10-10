package com.trazyar.level;
public final class ProjectCoachContentTest {
  static int tests=0;
  static void check(String label,boolean pass){if(!pass)throw new AssertionError(label);tests++;}
  public static void main(String[] args){
    check("six projects",ProjectCoachContent.count()==6);
    check("all matching arrays",ProjectCoachContent.TITLES.length==ProjectCoachContent.STEPS.length);
    check("all actions",ProjectCoachContent.ACTIONS.length==ProjectCoachContent.count());
    check("all guide targets",ProjectCoachContent.GUIDE_TOPICS.length==ProjectCoachContent.count());
    check("all caution descriptions",ProjectCoachContent.CAUTIONS.length==ProjectCoachContent.count());
    check("invalid id",!ProjectCoachContent.valid(-1)&&!ProjectCoachContent.valid(6));
    for(int i=0;i<ProjectCoachContent.count();i++){
      check("valid project "+i,ProjectCoachContent.valid(i));
      check("name "+i,ProjectCoachContent.TITLES[i].length()>8);
      check("subtitle "+i,ProjectCoachContent.SUBTITLES[i].length()>18);
      check("project steps "+i,ProjectCoachContent.totalSteps(i)>=4);
      check("caution "+i,ProjectCoachContent.CAUTIONS[i].length()>35);
      check("valid destination "+i,ProjectCoachContent.ACTIONS[i]>=0&&ProjectCoachContent.ACTIONS[i]<=4);
      check("matching chapter "+i,GuideContent.valid(ProjectCoachContent.GUIDE_TOPICS[i]));
      check("first step clamped "+i,ProjectCoachContent.clampStep(i,-22)==0);
      int last=ProjectCoachContent.totalSteps(i)-1;
      check("last step clamped "+i,ProjectCoachContent.nextStep(i,last)==last);
      check("previous step clamped "+i,ProjectCoachContent.previousStep(i,0)==0);
      for(String step:ProjectCoachContent.STEPS[i])check("nontrivial instructions "+i,step.length()>55);
    }
    check("washer uses installer",ProjectCoachContent.ACTIONS[ProjectCoachContent.WASHER]==4);
    check("cabinet uses installer",ProjectCoachContent.ACTIONS[ProjectCoachContent.CABINET]==4);
    check("shelf uses bubble",ProjectCoachContent.ACTIONS[ProjectCoachContent.SHELF]==0);
    check("door uses protractor",ProjectCoachContent.ACTIONS[ProjectCoachContent.DOOR]==2);
    check("square uses square",ProjectCoachContent.ACTIONS[ProjectCoachContent.SQUARE]==3);
    check("slope uses inclinometer",ProjectCoachContent.ACTIONS[ProjectCoachContent.SLOPE]==1);
    check("bigger last",ProjectCoachContent.totalSteps(ProjectCoachContent.WASHER)>=6);
    System.out.println("TrazYar project walkthrough tests passed: "+tests);
  }
}
