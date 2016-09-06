package filescanner.pandit.jai.com.filescanner.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import filescanner.pandit.jai.com.filescanner.ScanResult;
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
    private TextView mTvResult;

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
        if(!mIsConnected){
            connectService(true);
        }
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
                    onScanResultsFetched((ScanResult) msg.obj);
                    break;
            }

        }
    };

    private void onScanResultsFetched(ScanResult results){
        String deflatedResult = populateResults(results);
        if(deflatedResult == null){
            mTvResult.setText("");
        }else{
            mTvResult .setText(deflatedResult);
            setShareIntent(deflatedResult);
        }
        if(mShare != null){
            mShare.setEnabled(deflatedResult == null ? false: true);
        }
    }

    private String populateResults(ScanResult result){
        if(result == null){
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Avg File Size(bytes): " +String.valueOf(result.mAvgFileSize) + "\n");

        sb.append("\nBiggest files: \n");
        for(ScanResult.FileObj file: result.mBiggestFiles){
            sb.append("Path: " + file.PATH + "\n");
            sb.append("Size: " + file.SIZE + "\n\n");
        }
        sb.append("\nFreq file ext: \n");
        for(ScanResult.FileObj file: result.mMostFreqFiles){
            sb.append("Ext: " + file.PATH + "\n");
            sb.append("Freq: " + file.SIZE + "\n\n");
        }
        return sb.toString();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_main, container, false);
        mButton = (Button)root.findViewById(R.id.btn_scan);
        mProgress = (ProgressBar)root.findViewById(R.id.progress_scan);
        mTvResult = (TextView)root.findViewById(R.id.tv_result);
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
                        onScanResultsFetched(null);
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
        setHasOptionsMenu(true);

        return root;
    }

    private ShareActionProvider mShareActionProvider;
    private MenuItem mShare;

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_main, menu);

        // Locate MenuItem with ShareActionProvider
        mShare = menu.findItem(R.id.menu_item_share);
        updateState();

        // Fetch and store ShareActionProvider
        mShareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(mShare);

        super.onCreateOptionsMenu(menu,inflater);
    }

    private void setShareIntent(String text){
        if(text == null){
            return;
        }
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        shareIntent.setType("text/plain");
        if(mShareActionProvider != null){
            mShareActionProvider.setShareIntent(shareIntent);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateState();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
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
                    // try to load results.
                    onScanResultsFetched(mScanner.getLastScanResults());
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
