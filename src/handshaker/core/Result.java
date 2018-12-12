package handshaker.core;

import java.util.ArrayList;

public class Result {
    ArrayList<ResultPerIp> scanResult;

    public ArrayList<ResultPerIp> getScanResult() {
        return scanResult;
    }

    public void setScanResult(ArrayList<ResultPerIp> scanResult) {
        this.scanResult = scanResult;
    }

}
