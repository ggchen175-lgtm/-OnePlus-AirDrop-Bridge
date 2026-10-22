package dev.codex.moseycompat;

import android.app.Activity;
import android.content.*;
import android.os.*;
import android.net.Uri;
import android.widget.Toast;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.database.ContentObserver;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Local prototype: OShare 17.2.36 + GMS 26.40.32 only. */
public final class OShareBridge {
 static final String CONTROL="dev.codex.moseycompat.OSHARE_CONTROL_7c76a9d1";
 static final String RESULTS="dev.codex.moseycompat.OSHARE_RESULTS_7c76a9d1";
 static final String PRIV="android.permission.BLUETOOTH_PRIVILEGED";
 static final String TAG="codexAirDropId";
 static final Handler MAIN=new Handler(Looper.getMainLooper());
 static final Map<Long,String> names=new LinkedHashMap<>();
 static WeakReference<Activity> screen=new WeakReference<>(null);
 static boolean receiverReady=false,visible=false;
 static final Map<Long,int[]> uiTransfers=new HashMap<>();
 static final Map<Long,ArrayList<Uri>> grantedFiles=new HashMap<>();
 static volatile long activeSend=Long.MIN_VALUE;
 static volatile boolean stopRequested=false;
 static Object gmsService,provider,discoveryCallback;
 static Object receiveCallback,receiveProvider;
 static volatile boolean ownReceive=false,incomingActive=false;
 static Context gmsContext;
 static ClassLoader gmsLoader;
 static volatile boolean ownDiscovery=false;
 static volatile long lease=0;
 static final Map<Long,Object> targets=new LinkedHashMap<>();
 static void log(String s){XposedBridge.log("CodexOShare: "+s);}

