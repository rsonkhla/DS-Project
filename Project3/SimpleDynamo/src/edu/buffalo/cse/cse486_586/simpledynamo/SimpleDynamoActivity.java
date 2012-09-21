package edu.buffalo.cse.cse486_586.simpledynamo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class SimpleDynamoActivity extends Activity {
	/** Called when the activity is first created. */
	private Button mConConnect		= null;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        mConConnect = (Button) findViewById(R.id.buttonConnect);
        mConConnect.setOnClickListener(new View.OnClickListener() {
        	/*
        	 * When Connect is clicked, simply call next activity        	 
        	 */
			public void onClick(View v) {
                Intent i = new Intent(v.getContext(), ChatActivity.class);
    	        startActivity(i);
			}
        });
    }
}