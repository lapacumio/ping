package com.thesis.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import android.support.v7.app.ActionBarActivity;
import android.telephony.SmsManager;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

public class MainActivity extends ActionBarActivity {
	
	// Debugging
	private static final boolean DEBUG = true;
	private static final String TAG = "Main Activity";
	
	private static final int SETTINGS_REQUEST = 0;
	private static final int ADD_SMS_CONTACT_REQUEST = 1;
	private static final int CONNECT_BT_DEVICE_REQUEST = 2;

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    //public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
	
	private static final int MAX_MESSAGE_SIZE = 160;
	private static int TRIALS_NUM = 30;
	private static boolean isStart;
	private static boolean isEnd;
	private static int sentMsgIndex;
	
	private Button pingBtn;
	private Button resetBtn;
	private ListView resultLV;
	SmsManager smsManager;
	SMSReceiver BR_smsreceiver;
	
    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services
    private BluetoothChatService mChatService = null;
	private static ArrayAdapter<String> mResultArrayAdapter;
	private static ArrayList<String> smsContacts;
	
	// Variable for computing transmission rate
	private HashMap<Integer, Long> timeSent;
	private HashMap<Integer, Long> timeReceived;
	private HashMap<Integer, Long> RTT;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		isStart = false;
		isEnd = false;
		smsContacts = new ArrayList<String>();
		timeSent = new HashMap<Integer,Long>();
		timeReceived = new HashMap<Integer,Long>();
		RTT = new HashMap<Integer,Long>();
		sentMsgIndex = 0;
		
