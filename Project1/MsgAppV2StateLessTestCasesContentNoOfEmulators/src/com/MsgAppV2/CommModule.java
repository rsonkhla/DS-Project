package com.MsgAppV2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;

import android.util.Log;


/*
 * Functions 
 * 		- multicast messages if "DataMessage" or "OrderMessage"
 * 		- unicast to particular peer if "ElectionMessage" or "SequencerRequest"
 * 
 * Who is the sequencer?
 * Ask right neighbour in the connected list - who is the sequencer
 * Initiate election if
 *		- X-it returns null
 *		- X-sequencer crash is detected
 * 		- --its own message stays in buffer for some time
 * After new sequencer is selected each process sends its messages in its hold back queue
 * and msg id + seq id of last message it delivered 
 * Sequencer used that to assign seq id to those and new messages
 */

public class CommModule {
	private String mAppIP						= null;
	private int mAppPortNo						= -1;
	private int mServerPortNo					= 10000;
	private LinkedList<PeerAddress> mPeersList 	= new LinkedList<PeerAddress>();
	private Thread mRecvThread					= null;
	private Thread mSendThread					= null;
	ServerSocket serverSocket					= null;
	
	public CommModule(String appIP, int appPortNo) {
		mAppIP		= appIP;
		mAppPortNo	= appPortNo;
		
		synchronized (Sequencer.GetInstance().mSequencer) {
			if(Sequencer.GetInstance().mSequencer.mPortNo == -1) {
				Sequencer.GetInstance().StartSequencerElection(mAppIP, mAppPortNo);
				
				try {
					if(Sequencer.GetInstance().mSequencer.mPortNo == -1)
						Sequencer.GetInstance().mSequencer.wait();
				} catch (Exception e) {
					Log.i(Utility.mException, "CommModule: Multicast sequencer election failed. " + e.toString());
				}
			}
		}
	}
	
	public void Start() {
		// Initialize and execute Threads
		mRecvThread=new Thread(new RecvThread());
		mRecvThread.start();
        mSendThread=new Thread(new SendThread());
        mSendThread.start();
	}
	
