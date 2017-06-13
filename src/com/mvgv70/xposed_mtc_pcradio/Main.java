package com.mvgv70.xposed_mtc_pcradio;

import com.mvgv70.utils.Utils;

import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Main implements IXposedHookLoadPackage
{
  private static String songInfo = "";
  private static String radioTitle = "";
  private static Activity radioActivity = null;
  private static Service radioService = null;
  private static boolean mPlaying = false;
  private final static String TAG = "xposed-mtc-pcradio";
  
  @Override
  public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable 
  {
    // RadioActivity.onCreate(Bundle)
    XC_MethodHook onCreateActivity = new XC_MethodHook() {

      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"Activity.onCreate");
        radioActivity = (Activity)param.thisObject;
        // показать версию модуля
        try 
        {
          Context context = radioActivity.createPackageContext(getClass().getPackage().getName(), Context.CONTEXT_IGNORE_SECURITY);
          String version = context.getString(R.string.app_version_name);
          Log.d(TAG,"version="+version);
          Log.d(TAG,"android "+Build.VERSION.RELEASE);
        } catch (NameNotFoundException e) {}
        // обработчик com.android.music.playstatusrequest
        IntentFilter qi = new IntentFilter();
        qi.addAction("com.android.music.playstatusrequest");
        radioActivity.registerReceiver(tagsQueryReceiver, qi);
        // запуск штатных приложений
        IntentFilter mi = new IntentFilter();
        mi.addAction("com.microntek.canbusdisplay");
        radioActivity.registerReceiver(mtcAppReceiver, mi);
        Log.d(TAG,"receivers created");
      }
    };
    
    // RadioActivity.onDestroy()
    XC_MethodHook onDestroyActivity = new XC_MethodHook() {

       @Override
       protected void afterHookedMethod(MethodHookParam param) throws Throwable {
         Log.d(TAG,"Activity.onDestroy");
         // выключаем Receivers
         radioActivity.unregisterReceiver(tagsQueryReceiver);
         radioActivity.unregisterReceiver(mtcAppReceiver);
         radioActivity = null;
      }
    };
    
    // RadioService.onCreate()
    XC_MethodHook onCreateService = new XC_MethodHook() {

      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"Service.onCreate");
        radioService = (Service)param.thisObject;
      }
    };
    
    // RadioService.onDestroy()
    XC_MethodHook onDestroyService = new XC_MethodHook() {

      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"Service.onDestroy");
        radioService = null;
        mPlaying = false;
      }
    };
     
    // RadioService.onPlay()
    XC_MethodHook onPlay = new XC_MethodHook() {

      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"onPlay");
        mPlaying = true;
        // разослать интент о закрытии штатных приложений
        Log.d(TAG,"turn Microntek apps off");
        Intent intent = new Intent("com.microntek.bootcheck");
        intent.putExtra("class", radioService.getPackageName());
        radioService.sendBroadcast(intent);
      }
    };
    
    // RadioService.onPause()
   	XC_MethodHook onPause = new XC_MethodHook() {

      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"onPause");
        mPlaying = false;
      }
    };
     
    // RadioService.onStop()
    XC_MethodHook onStop = new XC_MethodHook() {

      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"onStop");
        mPlaying = false;
      }
    };
    
    // RadioService.setInfo(String,String)
    XC_MethodHook setInfo = new XC_MethodHook() {

      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        Log.d(TAG,"onInfo");
        // TODO: сохраним информацию о песне
        radioTitle = (String)param.args[0];
        songInfo = (String)param.args[1];
        Log.d(TAG,"radioTitle="+radioTitle+", songInfo="+songInfo);
        sendNotifyIntent(radioService);
      }
    }; 
     
    // start hooks
    if (!lpparam.packageName.equals("com.maxxt.pcradio")) return;
    Log.d(TAG,"com.maxxt.pcradio");
    Utils.readXposedMap();
    Utils.setTag(TAG);
    Utils.findAndHookMethod("com.maxxt.pcradio.RadioActivity", lpparam.classLoader, "onCreate", Bundle.class, onCreateActivity);
    Utils.findAndHookMethod("com.maxxt.pcradio.RadioActivity", lpparam.classLoader, "onDestroy", onDestroyActivity);
    Utils.findAndHookMethod("com.maxxt.pcradio.service.RadioService", lpparam.classLoader, "onCreate", onCreateService);
    Utils.findAndHookMethod("com.maxxt.pcradio.service.RadioService", lpparam.classLoader, "onDestroy", onDestroyService);
    Utils.findAndHookMethod("com.maxxt.pcradio.service.RadioService", lpparam.classLoader, "l", onPlay);
    Utils.findAndHookMethod("com.maxxt.pcradio.service.RadioService", lpparam.classLoader, "o", onPause);
    Utils.findAndHookMethod("com.maxxt.pcradio.service.RadioService", lpparam.classLoader, "p", onStop);
    Utils.findAndHookMethod("com.maxxt.pcradio.service.RadioService", lpparam.classLoader, "b", String.class, String.class, setInfo);
    Log.d(TAG,"com.maxxt.pcradio hook OK");
  }
  
  // отправка информации о воспроизведении
  private void sendNotifyIntent(Context context)
  {
    Intent intent = new Intent("com.android.music.playstatechanged");
    intent.putExtra(MediaStore.EXTRA_MEDIA_TITLE, songInfo);
    intent.putExtra(MediaStore.EXTRA_MEDIA_ARTIST, radioTitle);
    context.sendBroadcast(intent);
  }
  
  // обработчик com.android.music.querystate
  private BroadcastReceiver tagsQueryReceiver = new BroadcastReceiver()
  {

    public void onReceive(Context context, Intent intent)
    {
      // отправить информацию
      Log.d(TAG,"PCRadio: tags query receiver, mPlaying="+mPlaying);
      if (mPlaying) sendNotifyIntent(context);
    }
  };
  
  // com.microntek.canbusdisplay
  private BroadcastReceiver mtcAppReceiver = new BroadcastReceiver()
  {

    public void onReceive(Context context, Intent intent)
    {
      String type = intent.getStringExtra("type");
      Log.d(TAG,"com.microntek.canbusdisplay, type="+type);
      if (type.endsWith("-on"))
      {
        Log.d(TAG,"mPlaying="+mPlaying);
        // запускается штатная программа, выключим PCRadio
        if (mPlaying)
        {
          // TODO: поставим PcRadio на паузу
          Log.d(TAG,"turn PcRadio off");
          Utils.callMethod(radioService, "p");
        }
      }
    }
  };

}
