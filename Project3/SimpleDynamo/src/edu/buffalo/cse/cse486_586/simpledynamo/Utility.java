package edu.buffalo.cse.cse486_586.simpledynamo;

import java.security.MessageDigest;
import java.util.Formatter;

import android.net.Uri;
import android.util.Log;

public class Utility {
	public static String mMsgLog 	= "Message Log";
	public static String mException = "Exception Catched";
	public static String mUrlStr	= "content://edu.buffalo.cse.cse486_586.simpledynamo.provider";
	public static Uri mUrlUri		= Uri.parse(mUrlStr);
	public static String mIP		= "10.0.2.2";
	public static int mStrtNodePrt	= 11108;
	public static int mNoOfApps		= 5;
	public static int mQ_N			= 3;
	public static int mQ_R			= 2;
	public static int mQ_W			= 2;
	public static int mTimeOut		= 6000;
	
	public static String genHash(String input) {
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
}
