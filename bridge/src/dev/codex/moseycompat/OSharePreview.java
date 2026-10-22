package dev.codex.moseycompat;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Size;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import de.robv.android.xposed.*;

public final class OSharePreview {
 public static String nativeLibraryPath(){
  if(MoseyHook.modulePath!=null){
   String path=new File(MoseyHook.modulePath).getParent()+"/lib/arm64/libopenjpeg.so";
   if(new File(path).exists())return path;
  }
  try{return android.app.AndroidAppHelper.currentApplication().getPackageManager().getApplicationInfo("dev.codex.moseycompat",0).nativeLibraryDir+"/libopenjpeg.so";}
  catch(Throwable t){throw new IllegalStateException(t);}
 }
 static final String KEY="codex.airdrop.preview";
 static final ThreadLocal<byte[]> ASK=new ThreadLocal<>();
 static Bitmap phone;
 static android.content.Context moseyContext;
 static byte[] successPng;
 static String success(){
  if(successPng!=null)return source(successPng);
  Bitmap b=Bitmap.createBitmap(126,126,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(3);
  p.setColor(Color.rgb(42,193,109));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(7);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
  c.drawCircle(63,63,47,p);Path tick=new Path();tick.moveTo(40,64);tick.lineTo(56,80);tick.lineTo(86,47);c.drawPath(tick,p);
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,bytes);b.recycle();
  // Preview URI files are cleaned after an hour; keep bytes, never cache their expiring URI.
  successPng=bytes.toByteArray();return source(successPng);
 }
 static Bitmap iphone(){
  if(phone!=null)return phone;
  Bitmap b=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(3);
  p.setColor(Color.rgb(230,238,249));c.drawCircle(96,96,96,p);
  p.setColor(Color.rgb(46,52,63));c.drawRoundRect(58,28,134,164,15,15,p);
  p.setColor(Color.rgb(196,217,243));c.drawRoundRect(63,33,129,159,11,11,p);
  p.setColor(Color.rgb(46,52,63));c.drawRoundRect(81,39,111,47,4,4,p);
  c.drawRoundRect(83,151,109,154,1.5f,1.5f,p);phone=b;return b;
 }
 static byte[] jpeg(Bitmap bitmap){
  int width=bitmap.getWidth(),height=bitmap.getHeight();
  float scale=Math.min(1f,384f/Math.max(width,height));
  Bitmap small=Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(width*scale)),Math.max(1,Math.round(height*scale)),true);
  ByteArrayOutputStream out=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.JPEG,82,out);
  if(small!=bitmap)small.recycle();return out.toByteArray();
 }
 static byte[] normalize(byte[] data){
  if(data==null||data.length==0||data.length>2*1024*1024)return null;
  BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,o);
  if(o.outWidth<=0||o.outHeight<=0){
   if(data.length>=12&&data[4]=='j'&&data[5]=='P')try{
    com.gemalto.jp2.JP2Decoder decoder=new com.gemalto.jp2.JP2Decoder(data);
    com.gemalto.jp2.JP2Decoder.Header header=decoder.readHeader();
    if(header==null||header.width<=0||header.height<=0||header.width>8192||header.height>8192)return null;
    int skip=0;while(Math.max(header.width,header.height)/(1<<skip)>768)skip++;
    if(skip>0)decoder.setSkipResolutions(skip);
    Bitmap jp2=decoder.decode();if(jp2==null)return null;
    byte[] result=jpeg(jp2);jp2.recycle();OShareBridge.log("AirDrop JPEG2000 preview decoded bytes="+result.length);return result;
   }catch(Throwable t){OShareBridge.log("JPEG2000 preview decoder unavailable: "+t+" cause="+t.getCause());}
   return null;
  }
  int sample=1;while(Math.max(o.outWidth,o.outHeight)/sample>768)sample*=2;
  o.inJustDecodeBounds=false;o.inSampleSize=sample;
  Bitmap b=BitmapFactory.decodeByteArray(data,0,data.length,o);if(b==null)return null;
  byte[] result=jpeg(b);b.recycle();return result.length<160000?result:null;
 }
 static String source(byte[] data){
  if(data==null)return null;
  try{
   File folder=new File(OShareReceiveUI.app.getCacheDir(),"cache/codex-airdrop-preview");folder.mkdirs();
   File[] stale=folder.listFiles();if(stale!=null)for(File f:stale)if(System.currentTimeMillis()-f.lastModified()>3600000L)f.delete();
   File image=File.createTempFile("preview-",data.length>4&&data[1]=='P'?".png":".jpg",folder);
   try(FileOutputStream out=new FileOutputStream(image)){out.write(data);}
   Uri uri=(Uri)XposedHelpers.callStaticMethod(XposedHelpers.findClass("androidx.core.content.FileProvider",OShareReceiveUI.loader),
    "getUriForFile",OShareReceiveUI.app,"com.oplus.oshare.fileprovider",image);
   for(String pkg:new String[]{"com.android.systemui","com.oplus.pantanal.ums","com.nearme.instant.card"})
    OShareReceiveUI.app.grantUriPermission(pkg,uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
   OShareBridge.log("cloud preview prepared via local provider bytes="+data.length);
   return uri.toString();
  }catch(Throwable t){OShareBridge.log("cloud preview provider failed: "+t);return null;}
 }
 static String received(Uri uri){
  try{
   Bitmap b=OShareReceiveUI.app.getContentResolver().loadThumbnail(uri,new Size(384,384),null);
   byte[] data=jpeg(b);b.recycle();return source(data);
  }catch(Throwable t){OShareBridge.log("received preview unavailable: "+t.getClass().getSimpleName());return null;}
 }
 static byte[] imageData(Object value){
  if(value instanceof byte[])return normalize((byte[])value);
  if(value==null)return null;
  for(Field f:value.getClass().getDeclaredFields()){
   if(f.getType()==byte[].class)try{
    f.setAccessible(true);byte[] raw=(byte[])f.get(value);
    if(raw!=null){StringBuilder magic=new StringBuilder();for(int i=0;i<Math.min(12,raw.length);i++)magic.append(String.format(java.util.Locale.ROOT,"%02x",raw[i]&255));
     OShareBridge.log("AirDrop preview encoding bytes="+raw.length+" header="+magic);
    }
    return normalize(raw);
   }catch(Throwable ignored){}
  }
  return null;
 }
 static void installMosey(ClassLoader loader){
  XposedHelpers.findAndHookMethod(android.app.Application.class,"onCreate",new XC_MethodHook(){
   protected void afterHookedMethod(MethodHookParam p){
    moseyContext=(android.content.Context)p.thisObject;
    try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(MoseyHook.modulePath)){
     InputStream in=zip.getInputStream(zip.getEntry("assets/jp2-fixture.jp2"));
     ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
     while((count=in.read(buffer))>0)out.write(buffer,0,count);in.close();
     OShareBridge.log("JPEG2000 local fixture decoded="+(normalize(out.toByteArray())!=null));
    }catch(Throwable t){OShareBridge.log("JPEG2000 self-check failed: "+t+" cause="+t.getCause());}
   }
  });
  XposedHelpers.findAndHookMethod("bib",loader,"a",java.net.InetSocketAddress.class,XposedHelpers.findClass("wf",loader),XposedHelpers.findClass("bms",loader),new XC_MethodHook(){
   protected void beforeHookedMethod(MethodHookParam p){
    ASK.remove();
    if(!(p.args[1] instanceof Map))return;
    Map request=(Map)p.args[1];byte[] data=null;
    for(Object k:request.keySet()){
     String key=String.valueOf(k).toLowerCase(java.util.Locale.ROOT);
     if(!key.contains("sender")&&(key.contains("icon")||key.contains("thumbnail")||key.contains("preview"))){
      data=imageData(request.get(k));if(data!=null)break;
     }
    }
    if(data!=null)ASK.set(data);
    if(data!=null&&moseyContext!=null){
     Object sender=request.get("SenderComputerName");
     String name="";
     try{Object files=request.get("Files");Object[] array=(Object[])XposedHelpers.getObjectField(files,"a");
      if(array.length>0&&array[0] instanceof Map)name=String.valueOf(((Map)array[0]).get("FileName"));
     }catch(Throwable ignored){}
     moseyContext.sendBroadcast(new android.content.Intent(OShareReceiveUI.EVENT).setPackage("com.coloros.oshare")
      .putExtra("preview_only",true).putExtra("key_sender_name",String.valueOf(sender))
      .putExtra("key_data_name",name).putExtra(KEY,data),OShareBridge.PRIV);
     OShareBridge.log("AirDrop request preview forwarded to OShare");
    }
    OShareBridge.log("AirDrop Ask preview="+(data==null?"unavailable":data.length)+" keys="+request.keySet());
   }
   protected void afterHookedMethod(MethodHookParam p){ASK.remove();}
  });
  XposedBridge.hookAllConstructors(XposedHelpers.findClass("bhg",loader),new XC_MethodHook(){
   protected void afterHookedMethod(MethodHookParam p){byte[] data=ASK.get();if(data!=null)XposedHelpers.setAdditionalInstanceField(p.thisObject,KEY,data);}
  });
  XposedHelpers.findAndHookMethod("bhg",loader,"a",new XC_MethodHook(){
   protected void afterHookedMethod(MethodHookParam p){
    byte[] data=(byte[])XposedHelpers.getAdditionalInstanceField(p.thisObject,KEY);if(data==null||p.getResult()==null)return;
    Bundle extras=(Bundle)XposedHelpers.getObjectField(p.getResult(),"e");extras.putByteArray(KEY,data);
   }
  });
 }
}
