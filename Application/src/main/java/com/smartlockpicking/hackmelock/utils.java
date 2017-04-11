package com.smartlockpicking.hackmelock;

import android.support.annotation.NonNull;

import java.util.UUID;

/**
 * Created by ja on 27.01.17.
 */

public class utils {

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] hexStringToByteArray(String hex)
    {
        int i = hex.length();
        byte[] out = new byte[i / 2];
        for (int j = 0; j < i; j += 2) {
            out[(j / 2)] = ((byte)((Character.digit(hex.charAt(j), 16) << 4) + Character.digit(hex.charAt(1 + j), 16)));
        }
        return out;
    }

    public static byte[] intToByteArray(int a)
    {
        byte[] ret = new byte[4];
        ret[3] = (byte) (a & 0xFF);
        ret[2] = (byte) ((a >> 8) & 0xFF);
        ret[1] = (byte) ((a >> 16) & 0xFF);
        ret[0] = (byte) ((a >> 24) & 0xFF);
        return ret;
    }

    public static byte[] majorMinorToByteArray(int Major, int Minor)
    {
        byte[] ret = new byte[4];
        ret[3] = (byte) (Minor & 0xFF);
        ret[2] = (byte) ((Minor >> 8) & 0xFF);
        ret[1] = (byte) ((Major ) & 0xFF);
        ret[0] = (byte) ((Major >> 8) & 0xFF);
        return ret;
    }

    public static String majorMinorToHexString(int Major, int Minor) {
        return bytesToHex(majorMinorToByteArray(Major, Minor));
    }


    // from https://github.com/inthepocket/ibeacon-scanner-android
    /**
     * Converts byte[] to an iBeacon {@link UUID}.
     * From http://stackoverflow.com/a/9855338.
     *
     * @param bytes Byte[] to convert
     * @return UUID
     */
    public static UUID bytesToUuid(@NonNull final byte[] bytes)
    {
        final char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ )
        {
            final int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }

        final String hex = new String(hexChars);

        return UUID.fromString(hex.substring(0, 8) + "-" +
                hex.substring(8, 12) + "-" +
                hex.substring(12, 16) + "-" +
                hex.substring(16, 20) + "-" +
                hex.substring(20, 32));
    }

    /**
     * Converts a {@link UUID} to a byte[]. This is used to create a {@link android.bluetooth.le.ScanFilter}.
     * From http://stackoverflow.com/questions/29664316/bluetooth-le-scan-filter-not-working.
     *
     * @param uuid UUID to convert to a byte[]
     * @return byte[]
     */
    public static byte[] UuidToByteArray(@NonNull final UUID uuid)
    {
        final String hex = uuid.toString().replace("-","");
        final int length = hex.length();
        final byte[] result = new byte[length / 2];

        for (int i = 0; i < length; i += 2)
        {
            result[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i+1), 16));
        }

        return result;
    }
    /**
     * Convert major or minor to hex byte[]. This is used to create a {@link android.bluetooth.le.ScanFilter}.
     *
     * @param value major or minor to convert to byte[]
     * @return byte[]
     */
    public static byte[] integerToByteArray(final int value)
    {
        final byte[] result = new byte[2];
        result[0] = (byte) (value / 256);
        result[1] = (byte) (value % 256);

        return result;
    }

    /**
     * Convert major and minor byte array to integer.
     *
     * @param byteArray that contains major and minor byte
     * @return integer value for major and minor
     */
    public static int byteArrayToInteger(final byte[] byteArray)
    {
        return (byteArray[0] & 0xff) * 0x100 + (byteArray[1] & 0xff);
    }

    public static int[] hexStringToMajorMinor(final String hexstring) {

        byte[] hexMajor = hexStringToByteArray(hexstring.substring(0,4));
        byte[] hexMinor = hexStringToByteArray(hexstring.substring(4,8));

        int Major = byteArrayToInteger(hexMajor);
        int Minor = byteArrayToInteger(hexMinor);
        int[] ret = {Major, Minor};

        return ret;
    }

}
