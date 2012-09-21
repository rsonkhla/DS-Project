package com.MsgAppV2;

import java.util.HashMap;
import java.util.PriorityQueue;

import android.util.Log;

/*
 * Read and process data passed by chat activity
 * 		- add order information
 *		- pass to communication module
 * Read and process data passed by communication module
 * 		- buffers message till their total order information is received
 * 		- processes data and ordering messages
 */

public class MsgHndlngModule {
	private String mAppIP	= null;
	private int mAppPortNo	= 0;
	
	public MsgHndlngModule(String appIP, int appPortNo) {
		mAppIP		= appIP;
		mAppPortNo	= appPortNo;
	}
	
	public void Start() {
		// execute Threads
        Thread sendThread=new Thread(new SendThread());
        sendThread.start();
        Thread deliveryThread=new Thread(new DeliveryThread());
        deliveryThread.start();
	}
	
	public class SendThread extends Thread {
		public SendThread() {
			super("SendThread");
		}
		
		Message	msgObj	= new Message();
		String msgData 	= new String();
		int lamportClock= 0;
		int tst1MsgSeqNm=0;
		int tst2MsgSeqNm=0;
		
		public void run() {
			try {
				while(true) {
					msgObj.Reset();
					msgData	= "";
					
					
					synchronized (MsgPasser.GetInstance().mActivityToMsgHandler) {
						if(MsgPasser.GetInstance().mActivityToMsgHandler.size() == 0)
							MsgPasser.GetInstance().mActivityToMsgHandler.wait();
						msgData	= MsgPasser.GetInstance().mActivityToMsgHandler.poll();
						Log.i(Utility.mDebugInfo, "MsgHndlngModule: Receiving message from Activity to MsgHandler. " + msgData);
					}
					
					if(msgData.contains("Test-Message-")) {
						String str;
						if(msgData.equals("Test-Message-1")) {
							for(int i=0 ; i<5 ; i++) {
								str = "Test-Message-1:0-x-x-" +mAppPortNo/2+":"+tst1MsgSeqNm;
								SendDataMessage(str);
								tst1MsgSeqNm++;
								Thread.sleep(3000);
							}
						}
						else if(msgData.equals("Test-Message-2")) {
							str = "Test-Message-2:1-x-x-"+mAppPortNo/2+":"+tst2MsgSeqNm;
							SendDataMessage(str);
							tst2MsgSeqNm++;
						}
						else {
							String tok1[] 	= msgData.split("-x-x-");
							str				= tok1[0] + "-x-x-" +mAppPortNo/2+":"+tst2MsgSeqNm + tok1[1];
							SendDataMessage(str);
							tst2MsgSeqNm++;
						}
					}
					else {
						SendDataMessage(msgData);
					}
				}
			}
			catch (Exception e) {
				Log.i(Utility.mException, "MsgHndlngModule::SendThread::run. " + e.toString());
			}
		}
		
		private void SendDataMessage(String data) {
			msgObj.SetObject("DataMessage", lamportClock, mAppPortNo+":"+lamportClock, data, mAppIP, mAppPortNo, 0, 0);
			synchronized (MsgPasser.GetInstance().mMsgHandlerToCommModule) {
				MsgPasser.GetInstance().mMsgHandlerToCommModule.add(msgObj.EncodeString());
				MsgPasser.GetInstance().mMsgHandlerToCommModule.notify();
				Log.i(Utility.mDebugInfo, "MsgHndlngModule: Sending message from MsgHandler to CommModule. " + msgObj.EncodeString());
			}
			lamportClock++;
		}
		
    }	
	
	public class DeliveryThread extends Thread {
		public DeliveryThread() {
			super("DeliveryThread");
		}
		
		Message	msgObj1							= new Message();
		Message	msgObj2							= new Message();
		String msgStr 							= new String();
		int lamportClock						= 0;
		
		HashMap<String, Message> holdBackBuffer	= new HashMap<String, Message>();
		PriorityQueue<Message> dlvrInSeqOrder	= new PriorityQueue<Message>();
		int lastDlvrdMsgSeqNo					= -1;
		
