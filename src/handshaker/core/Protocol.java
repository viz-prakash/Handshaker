package handshaker.core;

import java.util.ArrayList;

import handshaker.core.Cipher;

public class Protocol {
	String name;
	ArrayList<Cipher> cipherResultList;
	

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public ArrayList<Cipher> getCipherResultList() {
		return cipherResultList;
	}

	public void setCipherResultList(ArrayList<Cipher> cipher) {
		this.cipherResultList = cipher;
	}

	
}