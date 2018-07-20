package co.smallet.keystorage;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Message;
import android.util.Base64;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.util.ArrayList;

import co.smallet.keystorage.contentprovider.KeyVaultContentProvider;
import co.smallet.keystorage.database.KeystorageDatabase;
import co.smallet.smalletandroidlibrary.AddressInfo;

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

    public static int getVersionCode(Context c) {
    try {
            PackageInfo pInfo = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pInfo.versionCode;
        } catch(PackageManager.NameNotFoundException e) {
            return -1;
        }
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

    public static void addAddressToDatabase(Integer hdCoinId, String address, Integer keyIndex, String privateKey, String owner) {
        String addressForKey = address;
        if (hdCoinId == 60)     // ETH is case insensitive
            addressForKey = address.toLowerCase();

        byte[] encData = null;
        byte[] iv = null;
        if (MainActivity.database.queryPrivateKeyEncrypted(addressForKey).getCount() == 0) {
            try {
                EnCryptor enc = new EnCryptor();
                encData = enc.encryptText(addressForKey, privateKey);
                iv = enc.getIv();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (encData != null && iv != null) {
            MainActivity.database.insertPrivateKey(addressForKey, Base64.encodeToString(encData, Base64.DEFAULT), Base64.encodeToString(iv, Base64.DEFAULT));
            addDataToContentProvider(hdCoinId, address, keyIndex, owner);
        }
    }

    private static void addDataToContentProvider(Integer hdCoinId,  String address, Integer keyIndex, String owner) {
        if (isKeyExistInDatabase(address))
            return;
        ContentValues values = new ContentValues();
        values.put(KeyVaultContentProvider.PUBLICKEY, address);
        values.put(KeyVaultContentProvider.KEYINDEX, keyIndex);
        values.put(KeyVaultContentProvider.HDCOINID, hdCoinId);
        values.put(KeyVaultContentProvider.OWNER, owner);

        //Uri uri = c.getContentResolver().insert(CONTENT_URI, values);
        KeyVaultContentProvider.myInsert(values);
    }

    private static boolean isKeyExistInDatabase(String address) {
        Cursor c = MainActivity.database.queryPrivateKeyEncrypted(address);
        return c.getCount() == 1;
    }

    private static void deleteAllPublicKeyInContentProviderAndDatabase(Context context) {
        //context.getContentResolver().delete(CONTENT_URI, null, null);
        KeyVaultContentProvider.myDelete(null, null);
        MainActivity.database.delete(KeystorageDatabase.PRIVATE_KEY_TABLE_NAME, null, null);
    }

    public static String getPublicAddressFromDatabase(Integer hdCoinId, String owner, int keyIndex) {
        Cursor c = KeyVaultContentProvider.queryPublicAddress(owner, hdCoinId, keyIndex);
        if(c.moveToNext())
            return c.getString(c.getColumnIndex(KeyVaultContentProvider.PUBLICKEY));
        else
            return null;
    }

    public static ArrayList<AddressInfo> getAddressListForOwnerFromDatabase(String owner) {
        ArrayList<AddressInfo> addressInfos = new ArrayList<>();
        Cursor c = KeyVaultContentProvider.queryPublicAddress(owner, -1, -1);
        while(c.moveToNext()) {
            AddressInfo addresInfo = new AddressInfo(c.getInt(c.getColumnIndex(KeyVaultContentProvider.HDCOINID)),
                            c.getInt(c.getColumnIndex(KeyVaultContentProvider.KEYINDEX)),
                            c.getString(c.getColumnIndex(KeyVaultContentProvider.PUBLICKEY)));
            addressInfos.add(addresInfo);
        }
        return addressInfos;
    }


    public static String decryptMasterSeed(Context c) {
        return decryptData(c, c.getString(R.string.master_seed), c.getString(R.string.encSeed), c.getString(R.string.ivSeed));
    }

    public static boolean isMasterKeyExist(Context c) {
        String encData = Utils.getPref(c).getString(c.getString(R.string.encSeed), null);
        return encData != null;
    }

    private static String decryptData(Context c, String alias, String dataKey, String ivKey) {
        String encData = Utils.getPref(c).getString(dataKey, "");
        String iv = Utils.getPref(c).getString(ivKey, "");
        return decryptData(alias, encData, iv);
    }

    private static String decryptData(String alias, String pkDataEnc, String pkIvEnc) {
        try {
            DeCryptor dec = new DeCryptor();
            String retVal = dec.decryptData(alias, Base64.decode(pkDataEnc, Base64.DEFAULT), Base64.decode(pkIvEnc, Base64.DEFAULT));
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
            deleteAllPublicKeyInContentProviderAndDatabase(c);
        }
    }

    public static String getPrivateKey(String address) {
        Cursor c =MainActivity.database.queryPrivateKeyEncrypted(address);
        if (c.moveToNext()) {
            String pkDataEnc = c.getString(c.getColumnIndex(KeystorageDatabase.PRIVATE_KEY_DATA));
            String pkIvEnc = c.getString(c.getColumnIndex(KeystorageDatabase.PRIVATE_KEY_IV));
            return decryptData(address, pkDataEnc, pkIvEnc);
        }
        return null;
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

        deleteAllPublicKeyInContentProviderAndDatabase(c);
    }
}
