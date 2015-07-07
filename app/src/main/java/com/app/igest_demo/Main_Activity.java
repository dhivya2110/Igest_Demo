package com.app.igest_demo;

import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Locale;


public class Main_Activity extends ActionBarActivity {
    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_DATA = 2;
    public static final int MESSAGE_CAl = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static boolean Gest1 = false;
    public static boolean Gest2 = false;
    public static boolean calibrationflag=false;
    public double yaw_0=0,pitch_0=0,roll_0=0,mx_0=0,my_0=0,mz_0=0;
    private static final int REQUEST_CONNECT_DEVICE = 100;
    private static final int REQUEST_ENABLE_BT = 101;
    //Local Bluetooth Adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothCommandService mCommandService;
    // Key names received from the BluetoothCommandService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    public static final String DATA_YAW = "yaw";
    public static final String DATA_INITIALIZED = "INITIALIZED";
    public static final String DATA_PITCH = "pitch";
    public static final String DATA_ROLL = "roll";
    // Name of the connected device
    private String mConnectedDeviceName = null;
    private Double yaw = null;
    private Double pitch = null;
    private Double roll = null;
    private Double mx = null;
    private Double my = null;
    private Double mz = null;
    private Button main_task;
    private Button initialize;
    private TextView display;
    FileWriter writer;
    private static TextToSpeech textToTalk;
    String toTalk;
    private HashMap<String, String> myHashAlarm;
    boolean onUtter1=true;
    MediaPlayer mediaPlayer1;
    MediaPlayer mediaPlayer2;
    String y_0, p_0, r_0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(R.string.app_name);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        myHashAlarm = new HashMap<String, String>();
        myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_ALARM));
        myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "SOME MESSAGE");

        initialize = (Button) findViewById(R.id.initialize);
        display = (TextView)findViewById(R.id.dispView);
        //added for audio files
        //int resid1 = getResources().getIdentifier("please_wait", "raw", getPackageName());
        //int resid2 = getResources().getIdentifier("price_1", "raw", getPackageName());
        int resid1 = getResources().getIdentifier("keep_quiet_1", "raw", getPackageName());
        int resid2 = getResources().getIdentifier("save_money_1", "raw", getPackageName());
        mediaPlayer1 = MediaPlayer.create(this, resid1);
        mediaPlayer2  = MediaPlayer.create(this, resid2);

        textToTalk = new TextToSpeech(getApplicationContext(),
                new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(int status) {
                        if (status == TextToSpeech.SUCCESS) {
                            textToTalk.setLanguage(Locale.UK);
                        /*    textToTalk.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                                @Override
                                public void onStart(String utteranceId) {
                                    OnUtter = true;
                                    Log.d("TTS", "Speaking");
                                }

                                @Override
                                public void onDone(String utteranceId) {
                                    OnUtter = false;
                                    Log.d("TTS", "Speaking done");
                                }

                                @Override
                                public void onError(String utteranceId) {
                                    OnUtter = false;
                                    Log.d("TTS", "Error in onutteranceprogresslistener");*/
                                }}
                            });


       initialize.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calibrationflag=true;
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main_, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case android.R.id.home:
                super.onBackPressed();
                return true;
            case R.id.scan:
                Intent deviceIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(deviceIntent, REQUEST_CONNECT_DEVICE);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, REQUEST_ENABLE_BT);
        } else if (mCommandService == null) {
            setTitle("Please connect to the device");
            setService();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.cancelDiscovery();
        }
        if (mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.disable();
        }
    }

    private void setService() {
        // Initialize the BluetoothChatService to perform bluetooth connections
                mCommandService = new BluetoothCommandService(this, mHandler);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    // Get the device MAC address
                    String address = data.getExtras()
                            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    // Get the BLuetoothDevice object
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                    Log.d("device", "device name " + device);
                    // Attempt to connect to the device
                    mCommandService.connect(device);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setTitle("Please connect to the device");
                    setService();
                } else {
                    // User did not enable Bluetooth or an error occured
                    Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                }
        }
    }

    // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothCommandService.STATE_CONNECTED:
                            setTitle(R.string.title_connected);
                            break;
                        case BluetoothCommandService.STATE_CONNECTING:
                            setTitle(R.string.title_connecting);
                            break;
                        case BluetoothCommandService.STATE_LISTEN:
                        case BluetoothCommandService.STATE_NONE:
                            setTitle(R.string.title_not_connected);
                            break;
                    }
                    break;
                case MESSAGE_CAl:
                    yaw_0=msg.getData().getDouble("YAW");
                    pitch_0=msg.getData().getDouble("PITCH");
                    roll_0=msg.getData().getDouble("ROLL");
                    y_0 = new DecimalFormat("#.##").format(yaw_0);
                    p_0 = new DecimalFormat("#.##").format(pitch_0);
                    r_0 = new DecimalFormat("#.##").format(roll_0);
                    mx_0 = msg.getData().getDouble("MAG_X");
                    my_0 = msg.getData().getDouble("MAG_Y");
                    mz_0 = msg.getData().getDouble("MAG_Z");
                    display.setText("Y " + y_0 + ", " + "P " + p_0 + ", " + "R " + r_0);
                    Log.v("YPR", "Y " + yaw_0 + " " + "P " + pitch_0 + " " + "R " + roll_0 + " ");
                    break;
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to "
                            + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_DATA:
                    //save data from bluetooth
                    yaw = msg.getData().getDouble(DATA_YAW);
                    pitch = msg.getData().getDouble(DATA_PITCH);
                    roll = msg.getData().getDouble(DATA_ROLL);
                    mx=msg.getData().getDouble("MAG_X");
                    my=msg.getData().getDouble("MAG_Y");
                    mz=msg.getData().getDouble("MAG_Z");

                    Log.d("Yaw ", " " + yaw.toString());
                    Log.d("Pitch ", " " + pitch.toString());
                    Log.d("pitch-pitch0"," " + Math.abs(pitch-pitch_0));
                    Log.d("Roll ", " " + roll.toString());
                    Log.d("roll-roll0"," " + Math.abs(roll-roll_0));
                    if(calibrationflag==true) {
                        find_gesture(yaw, pitch, roll);
                    }
                    /*if (OnUtter == false) {
                        find_gesture(yaw, pitch, roll);
                    } else {
                        Log.e("BCS", "Data not received. Talking");
                    }*/
            }
        }
    };

    public void find_gesture(Double yaw, Double pitch, Double roll) {
        Intent objIntent = new Intent(this, PlayAudio.class);
        startService(objIntent);

        if(Math.abs(pitch-pitch_0)>0.5)
        {
            initialize.setText("Pitch");
            display.setText("Hi, My name is Aravind. I am 18 years old");
            //textToTalk.speak("Please wait",TextToSpeech.QUEUE_FLUSH,null);
            //Media Player code added
            if(!mediaPlayer1.isPlaying() || !mediaPlayer2.isPlaying())
            {
                mediaPlayer1.start();
            }
        }
        else if(Math.abs(roll-roll_0)>1)
        {

            initialize.setText("Roll");
            display.setText("I have finished 10th standard");
           // textToTalk.speak("Price tag is under the product",TextToSpeech.QUEUE_FLUSH,null);

            //Media Player code added
            if(!mediaPlayer1.isPlaying() || !mediaPlayer2.isPlaying())
            {
                mediaPlayer2.start();
            }
        }
        /*else if(Math.sqrt(Math.pow(mx-mx_0,2.0)+Math.pow(my-my_0,2))>150)
        {
            initialize.setText("Yaw variation possibly");
        }*/
        else {
            initialize.setText("Baseline");
            display.setText("Y " + y_0 + ", " + "P " + p_0 + ", " + "R " + r_0);
            //mediaPlayer1.start();

        }

    }/*
        switch (1) {
            case 1:
                if (Gest1 == false && yaw < 0) {
                    //                   if (Gest1 == false) {
                    Log.d("Roll ", " " + roll.toString());
                    toTalk = "Please wait";
                    //textToTalk.speak(toTalk, TextToSpeech.QUEUE_FLUSH, myHashAlarm);
                    Gest1 = true;
                    break;
                }
                /*
            case 2:
            if(Gest2==false && pitch>0.3 && pitch<0.6)
            {
                Log.d("Roll ", " " + roll.toString());
                toTalk = "All of us should be like him";
                textToTalk.speak(toTalk, TextToSpeech.QUEUE_FLUSH, myHashAlarm);
                Gest2=true;
                break;
            }*/




}
