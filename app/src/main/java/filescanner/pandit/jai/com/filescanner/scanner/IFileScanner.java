package filescanner.pandit.jai.com.filescanner.scanner;

import filescanner.pandit.jai.com.filescanner.ScanResult;

/**
 * Created by Jai on 9/2/2016.
 */
public interface IFileScanner {

    public boolean isScanning();
    public int getCurrentProgress();
    public int getMaxProgress();
    public ScanResult getLastScanResults();
}
