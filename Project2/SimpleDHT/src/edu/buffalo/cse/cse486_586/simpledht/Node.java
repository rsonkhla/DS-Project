package edu.buffalo.cse.cse486_586.simpledht;


public class Node {
	 public int mMngrNodePort		= -1;
	 public int mPrevNodePort		= -1;
	 public String mPrevNodeHash	= null;
	 public int mCurrNodePort		= -1;
	 public String mCurrNodeHash	= null;
	 public int mNextNodePort		= -1;
	 public String mNextNodeHash	= null;
	 
	 public String Encode() {
		 return mMngrNodePort+"><"+mPrevNodePort+"><"+mPrevNodeHash+"><"+mCurrNodePort+"><"+mCurrNodeHash+"><"+mNextNodePort+"><"+mNextNodeHash;
	 }
	 
	 public Node Decode(String str) {
		 Node node	= new Node();
		 String[] tokens	= str.split("><");
		 
		 	mMngrNodePort	= Integer.parseInt(tokens[0]);
		 	mPrevNodePort	= Integer.parseInt(tokens[1]);
		 	mPrevNodeHash	= tokens[2];
		 	mCurrNodePort	= Integer.parseInt(tokens[3]);
		 	mCurrNodeHash	= tokens[4];
		 	mNextNodePort	= Integer.parseInt(tokens[5]);
		 	mNextNodeHash	= tokens[6];
		 	
		 return node;
	 }
}
