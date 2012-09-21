/**
 * Simple notes database access helper class. Defines the basic CRUD operations
 * for the notepad example, and gives the ability to list all notes as well as
 * retrieve or modify a specific note.
 * 
 * This has been improved from the first version of this tutorial through the
 * addition of better error handling and also using returning a Cursor instead
 * of using a collection of inner classes (which is less scalable and not
 * recommended).
 */
package edu.buffalo.cse.cse486_586.simpledht;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.Formatter;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.telephony.TelephonyManager;
import android.util.Log;

public class MsgDbAdapter extends ContentProvider{

	private static final String DATABASE_NAME 	= "chat.db";
    private static final String CHAT_DB_TABLE 	= "chatHistory";
    private static final String TEMP_DB_TABLE 	= "tempHistory";
    private static final String COL_MSG_KEY 	= "provider_key";
    private static final String COL_MSG_VAL 	= "provider_value";
    private static final String CHAT_TBL_CREATE = "create table " + CHAT_DB_TABLE + " (" + COL_MSG_KEY + " text primary key , " + COL_MSG_VAL + " text not null);";
    private static final String TEMP_TBL_CREATE	= "create table " + TEMP_DB_TABLE + " (" + COL_MSG_KEY + " text not null , " + COL_MSG_VAL + " text not null);";
    private static final int DATABASE_VERSION 	= 2;
    
    private DatabaseHelper mDbHelper;
    private static SQLiteDatabase mDb;
    private static final UriMatcher mUriMatcher;
    
    private Node mCurNode						= new Node();
    
    private Thread mRecvThread					= null;
	private Thread mSendThread					= null;
	private int mServerPortNo					= 10000;
    
