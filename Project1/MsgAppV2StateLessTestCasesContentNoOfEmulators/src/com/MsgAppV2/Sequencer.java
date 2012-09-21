package com.MsgAppV2;

import android.util.Log;

public class Sequencer {
	private static Sequencer mInstance	= null;
	private Message mSeqMessageObj		= new Message();
	private int mSeqOrderNo				= 0;
	
	public PeerAddress mSequencer		= new PeerAddress();
	public boolean mIsSequencer			= false;
	
	public static Sequencer GetInstance() {
		synchronized (Sequencer.class) {
			if(mInstance == null) {
				mInstance	= new Sequencer();
			}
		}
		return mInstance;
	}
	
	public void ProcessMessage(Message msg) {	
		/*
		 * Resposible for FIFO
		 * writes message to msgHdlertocomm buffer for multi casting
		 */
		if(mIsSequencer) {
			mSeqMessageObj.SetObject("OrderMessage", mSeqOrderNo++, msg.mMsgID, "", "", -1, 0, 0);
			synchronized (MsgPasser.GetInstance().mMsgHandlerToCommModule) {
				MsgPasser.GetInstance().mMsgHandlerToCommModule.add(mSeqMessageObj.EncodeString());
				MsgPasser.GetInstance().mMsgHandlerToCommModule.notify();
				Log.i(Utility.mDebugInfo, "Sequencer: Sending order message from Sequencer to CommModule. " + mSeqMessageObj.EncodeString());
			}
		}
	}
	
	private Sequencer() {
	}
	
	public void StartSequencerElection(String appIP, int appPortNo) {
		/*
		 * Chang and Roberts algorithm - ring algorithm
		 * 
		 * Sends a value to next alive process in circle
		 * Marks self as participating
		 */
		
		synchronized(mSequencer) {
			mSequencer.mIPAddress	= "10.0.2.2";
			mSequencer.mPortNo		= 11108;
			if(appPortNo == mSequencer.mPortNo && appIP.equals(mSequencer.mIPAddress)) {
				mIsSequencer	= true;
			}
			mSequencer.notify();
		}
	}
	
	public void ProcessSequencerElectionMessage() {
		/*
		 * Chang and Roberts algorithm - ring algorithm
		 * 
		 * if(received value greater than self)
		 * 		pass message
		 * else if(received value is less than self)
		 * 		if not participating 
		 * 			fwd its out value
		 * 		else
		 * 			ignore
		 * else
		 * 		elect self as sequencer
		 */
		
	}
}
