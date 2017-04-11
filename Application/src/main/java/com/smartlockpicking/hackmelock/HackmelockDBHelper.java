package com.smartlockpicking.hackmelock;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 *   based on https://github.com/obaro/android-sqlite-sample
 */

public class HackmelockDBHelper extends SQLiteOpenHelper {

        public static final String DATABASE_NAME = "hackmelock.db";
        private static final int DATABASE_VERSION = 1;

        private static final int KEYS_COUNT=24;

        public static final String KEYS_TABLE_NAME = "keys";
        public static final String KEYS_COLUMN_ID = "_id";
        public static final String KEYS_COLUMN_MAJORMINOR = "majorminor";
        public static final String KEYS_COLUMN_INDICATOR = "indicator";
        public static final String KEYS_COLUMN_VALUE = "value";

        public static final String CONFIG_TABLE_NAME = "configuration";
        public static final String CONFIG_COLUMN_ID = "_id";
        public static final String CONFIG_COLUMN_MAJORMINOR = "majorminor";
        public static final String CONFIG_COLUMN_NAME = "name";
        public static final String CONFIG_COLUMN_OWN = "own";
        public static final String CONFIG_COLUMN_AUTOUNLOCK = "autounlock";
        public static final String CONFIG_COLUMN_SHARING_START = "sharing_start";
        public static final String CONFIG_COLUMN_SHARING_STOP = "sharing_stop";

        public static final String LOG_TABLE_NAME = "logs";
        public static final String LOG_COLUMN_ID = "_id";
        public static final String LOG_COLUMN_MAJORMINOR = "majorminor";
        public static final String LOG_COLUMN_DESC = "description";
        public static final String LOG_COLUMN_TIMESTAMP = "timestamp";


