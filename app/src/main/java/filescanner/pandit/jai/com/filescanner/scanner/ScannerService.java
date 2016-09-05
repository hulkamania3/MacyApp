package filescanner.pandit.jai.com.filescanner.scanner;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import filescanner.pandit.jai.com.filescanner.R;
import filescanner.pandit.jai.com.filescanner.ScanResult;
import filescanner.pandit.jai.com.filescanner.ui.MainActivity;
import filescanner.pandit.jai.com.filescanner.utils.IntentConstants.INTENT_ACTIONS;
import filescanner.pandit.jai.com.filescanner.utils.IntentConstants.NOTIFICATIONS;
import filescanner.pandit.jai.com.filescanner.utils.MSG_CODES;

/**
 * Created by Jai on 9/2/2016.
 *
 * This is a started service by lifecycle.
 * However, it also accepts binding of the UI Clients and publish updates for the
 * clients.
 */
public final class ScannerService extends Service implements IFileScanner {

    private static final String TAG = ScannerService.class.getSimpleName();

    private IncomingHandler mHandler;
    private HandlerThread mThread;
    private Messenger mMessenger;
    private final IBinder mBinder = new LocalBinder();
    private List<Messenger> mClients = new CopyOnWriteArrayList<Messenger>();

    private ExecutorService mExecutor;
    private Future mCurrentScanTask;

    private boolean mIsScanning;
    private int mCurrentProgress;
    private int mMaxProgress;
    private ScanResult mLastScanResult;

    @Override
    public void onCreate() {
        Log.i(TAG, "OnCreate.");
        super.onCreate();
        //1. Setup for binding. All client registration/deregistration and
        // update will happen on the mThread handler thread.
        mThread = new HandlerThread("Scanner");
        mThread.start();
        mHandler = new IncomingHandler(mThread.getLooper());
        mMessenger = new Messenger(mHandler);

        //2. Setup for started service.
        // Atmost two thread, To support quick cancellation and restart, just in case.
        mExecutor = Executors.newFixedThreadPool(2);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "OnDestroy.");
        super.onDestroy();
        // 1. Close bindings.
        if(mHandler != null){
            mHandler.sendMessageAtFrontOfQueue(Message.obtain(mHandler, MSG_CODES.QUIT_HANDLER.ordinal()));
        }