		public void run() {
			try {
				while(true) {
					msgObj1.Reset();
					msgObj2.Reset();
					msgStr	= "";
					
					synchronized (MsgPasser.GetInstance().mCommModuleToMsgHandler) {
						if(MsgPasser.GetInstance().mCommModuleToMsgHandler.size() == 0)
							MsgPasser.GetInstance().mCommModuleToMsgHandler.wait();
						msgStr = MsgPasser.GetInstance().mCommModuleToMsgHandler.poll();
						Log.i(Utility.mDebugInfo, "MsgHndlngModule: Receiving message from CommModule to MsgHandler. " + msgStr);
					}
					if(msgObj1.DecodeString(msgStr)) {
						ProcessDataMessage();
					} 
					else {
						Log.i(Utility.mDebugInfo, "MsgHndlngModule: Received message decoding failed. " + msgStr);
					}
					
					if(dlvrInSeqOrder.size() > 0)
						Log.i(Utility.mDebugInfo, "MsgHndlngModule: dlvrInSeqOrder.peek().mOrderData = " + dlvrInSeqOrder.peek().mOrderData + " and lastDlvrdMsgSeqNo+1 = " + (lastDlvrdMsgSeqNo+1));
					
					while(dlvrInSeqOrder.size() > 0 && dlvrInSeqOrder.peek().mOrderData <= lastDlvrdMsgSeqNo+1) {
						msgObj2	= dlvrInSeqOrder.poll();
						if(msgObj2.mOrderData == lastDlvrdMsgSeqNo+1) {
							synchronized (MsgPasser.GetInstance().mMsgHandlerToActivity) {
								MsgPasser.GetInstance().mMsgHandlerToActivity.add(msgObj2.EncodeString());
								MsgPasser.GetInstance().mMsgHandlerToActivity.notify();
								Log.i(Utility.mDebugInfo, "MsgHndlngModule: Delivering message from MsgHandler to Activity. " + msgObj2.GetOriginalMessage());
								lastDlvrdMsgSeqNo++;
							}
						}
						else {
							Log.i(Utility.mDebugInfo, "MsgHndlngModule: Duplicate message found in delivery queue. " + msgObj2.GetOriginalMessage());
						}
					}
				}
			}
			catch (Exception e) {
				Log.i(Utility.mException, "MsgHndlngModule::DeliveryThread:run. " + e.toString());
			}
		}
		
		private void ProcessDataMessage() {
			if(!holdBackBuffer.containsKey(msgObj1.mMsgID)) {
				Log.i(Utility.mDebugInfo, "MsgHndlngModule: Received message part-1. " + msgObj1.EncodeString());
				holdBackBuffer.put(msgObj1.mMsgID, new Message(msgObj1));
				if(msgObj1.mMsgType.equals("DataMessage")) {
					Sequencer.GetInstance().ProcessMessage(msgObj1);
				}
			}
			else {
				Log.i(Utility.mDebugInfo, "MsgHndlngModule: Received message part-2. " + msgObj1.EncodeString());
				msgObj2	= holdBackBuffer.get(msgObj1.mMsgID);	
				if( (msgObj1.mMsgType.equals("DataMessage") && msgObj2.mMsgType.equals("OrderMessage")) ||
					(msgObj2.mMsgType.equals("DataMessage") && msgObj1.mMsgType.equals("OrderMessage")) ) {
					if(msgObj1.mMsgData.equals("")) {
						msgObj2.mOrderData	= msgObj1.mOrderData;
						ProcessDataMessage2(msgObj2);
					}
					else {
						msgObj1.mOrderData	= msgObj2.mOrderData;
						ProcessDataMessage2(msgObj1);
					}
					
					holdBackBuffer.remove(msgObj1.mMsgID);
				}
			}
		}
		
		private void ProcessDataMessage2(Message msg) {
			String msgData	= msg.mMsgData;
			if(msgData.contains("Test-Message-")) {
				String str;
				if(msgData.contains("Test-Message-1")) {
					String tok1[] 	= msgData.split("-x-x-");
					msg.mMsgData	= tok1[1];
					dlvrInSeqOrder.add(msg);
				}
				else if(msgData.contains("Test-Message-2")) {
					String tok1[] 	= msgData.split("-x-x-");
					String tok2[] 	= tok1[0].split(":");
					String tok3[] 	= tok1[1].split(":");
					
					int flag		= Integer.parseInt(tok2[1]);
					int count		= tok3.length / 2;
					msg.mMsgData	= tok3[0] + ":" + tok3[1];
					dlvrInSeqOrder.add(msg);
					
					int curDevNo	= (mAppPortNo/2-5554)/2;
					int prvDevNo	= (Integer.parseInt(tok3[0])-5554)/2;
					
					if((flag == 1) && (curDevNo == ((prvDevNo+1)%Utility.mNoOfEmulators))) {
						if(count < Utility.mNoOfEmulators) {
							str	= "Test-Message-2:1" + "-x-x-:" + tok1[1];
						}
						else {
							str	= "Test-Message-2:0" + "-x-x-:" + tok1[1];
						}
						synchronized(MsgPasser.GetInstance().mActivityToMsgHandler) {
							Log.i(Utility.mDebugInfo, "MsgHndlngModule: Forwarding Test2Message. " + str);
							MsgPasser.GetInstance().mActivityToMsgHandler.add(str);
							MsgPasser.GetInstance().mActivityToMsgHandler.notify();
						}
					}
				}
			}
			else {
				dlvrInSeqOrder.add(msg);
			}
		}	
    }	
}