    public HackmelockDBHelper(Context context) {
            super(context, DATABASE_NAME , null, DATABASE_VERSION);
            Log.d("DBHELPER", "NEW SUPER");
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE " + KEYS_TABLE_NAME +
                        "(" + KEYS_COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        KEYS_COLUMN_MAJORMINOR + " TEXT, " +
                        KEYS_COLUMN_INDICATOR + " TEXT, " +
                        KEYS_COLUMN_VALUE + " INTEGER)"
        );
        db.execSQL(
               "CREATE TABLE " + CONFIG_TABLE_NAME +
                        "(" + CONFIG_COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        CONFIG_COLUMN_MAJORMINOR + " TEXT, " +
                        CONFIG_COLUMN_NAME + " TEXT, " +
                        CONFIG_COLUMN_OWN + " INTEGER, " +
                        CONFIG_COLUMN_AUTOUNLOCK + " INTEGER, " +
                        CONFIG_COLUMN_SHARING_START + " INTEGER, " +
                        CONFIG_COLUMN_SHARING_STOP + " INTEGER )"
        );
        db.execSQL(
               "CREATE TABLE " + LOG_TABLE_NAME +
                        "(" + LOG_COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        LOG_COLUMN_MAJORMINOR + " TEXT, " +
                        LOG_COLUMN_DESC + " TEXT, " +
                        LOG_COLUMN_TIMESTAMP + " INTEGER)"
        );

    }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + KEYS_TABLE_NAME);
        onCreate(db);
    }

    public boolean insertKey(int Major, int Minor, String value, int indicator) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();

        contentValues.put(KEYS_COLUMN_MAJORMINOR, utils.majorMinorToHexString(Major, Minor));
        contentValues.put(KEYS_COLUMN_VALUE, value);
        contentValues.put(KEYS_COLUMN_INDICATOR, indicator);

        db.insert(KEYS_TABLE_NAME, null, contentValues);
        //tbd catch exception
        return true;
    }

    public boolean insertKeys(int Major, int Minor, byte[][] keys ) {
        for (int i=0; i< keys.length; i++) {
            insertKey(Major, Minor, utils.bytesToHex(keys[i]), i);
        }
        //tbd catch exception
        return true;
    }

    public byte[] getKey(int Major, int Minor, int indicator) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor res = db.query(KEYS_TABLE_NAME,
                new String[] { KEYS_COLUMN_INDICATOR, KEYS_COLUMN_VALUE },
                KEYS_COLUMN_MAJORMINOR + "=? AND " +  KEYS_COLUMN_INDICATOR + "=?",
                new String[] { utils.majorMinorToHexString(Major,Minor), Integer.toString(indicator)},
                null,null, null);

        if (res.moveToFirst()) {
            byte[] key = utils.hexStringToByteArray(res.getString(res.getColumnIndex(KEYS_COLUMN_VALUE)));
            return key;
        }
        else {
            return null;
        }
    }

    public byte[] getMasterKey(int Major, int Minor) {
        return getKey(Major, Minor, 0);
    }

    public byte[][] getAllKeys(int Major, int Minor) {
        byte[][] keys = new byte[KEYS_COUNT][];
        for (int i=0; i<KEYS_COUNT; i++) {
            keys[i] = getKey(Major, Minor, i);
        }
        return keys;
    }

    public Cursor getConfigRaw() {
        SQLiteDatabase db = this.getReadableDatabase();
        //get all data from config table
        Cursor res = db.query(CONFIG_TABLE_NAME,
                //tbd for specific lock, atm we have only one
                new String[] { CONFIG_COLUMN_MAJORMINOR, CONFIG_COLUMN_OWN, CONFIG_COLUMN_AUTOUNLOCK, CONFIG_COLUMN_SHARING_START, CONFIG_COLUMN_SHARING_STOP },
                null, null, null, null, null);
        return res;
    }


    public HackmelockDevice getConfig() {
        Cursor cursor = getConfigRaw();
        if (cursor.moveToFirst()){

            int[] majorminor = utils.hexStringToMajorMinor(cursor.getString(cursor.getColumnIndex(CONFIG_COLUMN_MAJORMINOR)));
            int Major = majorminor[0];
            int Minor = majorminor[1];
            int own = Integer.valueOf(cursor.getString(cursor.getColumnIndex(CONFIG_COLUMN_OWN)));
            int autounlock = Integer.valueOf(cursor.getString(cursor.getColumnIndex(CONFIG_COLUMN_AUTOUNLOCK)));
            int availableFrom = Integer.valueOf(cursor.getString(cursor.getColumnIndex(CONFIG_COLUMN_SHARING_START)));
            int availableTo = Integer.valueOf(cursor.getString(cursor.getColumnIndex(CONFIG_COLUMN_SHARING_STOP)));

            long currentTime = System.currentTimeMillis()/1000;
            if ((availableTo >0) && (availableTo < currentTime)) {
                //lock is not available any more
                return new HackmelockDevice();
            } else {
                HackmelockDevice hackmelockDevice = new HackmelockDevice(Major, Minor, own, autounlock);

                hackmelockDevice.keys=getAllKeys(Major, Minor);
                return hackmelockDevice;
            }
        }
        else {
            return new HackmelockDevice();
        }
    }

    public boolean insertConfig(int Major, int Minor, String name, int own, int autounlock, int sharing_start, int sharing_stop) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        Log.d("DB","WriteConfig start");

        contentValues.put(CONFIG_COLUMN_MAJORMINOR, utils.majorMinorToHexString(Major, Minor));
        contentValues.put(CONFIG_COLUMN_NAME, name);
        contentValues.put(CONFIG_COLUMN_OWN, own);
        contentValues.put(CONFIG_COLUMN_AUTOUNLOCK, autounlock);
        contentValues.put(CONFIG_COLUMN_SHARING_START, sharing_start);
        contentValues.put(CONFIG_COLUMN_SHARING_STOP, sharing_stop);

        db.insert(CONFIG_TABLE_NAME, null, contentValues);

        Log.d("DB","WriteConfig end");

        return true;
    }

    public boolean insertLog(int Major, int Minor, String desc) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();

        int timestamp = (int) (System.currentTimeMillis()/1000);

        contentValues.put(LOG_COLUMN_MAJORMINOR, utils.majorMinorToHexString(Major, Minor));
        contentValues.put(LOG_COLUMN_DESC, desc);
        contentValues.put(LOG_COLUMN_TIMESTAMP, timestamp);

        db.insert(LOG_TABLE_NAME, null, contentValues);
        return true;
    }

}