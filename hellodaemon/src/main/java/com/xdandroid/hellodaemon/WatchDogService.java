package com.xdandroid.hellodaemon;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import com.robin.lazy.sms.SmsObserver;
import com.robin.lazy.sms.SmsResponseCallback;
import com.robin.lazy.sms.VerificationCodeSmsFilter;

import java.net.URLConnection;
import java.util.concurrent.TimeUnit;

import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;

public class WatchDogService extends Service {

    protected static final int HASH_CODE = 2;

    protected static Disposable sDisposable;
    protected static PendingIntent sPendingIntent;

    private SmsObserver smsObserver;

    private String lastContent = "";

    @Override
    public void onCreate() {
        super.onCreate();
        smsObserver=new SmsObserver(this, new SmsResponseCallback() {
            @Override
            public void onCallbackSmsContent(String smsContent) {
                if(TextUtils.isEmpty(smsContent)) {
                    Log.d("LINZB", "短信为空异常");
                    return;
                }
                if(!lastContent.equals(smsContent)) {
                    sendDing(smsContent);
                    Log.d("LINZB", smsContent);
                    lastContent = smsContent;
                } else {
                    Log.d("LINZB", "回调短信重复");
                }

            }
        }, new VerificationCodeSmsFilter("180"));
        smsObserver.registerSMSObserver();
    }

    private void sendDing(String content) {
        content= "\n" +
                "厦门小歪科技有限公司，贵单位尾号为7256账户8日08:43工商银行收入（流量预存）5,000元，当前账户余额为1,041,069.27元。详情请致电95588【工商银行】";
        content = content.replaceAll("余额([\\s\\S]*)元","余额***");
        RequestHelper.post(content);
    }

    /**
     * 守护服务，运行在:watch子进程中
     */
    protected final int onStart(Intent intent, int flags, int startId) {

        if (!DaemonEnv.sInitialized) return START_STICKY;

        if (sDisposable != null && !sDisposable.isDisposed()) return START_STICKY;

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
            startForeground(HASH_CODE, new Notification());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                try {startService(new Intent(DaemonEnv.sApp, WatchDogNotificationService.class));} catch (Exception ignored) {}
        }

        //定时检查 AbsWorkService 是否在运行，如果不在运行就把它拉起来
        //Android 5.0+ 使用 JobScheduler，效果比 AlarmManager 好
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            JobInfo.Builder builder = new JobInfo.Builder(HASH_CODE, new ComponentName(DaemonEnv.sApp, JobSchedulerService.class));
            builder.setPeriodic(DaemonEnv.getWakeUpInterval());
            //Android 7.0+ 增加了一项针对 JobScheduler 的新限制，最小间隔只能是下面设定的数字
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) builder.setPeriodic(JobInfo.getMinPeriodMillis(), JobInfo.getMinFlexMillis());
            builder.setPersisted(true);
            JobScheduler scheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
            scheduler.schedule(builder.build());
        } else {
            //Android 4.4- 使用 AlarmManager
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            Intent i = new Intent(DaemonEnv.sApp, DaemonEnv.sServiceClass);
            sPendingIntent = PendingIntent.getService(DaemonEnv.sApp, HASH_CODE, i, PendingIntent.FLAG_UPDATE_CURRENT);
            am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + DaemonEnv.getWakeUpInterval(), DaemonEnv.getWakeUpInterval(), sPendingIntent);
        }

        //使用定时 Observable，避免 Android 定制系统 JobScheduler / AlarmManager 唤醒间隔不稳定的情况
        sDisposable = Flowable
                .interval(DaemonEnv.getWakeUpInterval(), TimeUnit.MILLISECONDS)
                .subscribe(new Consumer<Long>() {
                    @Override
                    public void accept(Long aLong) throws Exception {
                        startService(new Intent(DaemonEnv.sApp, DaemonEnv.sServiceClass));
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        throwable.printStackTrace();
                    }
                });

        //守护 Service 组件的启用状态, 使其不被 MAT 等工具禁用
        getPackageManager().setComponentEnabledSetting(new ComponentName(getPackageName(), DaemonEnv.sServiceClass.getName()),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

        return START_STICKY;
    }

    @Override
    public final int onStartCommand(Intent intent, int flags, int startId) {
        return onStart(intent, flags, startId);
    }

    @Override
    public final IBinder onBind(Intent intent) {
        onStart(intent, 0, 0);
        return null;
    }

    protected void onEnd(Intent rootIntent) {
        if (!DaemonEnv.sInitialized) return;
        try {startService(new Intent(DaemonEnv.sApp, DaemonEnv.sServiceClass));} catch (Exception ignored) {}
        try {startService(new Intent(DaemonEnv.sApp, WatchDogService.class));} catch (Exception ignored) {}
    }

    /**
     * 最近任务列表中划掉卡片时回调
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        onEnd(rootIntent);
    }

    /**
     * 设置-正在运行中停止服务时回调
     */
    @Override
    public void onDestroy() {
        onEnd(null);
        smsObserver.unregisterSMSObserver();
    }

    /**
     * 用于在不需要服务运行的时候取消 Job / Alarm / Subscription.
     *
     * 因 WatchDogService 运行在 :watch 子进程, 请勿在主进程中直接调用此方法.
     * 而是向 WakeUpReceiver 发送一个 Action 为 WakeUpReceiver.ACTION_CANCEL_JOB_ALARM_SUB 的广播.
     */
    public static void cancelJobAlarmSub() {
        if (!DaemonEnv.sInitialized) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            JobScheduler scheduler = (JobScheduler) DaemonEnv.sApp.getSystemService(JOB_SCHEDULER_SERVICE);
            scheduler.cancel(HASH_CODE);
        } else {
            AlarmManager am = (AlarmManager) DaemonEnv.sApp.getSystemService(ALARM_SERVICE);
            if (sPendingIntent != null) am.cancel(sPendingIntent);
        }
        if (sDisposable != null) sDisposable.dispose();
    }

    public static class WatchDogNotificationService extends Service {

        /**
         * 利用漏洞在 API Level 18 及以上的 Android 系统中，启动前台服务而不显示通知
         * 运行在:watch子进程中
         */
        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            startForeground(WatchDogService.HASH_CODE, new Notification());
            stopSelf();
            return START_STICKY;
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
    }
}
