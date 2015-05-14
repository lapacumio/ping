package com.thesis.test;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

public class SMSReceiver extends BroadcastReceiver {
	
	MainActivity main = null;
	
	void setMainActivityHandler(MainActivity main){
	    this.main=main;
	}
	
	public void onReceive(Context context, Intent intent)
	{
		Bundle bundle=intent.getExtras();
		
		Object[] messages=(Object[])bundle.get("pdus");
		SmsMessage[] sms=new SmsMessage[messages.length];
		
		for(int n=0;n<messages.length;n++){
			sms[n]=SmsMessage.createFromPdu((byte[]) messages[n]);
		}
		
		String message="";
		String source;
		for(SmsMessage msg:sms){
			message = msg.getMessageBody();
			source = msg.getOriginatingAddress();
			main.receiveSMS(source, message);
		}
	}
}
