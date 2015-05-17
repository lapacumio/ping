package com.thesis.test;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import android.support.v7.app.ActionBarActivity;
import android.telephony.SmsManager;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.SimpleAdapter;
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
	
	private Button pingBtn, resetBtn;
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
	
	// WiFi Direct variables
	
	//Instantiate an IntentFilter and set it to listen for WifiP2pManager status changes
	private final IntentFilter intentFilter = new IntentFilter();
	private WifiP2pManager mManager;
	private Channel mChannel;
	private WiFiDirectBroadcastReceiver mReceiver;
	private boolean isWifiP2pEnabled = false;

	private WifiP2pDevice device;
	private String MsgReceived;
	private int MsgSource;
	private int ClientSum;
	private int ClientNum;
	//private boolean MsgWAPSent = true;
	
	//Thread and pipes for the client's data exchange
	private PipedOutputStream pout_transmit_client = null;
	private PipedInputStream pin_transmit_client = null;
	private PipedOutputStream pout_rcv_client = null;
	private PipedInputStream pin_rcv_client = null;
	private ClientThread clientthread;
	//Thread and pipes for the server's data exchange
	private PipedOutputStream pout_rcv_server = null;
	private PipedInputStream pin_rcv_server = null;
	private PipedOutputStream pout_transmit_server = null;
	private PipedInputStream pin_transmit_server = null;
	private ServerThread serverthread;
	
	private Timer timer = new Timer("Timer_Display",true);
	
	//For Listview
	public ArrayList<HashMap<String, Object>> listItem = new ArrayList<HashMap<String, Object>>();
	public SimpleAdapter listItemAdapter;
	
	//For message obtained from Internet
	public String MsgWAP = null;
	
	//
	
	
	// Variable for computing transmission rate
	private HashMap<Integer, Long> timeSent;
	private HashMap<Integer, Long> timeReceived;
	private SparseArray<Long> RTT;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		isStart = false;
		isEnd = false;
		smsContacts = new ArrayList<String>();
		timeSent = new HashMap<Integer,Long>();
		timeReceived = new HashMap<Integer,Long>();
		RTT = new SparseArray<Long>();
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
        
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
    
        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);

        /*
        //Initialize the adapter for the listview
        listItemAdapter = new SimpleAdapter(this,
            	listItem,R.layout.list_view,
            	new String[] {"ItemNumber", "ItemMessage"},
            	new int[] {R.id.ItemNumber,R.id.ItemMessage});
         */
        
        //Initialize and register the BroadcastReceiver
        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);
        registerReceiver(mReceiver, intentFilter);
        
	}
	
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatService != null) mChatService.stop();
        unregisterReceiver(BR_smsreceiver);
    }
	
    private void computeResults() {
    	double sum=0;
    	long rtt=0;
    	int receivedPackets =0;
    	for(int i=0; i<TRIALS_NUM; i++){
    		try{ 
    			rtt = RTT.get(i);
    			sum = sum+rtt;
    			receivedPackets++;
    		}
    		catch(Exception e){}
    	}
    	double aveRTT = sum/receivedPackets;
    	double packetLoss = (1-(((double)receivedPackets)/TRIALS_NUM))*100;
    	//int min = TODO
    	//int max = TODO
    	
    	mResultArrayAdapter.add("average RTT: "+String.valueOf(aveRTT));
    	mResultArrayAdapter.add("packet loss: "+String.valueOf(packetLoss)+"%");
    }
    
    private void ping() {		
		if(isStart && sentMsgIndex>=TRIALS_NUM){
			computeResults();
			return;
		}
		else{
			String header = Integer.toString(sentMsgIndex) + 'x';
			char[] arr = new char[MAX_MESSAGE_SIZE-header.length()];
			Arrays.fill(arr, '0');
			String message = header + new String(arr);
			
			forwardMsg(message, -1);
			long time = System.currentTimeMillis();
			timeSent.put(sentMsgIndex, time);
			mResultArrayAdapter.add("Sent "+Integer.toString(sentMsgIndex)+": "+time); // temp
			sentMsgIndex++;
		}
	}
    
    private void receive(String message, int source /*WiFi*/) {
    	if(isStart){ acceptMsg(message); }
    	else if(isEnd){ returnMsg(message, (Integer) source); }
    	else{ forwardMsg(message, (Integer) source); }
    }
    private void receive(String message, long source /*Bluetooth*/){
    	if(isStart){ acceptMsg(message); }
    	else if(isEnd){ returnMsg(message, (Long) source); } //or should this be 'new Long(source)'
    	else{ forwardMsg(message, (Long) source); }
    }
    private void receive(String message, String source /*SMS*/){
    	if(isStart){ acceptMsg(message); }
    	else if(isEnd){ returnMsg(message, source); }
    	else{ forwardMsg(message, source); }
    }
    
	private void acceptMsg(String message) {
    	long time = System.currentTimeMillis();
    	String stringMsgID = message.split("x")[0];
    	int msgID = Integer.parseInt(stringMsgID);
    	timeReceived.put(msgID, time);
    	mResultArrayAdapter.add("Received "+msgID+": "+time);

    	long rtt = timeReceived.get(msgID)-timeSent.get(msgID);
		RTT.put(msgID, rtt);
		mResultArrayAdapter.add("RTT: "+Long.toString(rtt));
    	
		ping(); //send the next iteration;
	}

	private void returnMsg(String message, Object source) {
		mResultArrayAdapter.add("Returning "+message.split("x")[0]);
		if(source instanceof Integer ){
			//TODO send only to a specified user
			sendMessage(message);
		}
		else if(source instanceof Long){
			byte[] send = message.getBytes();
		    mChatService.specificWrite(send, (Long)source);
		}
		else if(source instanceof String){
			try{
				smsManager.sendTextMessage((String)source, null, message, null, null);
			} catch (Exception ex) {
				Toast.makeText(getApplicationContext(),"Your sms has failed...",
						Toast.LENGTH_SHORT).show();
				ex.printStackTrace();
			}
		}
	}

	private void forwardMsg(String message, Object source) {
		if(source instanceof Integer ){
			//TODO sendViaWiFi(message, (Integer)source);
			sendMessage(message);
			sendViaBT(message, -1);
			sendViaSMS(message, "");
		}
		else if(source instanceof Long){
			//sendViaWiFi(message, -1);
			sendMessage(message);
			sendViaBT(message, (Long) source);
			sendViaSMS(message, "");
		}
		else if(source instanceof String){
			//sendViaWiFi(message, -1);
			sendMessage(message);
			sendViaBT(message, -1);
			sendViaSMS(message, (String)source);
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
	private void sendViaSMS(String message, String source){
		try {
			for(String phoneNum : smsContacts){
				if(!phoneNum.equals(source)){
					smsManager.sendTextMessage(phoneNum, null, message, null, null);
					Toast.makeText(getApplicationContext(), "Your sms has successfully sent!",
							Toast.LENGTH_SHORT).show();
				}
			}
		} catch (Exception ex) {
			Toast.makeText(getApplicationContext(),"Your sms has failed...",
					Toast.LENGTH_SHORT).show();
			ex.printStackTrace();
		}
	}
	
	/* receiving via SMS */
	public void receiveSMS(String source, String message){
    	//TODO skip this function?
		receive(message, source);
	}
	
	/* sending via BT */
	private void sendViaBT(String message, long source){
		byte[] send = message.getBytes();
	    mChatService.write(send, source);
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
            	Tuple t = (Tuple)msg.obj;
            	byte[] readBuf = (byte[])t.left;
            	long source = (Long) t.right;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                if (readMessage.length() > 0) {
                	receive(readMessage, source);
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

    
    private Handler UIupdate = new Handler () {
	    public void handleMessage (Message msg) {
	        //Update the UI
	    	if(msg.what==1) {		//Show the message on UI
	    		//display(MsgReceived);
	    		receive(MsgReceived, -1);
	    	}
            else if(msg.what==2) {//Update check box availability on UI  	
	    		//Update check box availability
               
	    		if(!mReceiver.getWifiPeersInAdhoc().getIsServer()) {
	    			((CheckBox)findViewById(R.id.checkbox_go)).setEnabled(true);
	    			//Display the assigned client node ID
		    		//((TextView)findViewById(R.id.head)).setText("Message History--CT"+Integer.toString(ClientNum));
	    		} else {
	    			//Display GO for group owner
		    		//((TextView)findViewById(R.id.head)).setText("Message History--GO");
	    		}
	    		switch(ClientSum) {
	    			case 5: ((CheckBox)findViewById(R.id.checkbox_ct5)).setEnabled(true);
	    			case 4: ((CheckBox)findViewById(R.id.checkbox_ct4)).setEnabled(true);
	    			case 3: ((CheckBox)findViewById(R.id.checkbox_ct3)).setEnabled(true);
	    			case 2: ((CheckBox)findViewById(R.id.checkbox_ct2)).setEnabled(true);
	    			case 1: ((CheckBox)findViewById(R.id.checkbox_ct1)).setEnabled(true);
	    			default:;
	    		}
	    		switch(ClientNum) {
	    			case 1: ((CheckBox)findViewById(R.id.checkbox_ct1)).setEnabled(false); break;
	    			case 2: ((CheckBox)findViewById(R.id.checkbox_ct2)).setEnabled(false); break;
	    			case 3: ((CheckBox)findViewById(R.id.checkbox_ct3)).setEnabled(false); break;
	    			case 4: ((CheckBox)findViewById(R.id.checkbox_ct4)).setEnabled(false); break;
	    			case 5: ((CheckBox)findViewById(R.id.checkbox_ct5)).setEnabled(false); break;
	    			default:;
	    		}
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
            case R.id.discover_wifi_direct_devices:
                if (!isWifiP2pEnabled) {
                    Toast.makeText(getApplicationContext(), "Enable WiFi Direct from system settings",
                            Toast.LENGTH_SHORT).show();
                    return true;
                }
                discover();
                return true;
	        case R.id.disconnect_wifi_direct_devices:
	            //Kill the timer
	            timer.cancel();
	            
	            //Close the opened threads
	            if(clientthread!=null) {
	            	clientthread.interrupt();
	            	clientthread = null;
	            }
	            if(serverthread!=null) {
	            	serverthread.interrupt();
	            	serverthread = null;
	            }
	            
	            //Close the opened streams
	            if(pout_transmit_client!=null) {
	            	try {
	    				pout_transmit_client.close();
	    			} catch (IOException e) {
	    				//Catch Logic
	    			}
	            }
	            if(pin_transmit_client!=null) {
	            	try {
	    				pin_transmit_client.close();
	    			} catch (IOException e) {
	    				//Catch Logic
	    			}
	            }
	            if(pout_rcv_client!=null) {
	            	try {
	    				pout_rcv_client.close();
	    			} catch (IOException e) {
	    				//Catch Logic
	    			}
	            }
	            if(pin_rcv_client!=null) {
	            	try {
	    				pin_rcv_client.close();
	    			} catch (IOException e) {
	    				//Catch Logic
	    			}
	            }
	            if(pout_transmit_server!=null) {
	            	try {
	    				pout_transmit_server.close();
	    			} catch (IOException e) {
	    				//Catch Logic
	    			}
	            }
	            if(pin_transmit_server!=null) {
	            	try {
	    				pin_transmit_server.close();
	    			} catch (IOException e) {
	    				//Catch Logic
	    			}
	            }
	            if(pout_rcv_server!=null) {
	            	try {
	    				pout_rcv_server.close();
	    			} catch (IOException e) {
	    				//Catch Logic
	    			}
	            }
	            if(pin_rcv_server!=null) {
	            	try {
	    				pin_rcv_server.close();
	    			} catch (IOException e) {
	    				//Catch Logic
	    			}
	            }
                Toast.makeText(getApplicationContext(), "WiFi Direct successfully disconnected",
                        Toast.LENGTH_SHORT).show();
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
	
	/* WiFi Direct */
	
	public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }
	
    public WiFiDirectBroadcastReceiver getWiFiDirectBroadcastReceiver() {
    	return mReceiver;
    }
    
    //Discover peers
    public void discover() {
    	
    	mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Toast.makeText(getApplicationContext(), "Discovery Initiated!",
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reasonCode) {
                Toast.makeText(getApplicationContext(), "Discovery Failed! Reason Code:" + reasonCode,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    //Get a peer and connect
    public void connect(WifiP2pDevice device) {

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.groupOwnerIntent = 0;
        config.wps.setup = WpsInfo.PBC;

        mManager.connect(mChannel, config, new ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            	//Set IsConnected
        		mReceiver.getWifiPeersInAdhoc().setIsConnected(true);
        		//notify();
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(getApplicationContext(), "Connect failed. Retry.",
                        Toast.LENGTH_SHORT).show();
                //notify();
            }
        });
    }
	
  //Establish pipes between UI thread and Data Transmit thread, start the later thread
    public void ClientThreadStart(String host) {
    	
    	try {
	    	pout_transmit_client = new PipedOutputStream();
	    	pin_transmit_client = new PipedInputStream(pout_transmit_client);
	    	
	    	pout_rcv_client = new PipedOutputStream();
	    	pin_rcv_client = new PipedInputStream(pout_rcv_client);

	    	clientthread = new ClientThread(host,pout_rcv_client,pin_transmit_client);
	    	clientthread.setDaemon(true);
	    	clientthread.start();
    	} catch(IOException e) {
    		//Catch Logic
    	}
    	
    	//Schedule the timer
    	timer.schedule(new TimerTask() {
    		@Override
    		public void run() {
    			int tmp = MsgRcv();
    			//If a new message received, notify the handler
    			Message m = new Message();
    			m.what = tmp;
    			UIupdate.sendMessage(m);
    		}
    	},1000,500);
    }
    
    //Establish pipes between UI thread and Data Rcv thread, start the later thread
    public void ServerThreadStart() {
    	
    	try {
	    	pout_rcv_server = new PipedOutputStream();
	    	pin_rcv_server = new PipedInputStream(pout_rcv_server);
	    	
	    	pout_transmit_server = new PipedOutputStream();
	    	pin_transmit_server = new PipedInputStream(pout_transmit_server);
	    	
	    	serverthread = new ServerThread(pout_rcv_server,pin_transmit_server);
	    	serverthread.setDaemon(true);
	    	serverthread.start();
    	} catch(IOException e) {
    		//Catch Logic
    	}
    	
    	//Schedule the timer
    	timer.schedule(new TimerTask() {
    		@Override
    		public void run() {
    			int tmp = MsgRcv();
    			//If a new message received, notify the handler
				Message m = new Message();
    			m.what = tmp;
    			UIupdate.sendMessage(m);
    		}
    	},1000,500);
    }
    
  //Called when the user clicks the Send button
    public void sendMessage(String message) { 
    	
        //Get the content in the text box
    	int dst_addr = 0;
    	byte buf[]  = new byte[1024];

        //! EditText editText = (EditText) findViewById(R.id.edit_message);
        //! String message = editText.getText().toString();
        int len = message.length();
        if(len<=0) {
        	Toast.makeText(getApplicationContext(), "Empty message!",
                    Toast.LENGTH_SHORT).show();
        	return;
        }
        
        //Get the destination address from the check boxes
        dst_addr = 0;
        //dst_addr = 63;
        
        //if(((CheckBox)findViewById(R.id.checkbox_go)).isChecked()) {
        if(((CheckBox)findViewById(R.id.checkbox_go)).isEnabled()) {
        	dst_addr += 1;
        }
        //if(((CheckBox)findViewById(R.id.checkbox_ct1)).isChecked()) {
        if(((CheckBox)findViewById(R.id.checkbox_ct1)).isEnabled()) {
        	dst_addr += 2;
        }
        //if(((CheckBox)findViewById(R.id.checkbox_ct2)).isChecked()) {
        if(((CheckBox)findViewById(R.id.checkbox_ct2)).isEnabled()) {
        	dst_addr += 4;
        }
        //if(((CheckBox)findViewById(R.id.checkbox_ct3)).isChecked()) {
        if(((CheckBox)findViewById(R.id.checkbox_ct3)).isEnabled()) {
        	dst_addr += 8;
        }
        //if(((CheckBox)findViewById(R.id.checkbox_ct4)).isChecked()) {
        if(((CheckBox)findViewById(R.id.checkbox_ct4)).isEnabled()) {
        	dst_addr += 16;
        }
        //if(((CheckBox)findViewById(R.id.checkbox_ct5)).isChecked()) {
        if(((CheckBox)findViewById(R.id.checkbox_ct5)).isEnabled()) {
        	dst_addr += 32;
        }
        if(dst_addr<=0) {
        	Toast.makeText(getApplicationContext(), "No Receiver Selected!",
                    Toast.LENGTH_SHORT).show();
        	return;
        }
        
        
        //Send the message
        try{
        	buf = message.getBytes("UTF-8");
        	if(mReceiver.getWifiPeersInAdhoc().getIsServer()) {
        		pout_transmit_server.write(len);		//Message Head (message length,head and type not included)
        		pout_transmit_server.write(0);			//Message Type (0 for data,1 for protocol)
        		pout_transmit_server.write(0x01);		//Message Source (Binary,a "1" in the ith(0~7) bit stands for the ith client, 0x01 for server)
        		pout_transmit_server.write(dst_addr);	//Message Destination (Binary,a "1" in the ith(0~7) bit stands for the ith client, 0x01 for server)
	        	pout_transmit_server.write(buf,0,len);	//Message Body (message content)
        	} else {
	        	pout_transmit_client.write(len);		//Message Head (message length,head and type not included)
	        	pout_transmit_client.write(0);			//Message Type (0 for data,1 for protocol)
	        	pout_transmit_client.write((int)(java.lang.Math.pow(2,ClientNum)));	//Message Source (Binary,a "1" in the ith(0~7) bit stands for the ith client, 0x01 for server)
	        	pout_transmit_client.write(dst_addr);	//Message Destination (Binary,a "1" in the ith(0~7) bit stands for the ith client, 0x01 for server)
	        	pout_transmit_client.write(buf,0,len);	//Message Body (message content)
        	}
        } catch(IOException e) {
        	//Catch logic
        }
        
        //Clear the text box
        //! editText.setText("");
        
        //Accumulate the count of transmitted messages
        mReceiver.getWifiPeersInAdhoc().setTransmitMsgCnt(mReceiver.getWifiPeersInAdhoc().getTransmitMsgCnt()+1);
        
        //Get the handle of the list view
        //! ListView list = (ListView) findViewById(R.id.list_view);
        
        //Fill the data into the Hash map
        HashMap<String, Object> map = new HashMap<String, Object>();  
        if(mReceiver.getWifiPeersInAdhoc().getIsServer()) {
        	map.put("ItemNumber", "GO ");
        } else {
        	map.put("ItemNumber", "CT" + Integer.toString(ClientNum));  
        }
        map.put("ItemMessage", message);  
        listItem.add(map);  
        
        //Display the items
        //list.setAdapter(listItemAdapter);
        
        //Scroll to the bottom
        //list.setSelection(list.getBottom());
    }
    
    //Receive a new message(the returned value 0 for failure, 1 for data message, 2 for protocol message and 3 for Internet message)
    public int MsgRcv() {
    	
    	//Get the received message
        byte buf[]  = new byte[1024];
        String message = null;
        int msg_len;
        int msg_type;
        int msg_src;
        int msg_dst;
        int len;
        PipedInputStream pin_rcv = null;
        
        try{
        	if(mReceiver.getWifiPeersInAdhoc().getIsServer()) {
        		pin_rcv = pin_rcv_server;
        	} else {
        		pin_rcv = pin_rcv_client;
        	}
        	
        	if(pin_rcv.available()<=0) {
        		return 0;
        	}
        	msg_len = pin_rcv.read();	//Get the Message Head (message length,head and type not included)
        	msg_type = pin_rcv.read();	//Get the Message Type (0 for data,1 for protocol)
        	msg_src = pin_rcv.read();	//Get the Message Source (Binary,a "1" in the ith(0~7) bit stands for the ith client, 0x01 for server)
        	msg_dst = pin_rcv.read();	//Get the Message Destination (Binary,a "1" in the ith(0~7) bit stands for the ith client, 0x01 for server)
        	len = 0;
        	while(len<msg_len) {
        		len += pin_rcv.read(buf,len,msg_len-len);	//Get the Message Body (message content)
        	}
        	if(msg_type==0) {			//Judge the message type; store it into message if it is a data frame
        		message = new String(buf,0,msg_len,"UTF-8");
        	} else if(msg_type==1) {	//Set the availability of check boxes
        		ClientSum = buf[0];
        		ClientNum = buf[1];
        		return 2;
        	} else {	//Internet Connection Request
        		//Obtain the url
        		message = new String(buf,0,msg_len,"UTF-8");
        		MsgReceived = message;
        		
                //Remember who is requesting Internet access
                MsgSource = (int)java.lang.Math.round((java.lang.Math.log(msg_src)/java.lang.Math.log(2)));

                return 3;
        	}
        } catch(IOException e) {
        	//Catch logic
        	return 0;
        }
        
        //Update the received message and source node number
        MsgReceived = message;
        MsgSource = (int)java.lang.Math.round((java.lang.Math.log(msg_src)/java.lang.Math.log(2)));
        
        //Accumulate the count of received messages
        mReceiver.getWifiPeersInAdhoc().setRcvMsgCnt(mReceiver.getWifiPeersInAdhoc().getRcvMsgCnt()+1);
        
        return 1;
    }
}
