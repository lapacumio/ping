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
import android.widget.Toast;

public class SettingsActivity extends ActionBarActivity {

	private boolean isStart;
	private boolean isEnd;
	
	//Channel 1 Radiogroup
	//Channel 2
	//Channel 3
	private Button saveBtn;
	private CheckBox senderCB;
	private CheckBox receiverCB;
	private EditText trialsET;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);
		
		//get data from main
		Intent intent = getIntent();
		isStart = intent.getBooleanExtra("isStart", false);
		isEnd = intent.getBooleanExtra("isEnd", false);
		
		//TODO create view items
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
