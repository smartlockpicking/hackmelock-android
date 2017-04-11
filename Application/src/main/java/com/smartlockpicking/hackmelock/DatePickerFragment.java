package com.smartlockpicking.hackmelock;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.TextView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;


public class DatePickerFragment extends DialogFragment implements DatePickerDialog.OnDateSetListener {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the current date as the default date in the picker
        final Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        // Create a new instance of DatePickerDialog and return it
        return new DatePickerDialog(getActivity(), this, year, month, day);
    }


    public void onDateSet(DatePicker view, int year, int month, int day) {
        TextView dateView= (TextView) getActivity().findViewById(R.id.textTo);
        String dateToDisplay=view.getYear()+"-"+String.format("%02d", view.getMonth()+1)+"-"+String.format("%02d",view.getDayOfMonth());
        dateView.setText("Access valid to: "+dateToDisplay);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        try {
            Date mDate = sdf.parse(dateToDisplay);
            long timeInSeconds = mDate.getTime()/1000;
            Log.d("Share", "Date in milli : " + timeInSeconds);
            ((ShareActivity)getActivity()).setDateTo(timeInSeconds);
        } catch (ParseException e)
        {
            e.printStackTrace();
        }
    }
}