    static 
    {
    	mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    	mUriMatcher.addURI("edu.buffalo.cse.cse486_586.simpledht.provider", null, 1);
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
        	Log.i(Utility.mDebugInfo, "Creating Database.");
            db.execSQL(CHAT_TBL_CREATE);
            db.execSQL(TEMP_TBL_CREATE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.i(Utility.mDebugInfo, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("drop table if exists " + CHAT_DB_TABLE);
            db.execSQL("drop table if exists " + TEMP_DB_TABLE);
            onCreate(db);
        }
    }

	@Override
	public boolean onCreate() {
		mDbHelper = new DatabaseHelper(getContext());
        mDb = mDbHelper.getWritableDatabase();
        if(mDb==null)
        	return false;
        else {
        	deleteAllMessages();
        	
        	mRecvThread=new Thread(new RecvThread());
    		mRecvThread.start();
            mSendThread=new Thread(new SendThread());
            mSendThread.start();
        	
            mCurNode.mMngrNodePort	= Utility.mMngrPortNo;
        	TelephonyManager tel	= (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        	mCurNode.mCurrNodePort 	= Integer.parseInt(tel.getLine1Number().substring(tel.getLine1Number().length() - 4))*2;
        	mCurNode.mCurrNodeHash	= genHash(Integer.toString(mCurNode.mCurrNodePort/2));
        	
        	if(mCurNode.mCurrNodePort == mCurNode.mMngrNodePort) {
        		mCurNode.mPrevNodePort	= mCurNode.mMngrNodePort;
        		mCurNode.mPrevNodeHash	= mCurNode.mCurrNodeHash;
        		mCurNode.mNextNodePort	= mCurNode.mMngrNodePort;
        		mCurNode.mNextNodeHash	= mCurNode.mCurrNodeHash;
        	    //mMngrNodeList			= new LinkedList<Node>();
        	    //mMngrNodeList.add(mCurNode);
        	}
        	else {
        		synchronized (MsgPasser.GetInstance().mSendMsgBuf) {
        			Message msg	= new Message();
        			msg.SetObject("AddMeToCord.", "", "", Utility.mIP,mCurNode.mCurrNodePort, Utility.mIP, mCurNode.mMngrNodePort);			
        			MsgPasser.GetInstance().mSendMsgBuf.add(msg);
					MsgPasser.GetInstance().mSendMsgBuf.notify();
				}
        	}
        	
        	return true;
        }
	}
	
	@Override
	public Uri insert(Uri uri, ContentValues initialValues) {
		/*
		 * update insert
		 */
		if (mUriMatcher.match(uri) != 1) 
        { 
            throw new IllegalArgumentException("Unknown URI " + uri); 
        }
		
		ContentValues values;
        if (initialValues != null) 
        {
            values = new ContentValues(initialValues);
        }
        else 
        {
            values = new ContentValues();
        }
        
        // generate message from content values
    	// add this to message buffer
        Message msgObj	= new Message();
        msgObj.SetObject("StoreKeyValuePair.", (String)values.get("provider_key"), (String)values.get("provider_value"), Utility.mIP, mCurNode.mCurrNodePort, "", -1);
        MsgPasser.GetInstance().mInsrtQurryBuf.put(msgObj.mMsgID, msgObj);
        // call WhoCanStoreThisKey
        Message msgSend	= new Message();
        msgSend.SetObject("WhoCanStoreThisKey?", (String)values.get("provider_key"), "", Utility.mIP, mCurNode.mCurrNodePort, Utility.mIP, mCurNode.mCurrNodePort);
        WhoCanStoreThisKey(msgSend);
        
        return null;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		/*
		 * update query
		 */
		if (mUriMatcher.match(uri) != 1) 
        { 
            throw new IllegalArgumentException("Unknown URI " + uri); 
        }
        
		Cursor mCursor = null;
    	if(selection != null)
        {
    		// generate message
        	// add this to message buffer
            Message msgObj	= new Message();
            msgObj.SetObject("RetrieveKeyValuePair.", selection, "", Utility.mIP, mCurNode.mCurrNodePort, "", -1);
            MsgPasser.GetInstance().mInsrtQurryBuf.put(msgObj.mMsgID, msgObj);
            // call WhoCanStoreThisKey
            Message msgSend	= new Message();
            msgSend.SetObject("WhoCanStoreThisKey?", selection, "", Utility.mIP, mCurNode.mCurrNodePort, Utility.mIP, mCurNode.mCurrNodePort);
            WhoCanStoreThisKey(msgSend);
            // wait for returned message
            Message msgRecv	= new Message();
            try {
            	while(true) {
                	synchronized (MsgPasser.GetInstance().mRecvMsgBuf) {
        				if(MsgPasser.GetInstance().mRecvMsgBuf.containsKey(selection)) {
        					msgRecv = MsgPasser.GetInstance().mRecvMsgBuf.get(selection);
        					break;
        				}
        				else {
        					MsgPasser.GetInstance().mRecvMsgBuf.wait();
        				}
        			}
                }
            }	
            catch (Exception e) {
            	Log.i(Utility.mException, "Querying database : " + selection);
            }
            // create cursor from returned message
            mDb.execSQL("drop table if exists " + TEMP_DB_TABLE);
            mDb.execSQL(TEMP_TBL_CREATE);
            
            ContentValues values = new ContentValues();
    		values.put("provider_key", "" + msgRecv.mMsgID);
    		values.put("provider_value", msgRecv.mMsgData);
    		try
            {
            	long rowId = mDb.insert(TEMP_DB_TABLE, null, values);
                if (rowId > 0) 
                {
                	Uri noteUri = ContentUris.withAppendedId(Utility.mUrlUri, rowId);
                    getContext().getContentResolver().notifyChange(noteUri, null);
                    
                    try {
                    	mCursor =  mDb.rawQuery( "select rowid _id,* from tempHistory where " 
                                + COL_MSG_KEY + "=" + selection, null);
                    	if (mCursor != null) 
        				{
        					mCursor.moveToFirst();
        					mCursor.setNotificationUri(getContext().getContentResolver(), uri);
                            return mCursor;
        				}
                    }
                    catch(Exception e)
                    {
                    	e.printStackTrace();
                    }
                }
            }
            catch(SQLException e)
            {
                e.printStackTrace();
            }
            throw new SQLException("Failed to insert row into " + Utility.mUrlUri);
        }
        else
        {
            try
            {
            	mCursor =  mDb.rawQuery( "select rowid _id,* from chatHistory order by " 
                                          + "rowid" + " desc", null);
            	if (mCursor != null) 
				{
            		mCursor.moveToFirst();
            		mCursor.setNotificationUri(getContext().getContentResolver(), uri);
                    return mCursor;
				}
            }
            catch(Exception e)
            {
                        e.printStackTrace();
            }
        }
        //mCursor.setNotificationUri(getContext().getContentResolver(), uri);
        //return mCursor;
    	return null;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		return 0;
	}
	
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		return 0;
	}

	@Override
	public String getType(Uri uri) {
		if (mUriMatcher.match(uri) != 1) 
        { 
            throw new IllegalArgumentException("Unknown URI " + uri); 
        }
        return "vnd.android.cursor.dir/vnd.jwei512.notes";
	}
	
	static public void deleteAllMessages() {
		try
        {
			mDb.delete(CHAT_DB_TABLE, null, null);
			mDb.delete(TEMP_DB_TABLE, null, null);
        }
        catch(Exception e)
        {
        	e.printStackTrace();
        }
    }
	
	private String genHash(String input) {
		MessageDigest sha1	= null;
		try {
			sha1 = MessageDigest.getInstance("SHA-1");
		} catch (Exception e) {
			Log.i(Utility.mException, "Hash generation failed. " + e.toString());
			e.printStackTrace();
		}
		byte[] sha1Hash = sha1.digest(input.getBytes());
		Formatter formatter = new Formatter();
		for (byte b : sha1Hash) {
			formatter.format("%02x", b);
		}
		return formatter.toString();
	}
	
	public class SendThread extends Thread {
		public SendThread() {
			super("SendThread");
		}
		
		Message	msgObj	= new Message();

		public void run() {
			while(true) {
				msgObj.Reset();
				
				synchronized(MsgPasser.GetInstance().mSendMsgBuf) {
					if(MsgPasser.GetInstance().mSendMsgBuf.size() == 0) {
						try {
							MsgPasser.GetInstance().mSendMsgBuf.wait();
						}
						catch(Exception e) {
							Log.i(Utility.mException, "SendThread::run. " + e.toString());
						}
					}
					
					msgObj = MsgPasser.GetInstance().mSendMsgBuf.poll();
					UniCastMsg(msgObj, msgObj.mToIP, msgObj.mToPortNo);
				}
			}
		}
	}
	
	private void UniCastMsg(Message msg, String appIP, int appPort){
		try {
			Socket socket		= new Socket(appIP, appPort);
			if(socket.isConnected()) {
				
				OutputStream outStrm			= socket.getOutputStream();
				ObjectOutputStream objOutStrm	= new ObjectOutputStream(outStrm);
				objOutStrm.writeObject(msg);
				
				objOutStrm.close();
				outStrm.close();
				socket.close();
				
				Log.i(Utility.mDebugInfo, "Unicasted to " + appIP + ":" + appPort + " -> " + msg.EncodeString());
			}
			else {
				Log.i(Utility.mDebugInfo, "Unicasted cannot connect to " + appIP + ":" + appPort + " -> " + msg.EncodeString());
			}
			
		}
		catch(Exception e) {
			Log.i(Utility.mException, "Unicast failed for " + appIP + ":" + appPort + " -> "+ msg.EncodeString());
		}
	}
	
	public class RecvThread extends Thread {
		
		public RecvThread() {
			super("RecvThread");
		}
		
		ServerSocket serverSocket	= null;
		
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
			
			try {
				serverSocket	= new ServerSocket(mServerPortNo);
			}
			catch(Exception e) {
				Log.i(Utility.mException, "Server socket listening failed. " + e.toString());
			}
			
			while(true) {
				Message msgRecvd	= null;
				
				try {
					Socket socket				= serverSocket.accept();
					InputStream inStrm			= socket.getInputStream();
					ObjectInputStream objInStrm	= new ObjectInputStream(inStrm);
					msgRecvd					= (Message) objInStrm.readObject();
				}
				catch (Exception e) {
					Log.i(Utility.mException, "Read at server socket failed. " + e.toString());
				}
				
				Log.i(Utility.mDebugInfo, "Message received. " + msgRecvd.EncodeString());
		    	
				if(msgRecvd.mMsgType.equals("AddMeToCord.")) {
					AddMeToCord(msgRecvd);
		    	}
		    	else if(msgRecvd.mMsgType.equals("SetCordParameters.")) {
		    		SetCordParameters(msgRecvd);
		    	} 
		    	else if(msgRecvd.mMsgType.equals("WhoCanStoreThisKey?")) {
		    		WhoCanStoreThisKey(msgRecvd);
		    	}
		    	else if(msgRecvd.mMsgType.equals("Yes.WhatDoYouWant?")) {
		    		YesWhatDoYouWant(msgRecvd);
		    	}
		    	else if(msgRecvd.mMsgType.equals("StoreKeyValuePair.")) {
		    		StoreKeyValuePair(msgRecvd);
		    	}
				else if(msgRecvd.mMsgType.equals("RetrieveKeyValuePair.")) {
					RetrieveKeyValuePair(msgRecvd);
				}
				else if(msgRecvd.mMsgType.equals("QueryResponse.")) {
					QueryResponse(msgRecvd);
				}
		    	else {
		    		Log.i(Utility.mDebugInfo, "Unknown message type received.");
		    	}
			}
		}
	}
	
//	void AddMeToCord(Message msgObj) {	
//		/*
//		 * add element to a suitable location in mMngrNodeList and mMngrNodeHashList
//		 * send SetCordParameters messages to nodes whose next and prev will be changed.
//		 */
//		if(mCurNode.mCurrNodePort != Utility.mMngrPortNo)
//			return;
//		
//		Node newNode			= new Node();
//		newNode.mMngrNodePort	= Utility.mMngrPortNo;
//		newNode.mCurrNodePort	= msgObj.mFromPortNo;
//		newNode.mCurrNodeHash	= genHash(Integer.toString(newNode.mCurrNodePort/2));
//		
//		int i	= 0;
//		for(Iterator<Node> it=mMngrNodeList.iterator() ; it.hasNext() ;) {
//			Node node	= it.next();
//			if(newNode.mCurrNodeHash.compareTo(node.mCurrNodeHash) < 0) {
//				break;
//			}
//			i++;
//		}
//		
//		int prvNodeIndx	= 0;
//		int nxtNodeIndx	= 0;
//		if(i == 0) {
//			prvNodeIndx	= mMngrNodeList.size()-1;
//			nxtNodeIndx	= i;
//		}
//		else if (i != mMngrNodeList.size()) {
//			prvNodeIndx	= i-1;
//			nxtNodeIndx	= i;
//		}
//		else {
//			prvNodeIndx	= i-1;
//			nxtNodeIndx	= 0;
//		}
//		
//		Node prvNode	= mMngrNodeList.get(prvNodeIndx);
//		Node nxtNode	= mMngrNodeList.get(nxtNodeIndx);
//		
//		prvNode.mNextNodePort	= newNode.mCurrNodePort;
//		prvNode.mNextNodeHash	= newNode.mCurrNodeHash;
//		nxtNode.mPrevNodePort	= newNode.mCurrNodePort;
//		nxtNode.mPrevNodeHash	= newNode.mCurrNodeHash;
//		newNode.mPrevNodePort	= prvNode.mCurrNodePort;
//		newNode.mPrevNodeHash	= prvNode.mCurrNodeHash;
//		newNode.mNextNodePort	= nxtNode.mCurrNodePort;
//		newNode.mNextNodeHash	= nxtNode.mCurrNodeHash;
//		
//		mMngrNodeList.add(i, newNode);
//		
//		Message prvNodeMsg	= new Message();
//		prvNodeMsg.SetObject("SetCordParameters.", "", prvNode.Encode(), Utility.mIP, mCurNode.mCurrNodePort, Utility.mIP, prvNode.mCurrNodePort);
//		Message newNodeMsg	= new Message();
//		newNodeMsg.SetObject("SetCordParameters.", "", newNode.Encode(), Utility.mIP, mCurNode.mCurrNodePort, Utility.mIP, newNode.mCurrNodePort);
//		Message nxtNodeMsg	= new Message();
//		nxtNodeMsg.SetObject("SetCordParameters.", "", nxtNode.Encode(), Utility.mIP, mCurNode.mCurrNodePort, Utility.mIP, nxtNode.mCurrNodePort);
//		
//		synchronized (MsgPasser.GetInstance().mSendMsgBuf) {
//			MsgPasser.GetInstance().mSendMsgBuf.add(prvNodeMsg);
//			MsgPasser.GetInstance().mSendMsgBuf.add(newNodeMsg);
//			MsgPasser.GetInstance().mSendMsgBuf.add(nxtNodeMsg);
//			MsgPasser.GetInstance().mSendMsgBuf.notify();
//		}
//	}
	