 public static void installGms(Object service,ClassLoader loader){
  try{
   gmsService=service;gmsLoader=loader;
   gmsContext=(Context)XposedHelpers.callMethod(service,"getApplicationContext");
   if(receiverReady)return;
   receiverReady=true;
   XposedHelpers.findAndHookMethod("enfa",loader,"C",new XC_MethodHook(){
    protected void beforeHookedMethod(MethodHookParam p){if(ownDiscovery&&p.thisObject==provider)p.setResult(null);}
   });
   XposedHelpers.findAndHookMethod("enfa",loader,"G",XposedHelpers.findClass("endz",loader),new XC_MethodHook(){
    protected void beforeHookedMethod(MethodHookParam p){if(ownDiscovery&&p.thisObject==provider)p.setResult(null);}
   });
   XposedHelpers.findAndHookMethod("enfa",loader,"x",XposedHelpers.findClass("eiap",loader),XposedHelpers.findClass("endz",loader),new XC_MethodHook(){
    protected void beforeHookedMethod(MethodHookParam p){
     if(ownDiscovery&&p.thisObject==provider&&p.args[0]!=discoveryCallback)p.setResult(null);
    }
   });
   XposedHelpers.findAndHookMethod("enfa",loader,"B",new XC_MethodHook(){
    protected void beforeHookedMethod(MethodHookParam p){
     if(p.thisObject!=receiveProvider)return;
     if(ownReceive&&!incomingActive)p.setResult(null);
     else if(incomingActive)ownReceive=false;
    }
   });
   XposedHelpers.findAndHookMethod("enfa",loader,"F",String.class,XposedHelpers.findClass("endx",loader),XposedHelpers.findClass("com.google.android.gms.nearby.sharing.QrCodeMetadata",loader),new XC_MethodHook(){
    protected void beforeHookedMethod(MethodHookParam p){if(ownReceive&&!incomingActive&&p.thisObject==receiveProvider)p.setResult(null);}
   });
   XposedHelpers.findAndHookMethod("enfa",loader,"v",String.class,XposedHelpers.findClass("eicy",loader),XposedHelpers.findClass("endx",loader),new XC_MethodHook(){
    protected void beforeHookedMethod(MethodHookParam p){
     if(ownReceive&&p.thisObject==receiveProvider&&p.args[1]!=receiveCallback&&!incomingActive)p.setResult(null);
    }
   });
   XposedHelpers.findAndHookMethod("enfa",loader,"u",new XC_MethodHook(){
    protected void beforeHookedMethod(MethodHookParam p){
     if(p.thisObject==receiveProvider){ownReceive=false;log("receive provider shut down; awaiting replacement");}
    }
   });
   gmsContext.getContentResolver().registerContentObserver(Settings.System.getUriFor("oshare_state"),false,new ContentObserver(MAIN){
    public void onChange(boolean self){queueReceiveSync();}
   });
   gmsContext.registerReceiver(new BroadcastReceiver(){
    public void onReceive(Context c,Intent i){
     String op=i.getStringExtra("op");
     if("start".equals(op)){
      stopRequested=false;
      lease=SystemClock.elapsedRealtime();
      if(ownDiscovery){publish();return;}
      XposedHelpers.callMethod(gmsService,"ax",new Runnable(){public void run(){startDiscovery();}});
     }else if("stop".equals(op)){
      XposedHelpers.callMethod(gmsService,"ax",new Runnable(){public void run(){stopDiscovery();}});
     }else if("send".equals(op)){
      final Intent request=new Intent(i);
      XposedHelpers.callMethod(gmsService,"ax",new Runnable(){public void run(){sendDirect(request);}});
     }else if("receive_ui".equals(op)){
      OShareReceiveUI.registerToken(i);
     }else if("receive_action".equals(op)){
      final Intent request=new Intent(i);
      XposedHelpers.callMethod(gmsService,"ax",new Runnable(){public void run(){OShareReceiveUI.action(request);}});
     }
    }
   },new IntentFilter(CONTROL),PRIV,MAIN,Context.RECEIVER_EXPORTED);
   MAIN.postDelayed(new Runnable(){public void run(){
    if(ownDiscovery&&SystemClock.elapsedRealtime()-lease>25000)
     XposedHelpers.callMethod(gmsService,"ax",new Runnable(){public void run(){stopDiscovery();}});
    queueReceiveSync();
    MAIN.postDelayed(this,5000);
   }},5000);
   queueReceiveSync();
   log("GMS bridge receiver ready");
  }catch(Throwable t){XposedBridge.log(t);}
 }
 static void queueReceiveSync(){
  try{XposedHelpers.callMethod(gmsService,"ax",new Runnable(){public void run(){syncReceive();}});}
  catch(Throwable t){log("receive sync unavailable");}
 }
 static Object findProvider(){
  Object registry=XposedHelpers.getObjectField(gmsService,"d");
  for(Object info:(Iterable)XposedHelpers.callMethod(registry,"e")){
   if("com.google.android.mosey".equals(XposedHelpers.getObjectField(info,"b")))
    return XposedHelpers.callMethod(registry,"c",XposedHelpers.getObjectField(info,"a"));
  }
  return null;
 }
 static synchronized void syncReceive(){
  try{
   boolean enabled=Settings.System.getInt(gmsContext.getContentResolver(),"oshare_state",0)!=0;
   if(incomingActive||activeSend!=Long.MIN_VALUE)return;
   if(!enabled){
    if(ownReceive){ownReceive=false;XposedHelpers.callMethod(receiveProvider,"B");log("AirDrop receive stopped with OShare switch");}
    return;
   }
   Object current=findProvider();if(current==null)return;
   if(current==receiveProvider&&ownReceive)return;
   ownReceive=false;
   receiveProvider=current;
   provider=current;
   final Object receiving=current;
   final IBinder binder=((IInterface)XposedHelpers.getObjectField(current,"m")).asBinder();
   binder.linkToDeath(new IBinder.DeathRecipient(){public void binderDied(){
    if(receiveProvider==receiving){ownReceive=false;log("receive provider disconnected; scheduling recovery");MAIN.post(new Runnable(){public void run(){queueReceiveSync();}});}
   }},0);
   XposedHelpers.callMethod(provider,"B");
   receiveCallback=Proxy.newProxyInstance(gmsLoader,new Class[]{XposedHelpers.findClass("eicy",gmsLoader)},new InvocationHandler(){
    public Object invoke(Object p,Method m,final Object[] a){
     if(m.getDeclaringClass()==Object.class){
      if("hashCode".equals(m.getName()))return System.identityHashCode(p);
      if("equals".equals(m.getName()))return p==a[0];
      return "CodexOShareReceive";
     }
     if(a!=null&&a.length==2&&a[1]!=null){
      Object meta=a[1];int status=XposedHelpers.getIntField(meta,"a");
      boolean terminal=XposedHelpers.getBooleanField(meta,"e");
      incomingActive=!terminal;
      log("receive status="+status+" final="+terminal);
      OShareReceiveUI.incoming(a[0],a[1]);
      if(terminal)MAIN.postDelayed(new Runnable(){public void run(){queueReceiveSync();}},1500);
     }
     return null;
    }
   });
   Object builder=XposedHelpers.newInstance(XposedHelpers.findClass("endw",gmsLoader));
   XposedHelpers.setObjectField(builder,"a",XposedHelpers.getStaticObjectField(XposedHelpers.findClass("enfd",gmsLoader),"c"));
   XposedHelpers.setBooleanField(builder,"l",true);
   XposedHelpers.setIntField(builder,"b",(Integer)XposedHelpers.callStaticMethod(XposedHelpers.findClass("epyc",gmsLoader),"b",gmsContext));
   Object options=XposedHelpers.callMethod(builder,"a");
   ownReceive=true;
   XposedHelpers.callMethod(provider,"v",XposedHelpers.callMethod(gmsService,"L"),receiveCallback,options);
   log("AirDrop receive started from OShare switch");
  }catch(Throwable t){ownReceive=false;log("receive startup failed");XposedBridge.log(t);}
 }
 static synchronized void startDiscovery(){
  try{
   if(ownDiscovery)return;
   if(incomingActive||Boolean.TRUE.equals(XposedHelpers.callMethod(gmsService,"bk"))){log("transfer busy; discovery deferred");return;}
   provider=findProvider();
   if(provider==null){log("provider not ready");return;}
   // A pre-existing Quick Share scan retains its callback. Stop it before
   // starting the page-owned session so re-entering OShare gets fresh events.
   XposedHelpers.callMethod(provider,"C");
   Class<?> callback=XposedHelpers.findClass("eiap",gmsLoader);
   discoveryCallback=Proxy.newProxyInstance(gmsLoader,new Class[]{callback},new InvocationHandler(){
    public Object invoke(Object p,Method m,Object[] a){
    if(m.getDeclaringClass()==Object.class){
     if("hashCode".equals(m.getName()))return System.identityHashCode(p);
     if("equals".equals(m.getName()))return p==a[0];
     return "CodexOShareDiscovery";
    }
    if(!ownDiscovery)return null;
    if(a!=null&&a.length>0&&a[0]!=null){
     Object target=a[0];
     long id=XposedHelpers.getLongField(target,"a");
     synchronized(targets){
      if("c".equals(m.getName()))targets.remove(id);
      else targets.put(id,target);
     }
     log("discovery event="+m.getName()+" count="+targets.size());
     publish();
    }
    return null;
    }
   });
   Object builder=XposedHelpers.newInstance(XposedHelpers.findClass("endy",gmsLoader));
   XposedHelpers.setObjectField(builder,"m",XposedHelpers.callMethod(gmsService,"L"));
   Object options=XposedHelpers.callMethod(builder,"a");
   ownDiscovery=true;
   XposedHelpers.callMethod(provider,"x",discoveryCallback,options);
   log("requested live AirDrop discovery");
  }catch(Throwable t){ownDiscovery=false;log("discovery failed");XposedBridge.log(t);}
 }
 static synchronized void stopDiscovery(){
  try{
   if(activeSend!=Long.MIN_VALUE){stopRequested=true;return;}
   if(!ownDiscovery){publish();return;}
   ownDiscovery=false;
   if(provider!=null)XposedHelpers.callMethod(provider,"C");
   XposedHelpers.callMethod(gmsService,"bo",false);
   synchronized(targets){targets.clear();}
   publish();log("discovery stopped");
  }catch(Throwable t){XposedBridge.log(t);}
 }
 static void publish(){
  try{
   long[] ids;String[] labels;
   synchronized(targets){
    ids=new long[targets.size()];labels=new String[ids.length];int j=0;
    for(Map.Entry<Long,Object> e:targets.entrySet()){
     ids[j]=e.getKey();labels[j]=(String)XposedHelpers.getObjectField(e.getValue(),"b");j++;
    }
   }
   Intent i=new Intent(RESULTS).setPackage("com.coloros.oshare");
   i.putExtra("ids",ids).putExtra("labels",labels).putExtra("stopped",!ownDiscovery);
   gmsContext.sendBroadcast(i,PRIV);
  }catch(Throwable t){XposedBridge.log(t);}
 }
 static void command(Activity a,String op){
  a.sendBroadcast(new Intent(CONTROL).setPackage("com.google.android.gms").putExtra("op",op),PRIV);
 }
 static void sendUpdate(long id,int status,float percent,boolean terminal){
  Intent i=new Intent(RESULTS).setPackage("com.coloros.oshare");
  i.putExtra("transfer",true).putExtra("id",id).putExtra("status",status)
   .putExtra("percent",percent).putExtra("terminal",terminal);
  gmsContext.sendBroadcast(i,PRIV);
  log("send update status="+status+" progress="+percent+" final="+terminal);
  if(terminal&&activeSend==id){
   activeSend=Long.MIN_VALUE;
   if(stopRequested)XposedHelpers.callMethod(gmsService,"ax",new Runnable(){public void run(){stopDiscovery();}});
  }
 }
 static void sendDirect(Intent request){
  final long id=request.getLongExtra("id",Long.MIN_VALUE);
  if(incomingActive||activeSend!=Long.MIN_VALUE){sendUpdate(id,1007,0,true);return;}
  try{
   if(!ownDiscovery||provider==null)throw new IllegalStateException("Discovery session unavailable");
   Object target;synchronized(targets){target=targets.get(id);}
   if(target==null)throw new IllegalStateException("Target no longer visible");
   ArrayList<Uri> uris=request.getParcelableArrayListExtra("uris");
   if(uris==null||uris.isEmpty())throw new IllegalArgumentException("No selected files");
   Object builder=XposedHelpers.callMethod(target,"b");
   ((List)XposedHelpers.getObjectField(builder,"i")).clear();
   Object outgoing=XposedHelpers.callMethod(builder,"a");
   long attachmentId=System.nanoTime()&Long.MAX_VALUE;
   for(Uri uri:uris){
    String name="shared-file";long size=-1;
    try(Cursor c=gmsContext.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
     if(c!=null&&c.moveToFirst()){
      if(!c.isNull(0))name=c.getString(0);
      if(!c.isNull(1))size=c.getLong(1);
     }
    }
    if(size<=0){
     try(android.content.res.AssetFileDescriptor fd=gmsContext.getContentResolver().openAssetFileDescriptor(uri,"r")){
      if(fd!=null)size=fd.getLength();
     }
    }
    if(size<=0)throw new IllegalArgumentException("File size is unavailable or empty");
    String mime=gmsContext.getContentResolver().getType(uri);
    if(mime==null)mime="application/octet-stream";
    int type=mime.startsWith("image/")?1:mime.startsWith("video/")?2:mime.startsWith("audio/")?4:5;
    Object file=XposedHelpers.newInstance(XposedHelpers.findClass("com.google.android.gms.nearby.sharing.FileAttachment",gmsLoader),
     name,type,size,uri,new Bundle(),mime,++attachmentId,null,0L,0L,false);
    XposedHelpers.callMethod(outgoing,"l",file);
   }
   Class<?> callbackType=XposedHelpers.findClass("eicy",gmsLoader);
   Object callback=Proxy.newProxyInstance(gmsLoader,new Class[]{callbackType},new InvocationHandler(){
    public Object invoke(Object p,Method m,Object[] a){
     if(m.getDeclaringClass()==Object.class){
      if("hashCode".equals(m.getName()))return System.identityHashCode(p);
      if("equals".equals(m.getName()))return p==a[0];
      return "CodexOShareTransfer";
     }
     if(a!=null&&a.length==2&&a[1]!=null){
      Object meta=a[1];
      int status=XposedHelpers.getIntField(meta,"a");
      float percent=(Float)XposedHelpers.getObjectField(meta,"b");
      boolean terminal=XposedHelpers.getBooleanField(meta,"e");
      sendUpdate(id,status,percent,terminal||status==1006);
     }
     return null;
    }
   });
   Object options=XposedHelpers.newInstance(XposedHelpers.findClass("enff",gmsLoader),false,false,0,null,null);
   activeSend=id;
   sendUpdate(id,1001,0,false);
   int result=(Integer)XposedHelpers.callMethod(provider,"f",XposedHelpers.callMethod(gmsService,"L"),outgoing,callback,options);
   log("direct send request result="+result+" files="+uris.size());
   if(result!=0)sendUpdate(id,1007,0,true);
  }catch(Throwable t){log("direct send failed");XposedBridge.log(t);sendUpdate(id,1007,0,true);}
 }
 public static void installOshare(XC_LoadPackage.LoadPackageParam pkg){
  try{
   if(XposedHelpers.getLongField(pkg.appInfo,"longVersionCode")!=17002036L){log("unsupported OShare version");return;}
   OShareReceiveUI.install(pkg.classLoader);
   String activity="com.oplus.oshare.ui.ShareActivity";
   XposedHelpers.findAndHookMethod(activity,pkg.classLoader,"onResume",new XC_MethodHook(){
    protected void afterHookedMethod(MethodHookParam p){
     Activity a=(Activity)p.thisObject;screen=new WeakReference<>(a);visible=true;
     OShareReceiveUI.register(a.getApplicationContext());
     if(!receiverReady){
      receiverReady=true;
      a.getApplicationContext().registerReceiver(new BroadcastReceiver(){
       public void onReceive(Context c,Intent i){
        if(i.getBooleanExtra("transfer",false)){
         long id=i.getLongExtra("id",Long.MIN_VALUE);int status=i.getIntExtra("status",1000);
         int percent=Math.max(0,Math.min(100,Math.round(i.getFloatExtra("percent",0))));
         uiTransfers.put(id,new int[]{status,percent});
         Activity current=screen.get();if(current!=null&&visible)refresh(current);
         if(i.getBooleanExtra("terminal",false)){
          ArrayList<Uri> uris=grantedFiles.remove(id);
          if(uris!=null)for(Uri uri:uris)c.revokeUriPermission("com.google.android.gms",uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
         }
         return;
        }
        long[] ids=i.getLongArrayExtra("ids");String[] labels=i.getStringArrayExtra("labels");
        if(ids==null||labels==null||ids.length!=labels.length)return;
        names.clear();for(int k=0;k<ids.length;k++)names.put(ids[k],labels[k]);
        Activity current=screen.get();
        if(current!=null&&visible)refresh(current);
       }
      },new IntentFilter(RESULTS),PRIV,MAIN,Context.RECEIVER_EXPORTED);
     }
     command(a,"start");
     MAIN.removeCallbacks(heartbeat);MAIN.postDelayed(heartbeat,10000);
     log("OShare page resumed");
    }
   });
   XposedHelpers.findAndHookMethod(activity,pkg.classLoader,"onPause",new XC_MethodHook(){
    protected void beforeHookedMethod(MethodHookParam p){
     visible=false;MAIN.removeCallbacks(heartbeat);names.clear();command((Activity)p.thisObject,"stop");
    }
   });
   XposedHelpers.findAndHookMethod(activity,pkg.classLoader,"u1",new XC_MethodHook(){
    protected void beforeHookedMethod(MethodHookParam p){merge((Activity)p.thisObject,pkg.classLoader);}
   });
   XposedHelpers.findAndHookMethod(activity,pkg.classLoader,"n1",boolean.class,boolean.class,
    XposedHelpers.findClass("com.oplus.oshare.OplusOshareDevice",pkg.classLoader),boolean.class,new XC_MethodHook(){
     protected void beforeHookedMethod(MethodHookParam p){
      Object id=p.args[2]==null?null:XposedHelpers.getAdditionalInstanceField(p.args[2],TAG);
      if(id==null)return;
      p.setResult(null);
      Activity a=(Activity)p.thisObject;Intent original=a.getIntent();
      String action=original.getAction();
      if(!Intent.ACTION_SEND.equals(action)&&!Intent.ACTION_SEND_MULTIPLE.equals(action)){
       Toast.makeText(a,"请先从相册或文件管理选择文件，再通过互传发送",Toast.LENGTH_LONG).show();return;
      }
      ArrayList<Uri> uris=new ArrayList<>();
      if(Intent.ACTION_SEND_MULTIPLE.equals(action)){
       ArrayList<Uri> selected=original.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
       if(selected!=null)uris.addAll(selected);
      }else{
       Uri selected=original.getParcelableExtra(Intent.EXTRA_STREAM);
       if(selected!=null)uris.add(selected);
      }
      if(uris.isEmpty()){Toast.makeText(a,"没有可发送的文件",Toast.LENGTH_LONG).show();return;}
      long targetId=(Long)id;
      int[] existing=uiTransfers.get(targetId);
      if(existing!=null&&(existing[0]==1001||existing[0]==1003||existing[0]==1005))return;
      try{
       for(Uri uri:uris)a.grantUriPermission("com.google.android.gms",uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
       grantedFiles.put(targetId,uris);uiTransfers.put(targetId,new int[]{1001,0});refresh(a);
       Intent send=new Intent(CONTROL).setPackage("com.google.android.gms").putExtra("op","send").putExtra("id",targetId);
       send.putParcelableArrayListExtra("uris",uris);a.sendBroadcast(send,PRIV);
       log("requested direct send from original OShare page");
      }catch(Throwable t){XposedBridge.log(t);uiTransfers.put(targetId,new int[]{1007,0});refresh(a);}
     }
    });
   log("OShare 17.2.36 list bridge installed");
  }catch(Throwable t){XposedBridge.log(t);}
 }
 static final Runnable heartbeat=new Runnable(){public void run(){
  Activity a=screen.get();if(a!=null&&visible){command(a,"start");MAIN.postDelayed(this,10000);}
 }};
 static void refresh(Activity a){
  try{XposedHelpers.callMethod(a,"u1");}catch(Throwable t){XposedBridge.log(t);}
 }
 static void merge(Activity a,ClassLoader loader){
  try{
   ArrayList list=(ArrayList)XposedHelpers.getObjectField(a,"y4");
   for(Iterator it=list.iterator();it.hasNext();){
    Object model=it.next();Object nativeDevice=XposedHelpers.callMethod(model,"l");
    if(nativeDevice!=null&&XposedHelpers.getAdditionalInstanceField(nativeDevice,TAG)!=null)it.remove();
   }
   for(Map.Entry<Long,String> entry:names.entrySet()){
    Object model=XposedHelpers.newInstance(XposedHelpers.findClass("v9.d",loader));
    Object device=XposedHelpers.callMethod(model,"l");
    String label=entry.getValue();
    XposedHelpers.callMethod(device,"setName",label);
    XposedHelpers.callMethod(device,"setDisplayName",label);
    long id=entry.getKey();String mac=String.format(java.util.Locale.ROOT,"02:AD:%02X:%02X:%02X:%02X",(id>>>24)&255,(id>>>16)&255,(id>>>8)&255,id&255);
    XposedHelpers.callMethod(device,"setBleMac",mac);
    XposedHelpers.callMethod(device,"setVender",800);
    XposedHelpers.callMethod(model,"t0",OSharePreview.iphone());
    XposedHelpers.callMethod(model,"G0","AirDrop");
    int[] transfer=uiTransfers.get(id);
    if(transfer!=null){
     int status=transfer[0];
     String state=status==1006?"TRANSIT_SUCCESS":status==1005?"TRANSITING":status==1008?"TRANSIT_REJECT":status==1009?"CANCEL":status==1010?"TRANSIT_TIMEOUT":status==1007||status==1004?"TRANSIT_FAILED":"TRANSIT_WAIT";
     Object value=XposedHelpers.getStaticObjectField(XposedHelpers.findClass("com.oplus.oshare.OplusOshareState",loader),state);
     XposedHelpers.callMethod(device,"setState",value);
     XposedHelpers.callMethod(device,"setProgress",transfer[1]);
    }
    XposedHelpers.setAdditionalInstanceField(device,TAG,id);
    list.add(model);
   }
   log("merged AirDrop devices="+names.size());
  }catch(Throwable t){XposedBridge.log(t);}
 }
}
