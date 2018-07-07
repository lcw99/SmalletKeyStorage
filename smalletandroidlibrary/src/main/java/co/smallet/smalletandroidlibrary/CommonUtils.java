package co.smallet.smalletandroidlibrary;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class CommonUtils {
    public static boolean isAppInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        }
        catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static String buildInfoBody(Activity act) {
        String body = "The information below is provided for effective customer support.\n" +
                "If you do not want to provide it, please delete it.";
        body += "\nProgram Version: " + getProgramVersion(act);
        body += "\nDevice: " + Build.MANUFACTURER + "[" + Build.MODEL + "]";
        body += "\nOS: " + Build.VERSION.RELEASE + "(" + Build.VERSION.SDK_INT + ")";
        body += "\n";

        return body;
    }

    public static String getProgramVersion(Activity act) {
        String version = "";
        try {
            PackageInfo pInfo = act.getPackageManager().getPackageInfo(act.getPackageName(), 0);
            version = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return version;
    }

    public static Intent createEmailIntent(String eMail, String subject, String body) {
        final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
        emailIntent.setType("plain/text");
        emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[] { eMail });
        emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, subject);
        emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, body);

        return emailIntent;
    }

    public static Intent buildSendLogIntent(Activity act, String email) {
        LogCollector collector = new LogCollector(act);
        ArrayList<String> logs = collector.collect();

        String sdcardDir = Environment.getExternalStorageDirectory().getAbsolutePath();
        String logDir = sdcardDir + "/.StockMasterLogs/";
        Log.d("lcw", "logdir=" + logDir);
        File d = new File(logDir);
        if (!d.exists())
            d.mkdirs();
        File tempFile = new File(d, "log.txt");

        FileWriter fw = null;
        try {
            tempFile.createNewFile();
            fw = new FileWriter(tempFile);
            if (fw != null) {
                int start = logs.size() - 1000;
                int i = 0;
                for (String line : logs) {
                    if (i++ > start)
                        fw.write(line + "\n");
                }
                fw.close();
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        Intent emailIntent = createEmailIntent(email, act.getResources().getString(R.string.app_name) + " Log", buildInfoBody(act));
        Uri uri = Uri.fromFile(tempFile);
        emailIntent.putExtra(Intent.EXTRA_STREAM, uri);
        return emailIntent;
    }

    public static void startInstalledAppDetailsActivity(final Activity context) {
        if (context == null) {
            return;
        }
        final Intent i = new Intent();
        i.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.addCategory(Intent.CATEGORY_DEFAULT);
        i.setData(Uri.parse("package:" + context.getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        context.startActivity(i);
    }
}
