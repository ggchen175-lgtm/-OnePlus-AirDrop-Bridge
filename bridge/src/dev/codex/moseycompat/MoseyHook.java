package dev.codex.moseycompat;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class MoseyHook implements IXposedHookLoadPackage, de.robv.android.xposed.IXposedHookZygoteInit {
  public static String modulePath;
  public void initZygote(de.robv.android.xposed.IXposedHookZygoteInit.StartupParam p){modulePath=p.modulePath;}
  private static final String SERVICE = "com.google.android.moseyservice.IMoseyService/default";
  public void handleLoadPackage(XC_LoadPackage.LoadPackageParam pkg) {
    if ("com.coloros.oshare".equals(pkg.packageName)) {
      OShareBridge.installOshare(pkg);
      return;
    }
    if ("com.google.android.gms".equals(pkg.packageName)) {
      try {
        long version=XposedHelpers.getLongField(pkg.appInfo,"longVersionCode");
        if(version!=264032035L) {
          XposedBridge.log("CodexMosey: skipping GMS hooks for unsupported version="+version);
          return;
        }
        for(final String method:new String[]{"w","A"}) {
          XposedHelpers.findAndHookMethod("kbhv",pkg.classLoader,method,new XC_MethodReplacement(){
            int count=0;
            protected Object replaceHookedMethod(MethodHookParam p) {
              if(count++<2) XposedBridge.log("CodexMosey: gate called="+method);
              return true;
            }
          });
        }
        for(final String method:new String[]{"W","af"}) {
          XposedHelpers.findAndHookMethod("kbhx",pkg.classLoader,method,new XC_MethodReplacement(){
            int count=0;
            protected Object replaceHookedMethod(MethodHookParam p) {
              if(count++<3) XposedBridge.log("CodexMosey: flag getter called="+method);
              return true;
            }
          });
        }
        XposedHelpers.findAndHookMethod("com.google.android.gms.nearby.sharing.NearbySharingChimeraService",pkg.classLoader,"ae",boolean.class,new XC_MethodHook(){
          protected void afterHookedMethod(MethodHookParam p) {
            XposedBridge.log("CodexMosey: registry check F="+XposedHelpers.getBooleanField(p.thisObject,"F")+" running="+XposedHelpers.getBooleanField(p.thisObject,"bD"));
          }
        });
        XposedHelpers.findAndHookMethod("com.google.android.gms.nearby.sharing.NearbySharingChimeraService",pkg.classLoader,"onCreate",new XC_MethodHook(){
          protected void afterHookedMethod(MethodHookParam p) {
            XposedBridge.log("CodexMosey: nearby service created class="+p.thisObject.getClass());
            OShareBridge.installGms(p.thisObject,pkg.classLoader);
          }
        });
        XposedBridge.log("CodexMosey: GMS 26.40.32 external-provider gates enabled in "+pkg.processName);
      } catch(Throwable t) { XposedBridge.log(t); }
      return;
    }
    if (!"com.google.android.mosey".equals(pkg.packageName)) return;
    try{OSharePreview.installMosey(pkg.classLoader);}catch(Throwable t){XposedBridge.log(t);}
    XposedHelpers.findAndHookMethod(java.security.KeyStore.class,"getInstance",String.class,new XC_MethodHook(){
      protected void afterHookedMethod(MethodHookParam p){
        if(!"PKCS12".equalsIgnoreCase((String)p.args[0])||!p.hasThrowable())return;
        try {
          // Mosey creates an empty in-memory store for its ephemeral TLS identity.
          // No PKCS12 file is imported or exported in this receive path.
          java.security.KeyStore store=java.security.KeyStore.getInstance("BKS");
          p.setResult(store);
          XposedBridge.log("CodexMosey: ephemeral TLS store using available BKS provider="+store.getProvider().getName());
        } catch(Throwable t){XposedBridge.log("CodexMosey: ephemeral TLS store unavailable: "+t);}
      }
    });
    XposedHelpers.findAndHookMethod("android.os.ServiceManager", null,
      "waitForDeclaredService", String.class, new XC_MethodHook() {
        protected void beforeHookedMethod(MethodHookParam p) {
          if (!SERVICE.equals(p.args[0])) return;
          Object binder = XposedHelpers.callStaticMethod(
            XposedHelpers.findClass("android.os.ServiceManager", null), "getService", SERVICE);
          XposedBridge.log("CodexMosey: local service lookup=" + (binder != null));
          p.setResult(binder);
        }
      });
    XposedBridge.log("CodexMosey: hook loaded only in " + pkg.processName);
  }
}
