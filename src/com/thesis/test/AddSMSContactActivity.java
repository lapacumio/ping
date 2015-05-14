package com.thesis.test;

import java.util.ArrayList;

import android.support.v7.app.ActionBarActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

public class AddSMSContactActivity extends ActionBarActivity {

	EditText phoneNumET;
	EditText displayET;
	Button addBtn;
	Button saveBtn;
	Button clearBtn;
	
	ArrayList<String> savedPhoneNumbers;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_add_sms_contact);
		
		Intent intent = getIntent();
		
		phoneNumET = (EditText) findViewById(R.id.phoneNumET);
		displayET = (EditText) findViewById(R.id.savedContactsET);
		addBtn = (Button) findViewById(R.id.addBtn);
		saveBtn = (Button) findViewById(R.id.saveBtn2);
		clearBtn = (Button) findViewById(R.id.clearBtn);
		
		savedPhoneNumbers = intent.getStringArrayListExtra("smsContacts");
		
		addBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				saveContact();
				phoneNumET.setText("");
			}
		});
		saveBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				sendDataToMain();
				finish();
			}
		});
		clearBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				savedPhoneNumbers = new ArrayList<String>();
				displayET.setText("");
			}
		});
		
		for(String num : savedPhoneNumbers){
			displayET.append(num+'\n');
		}
		
	}
	
	private void saveContact() {
		String phoneNum = phoneNumET.getText().toString();
		savedPhoneNumbers.add(phoneNum);
		displayET.append(phoneNum+'\n');
	}
	
	private void sendDataToMain() {
		Intent i = new Intent();
		i.putStringArrayListExtra("smsContacts", savedPhoneNumbers);
		setResult(RESULT_OK,i);
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
