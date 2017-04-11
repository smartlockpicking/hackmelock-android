/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.smartlockpicking.hackmelock;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.TextView;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;

    private ImageButton mButtonOpen, mButtonClose, mButtonSync;

    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private BluetoothGattService hackmelockService;

    private BluetoothGattCharacteristic hackmelockCommandChar, hackmelockStatusChar, hackmelockDataTransferChar;


    private HackmelockDBHelper dbHelper = new HackmelockDBHelper(this);
    private int dataTransferCounter=0;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    private HackmelockDevice hackmelockDevice;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    // ACTION_WRITE_SUCCESS: write queue finished

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d("RECEIVER",action);
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                //will be needed in case of reconnection
                if (hackmelockDevice.status == HackmelockDevice.Status.AUTHENTICATED) {
                    hackmelockDevice.status = HackmelockDevice.Status.PAIRED;
                }
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                checkServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {

                Bundle extras = intent.getExtras();
                String characteristic = extras.getString("EXTRA_CHARACTERISTIC");
                String data = extras.getString("EXTRA_DATA");

                Log.d("ACTION_REC","CHAR: "+characteristic + " DATA: " + data);

                if (characteristic.equals("dataTransfer")) {
                    Log.d(TAG,"DataTransfer received " );
                    if (hackmelockDevice.status == HackmelockDevice.Status.SYNCING) {
                        if (dataTransferCounter == 0) { //first line of config = Major + Minor
                            //this will be the same Major and Minor as already connected
                            Log.d(TAG, "Received majorminor: " + data);
                        } else {
                            Log.d(TAG, "Received key id " + String.valueOf(dataTransferCounter-1) + " : " + data);
                            hackmelockDevice.keys[dataTransferCounter-1] = utils.hexStringToByteArray(data);
                            if (dataTransferCounter == 24) {
                                Log.d(TAG,"Received all configuration, writing to db...");
                                dbHelper.insertKeys(hackmelockDevice.Major, hackmelockDevice.Minor, hackmelockDevice.keys);
                                hackmelockDevice.status = HackmelockDevice.Status.AUTHENTICATED;
                            }
                        }
                        dataTransferCounter++;
                    } else {
                        Log.w(TAG, "DataTransfer received, but application not in SYNCING mode!");
                    }
                }

                if (characteristic.equals("status")) {
                    switch(hackmelockDevice.status) {
                        //pairing
                        case UNPAIRED:
                            Log.d("STATUS", "Unpaired");
                            if (data.equals("CONFIG")) {
                                displayData("Device in config mode");
                                startPairing();
                            } else {
                                Log.w("STATUS", "Unpaired, but device not in config mode!");
                                //tbd - display notification to user
                            }
                            break;
                        //authentication - we got challenge
                        case AUTHENTICATING:
                            Log.d("Authentication", "Challenge: " + data);
                            if (data.equals("AUTHENTICATED")) {
                                //we are already authenticated
                                hackmelockDevice.status= HackmelockDevice.Status.AUTHENTICATED;
                                //todo but now it disconnects ;)
                            } else {
                                byte[] challenge = utils.hexStringToByteArray(data);
                                int keyId=0;
                                if (hackmelockDevice.isOwn()) keyId=0;
                                else if (dbHelper.getKey(hackmelockDevice.Major, hackmelockDevice.Minor, 1)!=null) keyId=1;
                                else {
                                    Log.e(TAG, "No configured key!");
                                    //todo exception, disconnect
                                }
                                byte[] authResponse = hackmelockDevice.calculateResponse(challenge,keyId);
                                mBluetoothLeService.queueWriteDataToCharacteristic(hackmelockCommandChar, authResponse);
                            }
                            break;

                        default:
                            Log.d("Status","Status update: " + data);
                            //tbd - status update
                    }

                }
                //tx queue finished
            } else if (BluetoothLeService.ACTION_WRITE_SUCCESS.equals(action)) {
                switch(hackmelockDevice.status) {
                    case PAIRING:
                        Log.d("Pairing","Write success");
                        hackmelockDevice.status= HackmelockDevice.Status.PAIRED;
                        authenticate();
                        break;
                    case AUTHENTICATING:
                        Log.d("Auth","Write success");
                        hackmelockDevice.status= HackmelockDevice.Status.AUTHENTICATED;
                        displayData("authenticated");
                        //set buttons to visible
                        ImageButton openButton=(ImageButton)findViewById(R.id.button_open);
                        openButton.setVisibility(View.VISIBLE);
                        ImageButton closeButton=(ImageButton)findViewById(R.id.button_close);
                        closeButton.setVisibility(View.VISIBLE);

                        ImageButton syncButton=(ImageButton)findViewById(R.id.button_sync);
                        if (hackmelockDevice.isOwn())  syncButton.setVisibility(View.VISIBLE);

                        if (!isSynced()) {
                            // tbd - other icon/icon+text?
                            //auto sync
                            SyncKeys();
                        }
                        if (hackmelockDevice.autounlock == 1) { openLock(); }
                        break;
                    default:
                        Log.d("Write","Success");
                }
            }
        }
    };
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.lock_control);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);

        mButtonOpen = (ImageButton) findViewById(R.id.button_open);
        mButtonClose = (ImageButton) findViewById(R.id.button_close);
        mButtonSync = (ImageButton) findViewById(R.id.button_sync);

        mButtonOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i("BUTTON", "OPEN");
                openLock();
            }
        });

        mButtonClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i("BUTTON", "CLOSE");
                closeLock();
            }
        });

        mButtonSync.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i("BUTTON", "SYNC");
                SyncKeys();
            }
        });


        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    /**
     * Pairing in config mode
     */
    public void startPairing() {
        Log.d("Pairing","START");
        hackmelockDevice.status=HackmelockDevice.Status.PAIRING;

        Random r = new Random();
        int Major = r.nextInt(65535);
        int Minor = r.nextInt(65535);

        hackmelockDevice.Major = Major;
        hackmelockDevice.Minor = Minor;

        Log.d("Pairing","Major: " + Integer.toString(Major) + " Minor: " + Integer.toString(Minor));
        displayData("Pairing - Major:" + Integer.toString(Major) + " Minor:" + Integer.toString(Minor));

        byte[] value = utils.majorMinorToByteArray(Major, Minor);
        mBluetoothLeService.queueWriteDataToCharacteristic(hackmelockCommandChar,value);

        for (int i = 0; i<24; i++) {
            try {
                KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                keyGen.init(96);
                SecretKey secretKey = keyGen.generateKey();
                hackmelockDevice.keys[i] = secretKey.getEncoded();

                mBluetoothLeService.queueWriteDataToCharacteristic(hackmelockCommandChar,secretKey.getEncoded());
            }
            catch (NoSuchAlgorithmException e) {
                Log.e("Pairing", "CANNOT INITIALIZE AES RANDOM!!!");
                //tbd exception notification to user

            }
        }
        dbHelper.insertConfig(Major, Minor, "my lock", 1, 1, 0, 0);
        dbHelper.insertKeys(Major, Minor, hackmelockDevice.keys);
        hackmelockDevice.own=1;
        invalidateOptionsMenu();
    }

    public void authenticate(){
        Log.d("Authenticate","START");
        hackmelockDevice.status= HackmelockDevice.Status.AUTHENTICATING;
        displayData("Authenticating...");
        //subscribe to status notifications - not yet implemented in emulator
        //mBluetoothLeService.queueSetCharacteristicNotification(hackmelockStatusChar,true);
        //we just need to read the status, response will be serviced by received intent with response - DATA_AVAILABLE
        mBluetoothLeService.readCharacteristic(hackmelockStatusChar);
    }

    public void openLock(){
        byte[] command = utils.hexStringToByteArray(HackmelockDevice.cmdOpenLock);
        mBluetoothLeService.queueWriteDataToCharacteristic(hackmelockCommandChar,command);
    }

    public void closeLock(){
        byte[] command = utils.hexStringToByteArray(HackmelockDevice.cmdCloseLock);
        mBluetoothLeService.queueWriteDataToCharacteristic(hackmelockCommandChar,command);
    }

    //sync = scanned QR with master key, need to get others keys
    public Boolean isSynced(){
        if ((hackmelockDevice.keys[0] != null) && (hackmelockDevice.keys[1] == null)) {
            return false;
        }
        else
            return true;
    }

    public void SyncKeys(){
        byte[] command = utils.hexStringToByteArray(HackmelockDevice.cmdDataTransfer);
        //subscribe to notifications from DataTransfer characteristic
        mBluetoothLeService.queueSetCharacteristicNotification(hackmelockDataTransferChar,true);

        //invoke transferdata command
        hackmelockDevice.status= HackmelockDevice.Status.SYNCING;
        mBluetoothLeService.queueWriteDataToCharacteristic(hackmelockCommandChar, command);

    }

    private void clearUI() {
        //upon disconnect set buttons to invisible
        ImageButton openButton=(ImageButton)findViewById(R.id.button_open);
        openButton.setVisibility(View.INVISIBLE);
        ImageButton closeButton=(ImageButton)findViewById(R.id.button_close);
        closeButton.setVisibility(View.INVISIBLE);
        ImageButton syncButton=(ImageButton)findViewById(R.id.button_sync);
        syncButton.setVisibility(View.INVISIBLE);

        mDataField.setText(R.string.no_data);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.control, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }

        //if paired -> configured object, if not -> "empty" object
        hackmelockDevice = dbHelper.getConfig();

