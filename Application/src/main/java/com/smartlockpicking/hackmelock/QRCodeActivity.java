package com.smartlockpicking.hackmelock;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.view.View;
import com.google.zxing.Result;
import me.dm7.barcodescanner.zxing.ZXingScannerView;

public class QRCodeActivity extends Activity implements ZXingScannerView.ResultHandler {

    private ZXingScannerView mScannerView;
    private HackmelockDevice hackmelockDevice;
    private HackmelockDBHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Android > 6.0 required permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs camera access");
                builder.setMessage("Please grant camera access in order to scan QR code.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
                    }
                });
                builder.show();
            }
        };


        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrcode);
        QrScanner(findViewById(android.R.id.content));
    }

    public void QrScanner(View view){

        mScannerView = new ZXingScannerView(this);   // Programmatically initialize the scanner view
        setContentView(mScannerView);

        mScannerView.setResultHandler(this); // Register ourselves as a handler for scan results.
        mScannerView.startCamera();         // Start camera

    }

    @Override
    public void onPause() {
        super.onPause();
        mScannerView.stopCamera();           // Stop camera on pause
    }

    @Override
    public void handleResult(Result rawResult) {
        // Do something with the result here

        Log.d("QRCODE", rawResult.getText()); // Prints scan results
        Log.d("QRCODE", rawResult.getBarcodeFormat().toString()); // Prints the scan format (qrcode)

        // show the scanner result into dialog box.
/*        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Scan Result");
        builder.setMessage(rawResult.getText());
        AlertDialog alert1 = builder.create();
        alert1.show();
*/

        if (ParseQR(rawResult.getText())) {

            //fire scanning
            Intent activityConnectIntent = new Intent(QRCodeActivity.this, DeviceScanActivity.class);
            startActivity(activityConnectIntent);

        }
        // If you would like to resume scanning, call this method below:
        // mScannerView.resumeCameraPreview(this);
    }

    private Boolean ParseQR(String scan) {

        //todo check for proper format!

        String[] parts = scan.split(":");
        //format: majorminor:key:keyid:time_from:time_to
        //        1a3595e1:92b17bff64951eee48a73033:0:0:0

        Log.d("QRSCAN", parts[0] + " " + parts[1] + " " + parts[2] + " " + parts[3] + " " + parts[4]);

        int[] majorminor = utils.hexStringToMajorMinor(parts[0]);
        int Major = majorminor[0];
        int Minor = majorminor[1];
        byte[] key = utils.hexStringToByteArray(parts[1]);
        int keyid = Integer.valueOf(parts[2]);
        int own = 0;
        if (keyid == 0) { own = 1; };
        int from = Integer.valueOf(parts[3]);
        int to = Integer.valueOf(parts[4].trim());

        hackmelockDevice = new HackmelockDevice(Major, Minor);
        hackmelockDevice.keys[keyid] = key;
        if (own == 1) {
            // todo sync - initiate data transfer
            // hackmelockDevice.status = HackmelockDevice.Status.SYNCING;
        }
        dbHelper = new HackmelockDBHelper(this);
        dbHelper.insertConfig(Major, Minor, "my lock", own, 1, from, to);
        dbHelper.insertKey(Major, Minor, parts[1], keyid);

        return true;

    }
}
