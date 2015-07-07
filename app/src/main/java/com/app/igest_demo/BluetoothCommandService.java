package com.app.igest_demo;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.UUID;

public class BluetoothCommandService {
    // Debugging
    private static final String TAG = "BluetoothCommandService";
    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    private int readBufferPosition;
    private int counter=0;
    private int init_count=100;
    private static boolean cal_flag=false;

    double pitch=0,yaw=0,roll=0,ts=0,MAG_heading=0;
    double[] accel = new double[] {0,0,0};
    double[] magnetom = new double[] {0,0,0};
    double[] gyro = new double[] {0,0,0};
    double[][] DCM_Matrix = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
    double yaw_0=0,pitch_0=0,roll_0=0,mx_0=0,my_0=0,mz_0=0;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    /**
     * Constructor. Prepares a new BluetoothChat session.
     *
     * @param context The UI Activity Context
     * @param handler A Handler to send messages back to the UI Activity
     */
    public BluetoothCommandService(Context context, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        //mConnectionLostCount = 0;
        mHandler = handler;
    }

    /**
     * Set the current state of the chat connection
     *
     * @param state An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(Main_Activity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state.
     */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    public synchronized void start() {
        Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_LISTEN);
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     */
    public synchronized void connect(BluetoothDevice device) {
        Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device)
    {
        Log.d(TAG, "connected");

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        // Now we need wait for initialization to start. and we store socket..
        //setFlagSocket(socket);
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(Main_Activity.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Main_Activity.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        counter=0;
        // save connected device
        //mSavedDevice = device;
        // reset connection lost count
        //mConnectionLostCount = 0;

        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        Log.d(TAG, "stop");
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_NONE);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */

    private void connectionFailed() {
        setState(STATE_LISTEN);
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Main_Activity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Main_Activity.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private class ConnectThread extends Thread
    {
        private BluetoothSocket mmSocket=null;
        private BluetoothDevice mmDevice=null;

        public ConnectThread(BluetoothDevice device)
        {
            mmDevice = device;
            BluetoothSocket tmp = null;
            try {
                tmp = mmDevice.createRfcommSocketToServiceRecord(UUID.randomUUID());
                Log.d("socket", "comm socket created");
                mmSocket = tmp;
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("", e.getMessage());
            }
        }

        @Override
        public void run()
        {
            Log.i(TAG, "BEGIN mConnectThread");
            setName("ConnectThread");

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();
            try {
                mmSocket.connect();
                Log.d("socket", "connection established");
            }
            catch(Exception e) {
                e.printStackTrace();
                Log.d("", "trying fallback...");
                try {

                    mmSocket = (BluetoothSocket) mmDevice.getClass().getMethod("createRfcommSocket", new Class[]{int.class}).invoke(mmDevice, 1);
                    mmSocket.connect();
                    Log.d("", "Connected");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    connectionFailed();
                    // Close the socket
                    try {
                        mmSocket.close();
                    } catch (IOException e2) {
                        Log.e(TAG, "unable to close() socket during connection failure", e2);
                    }
                    // Start the service over to restart listening mode
                    BluetoothCommandService.this.start();
                    return;
                }
            }
            // Reset the ConnectThread because we're done
            synchronized (BluetoothCommandService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice);
        }

        public void cancel()
        {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    class ConnectedThread extends Thread
    {
        private final BluetoothSocket mSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private final static int MAX_LINE = 100;
        final byte delimiter=10;
        String data;

        ConnectedThread(BluetoothSocket mySocket)
        {
            Log.d("connected", "Connected Thread");
            this.mSocket=mySocket;
            InputStream tempIn=null;
            OutputStream tempOut=null;

            //Get the bluetooth sockets
            try
            {
                tempIn = mSocket.getInputStream();
                tempOut = mSocket.getOutputStream();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            inputStream = tempIn;
            outputStream=tempOut;
        }

        public void run() {
            Log.d("connected Thread", "Thread Running");
            byte[] readBuffer = new byte[1024];
            int bytes;
            while (true) {
                try {
                    int bytesAvailable = inputStream.available();
                    if (bytesAvailable > 0) {
                        byte[] packetBytes = new byte[bytesAvailable];
                        inputStream.read(packetBytes);
                        for (int i = 0; i < bytesAvailable; i++) {
                            byte b = packetBytes[i];
                            if (b == delimiter) {
                                byte[] encodedBytes = new byte[readBufferPosition];
                                System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                data = new String(encodedBytes, "US-ASCII");
                                readBufferPosition = 0;
                                if(Main_Activity.calibrationflag==true & cal_flag==false)
                                {
                                    calibrate(data);
                                }
                                else
                                {
                                    perform(data);
                                }
                                //Log.d("DATA", data);
                                /*Message msg = mHandler.obtainMessage(Main_Activity.MESSAGE_DATA);
                                Bundle bundle = new Bundle();
                                bundle.putString(Main_Activity.DATA_BLUETOOTH,data);
                                msg.setData(bundle);
                                mHandler.sendMessage(msg);
                                //Log.d("Success","data sent");*/
                            } else {
                                readBuffer[readBufferPosition++] = b;
                            }
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
        //write method
        public void write(String input) {
            byte[] msgBuffer = input.getBytes();           //converts entered String into bytes
            try {
                outputStream.write(msgBuffer);                //write bytes over BT connection via outstream
                // Log.d(TAG, msgBuffer.toString());
            } catch (IOException e) {
                //if you cannot write, close the application
                e.printStackTrace();

            }
        }
        public void cancel()
        {
            try
            {
                mSocket.close();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    public void perform(String data) {
        // Log.d(TAG,data);

        final String[] values = data.split(",");
        double[] temp1 = new double[]{0, 0, 0};
        double[] temp2 = new double[]{0, 0, 0};
        double[] x_axis = new double[]{1.0, 0.0, 0.0};

        try {
            accel[0] = Double.parseDouble(values[0]);
            accel[1] = Double.parseDouble(values[1]);
            accel[2] = Double.parseDouble(values[2]);
            magnetom[0] = Double.parseDouble(values[3]);
            magnetom[1] = Double.parseDouble(values[4]);
            magnetom[2] = Double.parseDouble(values[5]);
            gyro[0] = Double.parseDouble(values[6]);
            gyro[1] = Double.parseDouble(values[7]);
            gyro[2] = Double.parseDouble(values[8]);
            ts = Double.parseDouble(values[9]);
        } catch (Exception e) {
            e.printStackTrace();
        }/*
        Log.d("acceleration along","x " + accel[0]);
        Log.d("acceleration along","y " + accel[1]);
        Log.d("acceleration along","z " + accel[2]);
        //CALCULATE PITCH
        Log.d("gyro ","x " + gyro[0]);
        Log.d("gyro ","y " + gyro[1]);
        Log.d("gyro ","z " + gyro[2]);

        Log.d("magnetometer ","x " + magnetom[0]);
        Log.d("magnetometer ","y " + magnetom[1]);
        Log.d("magnetometer ","z " + magnetom[2]);
        */
        pitch = -(Math.atan2(accel[0], Math.sqrt((accel[1] * accel[1]) + (accel[2] * accel[2]))));
        //CALCULATE ROLL
        temp1 = Vector_Cross_Product(accel, x_axis);
        temp2 = Vector_Cross_Product(x_axis, temp1);
        // Normally using x-z-plane-component/y-component of compensated gravity vector
        // roll = atan2(temp2[1], sqrt(temp2[0] * temp2[0] + temp2[2] * temp2[2]));
        // Since we compensated for pitch, x-z-plane-component equals z-component:
        roll = Math.atan2(temp2[1], temp2[2]);
        //CALCULATE YAW
        yaw = Compass_Heading(magnetom, roll, pitch);
        //Init Rotation Matrix
        DCM_Matrix = init_rotation_matrix(yaw, pitch, roll);
        /*Log.d("Yaw "," " + yaw);
        Log.d("Pitch "," " + pitch);
        Log.d("roll "," " + roll);
        String y= Double.toString(yaw);
        String p= Double.toString(pitch);
        String r= Double.toString(roll);*/
        transmit_back(yaw, pitch, roll,magnetom);
    }

    public void calibrate(String data)
    {
        final String[] values = data.split(",");
        double[] temp1 = new double[]{0, 0, 0};
        double[] temp2 = new double[]{0, 0, 0};
        double[] x_axis = new double[]{1.0, 0.0, 0.0};

        try {
            accel[0] = Double.parseDouble(values[0]);
            accel[1] = Double.parseDouble(values[1]);
            accel[2] = Double.parseDouble(values[2]);
            magnetom[0] = Double.parseDouble(values[3]);
            magnetom[1] = Double.parseDouble(values[4]);
            magnetom[2] = Double.parseDouble(values[5]);
            gyro[0] = Double.parseDouble(values[6]);
            gyro[1] = Double.parseDouble(values[7]);
            gyro[2] = Double.parseDouble(values[8]);
            ts = Double.parseDouble(values[9]);
        } catch (Exception e) {
            e.printStackTrace();
        }

        pitch = -(Math.atan2(accel[0], Math.sqrt((accel[1] * accel[1]) + (accel[2] * accel[2]))));
        //CALCULATE ROLL
        temp1 = Vector_Cross_Product(accel, x_axis);
        temp2 = Vector_Cross_Product(x_axis, temp1);
        // Normally using x-z-plane-component/y-component of compensated gravity vector
        // roll = atan2(temp2[1], sqrt(temp2[0] * temp2[0] + temp2[2] * temp2[2]));
        // Since we compensated for pitch, x-z-plane-component equals z-component:
        roll = Math.atan2(temp2[1], temp2[2]);
        //CALCULATE YAW
        yaw = Compass_Heading(magnetom, roll, pitch);
        //Init Rotation Matrix
        DCM_Matrix = init_rotation_matrix(yaw, pitch, roll);

        if(counter<init_count)
        {
            yaw_0+=yaw;
            pitch_0+=pitch;
            roll_0+=roll;
            mx_0+=magnetom[0];
            my_0+=magnetom[1];
            mz_0+=magnetom[2];
            counter++;
        }
        else if(counter==init_count) {
            yaw_0 = yaw_0 / init_count;
            pitch_0 = pitch_0 / init_count;
            roll_0 = roll_0 / init_count;
            mx_0 = mx_0 / init_count;
            my_0 = my_0 / init_count;
            mz_0 = mz_0 /init_count;
            counter = 0;
            //transmit_back(yaw_0,pitch_0,roll_0);

            Log.d(" ", "INITIALIZED");
            cal_flag = true;




            Message msg = mHandler.obtainMessage(Main_Activity.MESSAGE_CAl);
            Bundle bundle = new Bundle();
            bundle.putDouble("YAW", yaw_0);
            bundle.putDouble("PITCH", pitch_0);
            bundle.putDouble("ROLL", roll_0);
            bundle.putDouble("MAG_X",mx_0);
            bundle.putDouble("MAG_Y",my_0);
            bundle.putDouble("MAG_Z",mz_0);
            msg.setData(bundle);
            mHandler.sendMessage(msg);
        }
    }
    public void transmit_back(Double yaw, Double pitch, Double roll, double[] mg)
    {
        Message msg = mHandler.obtainMessage(Main_Activity.MESSAGE_DATA);
        Bundle bundle = new Bundle();
        bundle.putDouble(Main_Activity.DATA_YAW,yaw);
        bundle.putDouble(Main_Activity.DATA_PITCH,pitch);
        bundle.putDouble(Main_Activity.DATA_ROLL,roll);
        bundle.putDouble("MAG_X",mg[0]);
        bundle.putDouble("MAG_Y",mg[1]);
        bundle.putDouble("MAG_Z",mg[2]);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }
    public double[] Vector_Cross_Product(double[] in1, double[] in2)
    {
        double[] out = new double[] {0,0,0};
        out[0] = (in1[1] * in2[2]) - (in1[2] * in2[1]);
        out[1] = (in1[2] * in2[0]) - (in1[0] * in2[2]);
        out[2] = (in1[0] * in2[1]) - (in1[1] * in2[0]);
        return out;
    }

    public double Compass_Heading(double[] magnetom, double roll, double pitch)
    {
        double mag_x=0, mag_y=0, cos_roll=0, sin_roll=0, cos_pitch=0, sin_pitch=0;
        cos_roll= Math.cos(roll);
        sin_roll= Math.sin(roll);
        cos_pitch= Math.cos(pitch);
        sin_pitch= Math.sin(pitch);

        // Tilt compensated magnetic field X
        mag_x = magnetom[0] * cos_pitch + magnetom[1] * sin_roll * sin_pitch + magnetom[2] * cos_roll * sin_pitch;
        // Tilt compensated magnetic field Y
        mag_y = magnetom[1] * cos_roll - magnetom[2] * sin_roll;
        //Magnetic Heading
        MAG_heading= Math.atan2(-mag_y, mag_x);
        return MAG_heading;
    }
    public double[][] init_rotation_matrix(double yaw, double pitch, double roll)
    {
        double[][] m = new double[3][3];
        double c1= Math.cos(roll),c2= Math.cos(pitch),c3= Math.cos(yaw);
        double s1= Math.sin(roll),s2= Math.sin(pitch),s3= Math.sin(yaw);
        // Euler angles, right-handed, intrinsic, XYZ convention
        // (which means: rotate around body axes Z, Y', X'')
        m[0][0] = c2 * c3;
        m[0][1] = c3 * s1 * s2 - c1 * s3;
        m[0][2] = s1 * s3 + c1 * c3 * s2;

        m[1][0] = c2 * s3;
        m[1][1] = c1 * c3 + s1 * s2 * s3;
        m[1][2] = c1 * s2 * s3 - c3 * s1;

        m[2][0] = -s2;
        m[2][1] = c2 * s1;
        m[2][2] = c1 * c2;
        return  m;
    }
}
