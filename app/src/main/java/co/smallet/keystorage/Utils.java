package co.smallet.keystorage;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Message;
import android.preference.Preference;
import android.util.Base64;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import co.smallet.smalletandroidlibrary.AddressInfo;
import co.smallet.smalletandroidlibrary.ObjectSerializer;

import static android.content.Context.MODE_PRIVATE;

public class Utils {
    @SuppressLint("SetJavaScriptEnabled")
    public static WebView initWebView(WebView webView) {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.e("webview", consoleMessage.message() + " -- From line "
                        + consoleMessage.lineNumber() + " of "
                        + consoleMessage.sourceId());
                String consoleMsg = consoleMessage.message();
                if (consoleMsg.toLowerCase().contains("error")) {
                    Message msg = new Message();
                    msg.what = Constants.TOAST_MESSAGE;
                    Bundle data = new Bundle();
                    data.putString("message", consoleMsg + "(" + consoleMessage.lineNumber() + ")");
                    msg.setData(data);
                    MainActivity.mHandle.sendMessage(msg);
                }
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
         return c.getSharedPreferences(Constants.PREFS_ADDRESS, MODE_PRIVATE);
    }

    public static void addAddressToPref(Context c,Integer hdCoinId,  String address, Integer keyIndex, String privateKey, String owner) {
        String addressForKey = address;
        if (hdCoinId == 60)     // ETH is case insensitive
            addressForKey = address.toLowerCase();

        ArrayList<Integer> issuedCoins = getIssuedCoinsFromPref(c);
        if (!issuedCoins.contains(hdCoinId))
            addIssuedCoins(c, hdCoinId);
        HashMap<Integer, String> publicKeys = getAddressListFromPref(c, "publickey", hdCoinId);
        ArrayList<AddressInfo> addressInfos = getAddressListForOwnerFromPref(c, owner);
        Log.e("keystorage", "addressInfos=" + addressInfos.size());
        byte[] encData = null;
        byte[] iv = null;
        SharedPreferences prefs = getPref(c);
        if (prefs.getString(getPrefKeyString("privatekeydata:", addressForKey), null) == null) {
            publicKeys.put(keyIndex, address);
            addressInfos.add(new AddressInfo(hdCoinId, keyIndex, address));
            try {
                EnCryptor enc = new EnCryptor();
                encData = enc.encryptText(addressForKey, privateKey);
                iv = enc.getIv();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // save the task list to preference
        SharedPreferences.Editor editor = getPref(c).edit();
        try {
            editor.putString(getPrefKeyString("publickey", hdCoinId.toString()), ObjectSerializer.serialize(publicKeys));
            editor.putString(getPrefKeyString("owner", owner), ObjectSerializer.serialize(addressInfos));
            if (encData != null) {
                editor.putString(getPrefKeyString("privatekeydata:", addressForKey), Base64.encodeToString(encData, Base64.DEFAULT));
                editor.putString(getPrefKeyString("privatekeyiv:", addressForKey), Base64.encodeToString(iv, Base64.DEFAULT));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        editor.commit();
    }

    public static HashMap<Integer, String> getAddressListFromPref(Context c, String prefix, Integer hdCoinId) {
        HashMap<Integer, String> publicKeys = new HashMap<>();
        SharedPreferences prefs = getPref(c);
        try {
            String str = prefs.getString(getPrefKeyString(prefix, hdCoinId.toString()), null);
            if (str != null)
                publicKeys = (HashMap<Integer, String>) ObjectSerializer.deserialize(str);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return publicKeys;
    }

    public static ArrayList<AddressInfo> getAddressListForOwnerFromPref(Context c, String owner) {
        ArrayList<AddressInfo> addressInfos = new ArrayList<>();
        String str = getAddressListForOwnerFromPrefEncoded(c, owner);
        try {
            if (str != null) {
                addressInfos = (ArrayList<AddressInfo>) ObjectSerializer.deserialize(str);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return addressInfos;
    }

    public static String getAddressListForOwnerFromPrefEncoded(Context c, String owner) {
        SharedPreferences prefs = getPref(c);
        String strEncoded = prefs.getString(getPrefKeyString("owner", owner), null);
        if (strEncoded != null) {
            try {
                ArrayList<AddressInfo> addressInfos = (ArrayList<AddressInfo>) ObjectSerializer.deserialize(strEncoded);
            } catch (ClassNotFoundException ex) {
                // AddressInfo class changed
                removeAllAccount(c);
                strEncoded = null;
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return strEncoded;
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
        return decryptData(c, c.getString(R.string.master_seed), c.getString(R.string.encSeed), c.getString(R.string.ivSeed));
    }

    public static boolean isMasterKeyExist(Context c) {
        String encData = Utils.getPref(c).getString(c.getString(R.string.encSeed), null);
        if (encData != null)
            return true;
        else
            return false;
    }

    private static String decryptData(Context c, String alias, String dataKey, String ivKey) {
        String encData = Utils.getPref(c).getString(dataKey, "");
        String iv = Utils.getPref(c).getString(ivKey, "");
        try {
            DeCryptor dec = new DeCryptor();
            String retVal = dec.decryptData(alias, Base64.decode(encData, Base64.DEFAULT), Base64.decode(iv, Base64.DEFAULT));
            return retVal;
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
            SharedPreferences prefs = getPref(c);
            SharedPreferences.Editor editor  = prefs.edit();
            editor.clear();     // mater seed changed, delete all prefs except login password
            try {
                editor.putString(c.getString(R.string.encSeed), Base64.encodeToString(encSeed, Base64.DEFAULT));
                editor.putString(c.getString(R.string.ivSeed), Base64.encodeToString(iv, Base64.DEFAULT));
            } catch (Exception e) {
                e.printStackTrace();
            }

            editor.commit();
        }
    }

    public static String getPrefKeyString(String prefix, String data) {
        return prefix + data;
    }

    public static String getPrivateKey(Context c, String address) {
        return decryptData(c, address, getPrefKeyString("privatekeydata:", address), getPrefKeyString("privatekeyiv:", address));
    }

    public static void removeAllAccount(Context c) {
        SharedPreferences pref = Utils.getPref(c);
        String encData = pref.getString(c.getString(R.string.encSeed), "");
        String iv = pref.getString(c.getString(R.string.ivSeed), "");
        String passwordHash = pref.getString(c.getString(R.string.passwordHash), "");
        SharedPreferences.Editor editor = pref.edit();
        editor.clear();
        if (!encData.equals("")) {
            editor.putString(c.getString(R.string.encSeed), encData);
            editor.putString(c.getString(R.string.ivSeed), iv);
            editor.putString(c.getString(R.string.passwordHash), passwordHash);
        }
        editor.commit();
    }
}
