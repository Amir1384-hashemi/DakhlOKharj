package com.trazyar.level;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
/** Streaming PCM tone. Always fades on/off to prevent audible clicks. */
public final class BeepEngine {
  static final int RATE=24000,BLOCK=512;
  private volatile boolean running=false,active=false,level=false;
  private volatile double error=20,tolerance=.5;
  private volatile float userVolume=.70f;
  private Thread worker;
  public synchronized void start(){
    if(running)return;
    running=true;worker=new Thread(this::render,"TrazYar-Beep");
    worker.setDaemon(true);worker.start();
  }
  public synchronized void stop(){
    running=false;active=false;Thread t=worker;worker=null;
    if(t!=null)t.interrupt();
  }
  public void update(boolean enabled,double deviation,double allowed,boolean locked,float volume){
    active=enabled;error=deviation;tolerance=allowed;level=locked;
    userVolume=Math.max(0,Math.min(1,volume));
  }
  private void render(){
    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
    int min=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
    AudioTrack audio=null;
    try {
      audio=new AudioTrack(AudioManager.STREAM_MUSIC,RATE,AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,Math.max(BLOCK*8,min>0?min:BLOCK*8),AudioTrack.MODE_STREAM);
      if(audio.getState()!=AudioTrack.STATE_INITIALIZED)return;
      audio.play();
      short[] buf=new short[BLOCK];
      long frame=0;
      double envelope=0,phase=0;
      final double inc=2*Math.PI*775/RATE;
      while(running&&!Thread.currentThread().isInterrupted()){
        BeepPattern.Step step=BeepPattern.of(error,tolerance,level);
        int period=Math.max(1,step.periodMs*RATE/1000);
        int on=Math.min(period,step.onMs*RATE/1000);
        boolean enabled=active;
        float vol=userVolume;
        for(int i=0;i<BLOCK;i++,frame++){
          boolean pulse=enabled&&(step.continuous||(frame%period)<on);
          double target=pulse?step.amplitude*vol:0;
          envelope+=(target-envelope)*(target>envelope?.045:.024);
          phase+=inc;if(phase>=2*Math.PI)phase-=2*Math.PI;
          double wave=.92*Math.sin(phase)+.08*Math.sin(phase*2);
          buf[i]=(short)(Math.max(-1,Math.min(1,wave*envelope))*26000);
        }
        if(audio.write(buf,0,buf.length)<0)break;
      }
    }catch(Throwable ignored){ // Audio backend should not crash measuring UI.
    }finally{
      if(audio!=null){try{audio.pause();audio.flush();audio.release();}catch(Exception ignored){}}
    }
  }
}
