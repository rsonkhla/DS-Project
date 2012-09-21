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
package com.MsgAppV2;

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
import android.util.Log;

public class MsgDbAdapter extends ContentProvider{

	private static final String DATABASE_NAME 	= "data.db";
    private static final String DATABASE_TABLE 	= "chatHistory";
    public static final String COL_MSG_KEY 		= "provider_key";
    public static final String COL_MSG_VAL 		= "provider_value";

    private static final String DATABASE_CREATE = "create table " + DATABASE_TABLE + " (" + COL_MSG_KEY + " text not null , " + COL_MSG_VAL + " text not null);";
    private static final int DATABASE_VERSION 	= 2;
    
    private DatabaseHelper mDbHelper;
    static private SQLiteDatabase mDb;
    private static final UriMatcher mUriMatcher;
    
    static 
    {
    	mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    	mUriMatcher.addURI("edu.buffalo.cse.cse486_586.provider", null, 1);
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
        	Log.i(Utility.mDebugInfo, "Creating Database.");
            db.execSQL(DATABASE_CREATE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.i(Utility.mDebugInfo, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("drop table if exists " + DATABASE_TABLE);
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
        	return true;
        }
	}
	
	@Override
	public Uri insert(Uri uri, ContentValues initialValues) {
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
        
        try
        {
        	long rowId = mDb.insert(DATABASE_TABLE, null, values);
            if (rowId > 0) 
            {
            	Uri noteUri = ContentUris.withAppendedId(Uri.parse("content://edu.buffalo.cse.cse486_586.provider"), rowId);
                getContext().getContentResolver().notifyChange(noteUri, null);
                return noteUri;
            }
        }
        catch(SQLException e)
        {
            e.printStackTrace();
        }
 
        throw new SQLException("Failed to insert row into " + uri);
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		if (mUriMatcher.match(uri) != 1) 
        { 
            throw new IllegalArgumentException("Unknown URI " + uri); 
        }
		Cursor mCursor = null;
    	if(selection != null)
        {
    		try
            {
    			mCursor =  mDb.rawQuery( "select rowid _id,* from chatHistory where " 
                                         + COL_MSG_KEY + "=" + selection, null);
                if (mCursor != null) 
                {
                	mCursor.moveToFirst();
                }
            }
            catch(Exception e)
            {
            	e.printStackTrace();
            }
        }
        else
        {
            try
            {
            	mCursor =  mDb.rawQuery( "select rowid _id,* from chatHistory order by " 
                                          + "rowid" + " desc", null);
            }
            catch(Exception e)
            {
                        e.printStackTrace();
            }
        }
        mCursor.setNotificationUri(getContext().getContentResolver(), uri);
        return mCursor;
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
			mDb.delete(DATABASE_TABLE, null, null);
        }
        catch(Exception e)
        {
        	e.printStackTrace();
        }
    }
}
