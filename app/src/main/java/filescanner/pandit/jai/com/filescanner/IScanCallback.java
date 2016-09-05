package filescanner.pandit.jai.com.filescanner;

/**
 * Created by Jai on 9/2/2016.
 */
public interface IScanCallback {

    public static enum SCAN_RESULT{
        SUCCESS,
        FAILED;

        private FAILURE mReason;

        public void setFailureReason(FAILURE reason ){

        }

        public FAILURE getFailureReason(){
            return mReason;
        }
    }

    public static enum FAILURE{
        NO_SDCARD,
        SCAN_FORCE_STOPPED,
        UNKNOWN;
    }

    public void onScanComplete(SCAN_RESULT result, ScanResult data);
    public void onScanProgress(int progress);
}
