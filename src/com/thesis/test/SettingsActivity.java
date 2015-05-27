package com.thesis.test;

import android.support.v7.app.ActionBarActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.Toast;

public class SettingsActivity extends ActionBarActivity {

	private boolean isStart;
	private boolean isEnd;
	private int messageSize;
	
	//Channel 1 Radiogroup
	//Channel 2
	//Channel 3
	private RadioGroup channel1, channel2, channel3;
	private RadioButton naRadio1, btRadio1, smsRadio1, wifiRadio1;
	private RadioButton naRadio2, btRadio2, smsRadio2, wifiRadio2;
	private RadioButton naRadio3, btRadio3, smsRadio3, wifiRadio3;
	private Button saveBtn;
	private CheckBox senderCB;
	private CheckBox receiverCB;
	private EditText messageSizeET;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);
		
		//get data from main
		Intent intent = getIntent();
		isStart = intent.getBooleanExtra("isStart", false);
		isEnd = intent.getBooleanExtra("isEnd", false);
		messageSize = intent.getIntExtra("messageSize", 0);
		
		//TODO create view items
		/*channel1 = (RadioGroup) findViewById(R.id.channel1);
		channel1.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
				// find which radio button is selected
				if(checkedId == R.id.naRadio1) {}
				else if(checkedId == R.id.btRadio1) {}
				else if(checkedId == R.id.smsRadio1) {}
				else if(checkedId == R.id.wifiRadio1) {}
			}
        });

		channel2 = (RadioGroup) findViewById(R.id.channel2);
		channel2.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
				// find which radio button is selected
				if(checkedId == R.id.naRadio2) {}
				else if(checkedId == R.id.btRadio2) {}
				else if(checkedId == R.id.smsRadio2) {}
				else if(checkedId == R.id.wifiRadio2) {}
			}
        });
		
		channel3 = (RadioGroup) findViewById(R.id.channel3);
		channel3.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
				// find which radio button is selected
				if(checkedId == R.id.naRadio3) {}
				else if(checkedId == R.id.btRadio3) {}
				else if(checkedId == R.id.smsRadio3) {}
				else if(checkedId == R.id.wifiRadio3) {}
			}
        });*/
		
		messageSizeET = (EditText) findViewById(R.id.messageSize);
		messageSizeET.append(Integer.toString(messageSize));
		senderCB = (CheckBox) findViewById(R.id.senderCB);
		senderCB.setChecked(isStart);
		receiverCB = (CheckBox) findViewById(R.id.receiverCB);
		receiverCB.setChecked(isEnd);
		saveBtn = (Button) findViewById(R.id.saveBtn);
		saveBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View view){
				sendDataToMain();
				finish();
			}
		});
	}
	
	public void onCheckboxClicked(View view) {
	    boolean checked = ((CheckBox) view).isChecked();
		switch(view.getId()) {
        case(R.id.receiverCB):
            isEnd = checked;
            break;
	    case(R.id.senderCB):
	        isStart = checked;
	        break;
	    }
	}
	
	private void sendDataToMain() {
	 	Intent intent = new Intent();
	 	intent.putExtra("messageSize", Integer.parseInt(messageSizeET.getText().toString()));
		intent.putExtra("isStart", isStart);
		intent.putExtra("isEnd", isEnd);
		setResult(RESULT_OK,intent);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
