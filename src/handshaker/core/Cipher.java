package handshaker.core;

public class Cipher {
	String name;
	Object handshakeData;
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public Object getHandshakeData() {
		return handshakeData;
	}
	public void setHandshakeData(Object handshakeData) {
		this.handshakeData = handshakeData;
	}
	
}