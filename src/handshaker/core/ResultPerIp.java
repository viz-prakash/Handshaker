package handshaker.core;

import java.util.ArrayList;

public class ResultPerIp {
    String ip;
    ArrayList<Protocol> protocol;

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public ArrayList<Protocol> getProtocol() {
        return protocol;
    }

    public void setProtocol(ArrayList<Protocol> protocol) {
        this.protocol = protocol;
    }

}
