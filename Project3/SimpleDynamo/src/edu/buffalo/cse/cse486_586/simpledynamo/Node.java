package edu.buffalo.cse.cse486_586.simpledynamo;

import java.util.ArrayList;
import java.util.Collections;

public class Node {
	public class SingleNode implements Comparable<SingleNode>{
		public int mPort	= -1;
		public String mHash	= null;
		
		SingleNode(int port) {
			mPort	= port;
			mHash	= Utility.genHash(Integer.toString(mPort/2));
		}

		public int compareTo(SingleNode another) {
			return mHash.compareTo(another.mHash);
		}	
	}	
	
	public ArrayList<SingleNode> mNodeArr	= new ArrayList<Node.SingleNode>(); 
	public int mP2ID	= -1;
	public int mP1ID	= -1;
	public int mC_ID	= -1;
	public int mR1ID	= -1;
	public int mR2ID	= -1;
	
	Node(int port) {
		int strtPort	= Utility.mStrtNodePrt;
		
		for(int i=0 ; i< Utility.mNoOfApps ; i++) {
			mNodeArr.add(new SingleNode(strtPort));
			strtPort	+= 4;
		}
		
		Collections.sort(mNodeArr);
		
		mC_ID	= WhoIsTheOwnerOfThisKey(Integer.toString(port/2));
		mP1ID	= getPrvID(mC_ID);
		mP2ID	= getPrvID(mP1ID);
		mR1ID	= getNxtID(mC_ID);
		mR2ID	= getNxtID(mR1ID);
	}
	
	int WhoIsTheOwnerOfThisKey(String key) {
		String hash	= Utility.genHash(key);
		int i;
		for(i=0 ; i< Utility.mNoOfApps ; i++) {
			if(hash.compareTo(mNodeArr.get(i).mHash) <= 0) {
				return i;
			}
		}
		if(i == Utility.mNoOfApps)
			return 0;
		else
			return -1;
	}
	
	int getNxtID(int id) {
		return (id+1) % Utility.mNoOfApps;
	}
	
	int getPrvID(int id) {
		return (id-1+Utility.mNoOfApps) % Utility.mNoOfApps;
	}
}