	void AddMeToCord(Message msgObj) {	
		/*
		 * add element to a suitable location in mMngrNodeList and mMngrNodeHashList
		 * send SetCordParameters messages to nodes whose next and prev will be changed.
		 */
		//if(mCurNode.mCurrNodePort != Utility.mMngrPortNo)
		//	return;
		
		Node newNode			= new Node();
		newNode.mMngrNodePort	= Utility.mMngrPortNo;
		newNode.mCurrNodePort	= msgObj.mFromPortNo;
		newNode.mCurrNodeHash	= genHash(Integer.toString(newNode.mCurrNodePort/2));
		
		if(mCurNode.mPrevNodePort == mCurNode.mCurrNodePort) {
			
			mCurNode.mPrevNodePort	= newNode.mCurrNodePort;
			mCurNode.mPrevNodeHash	= newNode.mCurrNodeHash;
			mCurNode.mNextNodePort	= newNode.mCurrNodePort;
			mCurNode.mNextNodeHash	= newNode.mCurrNodeHash;
			
			newNode.mPrevNodePort	= mCurNode.mCurrNodePort;
			newNode.mPrevNodeHash	= mCurNode.mCurrNodeHash;
			newNode.mNextNodePort	= mCurNode.mCurrNodePort;
			newNode.mNextNodeHash	= mCurNode.mCurrNodeHash;
			
			Message newNodeMsg		= new Message();
			newNodeMsg.SetObject("SetCordParameters.", "", newNode.Encode(), Utility.mIP, mCurNode.mCurrNodePort, Utility.mIP, newNode.mCurrNodePort);
			synchronized (MsgPasser.GetInstance().mSendMsgBuf) {
				MsgPasser.GetInstance().mSendMsgBuf.add(newNodeMsg);
				MsgPasser.GetInstance().mSendMsgBuf.notify();
			}
		} 
		else {
			// prv < cur
			if(mCurNode.mPrevNodeHash.compareTo(mCurNode.mCurrNodeHash) < 0) {
				// new node lies bw prv < new < cur
				if( mCurNode.mPrevNodeHash.compareTo(newNode.mCurrNodeHash) < 0 &&
					newNode.mCurrNodeHash.compareTo(mCurNode.mCurrNodeHash) < 0 
				  ) {
					UpdateNodeParams(newNode);
				}
				else {
					PassMsgToNextNode(msgObj);
				}
				
			}
			// prv > cur
			else {
				// new node lies bw new < cur || new > prv
				if( (newNode.mCurrNodeHash.compareTo(mCurNode.mCurrNodeHash) < 0) ||
				    (newNode.mCurrNodeHash.compareTo(mCurNode.mPrevNodeHash) > 0) 
				  ) {
					UpdateNodeParams(newNode);
				}
				else {
					PassMsgToNextNode(msgObj);
				}
				
			}

		}
	}
	
