package com.trazyar.level;

/** Run with the host JDK before Android compilation. */
public final class LevelMathTest {
  private static int count=0;
  static void eq(String name,double expected,double actual,double error){
    if(!Double.isFinite(actual)||Math.abs(expected-actual)>error)
      throw new AssertionError(name+": expected "+expected+", got "+actual);
    count++;
  }
  static void truth(String name,boolean actual){
    if(!actual)throw new AssertionError(name);
    count++;
  }
  public static void main(String[] args) {
    eq("flat degree",0,LevelMath.inclineDegrees(0,0,9.81),.00001);
    eq("flat grade",0,LevelMath.slopePercent(0,0,9.81),.00001);
    eq("45 degree incline",45,LevelMath.inclineDegrees(1,0,1),.00001);
    eq("100 percent grade",100,LevelMath.slopePercent(1,0,1),.00001);
    eq("30 degree",30,LevelMath.inclineDegrees(.5,0,Math.sqrt(3)/2),.00001);
    eq("57.7 percent grade",100/Math.sqrt(3),LevelMath.slopePercent(.5,0,Math.sqrt(3)/2),.00001);
    eq("90 degree incline",90,LevelMath.inclineDegrees(1,0,0),.00001);
    truth("vertical grade infinity",Double.isInfinite(LevelMath.slopePercent(1,0,0)));
    eq("upright portrait long edge=90",90,LevelMath.edgeFromHorizontal(0,9.81),.00001);
    eq("upside down portrait long edge=90",90,LevelMath.edgeFromHorizontal(0,-9.81),.00001);
    eq("landscape long edge=0",0,LevelMath.edgeFromHorizontal(9.81,0),.00001);
    eq("landscape reversed long edge=0",0,LevelMath.edgeFromHorizontal(-9.81,0),.00001);
    eq("diagonal edge=45",45,LevelMath.edgeFromHorizontal(1,1),.00001);
    eq("square 90",90,LevelMath.angleBetweenLines(0,90),.00001);
    eq("square another 90",90,LevelMath.angleBetweenLines(45,135),.00001);
    eq("square same line",0,LevelMath.angleBetweenLines(10,190),.00001);
    eq("square near wrap",2,LevelMath.angleBetweenLines(179,1),.00001);
    truth("upright sensor valid",LevelMath.canMeasureEdge(0,9.81,0));
    truth("flat sensor invalid",!LevelMath.canMeasureEdge(0,0,9.81));
    truth("tilted shallow invalid",!LevelMath.canMeasureEdge(1,1,9.81));
    System.out.println("TrazYar LevelMath tests passed: "+count);
  }
}