	public void Stop() {
		try {
			serverSocket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		mRecvThread.stop();
        mSendThread.stop();
	}
	
	
	
	LinkedList<PeerAddress> GetArrayListOfPeers() {
		LinkedList<PeerAddress> lnkdLst	= new LinkedList<PeerAddress>();
		
		for(int i=0 ; i<Utility.mNoOfEmulators ; i++) {
			lnkdLst.add(new PeerAddress("10.0.2.2", 11108 + i*4));
		}
		
		return lnkdLst;
	}
	
	public class SendThread extends Thread {
		public SendThread() {
			super("SendThread");
		}
		
		String msgStr 	= new String();
		Message	msgObj	= new Message();

		public void run() {
			while(true) {
				msgStr	= "";
				msgObj.Reset();
				
				synchronized(MsgPasser.GetInstance().mMsgHandlerToCommModule) {
					if(MsgPasser.GetInstance().mMsgHandlerToCommModule.size() == 0) {
						try {
							MsgPasser.GetInstance().mMsgHandlerToCommModule.wait();
						}
						catch(Exception e) {
							Log.i(Utility.mException, "CommModule::SendThread::run. " + e.toString());
						}
					}
						
					msgStr = MsgPasser.GetInstance().mMsgHandlerToCommModule.poll();
					Log.i(Utility.mDebugInfo, "CommModule: Receiving message from MsgHandler to CommModule. " + msgStr);
				}
				if(msgObj.DecodeString(msgStr)) {
					// Message will be of type "DataMessage" or "OrderMessage" only
					MultiCastMsg(msgObj);
				}
				else {
					Log.i(Utility.mMsgLog, "CommModule: Received message cannot be decoded. " + msgStr);
				}
			}
		}
	}
	
	public class RecvThread extends Thread {
		
		public RecvThread() {
			super("RecvThread");
		}
		
		@Override
		public void destroy() {
			if(serverSocket != null) {
				try {
					serverSocket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			super.destroy();
		}
		
		public void run() {				
			synchronized (mPeersList) {
				mPeersList	= GetArrayListOfPeers();
			}
			
			//ServerSocket serverSocket	= null;
			//Socket socket				= null;
			//BufferedReader inBuff		= null;
			String encodedMessage		= null;
			
			Message msgRecvd			= new Message();
			Message msgSent				= new Message();
			
			try {
				serverSocket	= new ServerSocket(mServerPortNo);
			}
			catch(Exception e) {
				Log.i(Utility.mException, "CommModule: Server socket listening failed. " + e.toString());
			}
			
			while(true) {
				try {
					Socket socket			= serverSocket.accept();
					BufferedReader inBuff	= new BufferedReader(new InputStreamReader(socket.getInputStream()));
					encodedMessage 			= inBuff.readLine();
					
					//inBuff.reset();
					inBuff.close();
					//socket.close();
				}
				catch (Exception e) {
					Log.i(Utility.mException, "CommModule: Read at server socket failed. " + e.toString());
				}
				
				Log.i(Utility.mDebugInfo, "CommModule: Message received. " + encodedMessage);
				
				msgRecvd.DecodeString(encodedMessage);
				encodedMessage	= "";
		    	
				if(msgRecvd.mMsgType.equals("DataMessage") || msgRecvd.mMsgType.equals("OrderMessage")) {
		    		synchronized (MsgPasser.GetInstance().mCommModuleToMsgHandler) {
		    			MsgPasser.GetInstance().mCommModuleToMsgHandler.add(msgRecvd.EncodeString());
		    			MsgPasser.GetInstance().mCommModuleToMsgHandler.notify();
		    		}
		    	}
		    	else if(msgRecvd.mMsgType.equals("SequencerRequest")) {
				    if(Sequencer.GetInstance().mIsSequencer) {
				    	msgSent.SetObject("SequencerResponse", -1, "", "", mAppIP, mAppPortNo, 0, 0);
				    	UniCastMsg(msgSent, msgRecvd.mAppIP, msgRecvd.mAppPortNo); 	
				    }
				    else {
				    	msgSent.SetObject("SequencerResponse", -1, "", "", "", -1, 0, 0);
				    	UniCastMsg(msgSent, msgRecvd.mAppIP, msgRecvd.mAppPortNo); 	
				    }
		    	} 
		    	else if(msgRecvd.mMsgType.equals("SequencerResponse")) {
		    		if(msgRecvd.mAppPortNo != -1) {
		    			Sequencer.GetInstance().mSequencer.mIPAddress	= msgRecvd.mAppIP;
			    		Sequencer.GetInstance().mSequencer.mPortNo		= msgRecvd.mAppPortNo;
			    		Sequencer.GetInstance().mIsSequencer			= false;
			    		Log.i(Utility.mDebugInfo, "CommModule - SequencerResponse received, and sequencer is set to " + msgRecvd.mAppIP +":" + msgRecvd.mAppPortNo);
		    		}
		    	}
		    	else {
		    		HandleUnknownMessages();
		    	}
			}
		}
	}
	
	private void UniCastMsg(Message msg, String appIP, int appPort){
		try {
			Socket socket		= new Socket(appIP, appPort);
			if(socket.isConnected()) {
				PrintWriter outBuff	= new PrintWriter(socket.getOutputStream(), true);
				
				outBuff.flush();
				outBuff.println(msg.EncodeString());
				outBuff.flush();
				outBuff.close();
				socket.close();
				
				Log.i(Utility.mDebugInfo, "CommModule: Unicasted to " + appIP + ":" + appPort + " -> " + msg.EncodeString());
			}
			else {
				Log.i(Utility.mDebugInfo, "CommModule: Unicasted cannot connect to " + appIP + ":" + appPort + " -> " + msg.EncodeString());
			}
			
		}
		catch(Exception e) {
			Log.i(Utility.mException, "CommModule: Unicast failed for " + appIP + ":" + appPort + " -> "+ msg.EncodeString());
		}
	}
	
	private void MultiCastMsg(Message msg) {
		PeerAddress peer		= null;
		Log.i(Utility.mDebugInfo, "CommModule: Multicasting message. " + msg.EncodeString());
		
		synchronized (mPeersList) {
			for(int i=0 ; i<mPeersList.size() ;i++) {
				peer	= mPeersList.get(i);
				if(Sequencer.GetInstance().mSequencer.mPortNo == peer.mPortNo && Sequencer.GetInstance().mSequencer.mIPAddress.equals(peer.mIPAddress)) {
					continue;
				}
				else {
					UniCastMsg(msg, peer.mIPAddress, peer.mPortNo);
				}
			}
		}
		
		UniCastMsg(msg, Sequencer.GetInstance().mSequencer.mIPAddress, Sequencer.GetInstance().mSequencer.mPortNo);	
	}
	
	private void HandleUnknownMessages() {
		Log.i(Utility.mDebugInfo, "CommModule - Unknown message received.");
	}
}
