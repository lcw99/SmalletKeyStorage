package co.smallet.keystorage;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;

import static android.content.Context.MODE_PRIVATE;

public class Utils {
    @SuppressLint("SetJavaScriptEnabled")
    public static WebView initWebView(WebView webView) {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d("webview", consoleMessage.message() + " -- From line "
                        + consoleMessage.lineNumber() + " of "
                        + consoleMessage.sourceId());
                return super.onConsoleMessage(consoleMessage);
            }
        });
        Log.e("WebViewActivity", "========================================");
        Log.e("WebViewActivity", "UA: " + webView.getSettings().getUserAgentString());

        return webView;
    }

    public static Integer getWordCount(String s) {
        String trim = s.trim();
        if (trim.isEmpty())
            return 0;
        return trim.split("\\s+").length;
    }

    public static SharedPreferences getPref(Context c) {
         return c.getSharedPreferences(Constants.MY_PREFS_NAME, MODE_PRIVATE);
    }

    public static void addPublicAddress(Context c,Integer hdCoinId,  String address, Integer keyIndex) {
        ArrayList<Integer> issuedCoins = getIssuedCoinsFromPref(c);
        if (!issuedCoins.contains(hdCoinId))
            addIssuedCoins(c, hdCoinId);
        HashMap<Integer, String> publicKeys = getPublicAddressListFromPref(c, hdCoinId);
        if (publicKeys.get(address) == null)
            publicKeys.put(keyIndex, address);

        // save the task list to preference
        SharedPreferences.Editor editor = getPref(c).edit();
        try {
            editor.putString(c.getString(R.string.hdCoinIdColon) + hdCoinId, ObjectSerializer.serialize(publicKeys));
        } catch (IOException e) {
            e.printStackTrace();
        }
        editor.commit();
    }

    public static HashMap<Integer, String> getPublicAddressListFromPref(Context c, Integer hdCoinId) {
        HashMap<Integer, String> publicKeys = new HashMap<>();
        SharedPreferences prefs = getPref(c);
        try {
            String str = prefs.getString(c.getString(R.string.hdCoinIdColon) + hdCoinId, null);
            if (str != null)
                publicKeys = (HashMap<Integer, String>) ObjectSerializer.deserialize(str);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return publicKeys;
    }

    public static void addIssuedCoins(Context c,Integer hdCoinId) {
        ArrayList<Integer> issuedCoins = getIssuedCoinsFromPref(c);
        if (null == issuedCoins) {
            issuedCoins = new ArrayList<>();
        }
        if (!issuedCoins.contains(hdCoinId))
            issuedCoins.add(hdCoinId);

        // save the task list to preference
        SharedPreferences.Editor editor = getPref(c).edit();
        try {
            editor.putString(c.getString(R.string.issuedCoins), ObjectSerializer.serialize(issuedCoins));
        } catch (IOException e) {
            e.printStackTrace();
        }
        editor.commit();
    }

    public static ArrayList<Integer> getIssuedCoinsFromPref(Context c) {
        ArrayList<Integer> issuedCoins = new ArrayList<>();
        SharedPreferences prefs = getPref(c);
        try {
            String str = prefs.getString(c.getString(R.string.issuedCoins), null);
            if (str != null)
                issuedCoins = (ArrayList<Integer>) ObjectSerializer.deserialize(str);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return issuedCoins;
    }

    public static String decryptMasterSeed(Context c) {
        String encSeed = Utils.getPref(c).getString(c.getString(R.string.encSeed), "");
        String ivSeed = Utils.getPref(c).getString(c.getString(R.string.ivSeed), "");
        try {
            DeCryptor dec = new DeCryptor();
            String masterSeed = dec.decryptData(c.getString(R.string.master_seed), Base64.decode(encSeed, Base64.DEFAULT), Base64.decode(ivSeed, Base64.DEFAULT));
            return masterSeed;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return "";
    }

    public static void encryptMasterSeedAndSave(Context c, String seedGenerated, String passphrase) {
        byte[] encSeed = null;
        byte[] iv = null;
        try {
            EnCryptor enc = new EnCryptor();
            encSeed = enc.encryptText(c.getString(R.string.master_seed), seedGenerated + (!passphrase.equals("") ? "|" + passphrase : ""));
            iv = enc.getIv();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (encSeed != null) {
            SharedPreferences prefs = c.getSharedPreferences(Constants.MY_PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor  = prefs.edit();
            try {
                editor.putString(c.getString(R.string.encSeed), Base64.encodeToString(encSeed, Base64.DEFAULT));
                editor.putString(c.getString(R.string.ivSeed), Base64.encodeToString(iv, Base64.DEFAULT));
                ArrayList<Integer> issuedCoins = getIssuedCoinsFromPref(c);
                if (issuedCoins.size() > 0) {
                    for (Integer hdCoinId : issuedCoins) {
                        editor.remove(c.getString(R.string.hdCoinIdColon) + hdCoinId);
                    }
                    editor.remove(c.getString(R.string.issuedCoins));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            editor.commit();
        }
    }
}
