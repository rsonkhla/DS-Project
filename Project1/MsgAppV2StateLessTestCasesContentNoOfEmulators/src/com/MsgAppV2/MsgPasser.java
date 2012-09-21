package com.MsgAppV2;

import java.util.LinkedList;
import java.util.Queue;

public class MsgPasser {
	private static MsgPasser mInstance				= null;
	public Queue<String> mActivityToMsgHandler 		= null;
	public Queue<String> mMsgHandlerToActivity 		= null;
	public Queue<String> mMsgHandlerToCommModule	= null;
	public Queue<String> mCommModuleToMsgHandler 	= null;
	
	public static MsgPasser GetInstance() {
		synchronized (MsgPasser.class) {
			if(mInstance == null) {
				mInstance	= new MsgPasser();
			}
		}
		return mInstance;
	}
	
	private MsgPasser() {
		mActivityToMsgHandler 	= new LinkedList<String>();
		mMsgHandlerToActivity 	= new LinkedList<String>();
		mMsgHandlerToCommModule = new LinkedList<String>();
		mCommModuleToMsgHandler = new LinkedList<String>();
	}
}
