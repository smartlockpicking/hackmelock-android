package com.smartlockpicking.hackmelock;


import android.app.DialogFragment;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.app.Activity;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public class ShareActivity extends Activity {

    private HackmelockDBHelper dbHelper = new HackmelockDBHelper(this);

    ImageView QRImage;

    Button buttonGenerate;
    HackmelockDevice hackmelockDevice;
    private int KeyID;
    private long DateTo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share);

        QRImage = (ImageView)findViewById(R.id.QRCode);

        buttonGenerate = (Button)findViewById(R.id.button_generate);
        buttonGenerate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                QRCodeWriter writer = new QRCodeWriter();
                hackmelockDevice = dbHelper.getConfig();

                String toEncode = utils.majorMinorToHexString(hackmelockDevice.Major, hackmelockDevice.Minor) + ":" +
                        utils.bytesToHex(hackmelockDevice.keys[KeyID]) + ":" + KeyID +":" + System.currentTimeMillis()/1000 + ":" + DateTo;
                Log.d("Share","Text to encode: " + toEncode); //contains sensitive information (key)
                try {
                    BitMatrix bitMatrix = writer.encode(toEncode, BarcodeFormat.QR_CODE, 256, 256);
                    int width = bitMatrix.getWidth();
                    int height = bitMatrix.getHeight();
                    Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
                    for (int x = 0; x < width; x++) {
                        for (int y = 0; y < height; y++) {
                            bmp.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                        }
                    }
                    QRImage.setImageBitmap(bmp);
                    QRImage.setVisibility(View.VISIBLE);

                    buttonGenerate.setVisibility(View.VISIBLE);
                } catch (WriterException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void showDatePickerDialog(View v) {
        DialogFragment newFragment = new DatePickerFragment();
        newFragment.show(getFragmentManager(), "datePicker");
    }

    public void onRadioButtonClicked(View view) {
        // Is the button now checked?
        boolean checked = ((RadioButton) view).isChecked();

        // Check which radio button was clicked
        switch(view.getId()) {
            case R.id.radio_administrative:
                if (checked)
                    KeyID=0;
                    break;
            case R.id.radio_guest:
                if (checked)
                    KeyID=1;
                    break;
        }
    }

    public void setDateTo(long dateTo) {
        this.DateTo=dateTo;
    }

}
