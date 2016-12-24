package org.shaolin.uimaster.app.service;

import java.io.ByteArrayInputStream;
import java.lang.ref.WeakReference;


import org.apache.http.Header;
import org.json.JSONArray;
import org.json.JSONObject;
import org.shaolin.uimaster.app.context.AppConfig;
import org.shaolin.uimaster.app.context.AppContext;
import org.shaolin.uimaster.app.R;
import org.shaolin.uimaster.app.api.remote.RService;
import org.shaolin.uimaster.app.bean.Constants;
import org.shaolin.uimaster.app.bean.Notice;
import org.shaolin.uimaster.app.bean.NoticeDetail;
import org.shaolin.uimaster.app.bean.Result;
import org.shaolin.uimaster.app.bean.ResultBean;
import org.shaolin.uimaster.app.broadcast.AlarmReceiver;
import org.shaolin.uimaster.app.ui.MainActivity;
import org.shaolin.uimaster.app.util.UIHelper;
import org.shaolin.uimaster.app.util.XmlUtils;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.loopj.android.http.AsyncHttpResponseHandler;

public class NoticeService extends Service {
    public static final String INTENT_ACTION_GET = "org.shaolin.uimaster.app.service.GET_NOTICE";
    public static final String INTENT_ACTION_CLEAR = "org.shaolin.uimaster.app.service.CLEAR_NOTICE";
    public static final String INTENT_ACTION_BROADCAST = "org.shaolin.uimaster.app.service.BROADCAST";
    public static final String INTENT_ACTION_SHUTDOWN = "org.shaolin.uimaster.app.service.SHUTDOWN";
    public static final String INTENT_ACTION_REQUEST = "org.shaolin.uimaster.app.service.REQUEST";
    public static final String BUNDLE_KEY_TPYE = "bundle_key_type";

    private static final long INTERVAL = 1000 * 120;
    private AlarmManager mAlarmMgr;

