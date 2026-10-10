package com.trazyar.level;

import java.util.Locale;

/**
 * Estimates required SHIMS/foot-extensions for a rigid rectangular planar top.
 * Coordinates: the phone's TOP points to the back of the object and its RIGHT
 * points to the object's right. Positive raw X means right edge is LOWER;
 * positive raw Y means top/back edge is LOWER. Dimensions are centimeters.
 *
 * All four outputs are non-negative millimeters, referenced to the high corner.
 * This does NOT diagnose rocking, individual unloaded feet, frame twist, or
 * nonplanar surfaces. The phone must stay flat on a rigid top and aligned.
 */
public final class InstallerMath {
  private InstallerMath(){}
  public static final int BACK_RIGHT=0,BACK_LEFT=1,FRONT_RIGHT=2,FRONT_LEFT=3;
  public static final String[] LABELS={
    "عقب راست","عقب چپ","جلو راست","جلو چپ"
  };
  public static final class Result {
    public final double widthCm,depthCm,xDeg,yDeg;
    public final double[] raiseMm;
    public final double maxRaiseMm,levelErrorDeg;
    Result(double width,double depth,double x,double y,double[] corrections){
      widthCm=width;depthCm=depth;xDeg=x;yDeg=y;
      raiseMm=corrections;
      double peak=0;
      for(double v:corrections)peak=Math.max(peak,v);
      maxRaiseMm=peak;
      levelErrorDeg=Math.max(Math.abs(x),Math.abs(y));
    }
  }
  public static Result compute(double widthCm,double depthCm,double xDeg,double yDeg){
    if(!Double.isFinite(widthCm)||!Double.isFinite(depthCm)||widthCm<=0||depthCm<=0||
        widthCm>1000||depthCm>1000)throw new IllegalArgumentException("invalid dimensions");
    if(!Double.isFinite(xDeg)||!Double.isFinite(yDeg)||Math.abs(xDeg)>15||Math.abs(yDeg)>15)
      throw new IllegalArgumentException("surface too steep for four-foot estimator");

    // Positive correction raises a lower corner to the height of the highest.
    // tan(deg) * width/2 [cm], converted into mm via *10.
    double halfX=Math.tan(Math.toRadians(xDeg))*widthCm*5;
    double halfY=Math.tan(Math.toRadians(yDeg))*depthCm*5;
    // Height relative to center: right = -halfX; back = -halfY.
    double[] heights={
      -halfX-halfY, // rear right
       halfX-halfY, // rear left
      -halfX+halfY, // front right
       halfX+halfY  // front left
    };
    double max=heights[0];
    for(double height:heights)max=Math.max(max,height);
    double[] corrections=new double[4];
    for(int i=0;i<4;i++)corrections[i]=Math.max(0,max-heights[i]);
    return new Result(widthCm,depthCm,xDeg,yDeg,corrections);
  }
  public static double parseCentimeters(String source){
    if(source==null)throw new IllegalArgumentException("empty");
    String s=source.trim().replace('٫','.').replace(',','.');
    StringBuilder normalized=new StringBuilder();
    for(int i=0;i<s.length();i++){
      char ch=s.charAt(i);
      if(ch>='۰'&&ch<='۹')ch=(char)('0'+ch-'۰');
      if(ch>='٠'&&ch<='٩')ch=(char)('0'+ch-'٠');
      normalized.append(ch);
    }
    final double value;
    try{value=Double.parseDouble(normalized.toString());}
    catch(Exception ex){throw new IllegalArgumentException("invalid number");}
    if(!Double.isFinite(value)||value<=0||value>1000)throw new IllegalArgumentException("dimension must be 0..1000 cm");
    return value;
  }
  public static String millimeters(double value){return String.format(Locale.US,"%.1f",value);}
}
