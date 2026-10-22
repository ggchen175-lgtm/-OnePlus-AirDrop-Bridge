import android.content.ComponentName;
import java.lang.reflect.*;
import java.io.*;
public class CompatComponents {
 static final String[] N={"com.google.android.gms.nearby.sharing.main.MainActivity","com.google.android.gms.nearby.sharing.ReceiveSurfaceService","com.google.android.gms.nearby.sharing.DirectShareService","com.google.android.gms.nearby.sharing.unified.UnifiedActivity"};
 public static void main(String[] args)throws Exception{
  Object pm=Class.forName("android.app.AppGlobals").getMethod("getPackageManager").invoke(null);
  Class<?> api=Class.forName("android.content.pm.IPackageManager");
  Method get=api.getMethod("getComponentEnabledSetting",ComponentName.class,int.class);
  if(args.length==0||"snapshot".equals(args[0])){
   for(String n:N)System.out.println(n+"="+get.invoke(pm,new ComponentName("com.google.android.gms",n),0));
   return;
  }
  if("enable".equals(args[0])){
   Method set=api.getMethod("setComponentEnabledSetting",ComponentName.class,int.class,int.class,int.class,String.class);
   for(String n:N){set.invoke(pm,new ComponentName("com.google.android.gms",n),1,1,0,"com.android.shell");System.out.println("enabled "+n);}
   api.getMethod("setPackageStoppedState",String.class,boolean.class,int.class).invoke(pm,"com.google.android.mosey",false,0);
  }else if("restore".equals(args[0])){
   Method set=api.getMethod("setComponentEnabledSetting",ComponentName.class,int.class,int.class,int.class,String.class);
   String[] values=args[1].split(",");if(values.length!=N.length)throw new IllegalArgumentException("Need four original states");
   for(int i=0;i<N.length;i++)set.invoke(pm,new ComponentName("com.google.android.gms",N[i]),Integer.parseInt(values[i]),1,0,"com.android.shell");
   System.out.println("Original component states restored");
  }else throw new IllegalArgumentException("Unknown action");
 }
}