	void UpdateNodeParams(Node newNode) {
		Node prvNode			= new Node();
		Message prvNodeMsg		= new Message();
		Message newNodeMsg		= new Message();
		Message curNodeMsg		= new Message();
		
		// set prev
		prvNode.mNextNodePort	= newNode.mCurrNodePort;
		prvNode.mNextNodeHash	= newNode.mCurrNodeHash;
		prvNodeMsg.SetObject("SetCordParameters.", "", prvNode.Encode(), Utility.mIP, mCurNode.mCurrNodePort, Utility.mIP, mCurNode.mPrevNodePort);
		synchronized (MsgPasser.GetInstance().mSendMsgBuf) {
			MsgPasser.GetInstance().mSendMsgBuf.add(prvNodeMsg);
			MsgPasser.GetInstance().mSendMsgBuf.notify();
		}
		
		// set new
		newNode.mPrevNodePort	= mCurNode.mPrevNodePort;
		newNode.mPrevNodeHash	= mCurNode.mPrevNodeHash;
		newNode.mNextNodePort	= mCurNode.mCurrNodePort;
		newNode.mNextNodeHash	= mCurNode.mCurrNodeHash;
		newNodeMsg.SetObject("SetCordParameters.", "", newNode.Encode(), Utility.mIP, mCurNode.mCurrNodePort, Utility.mIP, newNode.mCurrNodePort);
		synchronized (MsgPasser.GetInstance().mSendMsgBuf) {
			MsgPasser.GetInstance().mSendMsgBuf.add(newNodeMsg);
			MsgPasser.GetInstance().mSendMsgBuf.notify();
		}
				
		// set self
		mCurNode.mPrevNodePort	= newNode.mCurrNodePort;
		mCurNode.mPrevNodeHash	= newNode.mCurrNodeHash;
		curNodeMsg.SetObject("SetCordParameters.", "", mCurNode.Encode(), Utility.mIP, mCurNode.mCurrNodePort, Utility.mIP, mCurNode.mCurrNodePort);
		synchronized (MsgPasser.GetInstance().mSendMsgBuf) {
			MsgPasser.GetInstance().mSendMsgBuf.add(curNodeMsg);
			MsgPasser.GetInstance().mSendMsgBuf.notify();
		}
	}
	
