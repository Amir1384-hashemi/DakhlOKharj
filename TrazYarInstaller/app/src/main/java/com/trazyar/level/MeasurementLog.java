package com.trazyar.level;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Private, offline measurement journal; capped to 80 entries. */
public final class MeasurementLog {
  private static final String FILE="trazyar",KEY="measurementJournalV28";
  private static final int MAX=80;
  private MeasurementLog(){}

  public static final class Entry {
    public final long time;
    public final String name,tool,value,details;
    public Entry(long time,String name,String tool,String value,String details){
      this.time=time;this.name=name;this.tool=tool;this.value=value;this.details=details;
    }
  }
  public static void save(Context ctx,String name,String tool,String value,String details){
    SharedPreferences p=ctx.getSharedPreferences(FILE,Context.MODE_PRIVATE);
    List<Entry> old=list(ctx);
    // Insert newest first; avoid unlimited disk growth.
    old.add(0,new Entry(System.currentTimeMillis(),name,tool,value,details));
    JSONArray array=new JSONArray();
    for(int i=0;i<Math.min(MAX,old.size());i++){
      Entry e=old.get(i);JSONObject item=new JSONObject();
      try {
        item.put("time",e.time);item.put("name",e.name);
        item.put("tool",e.tool);item.put("value",e.value);item.put("details",e.details);
        array.put(item);
      }catch(Exception ignored){}
    }
    p.edit().putString(KEY,array.toString()).apply();
  }
  public static List<Entry> list(Context ctx){
    SharedPreferences p=ctx.getSharedPreferences(FILE,Context.MODE_PRIVATE);
    List<Entry> out=new ArrayList<>();
    try{
      JSONArray array=new JSONArray(p.getString(KEY,"[]"));
      for(int i=0;i<Math.min(MAX,array.length());i++){
        JSONObject e=array.optJSONObject(i);if(e==null)continue;
        out.add(new Entry(e.optLong("time",0),e.optString("name",""),
          e.optString("tool",""),e.optString("value",""),e.optString("details","")));
      }
    }catch(Exception ignored){}
    return out;
  }
  public static void clear(Context ctx){
    ctx.getSharedPreferences(FILE,Context.MODE_PRIVATE).edit().remove(KEY).apply();
  }
  public static String dateText(long timestamp){
    return new SimpleDateFormat("yyyy/MM/dd HH:mm",Locale.US).format(new Date(timestamp));
  }
  public static String text(Entry e){
    String project=e.name.isEmpty()?"بدون نام پروژه":e.name;
    return "گزارش اندازه‌گیری تراز یار\n"
      +"پروژه: "+project+"\n"
      +"تاریخ: "+dateText(e.time)+"\n"
      +"ابزار: "+e.tool+"\n"
      +"نتیجه: "+e.value+"\n"
      +"جزئیات: "+e.details+"\n";
  }
  public static String exportText(List<Entry> entries){
    StringBuilder sb=new StringBuilder("دفترچهٔ اندازه‌گیری تراز یار\n");
    sb.append("تعداد: ").append(entries.size()).append("\n\n");
    for(Entry e:entries){sb.append(text(e)).append("\n----------\n");}
    return sb.toString();
  }
}
