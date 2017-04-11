package com.smartlockpicking.hackmelock;

import android.util.Log;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class HackmelockDevice {

    public static UUID HACKMELOCK_IBEACON_UUID = UUID.fromString("6834636b-6d33-4c30-634b-38454163304e");

    public static UUID HACKMELOCK_SERVICE_UUID = UUID.fromString("6834636b-6d33-4c30-634b-357276314333");
    public static UUID HACKMELOCK_COMMAND_UUID = UUID.fromString("6834636b-6d33-4c30-634b-436852436d44");
    public static UUID HACKMELOCK_STATUS_UUID = UUID.fromString("6834636b-6d33-4c30-634b-436852537434");
    public static UUID HACKMELOCK_DATATRANSFER_UUID = UUID.fromString("6834636b-6d33-4c30-634b-436852443454");

    public static final String passLogin = "DDAAFF03040506070809101112131415";
    public static final String cmdOpenLock = "AA010203040506070809101112131415";
    public static final String cmdCloseLock = "BB010203040506070809101112131415";
    public static final String cmdDataTransfer = "01AA0203040506070809101112131415";

    public static final String statusAuthenticated = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    public static final String statusConfigMode = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";

    public int Major;
    public int Minor;
    public int autounlock;
    public int own=0;
    public byte[][] keys;



    public enum Status {
        UNPAIRED, PAIRING, PAIRED, SYNCING, CONNECTED, UNAUTHENTICATED, AUTHENTICATING, AUTHENTICATED, OPENING, CLOSING
    }

    public Status status;

    public HackmelockDevice(){
        Major = 0;
        Minor = 0;
        keys = new byte[24][];
        this.status=Status.UNPAIRED;
        this.own=0;
    }


    public HackmelockDevice(int Maj, int Min){
        Major = Maj;
        Minor = Min;

        //tbd read from file
        keys = new byte[24][];

        this.status=Status.PAIRED;
    }


    public HackmelockDevice(int Maj, int Min, int isOwn, int isAutounlock){
        Major = Maj;
        Minor = Min;
        //tbd read from file
        keys = new byte[24][];
        autounlock = isAutounlock;
        own = isOwn;

        this.status=Status.PAIRED;
    }

    public boolean isOwn() {
        if (this.own == 1) {
            return true;
        } else return false;
    }

     public byte[] calculateResponse(byte[] challenge, int keyId) {
         byte[] resp=new byte[17];
         byte[] auth=new byte[16];
         byte[] keyIdByte=new byte[1];

         Arrays.fill(keyIdByte, (byte) keyId);

        try {
            //device stores 12-bytes keys, we fill the rest with 0-s to make it 128-bit AES
            byte[] zeroPadding = new byte[4];
            Arrays.fill(zeroPadding, (byte) 0);
            byte[] key = new byte[16];

            Log.d("Crypto","Key[" + keyId + "]=" + utils.bytesToHex(keys[keyId]));

            System.arraycopy(keys[keyId], 0, key, 0, 12);
            System.arraycopy(zeroPadding, 0, key, 12, 4);

            Log.d("Crypto","KeyFinal " + utils.bytesToHex(key));

            SecretKeySpec sKeySpec = new SecretKeySpec(key, "AES");
            Cipher aesCipher = Cipher.getInstance("AES");
            aesCipher.init(Cipher.ENCRYPT_MODE, sKeySpec);
            byte[] sessionKeyFull = aesCipher.doFinal(challenge);
            byte[] sessionKey = Arrays.copyOf(sessionKeyFull,16);
            Log.d("Auth","Step 1 - session key : " + utils.bytesToHex(sessionKey));

            SecretKeySpec authKeySpec = new SecretKeySpec(sessionKey, "AES");
            Cipher secondAesCipher = Cipher.getInstance("AES");
            secondAesCipher.init(Cipher.ENCRYPT_MODE, authKeySpec);
            byte[] authFull = secondAesCipher.doFinal(utils.hexStringToByteArray(passLogin));
            auth = Arrays.copyOf(authFull,16);
            Log.d("Auth","Step 2 - auth : " + utils.bytesToHex(auth));
        }  catch (Exception e) {
            Log.e("Auth", "Cannot initialize AES! " + e.getMessage());
        }
         //concatenate keyid to the end of response
         System.arraycopy(auth, 0, resp, 0, 16);
         System.arraycopy(keyIdByte, 0, resp, 16, 1);
         return resp;
    }

}