	void PassMsgToNextNode(Message msgObj) {
		// pass to next
		msgObj.mToIP		= Utility.mIP;
		msgObj.mToPortNo	= mCurNode.mNextNodePort;
		synchronized (MsgPasser.GetInstance().mSendMsgBuf) {
			MsgPasser.GetInstance().mSendMsgBuf.add(msgObj);
			MsgPasser.GetInstance().mSendMsgBuf.notify();
		}
	}
	
	void SetCordParameters(Message msgObj) {
		/*
		 * just set prev and next parameters.
		 */
		Node node	= new Node();
		node.Decode(msgObj.mMsgData);
		//mCurNode	= node;
		if(node.mCurrNodePort	!= -1) {
			mCurNode.mCurrNodePort	= node.mCurrNodePort;
			mCurNode.mCurrNodeHash	= genHash(Integer.toString(mCurNode.mCurrNodePort/2));
		}
		if(node.mMngrNodePort	!= -1) {
			mCurNode.mMngrNodePort	= node.mMngrNodePort;
		}
		if(node.mNextNodePort	!= -1) {
			mCurNode.mNextNodePort	= node.mNextNodePort;
			mCurNode.mNextNodeHash	= genHash(Integer.toString(mCurNode.mNextNodePort/2));
		}
		if(node.mPrevNodePort	!= -1) {
			mCurNode.mPrevNodePort	= node.mPrevNodePort;
			mCurNode.mPrevNodeHash	= genHash(Integer.toString(mCurNode.mPrevNodePort/2));
		}
		
		Log.i(Utility.mDebugInfo, "Message received. SetCord -> " + mCurNode.Encode());
	}
	
