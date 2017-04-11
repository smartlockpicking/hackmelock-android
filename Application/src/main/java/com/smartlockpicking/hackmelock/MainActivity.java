package com.smartlockpicking.hackmelock;

import android.content.Intent;
import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

public class MainActivity extends Activity {

    HackmelockDBHelper dbHelper;
    HackmelockDevice hackmelockDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new HackmelockDBHelper(this);
        hackmelockDevice = dbHelper.getConfig();
        dbHelper.close();

        Log.d("MAIN","Status: " + hackmelockDevice.status);
        //if already paired - proceed to connection
        if (hackmelockDevice.status == HackmelockDevice.Status.PAIRED) {
           jumpToScan();
        }

        //else - make buttons visible...

        final Button buttonSetup = (Button) findViewById(R.id.button_setup);
        buttonSetup.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                jumpToScan();
            }
        });

        final Button buttonQR = (Button) findViewById(R.id.button_have_qr);
        buttonQR.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent activitySetupIntent = new Intent(MainActivity.this, QRCodeActivity.class);
                startActivity(activitySetupIntent);
            }
        });

    }

    private void jumpToScan(){
        Intent activitySetupIntent = new Intent(MainActivity.this, DeviceScanActivity.class);
        startActivity(activitySetupIntent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_about:
                Intent activityAboutIntent = new Intent(MainActivity.this, AboutActivity.class);
                startActivity(activityAboutIntent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }



}
