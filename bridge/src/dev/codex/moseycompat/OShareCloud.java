package dev.codex.moseycompat;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.util.*;
import de.robv.android.xposed.*;

/** Routes only this bridge's live receive session through OShare's own cloud card. */
public final class OShareCloud {
 static final String CARD="com.oplus.oshare.seeding.ConfirmSeedlingCard";
 static final String MANAGER="com.oplus.oshare.seeding.OShareSeedlingManager";
 static ClassLoader loader;
 static Object card;
 static long session=Long.MIN_VALUE;
 static int stage;
 static boolean owns=false,failed=false;
 static Intent latest;
 static String previewSource;
 static Intent pendingPreview;
 static long pendingPreviewTime;
 static void rememberPreview(Intent event){
  pendingPreview=new Intent(event);pendingPreviewTime=SystemClock.elapsedRealtime();
  OShareBridge.log("OShare received request preview");
 }
 static Object call(Object o,String method,Object... args){return XposedHelpers.callMethod(o,method,args);}
 static Object singleton(){return XposedHelpers.callStaticMethod(XposedHelpers.findClass(CARD,loader),"e0");}
 static void install(ClassLoader cl){
  loader=cl;
  final Class<?> type=XposedHelpers.findClass(CARD,cl);
  XposedHelpers.findAndHookMethod(type,"b1",org.json.JSONObject.class,boolean.class,new XC_MethodHook(){
   protected void beforeHookedMethod(MethodHookParam p){
    if(!owns||p.thisObject!=card||previewSource==null)return;
    decorate((org.json.JSONObject)p.args[0]);
   }
  });
  for(final String method:new String[]{"p0","q0","O","t0","N0","T","V"}){
   XposedBridge.hookAllMethods(type,method,new XC_MethodHook(){
    protected void beforeHookedMethod(MethodHookParam p){
     if(!owns||p.thisObject!=card)return;
     p.setResult(null);
     try{
      if("p0".equals(method)){
       if(stage==1002){command("accept");stage=1005;call(card,"Q0",OShareReceiveUI.app,false);}
       else if(stage==1006){open();end();}
      }else if("q0".equals(method)){
       if(stage==1002)command("deny");else if(stage!=1006&&stage<1007)command("cancel");
       end();
      }else if("O".equals(method)||"t0".equals(method)){
       if(stage==1002)command("deny");else if(stage!=1006)command("cancel");end();
      }else if("N0".equals(method)){open();end();}
      else {fallback();}
     }catch(Throwable t){OShareBridge.log("cloud action failed: "+t);}
    }
   });
  }
  // W normally sends the OShare transport ACK. Our ACK is sent through Mosey.
  XposedBridge.hookAllMethods(type,"W",new XC_MethodHook(){
   protected void beforeHookedMethod(MethodHookParam p){
    if(!owns||p.thisObject!=card)return;p.setResult(null);
    XposedHelpers.setObjectField(card,"b",p.args[0]);
    Object showing=Enum.valueOf((Class)XposedHelpers.findClass(MANAGER+"$SeedingState",loader),"SHOWING");
    XposedHelpers.setObjectField(card,"d",showing);
    ((Handler)XposedHelpers.getObjectField(card,"B")).removeMessages(2);
    call(card,"Z");OShareBridge.log("cloud receive card ready");
   }
  });
 }
 static void command(String action){
  OShareReceiveUI.app.sendBroadcast(new Intent(OShareBridge.CONTROL).setPackage("com.google.android.gms")
   .putExtra("op","receive_action").putExtra(OShareReceiveUI.ID,session).putExtra("action",action),OShareBridge.PRIV);
  OShareBridge.log("cloud action="+action);
 }
 static boolean receive(Intent event){
  int status=event.getIntExtra("status",1000);long id=event.getLongExtra(OShareReceiveUI.ID,Long.MIN_VALUE);
  try{
   if(status==1002){
    if(owns&&session==id)return true;
    Object manager=XposedHelpers.getStaticObjectField(XposedHelpers.findClass(MANAGER,loader),"a");
    if(!Boolean.TRUE.equals(call(manager,"n")))return false;
    Object existing=singleton();
    if(!owns&&Boolean.TRUE.equals(call(existing,"h0"))){OShareBridge.log("native cloud busy; retain receive dialog");return false;}
    if(owns)end();
    card=existing;session=id;stage=status;owns=true;failed=false;latest=new Intent(event);
    previewSource=OSharePreview.source(event.getByteArrayExtra(OSharePreview.KEY));
    if(previewSource==null&&pendingPreview!=null&&SystemClock.elapsedRealtime()-pendingPreviewTime<15000
     &&Objects.equals(pendingPreview.getStringExtra("key_sender_name"),event.getStringExtra("key_sender_name"))
     &&Objects.equals(pendingPreview.getStringExtra("key_data_name"),event.getStringExtra("key_data_name"))){
     previewSource=OSharePreview.source(pendingPreview.getByteArrayExtra(OSharePreview.KEY));pendingPreview=null;
    }
    call(card,"v0");
    call(card,"B0",event.getStringExtra("key_data_name"),Integer.valueOf(event.getIntExtra("key_data_num",1)),
     event.getStringExtra("key_data_size"),event.getStringExtra("key_mime_type"),event.getStringExtra("key_sender_name"),
     null,0,Boolean.FALSE,null,previewSource==null?null:Uri.parse(previewSource),
     event.getIntExtra("key_data_num",1),0);
    call(card,"R0",OShareReceiveUI.app,false);
    Handler timer=(Handler)XposedHelpers.getObjectField(card,"B");
    timer.removeMessages(3);timer.sendEmptyMessageDelayed(3,90000L);
    OShareBridge.log("cloud confirmation requested for real AirDrop session");return true;
   }
   if(!owns||session!=id||failed)return false;
   latest=new Intent(event);stage=status;
   if(status==1005){
    int percent=Math.max(0,Math.min(100,Math.round(event.getFloatExtra("percent",0))));
    call(card,"j1",OShareReceiveUI.app,percent);
   }else if(status==1006){
    ArrayList<Uri> uris=OShareReceiveUI.receivedUris.get(session);
    String mime=event.getStringExtra("key_mime_type");
    if(mime!=null&&mime.startsWith("image/")&&uris!=null&&!uris.isEmpty()){
     String source=OSharePreview.received(uris.get(0));if(source!=null)previewSource=source;
    }
    // Keep the completed cloud visible; its View button opens the actual received URI.
    call(card,"O0");
   }else if(event.getBooleanExtra("terminal",false)){
    call(card,"c1",OShareReceiveUI.app);
    final long done=session;OShareBridge.MAIN.postDelayed(new Runnable(){public void run(){if(owns&&session==done)end();}},4000);
   }
   OShareBridge.log("cloud receive status="+status);return true;
  }catch(Throwable t){OShareBridge.log("cloud unavailable: "+t);XposedBridge.log(t);if(owns)fallback();return false;}
 }
 static void fallback(){
  if(!owns||failed)return;failed=true;Intent request=latest==null?null:new Intent(latest);int state=stage;
  end();OShareBridge.log("cloud creation failed; restore native receive dialog");
  if(request!=null){request.putExtra("key_receive_state",state==1002?0:state==1006?2:1);OShareReceiveUI.launch(request);}
 }
 static void end(){
  if(card!=null){
   ((Handler)XposedHelpers.getObjectField(card,"B")).removeCallbacksAndMessages(null);
   call(card,"I0");call(card,"U0");
  }
  owns=false;session=Long.MIN_VALUE;
  previewSource=null;
 }
 static void decorate(org.json.JSONObject data){
  if(data==null)return;
  try{
   data.put("preview_src",previewSource).put("left_icon_size",48);
   data.put("icon_logo_d1",stage==1006?OSharePreview.success():"{{$r('images.logo_d1')}}");
   data.put("isShowPreview",true).put("isB0Show",true).put("isShowLottie",false);
  }catch(Throwable t){OShareBridge.log("cloud preview update failed");}
 }
 static void open(){
  ArrayList<Uri> uris=OShareReceiveUI.receivedUris.get(session);
  if(uris==null||uris.isEmpty())return;
  String mime=latest==null?null:latest.getStringExtra("key_mime_type");
  OShareReceiveUI.openReceived(OShareReceiveUI.app,uris,mime);
 }
}