	void WhoCanStoreThisKey(Message msgObj) {	
		/*
		 * if its present bw prev and cur then return true
		 * else pass message to next in the circle
		 */
		String msgKeyHash	= genHash(msgObj.mMsgID);
		Message msg			= new Message();
		
		if(mCurNode.mPrevNodeHash.compareTo(mCurNode.mCurrNodeHash) < 0) {
			if( mCurNode.mPrevNodeHash.compareTo(msgKeyHash) < 0 &&
				msgKeyHash.compareTo(mCurNode.mCurrNodeHash) < 0 ) {
				// request data
				msg.SetObject("Yes.WhatDoYouWant?", msgObj.mMsgID, "", Utility.mIP, mCurNode.mCurrNodePort, msgObj.mFromIP, msgObj.mFromPortNo);
			}
			else {
				// pass to next
				msg				= msgObj;
				msg.mToIP		= Utility.mIP;
				msg.mToPortNo	= mCurNode.mNextNodePort;
			}
		}
		else {
			if( (mCurNode.mPrevNodeHash.compareTo(msgKeyHash) < 0 && mCurNode.mCurrNodeHash.compareTo(msgKeyHash) < 0) ||
			    (mCurNode.mPrevNodeHash.compareTo(msgKeyHash) > 0 && mCurNode.mCurrNodeHash.compareTo(msgKeyHash) > 0) ) {
				// request data
				msg.SetObject("Yes.WhatDoYouWant?", msgObj.mMsgID, "", Utility.mIP, mCurNode.mCurrNodePort, msgObj.mFromIP, msgObj.mFromPortNo);
			}
			else {
				// pass to next
				msg				= msgObj;
				msg.mToIP		= Utility.mIP;
				msg.mToPortNo	= mCurNode.mNextNodePort;
			}
		}
		
		// send message
		synchronized (MsgPasser.GetInstance().mSendMsgBuf) {
			MsgPasser.GetInstance().mSendMsgBuf.add(msg);
			MsgPasser.GetInstance().mSendMsgBuf.notify();
		}
	}
	
