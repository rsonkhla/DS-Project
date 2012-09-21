package com.MsgAppV2;

import android.util.Log;

/*
 * List of strings
 * 
 * Message format
 * <...><...><...><...>
 * 
 * MessageType=<TYPE>
 * 		- DataMessage
 * 		- OrderMessage
 * 		- ElectionMessage
 * 
 * DataMessage - 
 * OrderData=FIFOData=<ProcessNo>:<SequenceNo>
 * MsgID=<MessageID>
 * MsgData=<MessageData>
 * 
 * OrderMessage -
 * OrderData=TOTALData=<SequenceNo>
 * MsgID=<MessageID>
 * 
 * ElectionMessage - 
 * 
 */

public class Message implements Comparable<Message>{
	public String mMsgType;
	public int    mOrderData;
	public String mMsgID;
	public String mMsgData;
	public String mAppIP;
	public int mAppPortNo;
	
	public Message() {
		Reset();
	}
	
	public Message(Message msg) {
		mMsgType	= msg.mMsgType;
		mOrderData	= msg.mOrderData;
		mMsgID		= msg.mMsgID;
		mMsgData	= msg.mMsgData;
		mAppIP		= msg.mAppIP;
		mAppPortNo	= msg.mAppPortNo;
	}
	
	void Reset() {
		mMsgType	= null;
		mOrderData	= -1;
		mMsgID		= null;
		mMsgData	= null;
		mAppIP		= null;
		mAppPortNo	= -1;
	}

	boolean SetObject(String msgType, int orderData, String msgID, String msgData, String appIP, int appPortNo, int testNo, int flag) {
		mMsgType	= msgType;
		mOrderData	= orderData;
		mMsgID		= msgID;
		mMsgData	= msgData;
		mAppIP		= appIP;
		mAppPortNo	= appPortNo;
		
		if(IsMessageSane()) {
			return true;
		}
		else {
			Reset();
			Log.i("DebugLog", "Sanity check failed.");
			return false;
		}
	}
	
	String EncodeString() {
		return  mMsgType + "><" + mOrderData + "><" + mMsgID + "><" + mMsgData + "><" + mAppIP + "><" + mAppPortNo;
	}
	
	boolean DecodeString(String msg) {
		Reset();
		
		String[] tokens	= msg.split("><");
		mMsgType	= tokens[0];
		mOrderData	= Integer.parseInt(tokens[1]);
		mMsgID		= tokens[2];
		mMsgData	= tokens[3];
		mAppIP		= tokens[4];
		mAppPortNo	= Integer.parseInt(tokens[5]);
		
		if(IsMessageSane()) {
			return true;
		}
		else {
			Reset();
			Log.i(Utility.mMsgLog, "Sanity check failed.");
			return false;
		}
	}
		
	boolean IsMessageSane() {
		if(mMsgType.equals("DataMessage")) {
			return true;
		}else if(mMsgType.equals("OrderMessage")) {
			return true;
		}else if(mMsgType.equals("SequencerRequest")) {
			return true;
		}else if(mMsgType.equals("SequencerResponse")) {
			return true;
		}else if(mMsgType.equals("AppPortInformation")) {
			return true;
		} else {
			return false;
		}
	}
	
	String GetOriginalMessage() {
		return mMsgData;
	}

	@Override
	public int compareTo(Message msg) {
		return Integer.valueOf(mOrderData).compareTo(msg.mOrderData);
	}

	@Override
	public boolean equals(Object o) {
		Message msg	= (Message) o;
		return Integer.valueOf(mOrderData).equals(Integer.valueOf(msg.mOrderData));
	}

	@Override
	public int hashCode() {
		return mOrderData;
	}
	
	
}
