package com.obnsoft.arduboyutils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

public class Utils {

    public static void showCustomDialog(
            Context context, int iconId, int titleId, View view, OnClickListener listener) {
        final AlertDialog dlg = new AlertDialog.Builder(context)
                .setIcon(iconId)
                .setTitle(titleId)
                .setView(view)
                .setPositiveButton(android.R.string.ok, listener)
                .create();
        if (listener != null) {
            dlg.setButton(AlertDialog.BUTTON_NEGATIVE,
                    context.getText(android.R.string.cancel), (OnClickListener) null);
        }
        if (view instanceof EditText) {
            view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        dlg.getWindow().setSoftInputMode(
                                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                    }
                }
            });
        }
        dlg.show();
    }

    public static void showToast(Context context, int msgId) {
        Toast.makeText(context, msgId, Toast.LENGTH_SHORT).show();
    }

    public static void showToast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    public static String getPathFromUri(final Context context, final Uri uri) {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        } else if ("arduboy".equalsIgnoreCase(uri.getScheme())) {
            return downloadFile(context, uri.getEncodedSchemeSpecificPart());
        }
        return null;
    }

    public static String downloadFile(final Context context, final String url) {
        final File file;
        try {
            String fileName = Uri.parse(url).getLastPathSegment();
            String suffix = null;
            if (fileName != null) {
                int idx = fileName.lastIndexOf('.');
                if (idx >= 0) {
                    suffix = fileName.substring(idx + 1);
                }
            }
            file = File.createTempFile("tmp", suffix, context.getCacheDir());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        MyAsyncTaskWithDialog.ITask task = new MyAsyncTaskWithDialog.ITask() {
            @Override
            public Boolean task() {
                InputStream in = null;
                OutputStream out = null;
                try {
                    out = new FileOutputStream(file);
                    HttpClient httpclient = new DefaultHttpClient();
                    HttpResponse httpResponse = httpclient.execute(new HttpGet(url));
                    in = httpResponse.getEntity().getContent();
                    byte[] buffer = new byte[1024 * 1024];
                    int length;
                    while ((length = in.read(buffer)) >= 0) {
                        out.write(buffer, 0, length);  
                    }
                    out.close(); 
                    in.close();
                } catch (Exception e){
                    e.printStackTrace();
                    file.delete();
                    return false;
                }
                return true;
            };
            @Override
            public void post(Boolean result) {
                if (!result) {
                    Utils.showToast(context, R.string.messageDownloadFailed);
                }
            }
        };
        MyAsyncTaskWithDialog.execute(context, R.string.messageDownloading, task);
        return file.getAbsolutePath();
    }
}
