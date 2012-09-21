package edu.buffalo.cse.cse486_586.simpledht;

import java.util.ArrayList;

import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

/*
 * This will act as main activity
 * Contains objects for connection and communication module
 * "Test1" and "Test2" buttons and message viewer is present here
 */

public class ChatActivity extends ListActivity{
	ArrayList<String> msgList	= new ArrayList<String>();
	ArrayAdapter<String> adptr 	= null;
	
	public static final int DELETE_ID = Menu.FIRST;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			setContentView(R.layout.chat);
			
			msgList.add("DebugMessage");
			adptr 				= new ArrayAdapter<String>(this, R.layout.message, R.id.textMessage, msgList);
			ListView listView 	= (ListView) getListView();
			listView.setAdapter(adptr);
			
			Button test1Button 	= (Button) findViewById(R.id.buttonTest1);
			test1Button.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					Test1Task task	= new Test1Task();
					task.execute();
				}
			});
			
			Button test2Button 	= (Button) findViewById(R.id.buttonTest2);
			test2Button.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					ContentResolver ctntRslv	= getContentResolver();
					Cursor 	myCursor			= ctntRslv.query(Utility.mUrlUri, null, null, null, null);
					UpdateUI(myCursor);
					myCursor.close();
				}
			});
		}
		catch (Exception e) {
			Log.i(Utility.mException, "ChatActivity:onCreate " + e.toString());
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
	        		UpdateUI(null);
	        		return true;
	        }
		}
		catch (Exception e) {
			Log.i(Utility.mException, "ChatActivity:onOptionsItemSelected " + e.toString());
		}
        
        return super.onMenuItemSelected(featureId, item);
    }
	
	public class Test1Task extends AsyncTask<Void, Cursor, Void> {

		@Override
		protected Void doInBackground(Void... params) {
			Log.i(Utility.mMsgLog, "Test1Task:doInBackground called.");
			/*
			 * 1-second delay
			 * Insert <”0”, “Test0”>
			 * Increment the sequence number and repeat the above insertion with 
			 * the 1-second delay until <”9”, “Test9”>.
			 */
			try {
				for(int i=0 ; i<10 ; i++) {
					Thread.sleep(1000);
					ContentResolver ctntRslv	= getContentResolver();
					ContentValues 	ctntVal 	= new ContentValues();
					ctntVal.put("provider_key", "" + i);
					ctntVal.put("provider_value", "Test" + i);
					ctntRslv.insert(Utility.mUrlUri, ctntVal);
				}
			} catch (Exception e) {
				Log.i(Utility.mException, "UpdateTask:doInBackground insert failed. " + e.toString());
				e.getStackTrace();
			}
			
			/*
			 * 1-second delay
			 * Query <”0”, “Test0”>
			 * Display “<0, Test0>”
			 * Increment the sequence number and repeat the query & 
			 * display with the 1-second delay until <”9”, “Test9”>.
			 */
			try {
				for(int i=0 ; i<10 ; i++) {
					Thread.sleep(1000);
					ContentResolver ctntRslv	= getContentResolver();
					Cursor 	myCursor			= ctntRslv.query(Utility.mUrlUri, null, Integer.toString(i), null, null);
					publishProgress(myCursor);
				}
			} catch (Exception e) {
				Log.i(Utility.mException, "UpdateTask:doInBackground query failed. " + e.toString());
				e.getStackTrace();
			}
			
			return null;
		}

		@Override
		protected void onProgressUpdate(Cursor... myCursor) {
			super.onProgressUpdate(myCursor);
			Log.i(Utility.mMsgLog, "Test1Task:onProgressUpdate called.");
			UpdateUI(myCursor[0]);
		}
	}
	
	private void UpdateUI(Cursor msgCursor) {
		Log.i(Utility.mMsgLog, "UpdateUI called.");
		
		if(msgCursor == null) {
			adptr.clear();
		}
		else {
			// extract messages from cursor and add to msgList
			msgCursor.moveToFirst();
	        while (msgCursor.isAfterLast() == false) {
	        	adptr.insert(msgCursor.getString(1) + ":" + msgCursor.getString(2), 0);
	            msgCursor.moveToNext();
	        }
	        msgCursor.close();
		}
    }
}
