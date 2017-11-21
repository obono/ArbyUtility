package com.obnsoft.arduboyutils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.physicaloid.lib.Physicaloid;

import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

public class MyApplication extends Application {

    public static final IntentFilter USB_RECEIVER_FILTER =
            new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);

    private Physicaloid mPhysicaloid;

    @Override
    public void onCreate() {
        super.onCreate();
        mPhysicaloid = new Physicaloid(getApplicationContext());
    }

    @Override
    public void onTerminate() {
        mPhysicaloid = null;
        super.onTerminate();
    }

    public Physicaloid getPhysicaloidInstance() {
        return mPhysicaloid;
    }

    public void showVersion(Context context) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View aboutView = inflater.inflate(R.layout.about, new ScrollView(context));
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(
                    getPackageName(), PackageManager.GET_META_DATA);
            TextView textView = (TextView) aboutView.findViewById(R.id.textAboutVersion);
            textView.setText("Version " + packageInfo.versionName);

            StringBuilder buf = new StringBuilder();
            InputStream in = getResources().openRawResource(R.raw.license);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String str;
            while((str = reader.readLine()) != null) {
                buf.append(str).append('\n');
            }
            textView = (TextView) aboutView.findViewById(R.id.textAboutMessage);
            textView.setText(buf.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        new AlertDialog.Builder(context)
                .setIcon(android.R.drawable.ic_menu_info_details)
                .setTitle(R.string.menuAbout)
                .setView(aboutView)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

}
