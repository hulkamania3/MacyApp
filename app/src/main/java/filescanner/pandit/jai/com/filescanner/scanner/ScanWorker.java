package filescanner.pandit.jai.com.filescanner.scanner;

import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;

import filescanner.pandit.jai.com.filescanner.ScanResult;

/**
 * Created by Jai on 9/4/2016.
 * This represents once scan task.
 */
public class ScanWorker implements Runnable {

    private static final String TAG = ScanWorker.class.getSimpleName();

    /**
     * Hold service weakly. If no service found, certainly this
     * working is going to die. The driving executor is already dying.
     */
    WeakReference<ScannerService> mService;

    /**
     * Utility data structures.
     */
    PriorityQueue<ScanResult.FileObj> minHeap = new PriorityQueue<>(10, new Comparator<ScanResult.FileObj>() {
        @Override
        public int compare(ScanResult.FileObj lhs, ScanResult.FileObj rhs) {
            return (int) (lhs.SIZE - rhs.SIZE);
        }
    });

    PriorityQueue<ScanResult.FileObj> fileExt = new PriorityQueue<>(10, new Comparator<ScanResult.FileObj>() {
        @Override
        public int compare(ScanResult.FileObj lhs, ScanResult.FileObj rhs) {
            return (int) (lhs.SIZE - rhs.SIZE);
        }
    });

    HashMap<String, Integer> countMap = new HashMap<>();

    /**
     * Checks if the current thread is interrupted.
     * @return
     */
    private boolean shouldContinue(){

        return !Thread.currentThread().isInterrupted();
    }

    public ScanWorker(ScannerService mService, int startId) {

        this.mService = new WeakReference<ScannerService>(mService);
        mStartId = startId;
    }

    private final int mStartId;

    public int getStartId(){
        return mStartId;
    }

    /**
     * Calculate total memory available in MB
     * @return size in MB
     */
    private long size() {
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        long bytesAvailable = (long) stat.getBlockSize() * (long) stat.getAvailableBlocks();
        long megAvailable = bytesAvailable / (1024 * 1024);
        Log.e("", "Available MB : " + megAvailable);
        return megAvailable;
    }

    private ScannerService getService(){
        ScannerService service = mService.get();
        if(service == null){
            Log.e(TAG, "Service is released");
        }
        return service;
    }

    private void runTest(){
        int max = 20;
        setMaxProgress(max);
        int i = 0;
        while(i<max){
            try{
                if(!shouldContinue())break;
                Thread.currentThread().sleep(1000);
                setCurrentProgress(i);
            }catch(InterruptedException ex){
                Log.e(TAG, "Task interrupted.");
                Thread.currentThread().interrupt();
                break;
            }
            i++;
        }
        onScanCompleted(Thread.currentThread().interrupted());
    }

    @Override
    public void run() {
        runTest();

        /*totalSize = size();
        setMaxProgress((int) totalSize);
        Log.d(TAG, "Thread started");
        ScanResult results = null;
        try {
            results = startScanning();
        } catch (Exception e) {
            e.printStackTrace();
        }
        setScanResult(results);
        onScanCompleted();*/
    }

    public ScanResult startScanning() throws InterruptedException{
        if (Environment.isExternalStorageEmulated()) {
            File extStore = Environment.getExternalStorageDirectory();
            String mPath = extStore.getAbsolutePath() + "/";
            final File f = new File(mPath);
            String[] ls = null;
            if (f.isDirectory()) {
                ls = f.list();
                if (ls != null) {
                    for (String str : ls) {
                        scanDirectory(mPath + "/" + str);
                    }
                }
            }

            if(!shouldContinue()){
                throw new InterruptedException();
            }

            ScanResult.FileObj[] biggestFiles = new ScanResult.FileObj[10];
            for(int i=0;i<10;i++){
                ScanResult.FileObj fff = minHeap.poll();
                Log.d(TAG, " Final file size " + fff.PATH + " " + fff.SIZE);
                biggestFiles[i] = fff;
            }

            //Most Frequent file Extensions
            for (String key : countMap.keySet()) {
                int frequency = countMap.get(key);
                if (fileExt.size() < 5) {
                    fileExt.add(new ScanResult.FileObj(frequency, key));
                } else {
                    ScanResult.FileObj ff = fileExt.peek();
                    if (ff.SIZE < frequency) {
                        fileExt.poll();
                        fileExt.add(new ScanResult.FileObj(frequency, key));
                    }
                }
            }

            ScanResult.FileObj[] freqUsed = new ScanResult.FileObj[10];
            for(int i=0;i<10;i++){
                ScanResult.FileObj fff = fileExt.poll();
                freqUsed[i] = fff;
                Log.d(TAG, " Extenstion to frequencey " + fff.PATH + " " + fff.SIZE);
            }

            return new ScanResult(biggestFiles, 5, freqUsed);
        }
        return null;
    }

    private File file;
    private long sizeSoFar;
    private long totalSize;

    /**
     * Scans a directory.
     * @param path
     */
    public void scanDirectory(String path) throws InterruptedException{
        file = new File(path);
        File list[] = file.listFiles();
        if(list == null || list.length == 0){
            return;
        }
        for (int i = 0; i < list.length; i++) {
            if (!shouldContinue()) {
                throw new InterruptedException();
            }
            if (list[i].isDirectory()) {
                Log.d(TAG, "Directory :" + list[i].getAbsolutePath());
                scanDirectory(path + "/" + list[i].getName());
            } else {
                String filename = list[i].getName();
                File f = new File(filename);
                long bytes2 = list[i].length();;
                long bytes = f.length();
                if (bytes != bytes2) {
                    Log.e(TAG, "ERRORRR");
                }
                long kb = bytes / 1024;
                long mb = kb / 1024;
                sizeSoFar += mb;

                // dispatch UI.
                int progress = (int)((sizeSoFar / totalSize) * 100);
                setCurrentProgress(progress);
                Log.d(TAG, "file:" + list[i].getAbsolutePath() + " " + mb);

                if (minHeap.size() < 10) {
                    minHeap.add(new ScanResult.FileObj(bytes, path + "/" + filename));
                } else {
                    ScanResult.FileObj FileObj = minHeap.peek();
                    if (FileObj.SIZE < bytes) {
                        minHeap.poll();
                        minHeap.add(new ScanResult.FileObj(bytes, path + "/" + filename));
                    }
                }

                String filenameArray[] = filename.split("\\.");
                String extension = filenameArray[filenameArray.length - 1];
                if (extension != null && !extension.isEmpty()) {
                    Integer c = countMap.get(extension);
                    if (c == null) {
                        countMap.put(extension, 1);
                    } else {
                        countMap.put(extension, c + 1);
                    }
                }
            }
        }
    }

    // ------------------- Helper Methods. ---------------------
    private void setCurrentProgress(int progress){
        ScannerService service = getService();
        if(service != null){
            service.setCurrentProgress(progress);
        }
    }

    private void setMaxProgress(int progress){
        ScannerService service = getService();
        if(service != null){
            service.setMaxProgress(progress);
        }
    }

    private void setScanResult(ScanResult result){
        ScannerService service = getService();
        if(service != null){
            service.setScanResult(result);
        }
    }

    private void onScanCompleted(boolean isInterrupted){
        ScannerService service = getService();
        if(service != null){
            service.onScanComplete(this, isInterrupted);
        }
    }
}
