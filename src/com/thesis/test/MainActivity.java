package com.thesis.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

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
import android.os.Environment;
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
import android.widget.EditText;
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
	protected static final int WIFI_MESSAGE = 1;
	protected static final int UPDATE_WIFI_CONNS = 2;

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
	
	private static int MAX_MESSAGE_SIZE = 160;
	private static final int MAX_WIFI_CONNS = 5;
	private static int TRIALS_NUM = 30;
	private static boolean isStart;
	private static boolean isEnd;
	private static int sentMsgIndex;
	
	private Button pingBtn, resetBtn;
	private ListView resultLV;
	private EditText filenameET;
	SmsManager smsManager;
	SMSReceiver BR_smsreceiver;
	private CheckBox senderCB;
	private CheckBox receiverCB;
	
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
	
	private boolean[] peer;

	private WifiP2pDevice device;
	//private String MsgReceived;
	//private int MsgSource;
	private int ClientSum=0;
	private int ClientNum=0;
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
		peer = new boolean[MAX_WIFI_CONNS+1];
		RTT = new SparseArray<Long>();
		sentMsgIndex = 0;
		
		senderCB = (CheckBox) findViewById(R.id.isStart);
		senderCB.setChecked(isStart);
		receiverCB = (CheckBox) findViewById(R.id.isEnd);
		receiverCB.setChecked(isEnd);
		
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
		filenameET = (EditText) findViewById(R.id.filenameET);
		
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
        
        //Initialize and register the BroadcastReceiver
        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);
        registerReceiver(mReceiver, intentFilter);
        
	}
	
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatService != null) mChatService.stop();
        unregisterReceiver(BR_smsreceiver);
        disconnectWiFiDirectDevice();
        unregisterReceiver(mReceiver);
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
    	
    	mResultArrayAdapter.add("AVERAGE RTT: "+String.valueOf(aveRTT));
    	mResultArrayAdapter.add("PACKET LOSS: "+String.valueOf(packetLoss)+"%");
    	
    	//Save to file
    	saveToFile(aveRTT, packetLoss);
    }
    
    public void onCheckboxClicked(View view) {
	    boolean checked = ((CheckBox) view).isChecked();
		switch(view.getId()) {
        case(R.id.isEnd):
            isEnd = checked;
            break;
	    case(R.id.isStart):
	        isStart = checked;
	        break;
	    }
	}
    
    /*private int convertToInt(int size){
    	switch(size){
			case(160):
				return 1;
			case(100):
				return 2;
			case(1000):
				return 3;
			case(10000):
				return 4;
			case(100000):
				return 5;
			default:
				return size;
    	}
    }
    private int convertToSize(int code){
    	switch(code){
        case(1):
			return 160;
		case(2):
			return 100;
		case(3):
			return 1000;
		case(4):
			return 10000;
		case(5):
			return 100000;
		default:
			return code;
    	}
    }*/
    
    private void saveToFile(double aveRTT, double packetLoss) {
	    String root = Environment.getExternalStorageDirectory().toString();
	    File myDir = new File(root + "/ping_results");
	    myDir.mkdirs();
	    String fname = (filenameET.getText()+".txt").replace(' ', '_');
	    fname.replace(' ', '_');
	    File file = new File(myDir, fname);
	    if (file.exists())
	        file.delete();
	    try {
	        FileOutputStream fOut = new FileOutputStream(file);
	        OutputStreamWriter mOutWriter = new OutputStreamWriter(fOut);
	        for(int i=0; i<RTT.size(); i++){
	        	mOutWriter.append(RTT.get(i,-1L)+",");
	        }
	        mOutWriter.append("\nAVERAGE RTT: "+String.valueOf(aveRTT));
	        mOutWriter.append("\nPACKET LOSS: "+String.valueOf(packetLoss)+"%");
	        mOutWriter.flush();
	        mOutWriter.close();
	        fOut.flush();
	        fOut.close();
	    }
	    catch (Exception e) {
	        e.printStackTrace();
	    }
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
			mResultArrayAdapter.add("Sent msgnum = "+Integer.toString(sentMsgIndex)); // temp
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
    	mResultArrayAdapter.add("  Received msgnum = "+msgID);

    	long rtt = timeReceived.get(msgID)-timeSent.get(msgID);
		RTT.put(msgID, rtt);
		mResultArrayAdapter.add("  RTT: "+Long.toString(rtt));
    	
		ping(); //send the next iteration;
	}

	private void returnMsg(String message, Object source) {
		mResultArrayAdapter.add("Returning "+message.split("x")[0]);
		if(source instanceof Integer ){
			sendViaWiFiSpecific(message, (Integer)source);
		}
		else if(source instanceof Long){
			byte[] size = new byte[1];
			size[0] = (byte) GlobalFunctions.convertToInt(message.length());
			mChatService.specificWrite(size, (Long)source);
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
		String stringSource = "";
		if(source instanceof Integer ){
			sendViaWiFi(message, (Integer)source);
			sendViaBT(message, -1);
			sendViaSMS(message, "");
			stringSource = Integer.toString((Integer)source);
		}
		else if(source instanceof Long){
			sendViaWiFi(message, -1);
			sendViaBT(message, (Long) source);
			sendViaSMS(message, "");
			stringSource = Long.toString((Long)source);
		}
		else if(source instanceof String){
			sendViaWiFi(message, -1);
			sendViaBT(message, -1);
			sendViaSMS(message, (String)source);
			stringSource = (String)source;
		}
		mResultArrayAdapter.add("Forwarding message from "+stringSource);
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
		try{
			//check if received message is from this app
			String stringMsgID = message.split("x")[0];
	    	Integer.parseInt(stringMsgID);
			receive(message, source);
		}catch(Exception e){
			return;
		}
	}
	
	/* sending via BT */
	private void sendViaBT(String message, long source){
		
		byte[] size = new byte[1];
		size[0] = (byte) GlobalFunctions.convertToInt(message.length());
		mChatService.write(size, source);
		
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
                	Toast.makeText(getApplicationContext(), "Connected",
                            Toast.LENGTH_SHORT).show();
                    break;
                case BluetoothChatService.STATE_CONNECTING:
                	Toast.makeText(getApplicationContext(), "Connecting",
                            Toast.LENGTH_SHORT).show();
                	break;
                case BluetoothChatService.STATE_LISTEN:
                case BluetoothChatService.STATE_NONE:
                	Toast.makeText(getApplicationContext(), "State:  none",
                            Toast.LENGTH_SHORT).show();
                    break;
                }
                break;
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
	    	if(msg.what==WIFI_MESSAGE) {		//Show the message on UI
	    		//display(MsgReceived);
	    		//receive(MsgReceived, -1);
	    		receive((String)msg.obj, msg.arg1);
	    	}
            else if(msg.what==UPDATE_WIFI_CONNS) {//Update check box availability on UI  	
	    		
	    		if(!mReceiver.getWifiPeersInAdhoc().getIsServer()) {
	    			peer[0] = true;
	    		}
	    		peer[ClientSum] = true;
	    		peer[ClientNum] = false;
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
	        	disconnectWiFiDirectDevice();
	        	return true;    
		}
		return super.onOptionsItemSelected(item);
	}


	private void disconnectWiFiDirectDevice() {
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
	}

	private void startSettingsActivity() {
		Intent i = new Intent(getApplicationContext(), SettingsActivity.class);
		i.putExtra("messageSize", MAX_MESSAGE_SIZE);
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
	    		senderCB.setChecked(isStart);
	    		isEnd = intent.getBooleanExtra("isEnd", false);
	    		receiverCB.setChecked(isEnd);
	    		MAX_MESSAGE_SIZE = intent.getIntExtra("messageSize", 0);
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

    	Runnable myRunnable = new Runnable(){
    	     public void run(){
    	    	while(true){
	     			Message m = MsgRcv();
	    	    	UIupdate.sendMessage(m);
    	    	}
    	    }
    	};

    	Thread thread = new Thread(myRunnable);
    	thread.start();
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
    	
    	Runnable myRunnable = new Runnable(){
    		public void run(){
    			while(true){
	    			Message m = MsgRcv();
		   	    	UIupdate.sendMessage(m);
    			}
	   	    }
	   	};
	   	Thread thread = new Thread(myRunnable);
	   	thread.start();
    }
    
    public void sendViaWiFiSpecific(String message, int source) {
    	int dst_addr = (int)Math.pow(2,source);
    	sendMessage(message, dst_addr);
    }
    
    public void sendViaWiFi(String message, int source) { 
    	int dst_addr = 0;
        for(int i=0; i<=MAX_WIFI_CONNS; i++){
        	if(peer[i] && i!=source && i!=ClientNum){ 
        		dst_addr = dst_addr + (int)Math.pow(2,i);
        	}
        }
        sendMessage(message, dst_addr);
    }
    public void sendMessage(String message, int dst_addr) { 
    	
        //Get the content in the text box
    	byte buf[]  = new byte[1024];

        int len = message.length();
        if(len<=0) {
        	Toast.makeText(getApplicationContext(), "Empty message!",
                    Toast.LENGTH_SHORT).show();
        	return;
        }
                
        if(dst_addr<=0) {
        	//Toast.makeText(getApplicationContext(), "No WiFi conection",
            //        Toast.LENGTH_SHORT).show();
        	return;
        }
        
        
        //Send the message
        try{
        	buf = message.getBytes("UTF-8");
        	if(mReceiver.getWifiPeersInAdhoc().getIsServer()) {
        		pout_transmit_server.write((byte) GlobalFunctions.convertToInt(len));		//Message Head (message length,head and type not included)
        		pout_transmit_server.write(0);			//Message Type (0 for data,1 for protocol)
        		pout_transmit_server.write(0x01);		//Message Source (Binary,a "1" in the ith(0~7) bit stands for the ith client, 0x01 for server)
        		pout_transmit_server.write(dst_addr);	//Message Destination (Binary,a "1" in the ith(0~7) bit stands for the ith client, 0x01 for server)
	        	pout_transmit_server.write(buf,0,len);	//Message Body (message content)
        	} else {
	        	pout_transmit_client.write((byte) GlobalFunctions.convertToInt(len));		//Message Head (message length,head and type not included)
	        	pout_transmit_client.write(0);			//Message Type (0 for data,1 for protocol)
	        	pout_transmit_client.write((int)(java.lang.Math.pow(2,ClientNum)));	//Message Source (Binary,a "1" in the ith(0~7) bit stands for the ith client, 0x01 for server)
	        	pout_transmit_client.write(dst_addr);	//Message Destination (Binary,a "1" in the ith(0~7) bit stands for the ith client, 0x01 for server)
	        	pout_transmit_client.write(buf,0,len);	//Message Body (message content)
        	}
        } catch(IOException e) {
        	//Catch logic
        }
        
        
        //Accumulate the count of transmitted messages
        mReceiver.getWifiPeersInAdhoc().setTransmitMsgCnt(mReceiver.getWifiPeersInAdhoc().getTransmitMsgCnt()+1);
        
        //Fill the data into the Hash map
        /*HashMap<String, Object> map = new HashMap<String, Object>();  
        if(mReceiver.getWifiPeersInAdhoc().getIsServer()) {
        	map.put("ItemNumber", "GO ");
        } else {
        	map.put("ItemNumber", "CT" + Integer.toString(ClientNum));  
        }
        map.put("ItemMessage", message);  
        listItem.add(map);*/
    }
    
    //Receive a new message(the returned value 0 for failure, 1 for data message, 2 for protocol message and 3 for Internet message)
    public Message MsgRcv() {
    	
    	//Get the received message
        byte buf[]  = new byte[1024];
        String message = null;
        int msg_len;
        int msg_type;
        int msg_src;
        int msg_dst;
        int len;
        PipedInputStream pin_rcv = null;
        Message m = new Message();
        
        try{
        	if(mReceiver.getWifiPeersInAdhoc().getIsServer()) {
        		pin_rcv = pin_rcv_server;
        	} else {
        		pin_rcv = pin_rcv_client;
        	}
        	
        	if(pin_rcv.available()<=0) {
        		m.what = 0;
        		return m;
        	}
        	msg_len = pin_rcv.read();	//Get the Message Head (message length,head and type not included)
        	msg_type = pin_rcv.read();	//Get the Message Type (0 for data,1 for protocol)
        	msg_src = pin_rcv.read();	//Get the Message Source (Binary,a "1" in the ith(0~7) bit stands for the ith client, 0x01 for server)
        	msg_dst = pin_rcv.read();	//Get the Message Destination (Binary,a "1" in the ith(0~7) bit stands for the ith client, 0x01 for server)
        	len = 0;
        	int readBytes = 0;
        	if(msg_type==0){ msg_len = GlobalFunctions.convertToSize(msg_len); }
        	while(len<msg_len) {
        		Log.i(TAG, "received it! "+len + "_" + msg_len);
        		readBytes = pin_rcv.read(buf,len,msg_len-len);
        		len = len + readBytes;	//Get the Message Body (message content)
        	}
        	if(msg_type==0) {			//Judge the message type; store it into message if it is a data frame
        		message = new String(buf,0,msg_len,"UTF-8");
        	} else if(msg_type==1) {	//Set the availability of check boxes
        		ClientSum = buf[0];
        		ClientNum = buf[1];
        		m.what = UPDATE_WIFI_CONNS;
        		return m;
        	}
        } catch(IOException e) {
        	//Catch logic
        	m.what = 0;
    		return m;
        }
        
        //Update the received message and source node number
        String MsgReceived = message;
        int MsgSource = (int)java.lang.Math.round((java.lang.Math.log(msg_src)/java.lang.Math.log(2)));
        
        //Accumulate the count of received messages
        mReceiver.getWifiPeersInAdhoc().setRcvMsgCnt(mReceiver.getWifiPeersInAdhoc().getRcvMsgCnt()+1);
        
        m.what = WIFI_MESSAGE;
        m.arg1 = MsgSource;
        m.obj = MsgReceived;
        return m;
    }
}
