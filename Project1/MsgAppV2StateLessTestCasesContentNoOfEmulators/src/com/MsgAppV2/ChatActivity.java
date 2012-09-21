package com.MsgAppV2;

import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SimpleCursorAdapter;

/*
 * This will act as main activity
 * Contains objects for connection and communication module
 * "Test1" and "Test2" buttons and message viewer is present here
 */

public class ChatActivity extends ListActivity{
	private String mAppIP	= null;
	private int mAppPortNo	= 0;
	private EditText 		mNewMessage;
	private Cursor 			mMsgCursor;
	private UpdateTask  	mTask;
	//private MsgDbAdapter	mChatHistory;
	private MsgHndlngModule mMsgHndlngModule;
	private CommModule 		mCommModule;
	
	public static final int DELETE_ID = Menu.FIRST;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			setContentView(R.layout.chat);
			
			//mChatHistory		= new MsgDbAdapter(this);
			TelephonyManager tel= (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
			String portStr 		= tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
			
			mAppIP				= "10.0.2.2";
			mAppPortNo			= Integer.parseInt(portStr)*2;
			
			mMsgHndlngModule	= new MsgHndlngModule(mAppIP, mAppPortNo);
			mMsgHndlngModule.Start();
			mCommModule			= new CommModule(mAppIP, mAppPortNo);
			mCommModule.Start();
	
			mNewMessage			= (EditText) findViewById(R.id.editStrMessage);
			Button sendButton 	= (Button) findViewById(R.id.buttonSend);
			
			UpdateUI();
			mTask	= new UpdateTask();
			mTask.execute();
			
			sendButton.setOnClickListener(new View.OnClickListener() {
				
				public void onClick(View v) {
					if(!mNewMessage.getText().toString().equals("")) {
						Log.i(Utility.mMsgLog, "Message Processes : " + mNewMessage.getText().toString());

						// send message
						try {
							synchronized(MsgPasser.GetInstance().mActivityToMsgHandler) {
								Log.i(Utility.mDebugInfo, "ChatActivity: Sending message from Activity to MsgHandler. " + mNewMessage.getText().toString());
								MsgPasser.GetInstance().mActivityToMsgHandler.add(mNewMessage.getText().toString());
								MsgPasser.GetInstance().mActivityToMsgHandler.notify();
							}
						} catch (Exception e) {
							Log.i(Utility.mException, "ChatActivity: Activity to MsgHandler message sending failed. " + e.toString());
						}
						
						mNewMessage.setText("");
					}
				}
			});
			
			Button test1Button 	= (Button) findViewById(R.id.buttonTest1);
			test1Button.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					try {
						synchronized(MsgPasser.GetInstance().mActivityToMsgHandler) {
							Log.i(Utility.mDebugInfo, "ChatActivity: Sending Test1 message from activity to msgHandler.");
							MsgPasser.GetInstance().mActivityToMsgHandler.add("Test-Message-1");
							MsgPasser.GetInstance().mActivityToMsgHandler.notify();
						}
					} catch (Exception e) {
						Log.i(Utility.mException, "ChatActivity: Activity to MsgHandler message sending failed. " + e.toString());
					}
				}
			});
			
			Button test2Button 	= (Button) findViewById(R.id.buttonTest2);
			test2Button.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					try {
						synchronized(MsgPasser.GetInstance().mActivityToMsgHandler) {
							Log.i(Utility.mDebugInfo, "ChatActivity: Sending Test1 message from activity to msgHandler.");
							MsgPasser.GetInstance().mActivityToMsgHandler.add("Test-Message-2");
							MsgPasser.GetInstance().mActivityToMsgHandler.notify();
						}
					} catch (Exception e) {
						Log.i(Utility.mException, "ChatActivity: Activity to MsgHandler message sending failed. " + e.toString());
					}
				}
			});
		}
		catch (Exception e) {
			Log.i(Utility.mException, "CatchActivity:onCreate " + e.toString());
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		try {
			boolean result	= super.onCreateOptionsMenu(menu);
			menu.add(0, DELETE_ID, 0, R.string.strButtonDelete);
			return result;
		}
	    catch (Exception e) {
	    	Log.i(Utility.mException, "ChatActivity:onCreateOptionsMenu " + e.toString());
	    	return false;
	    }
	}
	
	@Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
		try {
			switch (item.getItemId()) {
	        	case DELETE_ID:
	        		//mChatHistory.deleteAllMessages();
	        		MsgDbAdapter.deleteAllMessages();
	        		UpdateUI();
	        		return true;
	        }
		}
		catch (Exception e) {
			Log.i(Utility.mException, "ChatActivity:onOptionsItemSelected " + e.toString());
		}
        
        return super.onMenuItemSelected(featureId, item);
    }
	
	@Override
	protected void onStop() {
		super.onStop();
		mTask.cancel(true);
		mCommModule.Stop();
		super.onDestroy();
	}
	
	public class UpdateTask extends AsyncTask<Void, Void, Void> {

		@Override
		protected Void doInBackground(Void... params) {
			try {
				Log.i(Utility.mMsgLog, "UpdateTask:doInBackground called.");
				String input=new String();
				Message msg	= new Message();
				while(true) {
					synchronized(MsgPasser.GetInstance().mMsgHandlerToActivity) {
						//MsgPasser.GetInstance().mMsgHandlerToActivity.wait(); 
						// was getting some Interrupted exception

						input	= MsgPasser.GetInstance().mMsgHandlerToActivity.poll();
					}
					if(input != null) {
						Log.i(Utility.mDebugInfo, "ChatActivity: Receiving message from MsgHandler to Activity. " + input);
						msg.DecodeString(input);
						//mChatHistory.addMessage(msg);
						addMessage(msg);
						publishProgress();
					}
					
					// hence sleeping thread for some time
					Thread.sleep(50);
				}
			}
			catch (Exception e) {
				Log.i(Utility.mException, "UpdateTask:doInBackground " + e.toString());
				e.getStackTrace();
			}
			return null;
		}

		@Override
		protected void onProgressUpdate(Void... values) {
			super.onProgressUpdate(values);
			Log.i(Utility.mMsgLog, "UpdateTask:UI updated : ");
			UpdateUI();
		}
	}
	
	private void UpdateUI() {
		try {
			// Get all of the notes from the database and create the item list
			//mMsgCursor = mChatHistory.fetchAllMessages();
			mMsgCursor = fetchAllMessages();
			startManagingCursor(mMsgCursor);

	        String[] from = new String[] { MsgDbAdapter.COL_MSG_VAL };
	        int[] to = new int[] { R.id.textMessage };
	        
	        // Now create an array adapter and set it to display using our row
	        SimpleCursorAdapter connections =
	            new SimpleCursorAdapter(this, R.layout.message, mMsgCursor, from, to);
	        setListAdapter(connections);
		}
		catch (Exception e) {
			Log.i(Utility.mException, "CatchActivity:UpdateUI " + e.toString());
		}
    }
	
	private Cursor fetchAllMessages() {
		ContentResolver ctntRslv	= getContentResolver();
		Cursor 	msgCursor			= ctntRslv.query(Uri.parse("content://edu.buffalo.cse.cse486_586.provider"), null, null, null, null);
		if(msgCursor != null) {
			msgCursor.moveToFirst();
		}
		return msgCursor;
	}
	
	public void addMessage(Message msg) {
		
		ContentResolver ctrsv = getContentResolver();
    	ContentValues val = new ContentValues();
    	val.put("provider_key", "" + msg.mOrderData);
    	val.put("provider_value", msg.mMsgData);
    	ctrsv.insert(Uri.parse("content://edu.buffalo.cse.cse486_586.provider"), val );
    }
}
