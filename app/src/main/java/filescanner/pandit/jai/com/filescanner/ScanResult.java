package filescanner.pandit.jai.com.filescanner;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;
import java.util.List;

/**
 * Created by Jai on 9/2/2016.
 */
public final class ScanResult{

    public final List<FileObj> mBiggestFiles;
    public final long mAvgFileSize;
    public final List<FileObj> mMostFreqFiles;

    public ScanResult(List<FileObj> mBiggestFiles, List<FileObj> mMostFreqFiles) {
        this.mBiggestFiles = mBiggestFiles;
        this.mAvgFileSize = getAvgFileSize();
        this.mMostFreqFiles = mMostFreqFiles;
    }

    private long getAvgFileSize(){
        long total = 0;
        for(int i=0;i<mBiggestFiles.size();i++){
            total += mBiggestFiles.get(i).SIZE;
        }
        total = total/mBiggestFiles.size();
        return total;
    }

    /**
     * Atomic class.
     */
    public static final class FileObj implements Parcelable{
        public final long SIZE;
        public final String PATH;

        public FileObj(long size, String path) {
            this.SIZE = size;
            this.PATH = path;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(SIZE);
            dest.writeString(PATH);
        }

        private FileObj(Parcel in) {
            SIZE = in.readLong();
            PATH = in.readString();
        }

        public static final Parcelable.Creator<FileObj> CREATOR
                = new Parcelable.Creator<FileObj>() {
            public FileObj createFromParcel(Parcel in) {
                return new FileObj(in);
            }

            public FileObj[] newArray(int size) {
                return new FileObj[size];
            }
        };
    }
}
