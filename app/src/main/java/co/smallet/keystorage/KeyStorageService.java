package co.smallet.keystorage;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;
import android.os.Process;

import co.smallet.smalletlib.GlobalConstants;


public class KeyStorageService extends Service {
    static final int NOTIFICATION_ID = 543;
    public static boolean isServiceRunning = false;

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    private MainActivity main;
    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;
    static String publicAddress;

    public static String getPublicAddress() {
        return publicAddress;
    }

    public void setPublicAddress(String address) {
        publicAddress = address;
    }

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            // Normally we would do some work here, like download a file.
            // For our sample, we just sleep for 5 seconds.
            try {
                Log.e("keystoreageservce", "what=" + msg.what);
                switch(msg.what) {
                    case GlobalConstants.SERVICE_GET_ADDRESS:
                        /*
                        Intent walletIntent = MainActivity.buildWalletIntent("PUBLIC_ADDRESS");
                        String address = KeyStorageService.getPublicAddress();
                        Log.e("keystorage", "addr=" + address);
                        walletIntent.putExtra("ADDRESS", address);
                        sendBroadcast(walletIntent);
                        */

                        Intent i = new Intent();
                        i.setComponent(new ComponentName("co.smallet.wallet", "co.smallet.wallet.WalletService"));
                        i.putExtra("action", GlobalConstants.SERVICE_GET_ADDRESS);
                        i.putExtra("PUBLIC_ADDRESS", getPublicAddress());
                        ComponentName c = startService(i);

                        break;
                    case GlobalConstants.SERVICE_SIGN_TX:
                        i = new Intent(KeyStorageService.this, MainActivity.class);
                        startActivity(i);
                        if (main == null)
                            break;
                        Message msgToSend = new Message();
                        msgToSend.what = Constants.SIGN_TX;
                        msgToSend.setData(msg.getData());
                        MainActivity.mHandle.sendMessage(msgToSend);
                }

                //Thread.sleep(5000);
            } catch (Exception e) {
                // Restore interrupt status.
                Thread.currentThread().interrupt();
            }
            // Stop the service using the startId, so that we don't stop
            // the service in the middle of handling another job
            stopSelf(msg.arg1);
        }
    }

    @Override
    public void onCreate() {
        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        HandlerThread thread = new HandlerThread("ServiceStartArguments",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);

        startServiceWithNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "KeyStorage service starting", Toast.LENGTH_SHORT).show();

        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.what = intent.getIntExtra("action", 0);
        msg.setData(intent.getExtras());
        mServiceHandler.sendMessage(msg);

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return mBinder;
    }

    @Override
    public void onDestroy() {
        isServiceRunning = false;
        Toast.makeText(this, "KeyStorage  service done", Toast.LENGTH_SHORT).show();
    }

    //returns the instance of the service
    public class LocalBinder extends Binder {
        public KeyStorageService getService(){
            return KeyStorageService.this;
        }
    }

    public void setMainActivity(MainActivity act) {
        main= act;
    }

    void startServiceWithNotification() {
        if (isServiceRunning) return;
        isServiceRunning = true;

        Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);
        notificationIntent.setAction(Constants.ACTION_MAIN);  // A string containing the action name
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_stat_key_storage);

        Notification notification = new Notification.Builder(this)
                .setContentTitle(getResources().getString(R.string.app_name))
                .setTicker(getResources().getString(R.string.app_name))
                .setContentText(getResources().getString(R.string.app_name))
                .setSmallIcon(R.drawable.ic_stat_key_storage)
                .setLargeIcon(icon)
                .setContentIntent(contentPendingIntent)
                .setOngoing(true)
//                .setDeleteIntent(contentPendingIntent)  // if needed
                .build();
        notification.flags = notification.flags | Notification.FLAG_NO_CLEAR;     // NO_CLEAR makes the notification stay when the user performs a "delete all" command
        startForeground(NOTIFICATION_ID, notification);
    }

    void stopMyService() {
        stopForeground(true);
        stopSelf();
        isServiceRunning = false;
    }
}