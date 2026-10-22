package dev.codex.moseycompat;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.View;
import android.widget.ProgressBar;
import java.lang.ref.WeakReference;
import java.util.*;
import de.robv.android.xposed.*;

/** Reuses the stock OShare ReceiveDialogActivity for marked AirDrop sessions. */
public final class OShareReceiveUI {
 static final String MARK="codex_airdrop_receive";
 static final String ID="codex_airdrop_receive_id";
 static final String EVENT="dev.codex.moseycompat.RECEIVE_EVENT_7c76a9d1";
 static final String ACTIVITY="com.oplus.oshare.ui.ReceiveDialogActivity";
 static PendingIntent uiToken;
 static Context app;
 static ClassLoader loader;
 static boolean registered=false;
 static WeakReference<Activity> dialogActivity=new WeakReference<>(null);
 static final Map<Long,Object> incomingTargets=new java.util.concurrent.ConcurrentHashMap<>();
 static final Map<Long,Integer> stages=new java.util.concurrent.ConcurrentHashMap<>();
 static final Map<Long,Intent> details=new HashMap<>();
 static final Map<Long,ArrayList<Uri>> receivedUris=new HashMap<>();

 static void registerToken(Intent i){
  uiToken=i.getParcelableExtra("token");
  OShareBridge.log("native receive UI token registered");
 }
 static void action(Intent request){
  long id=request.getLongExtra(ID,Long.MIN_VALUE);Object target=incomingTargets.get(id);
  Integer stage=stages.get(id);String action=request.getStringExtra("action");
  if(target==null||stage==null)return;
  String method;
  if("accept".equals(action)&&stage==1002)method="a";
  else if("deny".equals(action)&&stage==1002)method="c";
  else if("cancel".equals(action)&&stage!=1006&&stage!=1007&&stage!=1008&&stage!=1009)method="b";
  else return;
  try{
   int result=(Integer)XposedHelpers.callMethod(OShareBridge.provider,method,target);
   OShareBridge.log("native receive UI action="+action+" result="+result);
  }catch(Throwable t){XposedBridge.log(t);}
 }
 static void incoming(final Object target,final Object meta){
  try{
   if(uiToken==null){
    OShareBridge.log("native UI unavailable; retaining original receive confirmation");
    XposedHelpers.callMethod(OShareBridge.gmsService,"ax",new Runnable(){public void run(){
     XposedHelpers.callMethod(OShareBridge.gmsService,"an",target,meta);
    }});return;
   }
   long id=XposedHelpers.getLongField(target,"a");
   int status=XposedHelpers.getIntField(meta,"a");
   incomingTargets.put(id,target);stages.put(id,status);
   Intent event=new Intent().putExtra(MARK,true).putExtra(ID,id).putExtra("status",status)
    .putExtra("percent",(Float)XposedHelpers.getObjectField(meta,"b"))
    .putExtra("terminal",XposedHelpers.getBooleanField(meta,"e"));
   event.putExtra("key_sender_name",(String)XposedHelpers.getObjectField(target,"b"));
   List files=(List)XposedHelpers.callMethod(target,"f");
   long bytes=0;String name="文件",mime="application/octet-stream";
   ArrayList<Uri> uris=new ArrayList<>();int n=0;
   for(Object file:files){
    if(n++==0){name=(String)XposedHelpers.getObjectField(file,"a");mime=(String)XposedHelpers.getObjectField(file,"f");}
    bytes+=XposedHelpers.getLongField(file,"c");
    Uri uri=(Uri)XposedHelpers.getObjectField(file,"d");
    if(uri!=null){
     uris.add(uri);
     if("content".equals(uri.getScheme())){
      try{OShareBridge.gmsContext.grantUriPermission("com.coloros.oshare",uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
      catch(SecurityException e){OShareBridge.log("received URI grant unavailable");}
     }
    }
   }
   event.putExtra("key_data_name",name).putExtra("key_data_num",files.size())
    .putExtra("key_data_size",android.text.format.Formatter.formatFileSize(OShareBridge.gmsContext,bytes))
    .putExtra("key_mime_type",mime).putExtra("key_sender_is_ios",true);
   event.putParcelableArrayListExtra("received_uris",uris);
   if(status==1002){
    Bundle extras=(Bundle)XposedHelpers.getObjectField(target,"e");
    byte[] preview=extras.getByteArray(OSharePreview.KEY);
    if(preview!=null&&preview.length<160000)event.putExtra(OSharePreview.KEY,preview);
    event.putExtra("key_receive_state",0);
    event.setAction(EVENT).setPackage("com.coloros.oshare");
    OShareBridge.gmsContext.sendBroadcast(event,OShareBridge.PRIV);
    OShareBridge.log("requested OShare cloud receive confirmation");
   }else{
    event.setAction(EVENT).setPackage("com.coloros.oshare");
    OShareBridge.gmsContext.sendBroadcast(event,OShareBridge.PRIV);
   }
  }catch(Throwable t){OShareBridge.log("native receive UI dispatch failed");XposedBridge.log(t);}
 }
 static void register(Context context){
  try{
   app=context.getApplicationContext();
   if(!registered){
    registered=true;
    app.registerReceiver(new BroadcastReceiver(){public void onReceive(Context c,Intent i){update(i);}},
     new IntentFilter(EVENT),OShareBridge.PRIV,OShareBridge.MAIN,Context.RECEIVER_EXPORTED);
    OShareBridge.MAIN.postDelayed(new Runnable(){public void run(){
     register(app);OShareBridge.MAIN.postDelayed(this,30000);
    }},30000);
   }
   Intent launch=new Intent().setComponent(new ComponentName("com.coloros.oshare",ACTIVITY));
   launch.putExtra(MARK,true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
   ActivityOptions opts=ActivityOptions.makeBasic();opts.setPendingIntentCreatorBackgroundActivityStartMode(1);
   PendingIntent token=PendingIntent.getActivity(app,41620,launch,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE,opts.toBundle());
   app.sendBroadcast(new Intent(OShareBridge.CONTROL).setPackage("com.google.android.gms")
    .putExtra("op","receive_ui").putExtra("token",token),OShareBridge.PRIV);
  }catch(Throwable t){XposedBridge.log(t);}
 }
 static boolean marked(Activity a){return a!=null&&a.getIntent()!=null&&a.getIntent().getBooleanExtra(MARK,false);}
 static long id(Activity a){return a.getIntent().getLongExtra(ID,Long.MIN_VALUE);}
 static void send(Activity a,String action){
  a.sendBroadcast(new Intent(OShareBridge.CONTROL).setPackage("com.google.android.gms")
   .putExtra("op","receive_action").putExtra(ID,id(a)).putExtra("action",action),OShareBridge.PRIV);
 }
 static void removeLegacyTimeout(Activity a){
  try{
   Handler h=(Handler)XposedHelpers.getObjectField(a,"b4");
   Object r=XposedHelpers.getObjectField(a,"c4");if(r instanceof Runnable)h.removeCallbacks((Runnable)r);
   XposedHelpers.callMethod(XposedHelpers.getObjectField(a,"a4"),"a",0);
   XposedHelpers.setObjectField(a,"c4",null);
  }catch(Throwable t){XposedBridge.log(t);}
 }
 static void render(Activity a,int mode,int percent){
  try{
   Intent i=new Intent(a.getIntent()).putExtra("key_receive_state",mode).putExtra("key_receive_percent",percent);
   a.setIntent(i);XposedHelpers.callMethod(a,"S0",i);removeLegacyTimeout(a);
  }catch(Throwable t){XposedBridge.log(t);}
 }
 static void viewFiles(Activity a){
  try{
   ArrayList<Uri> uris=receivedUris.get(id(a));
   if(uris!=null&&!uris.isEmpty()&&"content".equals(uris.get(0).getScheme())){
    String mime=a.getIntent().getStringExtra("key_mime_type");
    openReceived(a,uris,mime);
   }else{
    a.startActivity(new Intent("android.intent.action.VIEW_DOWNLOADS"));
   }
  }catch(Throwable t){XposedBridge.log(t);}
  a.finish();
 }
 static void openReceived(Context context,ArrayList<Uri> uris,String declaredMime){
  if(uris==null||uris.isEmpty())return;
  Uri uri=uris.get(0);String mime=declaredMime;
  try{
   String actual=context.getContentResolver().getType(uri);
   if(actual!=null&&!"application/octet-stream".equals(actual))mime=actual;
  }catch(Throwable ignored){}
  if(mime==null||"application/octet-stream".equals(mime)||"*/*".equals(mime)){
   String name=uri.getLastPathSegment();
   try(android.database.Cursor cursor=context.getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
    if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);
   }catch(Throwable ignored){}
   if(name!=null&&name.lastIndexOf('.')>=0){
    String ext=name.substring(name.lastIndexOf('.')+1).toLowerCase(java.util.Locale.ROOT);
    String detected=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
    if(detected!=null)mime=detected;
   }
  }
  if(mime==null)mime="application/octet-stream";
  Intent view=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
  view.setClipData(ClipData.newRawUri("Received file",uri));
  if(!(context instanceof Activity))view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  if(mime.startsWith("video/")||mime.startsWith("image/")){
   Intent gallery=new Intent(view).setPackage("com.coloros.gallery3d");
   if(gallery.resolveActivity(context.getPackageManager())!=null){
    try{context.startActivity(gallery);OShareBridge.log("received media opened in Gallery type="+mime);return;}
    catch(ActivityNotFoundException|SecurityException unavailable){OShareBridge.log("Gallery unavailable; retain system viewer");}
   }
  }
  context.startActivity(view);
 }
 static void wire(final Activity a,Dialog d,String role){
  if(!marked(a)||d==null)return;
  View positive=d.findViewById(android.R.id.button1),negative=d.findViewById(android.R.id.button2);
  if("request".equals(role)){
   if(positive!=null)positive.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
    removeLegacyTimeout(a);send(a,"accept");render(a,1,0);
   }});
   if(negative!=null)negative.setOnClickListener(new View.OnClickListener(){public void onClick(View v){send(a,"deny");a.finish();}});
  }else if("progress".equals(role)){
   if(negative!=null)negative.setOnClickListener(new View.OnClickListener(){public void onClick(View v){send(a,"cancel");a.finish();}});
   if(positive!=null)positive.setOnClickListener(new View.OnClickListener(){public void onClick(View v){a.finish();}});
  }else if("success".equals(role)){
   if(positive!=null)positive.setOnClickListener(new View.OnClickListener(){public void onClick(View v){viewFiles(a);}});
   if(negative!=null)negative.setOnClickListener(new View.OnClickListener(){public void onClick(View v){a.finish();}});
  }else if("cancel".equals(role)){
   if(positive!=null)positive.setOnClickListener(new View.OnClickListener(){public void onClick(View v){render(a,1,XposedHelpers.getIntField(a,"s4"));}});
   if(negative!=null)negative.setOnClickListener(new View.OnClickListener(){public void onClick(View v){send(a,"cancel");a.finish();}});
  }
 }
 static void created(final Activity a){
  if(!marked(a))return;
  dialogActivity=new WeakReference<>(a);details.put(id(a),new Intent(a.getIntent()));removeLegacyTimeout(a);
  Object d=XposedHelpers.getObjectField(a,"l4");
  if(d instanceof Dialog)wire(a,(Dialog)d,a.getIntent().getIntExtra("key_receive_state",0)==0?"request":"progress");
  final long task=id(a);
  OShareBridge.MAIN.postDelayed(new Runnable(){public void run(){
   Activity current=dialogActivity.get();
   if(current==a&&!a.isFinishing()&&id(a)==task&&a.getIntent().getIntExtra("key_receive_state",0)==0){send(a,"deny");a.finish();}
  }},45000);
  OShareBridge.log("original OShare receive dialog active");
 }
 static void update(Intent event){
  try{
   if(event.getBooleanExtra("preview_only",false)){OShareCloud.rememberPreview(event);return;}
   long id=event.getLongExtra(ID,Long.MIN_VALUE);int status=event.getIntExtra("status",1000);
   ArrayList<Uri> uris=event.getParcelableArrayListExtra("received_uris");
   if(uris!=null&&!uris.isEmpty())receivedUris.put(id,uris);
   if(OShareCloud.receive(event))return;
   if(status==1002){launch(new Intent(event).putExtra("key_receive_state",0));return;}
   Activity a=dialogActivity.get();
   if(a==null||a.isFinishing()||!marked(a)||id(a)!=id){
    if(status==1006){
     Intent done=new Intent(event).setComponent(new ComponentName("com.coloros.oshare",ACTIVITY))
      .setAction(null).putExtra("key_receive_state",2).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
     app.startActivity(done);
    }
    return;
   }
   int percent=Math.max(0,Math.min(100,Math.round(event.getFloatExtra("percent",0))));
   if(status==1005){
    if(a.getIntent().getIntExtra("key_receive_state",0)!=1)render(a,1,percent);
    XposedHelpers.setIntField(a,"s4",percent);
    Dialog d=(Dialog)XposedHelpers.getObjectField(a,"l4");
    if(d!=null){
     int progress=a.getResources().getIdentifier("progress","id","com.coloros.oshare");
     View bar=d.findViewById(progress==0?android.R.id.progress:progress);
     if(bar instanceof ProgressBar)((ProgressBar)bar).setProgress(percent);
     d.setTitle("正在接收（"+percent+"%）");
    }
   }else if(status==1006)render(a,2,100);
   else if(event.getBooleanExtra("terminal",false)){render(a,4,percent);}
   OShareBridge.log("original receive dialog status="+status);
  }catch(Throwable t){XposedBridge.log(t);}
 }
 static void install(final ClassLoader cl){
  loader=cl;
  OShareCloud.install(cl);
  XposedHelpers.findAndHookMethod("com.oplus.oshare.OShareApplication",cl,"onCreate",new XC_MethodHook(){
   protected void afterHookedMethod(MethodHookParam p){register((Context)p.thisObject);}
  });
  final Class<?> activity=XposedHelpers.findClass(ACTIVITY,cl);
  XposedHelpers.findAndHookMethod(activity,"onCreate",Bundle.class,new XC_MethodHook(){
   protected void beforeHookedMethod(MethodHookParam p){if(marked((Activity)p.thisObject))XposedHelpers.setStaticBooleanField(activity,"v4",false);}
   protected void afterHookedMethod(MethodHookParam p){created((Activity)p.thisObject);}
  });
  XposedHelpers.findAndHookMethod(activity,"onNewIntent",Intent.class,new XC_MethodHook(){
   protected void beforeHookedMethod(MethodHookParam p){
    Activity a=(Activity)p.thisObject;Intent i=(Intent)p.args[0];
    if(marked(a)||i.getBooleanExtra(MARK,false))a.setIntent(i);
    if(i.getBooleanExtra(MARK,false))XposedHelpers.setStaticBooleanField(activity,"v4",false);
   }
   protected void afterHookedMethod(MethodHookParam p){created((Activity)p.thisObject);}
  });
  String[] methods={"k1","o1","q1","r1"};final String[] roles={"request","progress","success","cancel"};
  for(int j=0;j<methods.length;j++){
   final String role=roles[j];
   XposedBridge.hookAllMethods(activity,methods[j],new XC_MethodHook(){
    protected void afterHookedMethod(MethodHookParam p){if(p.getResult() instanceof Dialog)wire((Activity)p.thisObject,(Dialog)p.getResult(),role);}
   });
  }
  XposedHelpers.findAndHookMethod(activity,"m1",new XC_MethodHook(){
   protected void beforeHookedMethod(MethodHookParam p){Activity a=(Activity)p.thisObject;if(marked(a)){p.setResult(null);viewFiles(a);}}
  });
  XposedHelpers.findAndHookMethod(activity,"Q0",int.class,Intent.class,new XC_MethodHook(){
   protected void beforeHookedMethod(MethodHookParam p){
    Activity a=(Activity)p.thisObject;if(!marked(a))return;p.setResult(null);
    int action=((Intent)p.args[1]).getIntExtra("key_action_type",0);
    send(a,action==10?"accept":"deny");if(action==10)render(a,1,0);else a.finish();
   }
  });
  XposedBridge.hookAllMethods(activity,"I0",new XC_MethodHook(){
   protected void afterHookedMethod(MethodHookParam p){
    if(p.args[0] instanceof Activity&&marked((Activity)p.args[0])&&p.getResult() instanceof Intent)
     ((Intent)p.getResult()).putExtra(MARK,true).putExtra(ID,id((Activity)p.args[0]));
   }
  });
 }
 static void launch(Intent event){
  event.setComponent(new ComponentName("com.coloros.oshare",ACTIVITY)).setAction(null)
   .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
  app.startActivity(event);
 }
}