		pingBtn = (Button) findViewById(R.id.pingBtn);
		pingBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View view){
				ping();
			}
		});
		resetBtn = (Button) findViewById(R.id.clearBtn2);
		resetBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View view){
				reset();
			}
		});
		resultLV = (ListView) findViewById(R.id.resultLV);
		mResultArrayAdapter = new ArrayAdapter<String>(this, R.layout.result);
		resultLV.setAdapter(mResultArrayAdapter);
		
		// Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
        }
        
        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(this, mHandler);
        
        // setup SMS broadcast receiver
        BR_smsreceiver = null;
        BR_smsreceiver = new SMSReceiver();
        BR_smsreceiver.setMainActivityHandler(this);
        IntentFilter fltr_smsreceived = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
        fltr_smsreceived.setPriority(999);
        registerReceiver(BR_smsreceiver,fltr_smsreceived); 
        
        smsManager = SmsManager.getDefault();
	}
	
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatService != null) mChatService.stop();
        unregisterReceiver(BR_smsreceiver);
    }
	
    private void computeResults() {
    	double sum=0;
    	int receivedPackets =0;
    	for(int i=0; i<TRIALS_NUM; i++){
    		try{ 
    			RTT.put(i, timeReceived.get(i)-timeSent.get(i));
    			sum = sum+RTT.get(i);
    			receivedPackets++;
    		}
    		catch(Exception e){}
    	}
    	double aveRTT = sum/receivedPackets;
    	double packetLoss = (1-(((double)receivedPackets)/TRIALS_NUM))*100;
    	//int min = TODO
    	//int max = TODO
    	
    	//TODO output result
    	mResultArrayAdapter.add("average RTT: "+String.valueOf(aveRTT));
    	mResultArrayAdapter.add("packet loss: "+String.valueOf(packetLoss)+"%");
    }
    
    private void ping() {		
		if(isStart && sentMsgIndex>=TRIALS_NUM){
			computeResults();
			return;
		}
		else{
			// TODO send()
			String header = Integer.toString(sentMsgIndex) + 'x';
			char[] arr = new char[MAX_MESSAGE_SIZE-header.length()];
			Arrays.fill(arr, '0');
			String message = header + new String(arr);
			
			sendViaBT(message); // temp
			sendViaSMS(message); // temp
			long time = System.currentTimeMillis();
			timeSent.put(sentMsgIndex, time);
			mResultArrayAdapter.add("Sent "+Integer.toString(sentMsgIndex)+": "+time); // temp
			mResultArrayAdapter.add("Sent "+message); // temp
			sentMsgIndex++;
		}
	}
    
    private void receive(String message) {
    	if(isStart){
	    	long time = System.currentTimeMillis();
	    	if(DEBUG) Log.i(TAG, message);
	    	String stringMsgID = message.split("x")[0];
	    	if(DEBUG) Log.i(TAG, "Split");
	    	int msgID = Integer.parseInt(stringMsgID);
	    	if(DEBUG) Log.i(TAG, "saved ID");
	    	timeReceived.put(msgID, time);
	    	if(DEBUG) Log.i(TAG, "Recorded time");
	    	
	    	mResultArrayAdapter.add("Received "+msgID+": "+time); // temp
	    	
	    	ping(); //send the next iteration;
    	}
    	else if(isEnd){
    		// TODO send to source
    		sendViaBT(message); // temp
    		sendViaSMS(message); // temp
    	}
    	else{
    		// TODO forward
    		sendViaBT(message); // temp
    		sendViaSMS(message); // temp
		}
    }
    
    private void reset() {
		sentMsgIndex=0;
		timeSent.clear();
		timeReceived.clear();
		RTT.clear();
		mResultArrayAdapter.clear();
	}
    
	/* sending via SMS */
	private void sendViaSMS(String message){
		try {
			for(String phoneNum : smsContacts){
				//SmsManager smsManager = SmsManager.getDefault();
				smsManager.sendTextMessage(phoneNum, null, message, null, null);
				Toast.makeText(getApplicationContext(), "Your sms has successfully sent!",
						Toast.LENGTH_SHORT).show();
			}
		} catch (Exception ex) {
			Toast.makeText(getApplicationContext(),"Your sms has failed...",
					Toast.LENGTH_SHORT).show();
			ex.printStackTrace();
		}
	}
	
	/* receiving via SMS */
	public void receiveSMS(String source, String message){
    	//TODO what to do with this
		receive(message);
	}
	
	/* sending via BT */
	private void sendViaBT(String message){
		byte[] send = message.getBytes();
	    mChatService.write(send);
	}
	
    // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                if(DEBUG) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case BluetoothChatService.STATE_CONNECTED:
                	//titleTV.setText("Connected to");
                    //titleTV.append(mConnectedDeviceName);
                    //mConversationArrayAdapter.clear();
                	Toast.makeText(getApplicationContext(), "Connected",
                            Toast.LENGTH_SHORT).show();
                    break;
                case BluetoothChatService.STATE_CONNECTING:
                	//titleTV.setText("Connecting");
                	Toast.makeText(getApplicationContext(), "Connecting",
                            Toast.LENGTH_SHORT).show();
                	break;
                case BluetoothChatService.STATE_LISTEN:
                case BluetoothChatService.STATE_NONE:
                	Toast.makeText(getApplicationContext(), "State:  none",
                            Toast.LENGTH_SHORT).show();
                	//titleTV.setText("Not connected");
                    break;
                }
                break;
            /*case MESSAGE_WRITE:
                byte[] writeBuf = (byte[]) msg.obj;
                // construct a string from the buffer
                String writeMessage = new String(writeBuf);
                //mConversationArrayAdapter.add("Me:  " + writeMessage);
                break;*/
            case MESSAGE_READ:
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                if (readMessage.length() > 0) {
                	//TODO do something with received message
                	receive(readMessage);
                }
                break;
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case MESSAGE_TOAST:
            	//if (!msg.getData().getString(TOAST).contains("Unable to connect device")) {
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                            Toast.LENGTH_SHORT).show();            		
            	//}
                break;
            }
        }
    };

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		switch(item.getItemId()){
			case(R.id.action_settings):
				startSettingsActivity();
				return true;
			case(R.id.add_sms_contact):
				startAddSMSContactActivity();
				return true;
	        case R.id.scan_for_bt_devices:
	            startBTDeviceListActivity();
	            return true;
		}
		return super.onOptionsItemSelected(item);
	}


	private void startSettingsActivity() {
		Intent i = new Intent(getApplicationContext(), SettingsActivity.class);
		i.putExtra("isStart", isStart);
		i.putExtra("isEnd", isEnd);
		//i.putExtra("Trials", TRIALS_NUM);
		startActivityForResult(i, SETTINGS_REQUEST);
	}
	private void startAddSMSContactActivity() {
		Intent i = new Intent(getApplicationContext(), AddSMSContactActivity.class);
		i.putStringArrayListExtra("smsContacts", smsContacts);
		startActivityForResult(i, ADD_SMS_CONTACT_REQUEST);
	}
	private void startBTDeviceListActivity() {
    	Intent serverIntent = new Intent(this, BTDeviceListActivity.class);
        startActivityForResult(serverIntent, CONNECT_BT_DEVICE_REQUEST);
	}
	
	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        switch(requestCode){
        case(SETTINGS_REQUEST):
        	if(resultCode == RESULT_OK){
	    		isStart = intent.getBooleanExtra("isStart", false);
	    		isEnd = intent.getBooleanExtra("isEnd", false);
    		}
        	else if (resultCode==RESULT_CANCELED){ /* TODO log that there was error*/ }
	        break;
        case(ADD_SMS_CONTACT_REQUEST):
        	if(resultCode == RESULT_OK){
        		smsContacts = intent.getStringArrayListExtra("smsContacts");
        	}
        	else if (resultCode==RESULT_CANCELED){ /* TODO log that there was error*/ }
        	break;
		case(CONNECT_BT_DEVICE_REQUEST):
	        // When DeviceListActivity returns with a device to connect
	        if (resultCode == Activity.RESULT_OK) {
	            // Get the device MAC address
	            String address = intent.getExtras()
	                                 .getString(BTDeviceListActivity.EXTRA_DEVICE_ADDRESS);
	            // Get the BLuetoothDevice object
	            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
	            // Attempt to connect to the device
	            mChatService.connect(device);
	        }
	        break;
        }
    }
}
