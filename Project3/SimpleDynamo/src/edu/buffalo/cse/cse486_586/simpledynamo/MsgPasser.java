package edu.buffalo.cse.cse486_586.simpledynamo;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

public class MsgPasser {
	private static MsgPasser mInstance				= null;
	public Queue<Message> mSendMsgBuf				= null;
	//public Queue<Message> mRecvMsgBuf 			= null;
	public HashMap<String, Message> mInsrtQurryBuf	= null;
	public HashMap<String, Message> mRecvMsgBuf		= null;
	
	public static MsgPasser GetInstance() {
		synchronized (MsgPasser.class) {
			if(mInstance == null) {
				mInstance	= new MsgPasser();
			}
		}
		return mInstance;
	}
	
	private MsgPasser() {
		mSendMsgBuf 	= new LinkedList<Message>();
		//mRecvMsgBuf 	= new LinkedList<Message>();
		mInsrtQurryBuf	= new HashMap<String, Message>();
		mRecvMsgBuf	= new HashMap<String, Message>();
	}
}
