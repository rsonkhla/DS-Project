package com.MsgAppV2;


public class PeerAddress {
	public String mIPAddress;
	public int mPortNo;
	
	public PeerAddress() {
		mIPAddress		= null;
		mPortNo			= -1;
	}
	
	public PeerAddress(PeerAddress peer) {
		mIPAddress		= peer.mIPAddress;
		mPortNo			= peer.mPortNo;
	}
	
	public PeerAddress(String IPAddress, int PortNo) {
		mIPAddress		= IPAddress;
		mPortNo			= PortNo;
	}
}
