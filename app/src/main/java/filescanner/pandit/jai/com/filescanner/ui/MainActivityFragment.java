package filescanner.pandit.jai.com.filescanner.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import filescanner.pandit.jai.com.filescanner.scanner.IFileScanner;
import filescanner.pandit.jai.com.filescanner.R;
import filescanner.pandit.jai.com.filescanner.scanner.ScannerService;
import filescanner.pandit.jai.com.filescanner.utils.IntentConstants.INTENT_ACTIONS;
import filescanner.pandit.jai.com.filescanner.utils.MSG_CODES;

/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends Fragment{

    private static final String TAG = MainActivityFragment.class.getSimpleName();

    private Button mButton;
    private ProgressBar mProgress;
    private TextView mTvBiggestSize;
    private TextView mTvAvgSize;
    private TextView mTvFreqFiles;

    // Remote objects.
    private Messenger mScannerServiceMessenger;
    private IFileScanner mScanner;

    private Messenger mLocalMessenger;

    private static enum STATE{
        SCANNING,
        IDLE;
    }

    private STATE mState = STATE.IDLE;

    private boolean mIsConnected;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        mLocalMessenger = new Messenger(mCallback);
    }

    private Handler mCallback = new Handler(Looper.getMainLooper()){

        @Override
        public void handleMessage(Message msg) {
            switch(MSG_CODES.values()[msg.what]){
                case SCAN_STARTED:
                    Log.i(TAG, "Callback: Scan started.");
                    mState = STATE.SCANNING;
                    mButton.setEnabled(true);
                    mButton.setText("Stop");
                    break;
                case SCAN_STOPPED:
                    Log.i(TAG, "Callback: Scan stopped.");
                    mState = STATE.IDLE;
                    mButton.setEnabled(true);
                    mButton.setText("Start");

                    mProgress.setEnabled(false);
                    mProgress.setProgress(0);
                    mProgress.setMax(0);
                    break;
                case SCAN_PROGRESS:
                    Log.i(TAG, "Callback: Scan progress: " + Integer.toString(msg.arg1));
                    mProgress.setProgress(msg.arg1);
                    break;
                case SCAN_MAX_PROGRESS:
                    Log.i(TAG, "Callback: Max progress: " + Integer.toString(msg.arg1));
                    mProgress.setEnabled(true);
                    mProgress.setMax(msg.arg1);
                    break;
                case SCAN_RESULTS:
                    Log.i(TAG, "Callback: Scan results!!");
                    break;
            }

        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_main, container, false);
        mButton = (Button)root.findViewById(R.id.btn_scan);
        mProgress = (ProgressBar)root.findViewById(R.id.progress_scan);
        mTvAvgSize = (TextView)root.findViewById(R.id.tv_avgSize);
        mTvBiggestSize = (TextView)root.findViewById(R.id.tv_biggestFiles);
        mTvFreqFiles = (TextView)root.findViewById(R.id.tv_freqFiles);
        mProgress.setMax(20);
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!checkBinding()){
                    // Service not connected, retry is made for the connetion.
                    // but for now lets not go further, as callbacks are important.
                    Toast.makeText(getActivity(), "Action aborted service not connected.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent intent = new Intent(getActivity(), ScannerService.class);
                switch (mState) {
                    case IDLE:
                        intent.setAction(INTENT_ACTIONS.START_SCANNING);
                        break;
                    case SCANNING:
                        intent.setAction(INTENT_ACTIONS.STOP_SCANNING);
                        break;
                }
                // wait to hear back from service.
                mButton.setEnabled(false);
                getActivity().startService(intent);
            }
        });

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();

    }

    @Override
    public void onStart() {
        super.onStart();
        if(!mIsConnected){
            connectService(true);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if(mIsConnected){
            connectService(false);
            mIsConnected = false;
        }
    }

    private boolean checkBinding(){
        if(!mIsConnected && mScanner == null){
            connectService(true);
            return false;
        }
        return true;
    }

    /**
     * When client connects and a scan is already in progress.
     */
    private void updateState(){
        if(mIsConnected && mScanner != null){
            mState = mScanner.isScanning()? STATE.SCANNING: STATE.IDLE;
            switch (mState){
                case SCANNING:
                    mButton.setEnabled(true);
                    mButton.setText("Stop");
                    mProgress.setEnabled(true);
                    mProgress.setMax(mScanner.getMaxProgress());
                    mProgress.setProgress(mScanner.getCurrentProgress());
                    break;
                case IDLE:
                    mButton.setEnabled(true);
                    mButton.setText("Start");
                    mProgress.setEnabled(false);
                    mProgress.setProgress(0);
                    break;
            }
        }
    }

    private void connectService(boolean connect){
        if(connect){
            Intent intent = new Intent(getActivity().getApplicationContext(), ScannerService.class);
            getActivity().getApplicationContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }else{
            Message msg = Message.obtain();
            msg.what = MSG_CODES.UNREGISTER_CLIENT.ordinal();
            msg.replyTo = mLocalMessenger;
            try{
                mScannerServiceMessenger.send(msg);
            }catch (RemoteException ex){
                Log.e(TAG, "Failed to unregister client");
            }

            getActivity().getApplicationContext().unbindService(mConnection);
        }
    }

    private ServiceConnection mConnection = new  ServiceConnection(){

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "Service connected");
            mIsConnected = true;
            ScannerService.LocalBinder binder = (ScannerService.LocalBinder)service;
            mScanner = binder.getService();
            mScannerServiceMessenger = new Messenger(binder.getBinder());

            // register client to the service.
            Message msg = Message.obtain();
            msg.what = MSG_CODES.REGISTER_CLIENT.ordinal();
            msg.replyTo = mLocalMessenger;
            try{
                mScannerServiceMessenger.send(msg);
            }catch(RemoteException ex){
                Log.e(TAG, "Failed to register client.");

                // we will try again at some point later.
                MainActivityFragment.this.connectService(false);
                return;
            }

            // Update UI ASAP.
            updateState();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "Service disconnected");
            mIsConnected = false;
            mScanner = null;
        }
    };

}
