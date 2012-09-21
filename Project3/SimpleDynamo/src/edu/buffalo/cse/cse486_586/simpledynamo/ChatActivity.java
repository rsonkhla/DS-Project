package edu.buffalo.cse.cse486_586.simpledynamo;

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
	
	public static final int CLR_SCRN_ID 		= Menu.FIRST;
	public static final int CLR_DB_N_SCRN_ID 	= Menu.FIRST + 1;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		try {
			super.onCreate(savedInstanceState);
			setContentView(R.layout.chat);
			
			msgList.add("DebugMessage");
			adptr 				= new ArrayAdapter<String>(this, R.layout.message, R.id.textMessage, msgList);
			ListView listView 	= (ListView) getListView();
			listView.setAdapter(adptr);
			
			Button put1Button 	= (Button) findViewById(R.id.buttonPut1);
			put1Button.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					PutTask task	= new PutTask("Put1");
					task.execute();
				}
			});
			
			Button put2Button 	= (Button) findViewById(R.id.buttonPut2);
			put2Button.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					PutTask task	= new PutTask("Put2");
					task.execute();
				}
			});
			
			Button put3Button 	= (Button) findViewById(R.id.buttonPut3);
			put3Button.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					PutTask task	= new PutTask("Put3");
					task.execute();
				}
			});
			
			Button getButton 	= (Button) findViewById(R.id.buttonGet);
			getButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					GetTask task	= new GetTask();
					task.execute();
				}
			});
			
			Button dumpButton 	= (Button) findViewById(R.id.buttonDump);
			dumpButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					ContentResolver ctntRslv	= getContentResolver();
					Cursor 	myCursor			= ctntRslv.query(Utility.mUrlUri, null, null, null, null);
					UpdateUI(myCursor);
					//myCursor.close();
				}
			});
		}
		catch (Exception e) {
			Log.i(Utility.mException, "ChatActivity:onCreate " + e.toString());
			e.printStackTrace();
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		try {
			boolean result	= super.onCreateOptionsMenu(menu);
			menu.add(0, CLR_SCRN_ID, 0, R.string.strButtonClrScrn);
			menu.add(0, CLR_DB_N_SCRN_ID, 0, R.string.strButtonClrDbNScrn);
			return result;
		}
	    catch (Exception e) {
	    	Log.i(Utility.mException, "ChatActivity:onCreateOptionsMenu " + e.toString());
	    	e.printStackTrace();
	    	return false;
	    }
	}
	
	@Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
		try {
			switch (item.getItemId()) {
	        	case CLR_SCRN_ID:
	        		adptr.clear();
	        		UpdateUI(null);
	        		return true;
	        	case CLR_DB_N_SCRN_ID:
	        		MsgDbAdapter.deleteAllMessages();
	        		adptr.clear();
	        		UpdateUI(null);
	        		return true;
	        }
		}
		catch (Exception e) {
			Log.i(Utility.mException, "ChatActivity:onOptionsItemSelected " + e.toString());
			e.printStackTrace();
		}
        
        return super.onMenuItemSelected(featureId, item);
    }
	
	public class PutTask extends AsyncTask<Void, Cursor, Void> {

		String putName;
		
		public PutTask(String string) {
			putName	= string;
		}

		@Override
		protected Void doInBackground(Void... params) {
			Log.i(Utility.mMsgLog, "PutTask:doInBackground called for " + putName + ".");
			/*
			 * 3-second delay
			 * Insert <�0�, �putName0�>
			 * Increment the sequence number and repeat the above insertion with 
			 * the 3-second delay until <�9�, �putName9�>.
			 */
			try {
				for(int i=0 ; i<10 ; i++) {
					Thread.sleep(3000);
					ContentResolver ctntRslv	= getContentResolver();
					ContentValues 	ctntVal 	= new ContentValues();
					ctntVal.put("provider_key", "" + i);
					ctntVal.put("provider_value", putName + i);
					ctntRslv.insert(Utility.mUrlUri, ctntVal);
				}
			} catch (Exception e) {
				Log.i(Utility.mException, "PutTask:doInBackground insert failed for " + putName + ". " + e.toString());
				e.getStackTrace();
			}
	
			return null;
		}
	}
	
	public class GetTask extends AsyncTask<Void, Cursor, Void> {

		@Override
		protected Void doInBackground(Void... params) {
			Log.i(Utility.mMsgLog, "GetTask:doInBackground called.");
			
			/*
			 * 1-second delay
			 * Query <�0�, �Test0�>
			 * Display �<0, Test0>�
			 * Increment the sequence number and repeat the query & 
			 * display with the 1-second delay until <�9�, �Test9�>.
			 */
			try {
				for(int i=0 ; i<10 ; i++) {
					Thread.sleep(3000);
					ContentResolver ctntRslv	= getContentResolver();
					Cursor 	myCursor			= ctntRslv.query(Utility.mUrlUri, null, Integer.toString(i), null, null);
					publishProgress(myCursor);
				}
			} catch (Exception e) {
				Log.i(Utility.mException, "GetTask:doInBackground query failed. " + e.toString());
				e.getStackTrace();
			}
			
			return null;
		}

		@Override
		protected void onProgressUpdate(Cursor... myCursor) {
			super.onProgressUpdate(myCursor);
			Log.i(Utility.mMsgLog, "GetTask:onProgressUpdate called.");
			UpdateUI(myCursor[0]);
		}
	}
	
	private void UpdateUI(Cursor msgCursor) {
		Log.i(Utility.mMsgLog, "UpdateUI called.");
		
		if(msgCursor == null || msgCursor.moveToFirst() == false) {
			//empty
		}
		else {
			// extract messages from cursor and add to msgList
			// msgCursor.moveToFirst();
	        while (msgCursor.isAfterLast() == false) {
	        	adptr.insert(msgCursor.getString(1) + ":" + msgCursor.getString(2), 0);
	            msgCursor.moveToNext();
	        }
	        msgCursor.close();
		}
    }
}