    private Notice mNotice;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Constants.INTENT_ACTION_NOTICE.equals(action)) {
        	Notice notice = (Notice) intent.getSerializableExtra("notice_bean");
                int atmeCount = notice.getAtmeCount();// @我
                int msgCount = notice.getMsgCount();// 私信
                int reviewCount = notice.getReviewCount();// 评论
                int newFansCount = notice.getNewFansCount();// 新粉丝
                int newLikeCount = notice.getNewLikeCount();// 点赞数
                int activeCount = atmeCount + reviewCount + msgCount
                        + newFansCount + newLikeCount;
                if (activeCount == 0) {
                    NotificationManagerCompat.from(NoticeService.this).cancel(
                            R.string.you_have_news_messages);
                }
            } else if (INTENT_ACTION_BROADCAST.equals(action)) {
                if (mNotice != null) {
                   // UIHelper.sendBroadCast(NoticeService.this, mNotice);
                }
            } else if (INTENT_ACTION_SHUTDOWN.equals(action)) {
                stopSelf();
            } else if (INTENT_ACTION_REQUEST.equals(action)) {
                requestNotice();
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mAlarmMgr = (AlarmManager) getSystemService(ALARM_SERVICE);
        startRequestAlarm();
        requestNotice();

        IntentFilter filter = new IntentFilter(INTENT_ACTION_BROADCAST);
        filter.addAction(Constants.INTENT_ACTION_NOTICE);
        filter.addAction(INTENT_ACTION_SHUTDOWN);
        filter.addAction(INTENT_ACTION_REQUEST);
        registerReceiver(mReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        cancelRequestAlarm();
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    private void startRequestAlarm() {
        cancelRequestAlarm();
        // 从1秒后开始，每隔2分钟执行getOperationIntent()
        mAlarmMgr.setRepeating(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1000, INTERVAL,
                getOperationIntent());
    }

    /**
     * <!--  --> 即使启动PendingIntent的原进程结束了的话,PendingIntent本身仍然还存在，可在其他进程（
     * PendingIntent被递交到的其他程序）中继续使用.
     * 如果我在从系统中提取一个PendingIntent的，而系统中有一个和你描述的PendingIntent对等的PendingInent,
     * 那么系统会直接返回和该PendingIntent其实是同一token的PendingIntent，
     * 而不是一个新的token和PendingIntent。然而你在从提取PendingIntent时，通过FLAG_CANCEL_CURRENT参数，
     * 让这个老PendingIntent的先cancel()掉，这样得到的pendingInten和其token的就是新的了。
     */
    private void cancelRequestAlarm() {
        mAlarmMgr.cancel(getOperationIntent());
    }

    /**
     * OSC采用轮询方式实现消息推送<br>
     * 每次被调用都去执行一次{@link #ALARM_SERVICE}onReceive()方法
     * 
     * @return
     */
    private PendingIntent getOperationIntent() {
        Intent intent = new Intent(this, AlarmReceiver.class);
        PendingIntent operation = PendingIntent.getBroadcast(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        return operation;
    }

    private void clearNotice(int uid, int type) {
        RService.clearNotice(uid, type, mClearNoticeHandler);
    }

    private int lastNotifiyCount;

    private void notification(int count, JSONObject notice) {
        if (count == 0) {
            lastNotifiyCount = 0;
            NotificationManagerCompat.from(this).cancel(
                    R.string.you_have_news_messages);
            return;
        }
        if (count == lastNotifiyCount)
            return;

        lastNotifiyCount = count;

        Resources res = getResources();
        String contentTitle = res.getString(R.string.you_have_news_messages,
                count);
        String contentText;
        StringBuffer sb = new StringBuffer();
        sb.append(getString(R.string.msg_count, count)).append(" ");
        contentText = sb.toString();

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("NOTICE", true);

        PendingIntent pi = PendingIntent.getActivity(this, 1000, intent,
                PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                this).setTicker(contentTitle).setContentTitle(contentTitle)
                .setContentText(contentText).setAutoCancel(true)
                .setContentIntent(pi).setSmallIcon(R.drawable.ic_notification);

        if (AppContext.get(AppConfig.KEY_NOTIFICATION_SOUND, true)) {
            builder.setSound(Uri.parse("android.resource://"
                    + AppContext.getInstance().getPackageName() + "/"
                    + R.raw.notificationsound));
        }
        if (AppContext.get(AppConfig.KEY_NOTIFICATION_VIBRATION, true)) {
            long[] vibrate = { 0, 10, 20, 30 };
            builder.setVibrate(vibrate);
        }

        Notification notification = builder.build();

        NotificationManagerCompat.from(this).notify(
                R.string.you_have_news_messages, notification);
    }

    private final AsyncHttpResponseHandler mGetNoticeHandler = new AsyncHttpResponseHandler() {

        @Override
        public void onSuccess(int arg0, cz.msebera.android.httpclient.Header[] arg1, byte[] arg2) {
            try {
                String jsonStr = new String(arg2);
                JSONArray array = new JSONArray(jsonStr);
                int length = array.length();
                for (int i = 0; i < length; i++) {
                    JSONObject item = array.getJSONObject(i);
                    UIHelper.sendBroadCast(NoticeService.this, item);
                    if (AppContext.get(AppConfig.KEY_NOTIFICATION_ACCEPT, true)) {
                        notification(length, item);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                onFailure(arg0, arg1, arg2, e);
            }
        };

        @Override
        public void onFailure(int arg0, cz.msebera.android.httpclient.Header[] arg1, byte[] arg2,
                Throwable arg3) {
            arg3.printStackTrace();
        }
    };

    private final AsyncHttpResponseHandler mClearNoticeHandler = new AsyncHttpResponseHandler() {

        @Override
        public void onSuccess(int arg0, cz.msebera.android.httpclient.Header[] arg1, byte[] arg2) {
            try {

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onFailure(int arg0, cz.msebera.android.httpclient.Header[] arg1, byte[] arg2,
                Throwable arg3) {}
    };

    /**
     * 请求是否有新通知
     */
    private void requestNotice() {
        RService.getNotifications(mGetNoticeHandler);
    }

    private static class ServiceStub extends INoticeService.Stub {
        WeakReference<NoticeService> mService;

        ServiceStub(NoticeService service) {
            mService = new WeakReference<NoticeService>(service);
        }

        @Override
        public void clearNotice(int uid, int type) throws RemoteException {
            mService.get().clearNotice(uid, type);
        }

        @Override
        public void scheduleNotice() throws RemoteException {
            mService.get().startRequestAlarm();
        }

        @Override
        public void requestNotice() throws RemoteException {
            mService.get().requestNotice();
        }
    }

    private final IBinder mBinder = new ServiceStub(this);
}