        // 2. Stop worker threads.
        stopScanning(false);
        // Service is getting killed so kill aggressively. No
        // need to be nice with this guy anymore :)
        mExecutor.shutdownNow();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "OnBind.");
        return mBinder;
    }

    /**
     * Exposes, IFileScanner as well as binder.
     */
    public final class LocalBinder extends Binder {
        public IFileScanner getService() {
            // Return this instance of ScannerService so clients can call public methods
            return ScannerService.this;
        }

        public IBinder getBinder(){
            return mMessenger.getBinder();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "OnStartCommand.");
        String action = intent.getAction();
        if(action.equalsIgnoreCase(INTENT_ACTIONS.START_SCANNING)){
            startScanning(startId);
        }else if(action.equalsIgnoreCase(INTENT_ACTIONS.STOP_SCANNING)){
            stopScanning(true);
            stopSelf();
        }
        return START_STICKY;
    }

    /**
     * Starts scanning.
     */
    synchronized private void startScanning(int startId){
        stopScanning(false);
        setScanning(true);
        mCurrentScanTask = mExecutor.submit(new ScanWorker(this, startId));
        startForegroud();
        sendCallback(MSG_CODES.SCAN_STARTED);
    }

    synchronized void onScanComplete(ScanWorker worker, boolean isInterrupted){
        // release worker. as it completed its job.
        mCurrentScanTask = null;
        stopScanning(!isInterrupted);
        stopSelf(worker.getStartId());
    }

    /**
     * Stops Scanning.
     * @param callbackClients: True, If client should be reported.
     */
    synchronized void stopScanning(boolean callbackClients){
        setScanning(false);
        setMaxProgress(0);
        setCurrentProgress(0);
        stopPreviousTask();
        stopForeground(true);
        if(callbackClients){
            sendCallback(MSG_CODES.SCAN_STOPPED);
        }
    }

    private void stopPreviousTask(){
        if (mCurrentScanTask != null) {
            if (!mCurrentScanTask.isDone()) {
                Log.i(TAG, "Cancelling task.");
                mCurrentScanTask.cancel(true);
            }
            mCurrentScanTask = null;
        }
    }

    /**
     * Handler for reg/dereg and update UI clients.
     */
    private class IncomingHandler extends Handler {

        public IncomingHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (MSG_CODES.values()[msg.what]) {
                case REGISTER_CLIENT:
                    Log.i(TAG, "Client registered.");
                    mClients.add(msg.replyTo);
                    break;
                case UNREGISTER_CLIENT:
                    Log.i(TAG, "Client un-registered.");
                    mClients.remove(msg.replyTo);
                    break;
                case QUIT_HANDLER:
                    mHandler.removeCallbacksAndMessages(null);
                    mHandler = null;
                    mThread.quit();
                    break;

            }
        }
    }

    // Thread Access: UI and Scan Worker.
    synchronized void setScanResult(ScanResult result){
        mLastScanResult = result;
        if(isScanning()){
            sendCallback(MSG_CODES.SCAN_RESULTS, mLastScanResult);
        }
    }

    synchronized void setMaxProgress(int progress){
        mMaxProgress = progress;
        if(isScanning()){
            sendCallback(MSG_CODES.SCAN_MAX_PROGRESS, mMaxProgress);
        }
    }

    synchronized void setScanning(boolean scanning){
        mIsScanning = scanning;
    }

    synchronized void setCurrentProgress(int progress){
        mCurrentProgress = progress;
        if(isScanning()){
            sendCallback(MSG_CODES.SCAN_PROGRESS, mCurrentProgress);
        }
    }

    @Override
    synchronized public boolean isScanning() {
        return mIsScanning;
    }

    @Override
    synchronized public int getCurrentProgress() {
        return mCurrentProgress;
    }

    @Override
    synchronized public int getMaxProgress() {
        return mMaxProgress;
    }

    /**
     * Starts the service in foreground mode.
     */
    private void startForegroud(){
        // Start in foreground.
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        Bitmap icon = BitmapFactory.decodeResource(getResources(),
                R.mipmap.ic_launcher);

        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle("Scanner Service")
                .setTicker("Scanning..")
                .setContentText("Scanning is on progress")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(
                        Bitmap.createScaledBitmap(icon, 128, 128, false))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
        startForeground(NOTIFICATIONS.NOTIFICATION_ID,
                notification);
    }

    //------------------ Helper Methods. --------------
    public void sendCallback(MSG_CODES command){
        Message msg = Message.obtain();
        msg.what = command.ordinal();
        sendClients(msg);
    }

    public void sendCallback(MSG_CODES command, ScanResult result){
        Message msg = Message.obtain();
        msg.what = command.ordinal();
        msg.obj = result;
        sendClients(msg);
    }

    public void sendCallback(MSG_CODES command, int arg1){
        Message msg = Message.obtain();
        msg.what = command.ordinal();
        msg.arg1 = arg1;
        sendClients(msg);
    }

    public void sendCallback(MSG_CODES command, int arg1, ScanResult result){
        Message msg = Message.obtain();
        msg.what = command.ordinal();
        if(command == MSG_CODES.SCAN_STARTED){
            msg.arg1 = mMaxProgress;
        }
        if(result != null){
            msg.obj = result;
        }
        sendClients(msg);
    }

    private void sendClients(Message msg){
        try{
            for(Messenger client: mClients){
                client.send(msg);
            }
        }catch(RemoteException ex){
            Log.e(ScannerService.TAG, "Failed to send callback to client.");
        }
    }
}// End of class.
