package com.MsgAppV2;

/*
 * Acts as default screen.
 * "Connect Button"
 * Passes control to second - chat activity
 */

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class MsgAppV2Activity extends Activity {
    /** Called when the activity is first created. */
	private Button mConConnect		= null;
	private EditText mNoOfEmu		= null;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        mNoOfEmu	= (EditText) findViewById(R.id.editNoOfDev);
        
        mConConnect = (Button) findViewById(R.id.buttonConnect);
        mConConnect.setOnClickListener(new View.OnClickListener() {
        	/*
        	 * When Connect is clicked, simply call next activity        	 
        	 */
			@Override
			public void onClick(View v) {
				if(mNoOfEmu.getText().toString() != "")
					Utility.mNoOfEmulators = Integer.parseInt(mNoOfEmu.getText().toString());
				else 
					Utility.mNoOfEmulators	= 0;
                Intent i = new Intent(v.getContext(), ChatActivity.class);
    	        startActivity(i);
			}
        });
    }
}