//        if (hackmelockDevice) {
            if (hackmelockDevice.isOwn())
                menu.findItem(R.id.menu_share).setVisible(true);
//        }
//        if (hackmelockDevice.status == HackmelockDevice.Status.UNPAIRED)

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case R.id.menu_share:
                Intent activityShareIntent = new Intent(DeviceControlActivity.this, ShareActivity.class);
                startActivity(activityShareIntent);
                return true;
            case R.id.menu_about:
                Intent activityAboutIntent = new Intent(DeviceControlActivity.this, AboutActivity.class);
                startActivity(activityAboutIntent);
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        }
    }

    // Iterate through the GATT Services/Characteristics.
    private void checkServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            //           uuid = gattService.getUuid().toString();
            UUID serviceUuid = gattService.getUuid();
            if (hackmelockDevice.HACKMELOCK_SERVICE_UUID.equals(serviceUuid)) {
                Log.d("GattServices", "Found Hackmelock service!");
                hackmelockService = gattService;
                hackmelockCommandChar = hackmelockService.getCharacteristic(hackmelockDevice.HACKMELOCK_COMMAND_UUID);
                hackmelockStatusChar = hackmelockService.getCharacteristic(hackmelockDevice.HACKMELOCK_STATUS_UUID);
                hackmelockDataTransferChar = hackmelockService.getCharacteristic(hackmelockDevice.HACKMELOCK_DATATRANSFER_UUID);

                ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                        new ArrayList<HashMap<String, String>>();
                List<BluetoothGattCharacteristic> gattCharacteristics =
                        gattService.getCharacteristics();
                ArrayList<BluetoothGattCharacteristic> charas =
                        new ArrayList<BluetoothGattCharacteristic>();

                // Loops through available Characteristics.
                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    charas.add(gattCharacteristic);
                    HashMap<String, String> currentCharaData = new HashMap<String, String>();
//                    uuid = gattCharacteristic.getUuid().toString();
                    UUID characteristicUuid = gattCharacteristic.getUuid();
                    if (hackmelockDevice.HACKMELOCK_STATUS_UUID.equals(characteristicUuid)) {
                        Log.d("GattServices", "Found Hackmelock status characteristic");
                        //if device already paired, read status = auth challenge
                        if (hackmelockDevice.status == HackmelockDevice.Status.PAIRED) {
//                            hackmelockDevice.status = HackmelockDevice.Status.AUTHENTICATING;
                            authenticate();
                        } else {
                            Log.d("GattServices","Status: " + hackmelockDevice.status.toString());
                            // else - if device not yet paired, read status = init pairing
                            mBluetoothLeService.readCharacteristic(gattCharacteristic);
                        }
                    }
                }
                mGattCharacteristics.add(charas);
                gattCharacteristicData.add(gattCharacteristicGroupData);

            }
        }
    }


    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_WRITE_SUCCESS);
        intentFilter.addAction(BluetoothLeService.ACTION_WRITE_FAILURE);

        return intentFilter;
    }

}