	void YesWhatDoYouWant(Message msgObj) {	
		/*
		 * send store or retrieve message corresponding to the key
		 */
		Message msgSend		= new Message(MsgPasser.GetInstance().mInsrtQurryBuf.get(msgObj.mMsgID));
		msgSend.mToIP		= msgObj.mFromIP;
		msgSend.mToPortNo	= msgObj.mFromPortNo;
		synchronized (MsgPasser.GetInstance().mSendMsgBuf) {
			MsgPasser.GetInstance().mSendMsgBuf.add(msgSend);
			MsgPasser.GetInstance().mSendMsgBuf.notify();
		}
	}
	
//	void StoreKeyValuePair(Message msgObj) {	
//		/*
//		 * 
//		 */
//		ContentValues values = new ContentValues();
//		values.put("provider_key", "" + msgObj.mMsgID);
//		values.put("provider_value", msgObj.mMsgData);
//		try
//        {
//        	long rowId = mDb.insert(CHAT_DB_TABLE, null, values);
//            if (rowId > 0) 
//            {
//            	Uri noteUri = ContentUris.withAppendedId(Utility.mUrlUri, rowId);
//                getContext().getContentResolver().notifyChange(noteUri, null);
//                return;
//            }
//        }
//        catch(SQLException e)
//        {
//            e.printStackTrace();
//        }
//        throw new SQLException("Failed to insert row into " + Utility.mUrlUri);
//	}
	void StoreKeyValuePair(Message msgObj) {	
		/*
		 * 
		 */
		ContentValues values = new ContentValues();
		values.put("provider_key", "" + msgObj.mMsgID);
		values.put("provider_value", msgObj.mMsgData);
		try
        {
        	long rowId = mDb.insert(CHAT_DB_TABLE, null, values);
            if (rowId > 0) 
            {
            	Uri noteUri = ContentUris.withAppendedId(Utility.mUrlUri, rowId);
                getContext().getContentResolver().notifyChange(noteUri, null);
                return;
            }
        }
        catch(SQLException e)
        {
        	String strArr[]	= new String[1];
        	strArr[0]		= (String)values.get("provider_key");
        	mDb.delete(CHAT_DB_TABLE, null, strArr);
        	
        	long rowId = mDb.insert(CHAT_DB_TABLE, null, values);
            if (rowId > 0) 
            {
            	Uri noteUri = ContentUris.withAppendedId(Utility.mUrlUri, rowId);
                getContext().getContentResolver().notifyChange(noteUri, null);
                return; 
            }
            
            e.printStackTrace();
        }
	}
	
	void RetrieveKeyValuePair(Message msgObj) {	
		/*
		 * query local database
		 */
		Cursor mCursor	= null;
		try
        {
			mCursor =  mDb.rawQuery( "select rowid _id,* from chatHistory where " 
                                     + COL_MSG_KEY + "=" + msgObj.mMsgID, null);
            if (mCursor != null) 
            {
            	mCursor.moveToFirst();
            }
        }
        catch(Exception e)
        {
        	e.printStackTrace();
        }
		
		String msgData		= mCursor.getString(2);
		
		Message msgSend		= new Message();
		msgSend.mMsgType	= "QueryResponse.";
		msgSend.mMsgID		= msgObj.mMsgID;
		msgSend.mMsgData	= msgData;
		msgSend.mFromIP		= Utility.mIP;
		msgSend.mFromPortNo	= mCurNode.mCurrNodePort;
		msgSend.mToIP		= msgObj.mFromIP;
		msgSend.mToPortNo	= msgObj.mFromPortNo;
		
		synchronized (MsgPasser.GetInstance().mSendMsgBuf) {
			MsgPasser.GetInstance().mSendMsgBuf.add(msgSend);
			MsgPasser.GetInstance().mSendMsgBuf.notify();
		}
	}
	
	void QueryResponse(Message msgObj) {
		synchronized (MsgPasser.GetInstance().mRecvMsgBuf) {
			MsgPasser.GetInstance().mRecvMsgBuf.put(msgObj.mMsgID, msgObj);
			MsgPasser.GetInstance().mRecvMsgBuf.notify();
		}
	}
}
