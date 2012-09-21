package edu.buffalo.cse.cse486_586.simpledynamo;

import java.io.Serializable;

/*
 * Type of messages,
 * 
 * AreYouAlive?
 * YesIAmAlive.
 * GiveMeThisNodesData.
 * Ok.HereIsThisNodesData.
 * InsertKeyValuePairAsCoordinator.
 * InsertKeyValuePair.
 * InsertSuccessful.
 * InsertFailed.
 * QueryKeyValuePairAsCoordinator.
 * QueryKeyValuePair.
 * QuerySuccessful.
 * QueryFailed.
 * 
 */

@SuppressWarnings("serial")
public class Message implements Serializable{
	public String mMsgType	= null;
	public String mMsgID	= null;
	public String mMsgData	= null;
	public String mFromIP	= null;
	public int mFromPortNo 	= -1;
	public String mToIP		= null;
	public int mToPortNo	= -1;
	
	public Message() {
		Reset();
	}
	
	public Message(Message msg) {
		mMsgType	= msg.mMsgType;
		mMsgID		= msg.mMsgID;
		mMsgData	= msg.mMsgData;
		mFromIP		= msg.mFromIP;
		mFromPortNo	= msg.mFromPortNo;
		mToIP		= msg.mToIP;
		mToPortNo	= msg.mToPortNo;
	}
	
	void Reset() {
		mMsgType	= null;
		mMsgID		= null;
		mMsgData	= null;
		mFromIP		= null;
		mFromPortNo	= -1;
		mToIP		= null;
		mToPortNo	= -1;
	}

	void SetObject(String msgType, String msgID, String msgData, String fromIP, int fromPortNo, String toIP, int toPortNo) {
		mMsgType	= msgType;
		mMsgID		= msgID;
		mMsgData	= msgData;
		mFromIP		= fromIP;
		mFromPortNo	= fromPortNo;
		mToIP		= toIP;
		mToPortNo	= toPortNo;
	}
	
	String EncodeString() {
		return  mMsgType + "><" + mMsgID + "><" + mMsgData + "><" + mFromPortNo + "><" + mToPortNo;
	}
}
