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
package edu.buffalo.cse.cse486_586.simpledynamo;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

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
    
    private Node mCurNode						= null;
    private Thread mRecvThread					= null;
    private int mServerPortNo					= 10000;
    
    static 
    {
    	mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    	mUriMatcher.addURI("edu.buffalo.cse.cse486_586.simpledynamo.provider", null, 1);
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {
    	
        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
        	Log.i(Utility.mMsgLog, "DatabaseHelper: onCreate called.");
            db.execSQL(CHAT_TBL_CREATE);
            db.execSQL(TEMP_TBL_CREATE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.i(Utility.mMsgLog, "DatabaseHelper: Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("drop table if exists " + CHAT_DB_TABLE);
            db.execSQL("drop table if exists " + TEMP_DB_TABLE);
            onCreate(db);
        }
    }

	@Override
	public boolean onCreate() {
		Log.i(Utility.mMsgLog, "MsgDbAdapter: onCreate called.");
		mDbHelper = new DatabaseHelper(getContext());
        mDb = mDbHelper.getWritableDatabase();
        if(mDb==null)
        	return false;
        else {
        	deleteAllMessages();
        	
        	TelephonyManager tel	= (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        	mCurNode 				= new Node(Integer.parseInt(tel.getLine1Number().substring(tel.getLine1Number().length() - 4))*2);
        	
        	JoinDynamoConsistently();
        	
        	// now its safe to start server thread
        	mRecvThread=new Thread(new RecvThread());
    		mRecvThread.start();
        	
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
        
        String key	= (String)values.get("provider_key");
        int owner	= mCurNode.WhoIsTheOwnerOfThisKey(key);
        
        boolean alv	= AreYouAlive(mCurNode.mNodeArr.get(owner).mPort);
        if(alv) {
        	return SendInsertKeyValuePairToCoordinator((String)values.get("provider_key"), (String)values.get("provider_value"), mCurNode.mNodeArr.get(owner).mPort);
        }
        else {
        	owner	= mCurNode.getNxtID(owner);
        	alv	= AreYouAlive(mCurNode.mNodeArr.get(owner).mPort);
        	if(alv) {
        		return SendInsertKeyValuePairToCoordinator((String)values.get("provider_key"), (String)values.get("provider_value"), mCurNode.mNodeArr.get(owner).mPort);
        	}
        }
        
        /*
         * return null on failure
         */
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
    		int owner	= mCurNode.WhoIsTheOwnerOfThisKey(selection);
    		boolean alv	= AreYouAlive(mCurNode.mNodeArr.get(owner).mPort);
            if(alv) {
            	return QueryKeyValuePairAsCoordinator(uri, selection, mCurNode.mNodeArr.get(owner).mPort);
            }
            else {
            	owner	= mCurNode.getNxtID(owner);
            	alv	= AreYouAlive(mCurNode.mNodeArr.get(owner).mPort);
            	if(alv) {
            		return QueryKeyValuePairAsCoordinator(uri, selection, mCurNode.mNodeArr.get(owner).mPort);
            	}
            }
        }
        else
        {
            try
            {
            	mCursor =  mDb.rawQuery( "select rowid _id,* from chatHistory order by " 
                                          + "rowid" + " desc", null);
            	if (mCursor != null && mCursor.moveToFirst() != false) 
				{
            		mCursor.setNotificationUri(getContext().getContentResolver(), uri);
                    return mCursor;
				}
            }
            catch(Exception e)
            {
            	e.printStackTrace();
            }
        }
    	
    	/*
         * return null on failure
         */
    	return null;
	}
	
	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
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
	
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		return 0;
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
	
	boolean AreYouAlive(int port) {
		/*
		 * connect to server port
		 * send are you alive message 
		 * wait/timeout for response
		 * return true if response received
		 * else return false
		 */
		Log.i(Utility.mMsgLog, "MsgDbAdapter: AreYouAlive called to check " + port);
		Message msgSent		= new Message();
		msgSent.mMsgType	= "AreYouAlive?";
		Message msgRecv		= (Message)SendMsgAndReceiveReply(msgSent, port, Utility.mTimeOut);
		if(msgRecv != null) {
			if(msgRecv.mMsgType.equals("YesIAmAlive.")) {
				Log.i(Utility.mMsgLog, "MsgDbAdapter: AreYouAlive returning true.");
				return true;
			}
		} 
		Log.i(Utility.mMsgLog, "MsgDbAdapter: AreYouAlive returning false.");
		return false;
	}
	
	Object SendMsgAndReceiveReply(Message msg, int toPort, int timeOut) {
		SendThread sndTh	= new SendThread(msg, toPort, timeOut);
		Thread tmp	= new Thread(sndTh);
		tmp.start();
		try {
			tmp.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return sndTh.mRecvObj;
	}
	
	Object SendMsgAndReceiveReplyThread(Message msg, int toPort, int timeOut) {
		/*
		 * send message toport
		 * if timeout value is specified this means we are expecting a msg in reply
		 * hence return this message else return null
		 */
		Socket socket	= Connect(toPort);
		if(socket != null) {
			Object msgRecv	= new Object();
			
			try {
				OutputStream outStrm			= socket.getOutputStream();
				ObjectOutputStream objOutStrm	= new ObjectOutputStream(outStrm);
				objOutStrm.writeObject(msg);
				objOutStrm.flush();
				
				if(timeOut != 0) {
					socket.setSoTimeout(timeOut);
					InputStream inStrm			= socket.getInputStream();
					ObjectInputStream objInStrm	= new ObjectInputStream(inStrm);
					msgRecv						= objInStrm.readObject();
				}
				
				socket.close();
				return msgRecv;
			}
			catch (SocketTimeoutException e) {
				Log.i(Utility.mException, "SendMsgAndReceiveReply: Read timeout: " + msg.mMsgType + " by " + mCurNode.mNodeArr.get(mCurNode.mC_ID).mPort + " from " + toPort + ". " + e.toString());
				e.printStackTrace();
			}
			catch (Exception e) {
				Log.i(Utility.mException, "SendMsgAndReceiveReply: Read/Write failed: " + msg.mMsgType + " from " + mCurNode.mNodeArr.get(mCurNode.mC_ID).mPort + " to " + toPort + ". " + e.toString());
				e.printStackTrace();
			}
		}
		else {
			Log.i(Utility.mMsgLog, "SendMsgAndReceiveReply: Can not connect: " + msg.mMsgType + " from " + mCurNode.mNodeArr.get(mCurNode.mC_ID).mPort + " to " + toPort + ".");
		}
		
		return null;
	}
	
	void JoinDynamoConsistently() {
		/*
		 * ask r1 or r2 for updated data
		 * ask p1 and p2 for data to be replicated
		 */		
		Log.i(Utility.mMsgLog, "MsgDbAdapter: JoinDynamoConsistently called.");
		// get current node updated data
		GiveMeThisNodesData(mCurNode.mNodeArr.get(mCurNode.mC_ID).mPort, mCurNode.mR1ID, mCurNode.mR2ID);
		
		// get p1 data
		GiveMeThisNodesData(mCurNode.mNodeArr.get(mCurNode.mP1ID).mPort, mCurNode.mP1ID, mCurNode.mR1ID);
		
		// get p2 data
		GiveMeThisNodesData(mCurNode.mNodeArr.get(mCurNode.mP2ID).mPort, mCurNode.mP2ID, mCurNode.mP1ID);
	}
	
	@SuppressWarnings("unchecked")
	void GiveMeThisNodesData(int node, int pref1, int pref2) {
		
		Message msg		= new Message();
		msg.mMsgType	= "GiveMeThisNodesData.";
		msg.mMsgID		= Integer.toString(node/2);
		
		Log.i(Utility.mMsgLog, "MsgDbAdapter: GiveMeThisNodesData called. Node: " + node + " R1: " + mCurNode.mNodeArr.get(pref1).mPort + " R2: " + mCurNode.mNodeArr.get(pref2).mPort);
	
		boolean alv	= AreYouAlive(mCurNode.mNodeArr.get(pref1).mPort);
		if(alv) {
			msg.mToPortNo	= mCurNode.mNodeArr.get(pref1).mPort;
		}
		else {
			alv	= AreYouAlive(mCurNode.mNodeArr.get(pref2).mPort);
			if(alv) {
				msg.mToPortNo	= mCurNode.mNodeArr.get(pref2).mPort;
			}
		}
		
		if(alv) {
			/*
			 * send message to toIP to get data corresponding to node msgID
			 */
			ArrayList<Message> msgRecvd	= (ArrayList<Message>)SendMsgAndReceiveReply(msg, msg.mToPortNo, Utility.mTimeOut);
				
			if(msgRecvd != null) {
				for(int i=0 ; i<msgRecvd.size() ; i++) {
					StoreKeyValuePair(msgRecvd.get(i));
				}
			}
		}
	}
	
//	void StoreKeyValuePair(Message msgObj) {	
//		/*
//		 * store msg key value pair in DB
//		 */
//		Log.i(Utility.mMsgLog, "MsgDbAdapter: StoreKeyValuePair called for " + msgObj.mMsgID + ":" + msgObj.mMsgData);
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
//        	long rowId	= 0;
//        	String strArr[]	= new String[1];
//        	strArr[0]		= (String)values.get("provider_key");
//        	
//        	rowId	= mDb.delete(CHAT_DB_TABLE, null, strArr);
//        	if (rowId > 0) 
//            {
//            	Uri noteUri = ContentUris.withAppendedId(Utility.mUrlUri, rowId);
//                getContext().getContentResolver().notifyChange(noteUri, null);
//            }
//        	
//        	rowId = mDb.insert(CHAT_DB_TABLE, null, values);
//            if (rowId > 0) 
//            {
//            	Uri noteUri = ContentUris.withAppendedId(Utility.mUrlUri, rowId);
//                getContext().getContentResolver().notifyChange(noteUri, null);
//                return; 
//            }
//            
//            e.printStackTrace();
//        }
//	}
	
	void StoreKeyValuePair(Message msgObj) {	
		Log.i(Utility.mMsgLog, "MsgDbAdapter: StoreKeyValuePair called for " + msgObj.mMsgID + ":" + msgObj.mMsgData);
		ContentValues values = new ContentValues();
		values.put("provider_key", "" + msgObj.mMsgID);
		values.put("provider_value", msgObj.mMsgData);
		long rowId = 0;
		
    	String strKey = (String)values.get("provider_key");
		try
		{
			rowId = mDb.delete(CHAT_DB_TABLE, "provider_key" + "=" + strKey, null);
			if (rowId > 0) 
	        {
	            Uri noteUri = ContentUris.withAppendedId(Utility.mUrlUri, rowId);
	            getContext().getContentResolver().notifyChange(noteUri, null);
	        }
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
		
    	rowId = mDb.insert(CHAT_DB_TABLE, null, values);
        if (rowId > 0) 
        {
            Uri noteUri = ContentUris.withAppendedId(Utility.mUrlUri, rowId);
            getContext().getContentResolver().notifyChange(noteUri, null);
            return;
        }
        
        Log.i(Utility.mException, "MsgDbAdapter: StoreKeyValuePair can not insert " + msgObj.mMsgID + ":" + msgObj.mMsgData);
	}
	
	private Socket Connect(int appPort){
		Socket socket	= null;
		
		try {
			socket	= new Socket(Utility.mIP, appPort);
			if(socket.isConnected()) {			
				Log.i(Utility.mMsgLog, "Connected to " + appPort + ".");
			}
			else {
				Log.i(Utility.mMsgLog, "Cannot connect to " + appPort + ".");
			}
		}
		catch(Exception e) {
			Log.i(Utility.mException, "Connection request failed for " + appPort + ".");
			e.printStackTrace();
		}
		
		return socket;
	}
	
	Uri SendInsertKeyValuePairToCoordinator(String key, String value, int port) {
		/*
    	 * send data that we want to store
    	 * InsertKeyValuePairAsCoordinator.
    	 * wait for confirmation
    	 * return new uri on success and null on failure
    	 */
		Message msgSent		= new Message();
		msgSent.mMsgType	= "InsertKeyValuePairAsCoordinator.";
		msgSent.mMsgID		= key;
		msgSent.mMsgData	= value;
		
		Message msgRecv	= (Message)SendMsgAndReceiveReply(msgSent, port, Utility.mTimeOut);
		if(msgRecv != null)
		{
			if(msgRecv.mMsgType.equals("InsertSuccessful."))
				return Uri.parse("");
		}
		
		return null;
	}
	
	Cursor QueryKeyValuePairAsCoordinator(Uri uri, String key, int port) {
		/*
    	 * send data that we want to query
    	 * QueryKeyValuePairAsCoordinator.
    	 * wait for response
    	 * return cursor on success and null on failure
    	 */
		Message msgSent		= new Message();
		msgSent.mMsgType	= "QueryKeyValuePairAsCoordinator.";
		msgSent.mMsgID		= key;
		
		Message msgRecv	= (Message)SendMsgAndReceiveReply(msgSent, port, Utility.mTimeOut);
		if(msgRecv != null)
		{
			Cursor mCursor = null;
			
			if(msgRecv.mMsgType.equals("QuerySuccessful.")) {
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
	                                + COL_MSG_KEY + "=" + key, null);
	                    	if (mCursor != null && mCursor.moveToFirst() != false) 
	        				{
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
	            throw new SQLException("Failed to insert row into " + uri);
			}		
		}
		
		return null;
	}
	
	public class SendThread extends Thread {
	
		public SendThread(Message msg, int toPort, int timeOut) {
			super("SendThread");
			mMsg	= msg;
			mToPort	= toPort;
			mTimeOut= timeOut;
		}
		
		Message	mMsg	= new Message();
		int mToPort		= -1;
		int mTimeOut	= -1;
		Object mRecvObj	= new Object();

		public void run() {
			mRecvObj	= SendMsgAndReceiveReplyThread(mMsg, mToPort, mTimeOut);
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
				e.printStackTrace();
			}
			
			while(true) {
				Message msgRecvd	= null;
				Socket socket		= null;
				
				try {
					socket						= serverSocket.accept();
					InputStream inStrm			= socket.getInputStream();
					ObjectInputStream objInStrm	= new ObjectInputStream(inStrm);
					msgRecvd					= (Message) objInStrm.readObject();
				}
				catch (Exception e) {
					Log.i(Utility.mException, "Read at server socket failed. " + e.toString());
					e.printStackTrace();
				}
				
				Log.i(Utility.mMsgLog, "Message received. " + msgRecvd.EncodeString());
		    	
				if(msgRecvd.mMsgType.equals("AreYouAlive?")) {
					ReplyAreYouAlive(socket);
				}
				else if(msgRecvd.mMsgType.equals("GiveMeThisNodesData.")) {
					ReplyGiveMeThisNodesData(socket, msgRecvd);
				}
				else if(msgRecvd.mMsgType.equals("InsertKeyValuePairAsCoordinator.")) {
					ReplyInsertKeyValuePairAsCoordinator(socket, msgRecvd);
				}
				else if(msgRecvd.mMsgType.equals("InsertKeyValuePair.")) {
					ReplyInsertKeyValuePair(socket, msgRecvd);
				}
				else if(msgRecvd.mMsgType.equals("QueryKeyValuePairAsCoordinator.")) {
					ReplyQueryKeyValuePairAsCoordinator(socket, msgRecvd);
				}
				else if(msgRecvd.mMsgType.equals("QueryKeyValuePair.")) {
					ReplyQueryKeyValuePair(socket, msgRecvd);
				}
		    	else {
		    		Log.i(Utility.mMsgLog, "Unknown message type received.");
		    	}
			}
		}
		
		void SendMsgOverSocket(Socket socket, Object msg) {
			/*
			 * send message over socket
			 */
			try {
				OutputStream outStrm			= socket.getOutputStream();
				ObjectOutputStream objOutStrm	= new ObjectOutputStream(outStrm);
				objOutStrm.writeObject(msg);
				objOutStrm.flush();
			}
			catch (Exception e) {
				Log.i(Utility.mException, "SendMsgOverSocket: Write failed.");
				e.printStackTrace();
			}
		}
		
		void ReplyAreYouAlive(Socket socket) {
			/*
			 * reply YesIAmAlive. message to socket
			 */
			Message msg 	= new Message();
			msg.mMsgType	= "YesIAmAlive.";
			
			SendMsgOverSocket(socket, msg);
		}
		
		void ReplyGiveMeThisNodesData(Socket socket, Message msg) {
			/*
			 * scan through data base and return key-value pair which correspond to node mentioned in msg
			 * send Ok.HereIsThisNodesData.
			 */
			int ownerID					= mCurNode.WhoIsTheOwnerOfThisKey(msg.mMsgID);
			Cursor msgCursor			= query(Utility.mUrlUri ,null, null, null, null);
			ArrayList<Message> msgArr	= new ArrayList<Message>();
			Message tmp					= new Message();
			
			if(msgCursor != null && msgCursor.moveToFirst() != false) {
		        while (msgCursor.isAfterLast() == false) {
		        	tmp.mMsgID	= msgCursor.getString(msgCursor.getColumnIndex(COL_MSG_KEY));
		        	tmp.mMsgData= msgCursor.getString(msgCursor.getColumnIndex(COL_MSG_VAL));
		        	
		        	if(mCurNode.WhoIsTheOwnerOfThisKey(tmp.mMsgID) == ownerID)
		        		msgArr.add(new Message(tmp));
		        	
		            msgCursor.moveToNext();
		        }
		        msgCursor.close();
			}
			
			SendMsgOverSocket(socket, msgArr);
		}
		
		void ReplyInsertKeyValuePairAsCoordinator(Socket socket, Message msg) {
			/*
			 * store data
			 * replicate data
			 * send InsertSuccessful. on success
			 * and InsertFailed. on failure
			 */
			boolean isR1Alive	= false;
			boolean isR2Alive	= false;
			int w				= 0;
			Message msgRecv		= new Message();	
			int owner			= mCurNode.WhoIsTheOwnerOfThisKey(msg.mMsgID);
			
			msg.mMsgType		= "InsertKeyValuePair.";
			
			if(owner == mCurNode.mC_ID) {
				// coordinator
				isR1Alive	= AreYouAlive(mCurNode.mNodeArr.get(mCurNode.mR1ID).mPort);
				isR2Alive	= AreYouAlive(mCurNode.mNodeArr.get(mCurNode.mR2ID).mPort);
				if(isR1Alive || isR2Alive) {
					// if either of R1 or R2 is alive then inert self and that node
					StoreKeyValuePair(msg);
					w++;
					// coordinator
					msgRecv	= (Message)SendMsgAndReceiveReply(msg, mCurNode.mNodeArr.get(mCurNode.mR1ID).mPort, Utility.mTimeOut);
					if(msgRecv != null)
					{
						if(msgRecv.mMsgType.equals("InsertSuccessful."))
							w++;
					}
					msgRecv	= (Message)SendMsgAndReceiveReply(msg, mCurNode.mNodeArr.get(mCurNode.mR2ID).mPort, Utility.mTimeOut);
					if(msgRecv != null)
					{
						if(msgRecv.mMsgType.equals("InsertSuccessful."))
							w++;
					}
				}
			}
			owner	= mCurNode.getNxtID(owner);
			if(owner == mCurNode.mC_ID) {
				// 1st replica
				// coordinator
				isR1Alive	= AreYouAlive(mCurNode.mNodeArr.get(mCurNode.mR1ID).mPort);
				if(isR1Alive) {
					// if either of R1 is alive then inert self and that node
					StoreKeyValuePair(msg);
					w++;
					// coordinator
					msgRecv	= (Message)SendMsgAndReceiveReply(msg, mCurNode.mNodeArr.get(mCurNode.mR1ID).mPort, Utility.mTimeOut);
					if(msgRecv != null)
					{
						if(msgRecv.mMsgType.equals("InsertSuccessful."))
							w++;
					}
				}
			}
			owner	= mCurNode.getNxtID(owner);
			if(owner == mCurNode.mC_ID) {
				// 2nd replica
				// dont insert
			}
			
			Message msgSend	= new Message();
			if(w >= Utility.mQ_W) {
				msgSend.mMsgType	= "InsertSuccessful.";
				SendMsgOverSocket(socket, msgSend);
			}
			else {
				msgSend.mMsgType	= "InsertFailed.";
				SendMsgOverSocket(socket, msgSend);
			}		
		}
		
		void ReplyInsertKeyValuePair(Socket socket, Message msg) {
			/*
			 * store data
			 * send InsertSuccessful. on success
			 * and InsertFailed. on failure
			 */
			StoreKeyValuePair(msg);
			
			Message tmp		= new Message();
			tmp.mMsgType	= "InsertSuccessful.";
			SendMsgOverSocket(socket, tmp);
		}
		
		void ReplyQueryKeyValuePairAsCoordinator(Socket socket, Message msg) {
			/*
			 * retrieve key from its data base
			 * retrieve key from replications
			 * verify quorum
			 * send QuerySuccessful. on success
			 * and QueryFailure. on failure
			 */
			int r			= 0;
			msg.mMsgType	= "QueryKeyValuePair.";
			
//			Message msgRecv1	= (Message)SendMsgAndReceiveReply(msg, mCurNode.mC_ID, Utility.mTimeOut);
//			if(msgRecv1 != null)
//			{
//				if(msgRecv1.mMsgType.equals("QuerySuccessful."))
//					r++;
//			}
			Message msgRecv1	= new Message();
			Cursor mCursor	= null;
			try
	        {
				mCursor =  mDb.rawQuery( "select rowid _id,* from chatHistory where " 
	                                     + COL_MSG_KEY + "=" + msg.mMsgID, null);
	            if (mCursor != null && mCursor.moveToFirst() != false) 
	            {
	    			msgRecv1.mMsgType	= "QuerySuccessful.";
	    			msgRecv1.mMsgID		= msg.mMsgID;
	    			msgRecv1.mMsgData	= mCursor.getString(mCursor.getColumnIndex(COL_MSG_VAL));
	    			r++;
	            }
	        }
	        catch(Exception e)
	        {
	        	e.printStackTrace();
	        }
			
			Message msgRecv2	= (Message)SendMsgAndReceiveReply(msg, mCurNode.mNodeArr.get(mCurNode.mR1ID).mPort, Utility.mTimeOut);
			if(msgRecv2 != null)
			{
				if(msgRecv2.mMsgType.equals("QuerySuccessful."))
					r++;
			}
			
			Message msgRecv3	= (Message)SendMsgAndReceiveReply(msg, mCurNode.mNodeArr.get(mCurNode.mR2ID).mPort, Utility.mTimeOut);
			if(msgRecv3 != null)
			{
				if(msgRecv3.mMsgType.equals("QuerySuccessful."))
					r++;
			}
			
			if(r >= Utility.mQ_R) {
				if( msgRecv1.mMsgData.equals(msgRecv2.mMsgData) || 
					msgRecv2.mMsgData.equals(msgRecv3.mMsgData) ||
					msgRecv3.mMsgData.equals(msgRecv1.mMsgData) ) {
					
					msg.mMsgType	= "QuerySuccessful.";
					
					if(msgRecv1.mMsgData.equals(msgRecv2.mMsgData))
						msg.mMsgData	= msgRecv1.mMsgData;
					else
						msg.mMsgData	= msgRecv2.mMsgData;
					
					SendMsgOverSocket(socket, msg);
					return;
				}
			}
			
			msg.mMsgType	= "QueryFailed.";
			SendMsgOverSocket(socket, msg);	
		}
		
		void ReplyQueryKeyValuePair(Socket socket, Message msgObj) {
			/*
			 * retrieve key from its data base
			 * send QuerySuccessful. on success
			 * and QueryFailure. on failure
			 */
			Cursor mCursor	= null;
			try
	        {
				mCursor =  mDb.rawQuery( "select rowid _id,* from chatHistory where " 
	                                     + COL_MSG_KEY + "=" + msgObj.mMsgID, null);
	            if (mCursor != null && mCursor.moveToFirst() != false) 
	            {
	            	Message tmp		= new Message();
	    			tmp.mMsgType	= "QuerySuccessful.";
	    			tmp.mMsgID		= msgObj.mMsgID;
	    			tmp.mMsgData	= mCursor.getString(mCursor.getColumnIndex(COL_MSG_VAL));
	    			
	    			SendMsgOverSocket(socket, tmp);
	    			return;
	            }
	            else {
	            	Message tmp		= new Message();
	    			tmp.mMsgType	= "QueryFailed.";
	    			tmp.mMsgID		= msgObj.mMsgID;
	    			SendMsgOverSocket(socket, tmp);
	    			return;
	            }
	        }
	        catch(Exception e)
	        {
	        	e.printStackTrace();
	        }
		}
	}
}
