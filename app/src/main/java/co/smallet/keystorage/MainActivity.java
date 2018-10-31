package co.smallet.keystorage;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.webkit.WebView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.google.zxing.qrcode.QRCodeWriter;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import org.json.JSONException;
import org.json.JSONObject;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.Sign;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import co.smallet.keystorage.PublicKeysAdapter.ClickListener;
import co.smallet.keystorage.database.KeystorageDatabase;
import co.smallet.smalletandroidlibrary.AddressInfo;
import co.smallet.smalletandroidlibrary.GlobalConstants;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    static public Integer coinHdCodeETH = 60;        // ETH

    public static MainActivity main;
    Spinner spCoins;
    HashMap<Integer, Coin> coinList = null;
    private static int CODE_AUTHENTICATION_VERIFICATION = 241;

    KeyStorageService mKeyStorageService;
    boolean mBound = false;

    String onStartAction;
    String callPackage;
    String callClass;

    static public KeystorageDatabase database;

    boolean finishAfterSign;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences pref = Utils.getPref(this);
        int versionNumberSaved = pref.getInt("version_number", -1);
        int currentVersionNumber = Utils.getVersionCode(this);
        if (versionNumberSaved != currentVersionNumber) { // new version first run case

        }
        pref.edit().putInt("version_number", currentVersionNumber).apply();

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        finishAfterSign = sharedPref.getBoolean("finish_after_sign", true);

        if (!BuildConfig.DEBUG) {
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            if (km.isKeyguardSecure()) {
                Intent i = km.createConfirmDeviceCredentialIntent(getString(R.string.auth_required),
                        getString(R.string.auth_required_desc));
                startActivityForResult(i, CODE_AUTHENTICATION_VERIFICATION);
            } else {
                Intent intent = new Intent(this, LoginActivity.class);
                intent.putExtra("loginType", "loginOnly");
                startActivityForResult(intent, CODE_AUTHENTICATION_VERIFICATION);
            }
        }

        main = this;

        database = new KeystorageDatabase(this);

        Intent intent = getIntent();
        if (intent != null) {
            onStartAction = intent.getAction();
            Log.e("keystorage", "action=========================" + onStartAction);
            if (onStartAction != null && onStartAction.equals("return_to_wallet")) {
                callPackage = intent.getStringExtra("call_package");
                callClass = intent.getStringExtra("call_class");
            }
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        spCoins = findViewById(R.id.spCoins);

        if (coinList == null) {
            if (!Utils.isMasterKeyExist(this)) {
                Intent i = new Intent(this, SignupActivity.class);
                startActivity(i);
            } else {
                generateAddress(60, -2, "", false);
            }
        } else
            showPublicKeys();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode==CODE_AUTHENTICATION_VERIFICATION) {
            super.onActivityResult(requestCode, resultCode, data);
            if (resultCode == RESULT_OK ) {
                //Toast.makeText(this, "Success: Verified user's identity", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.auth_failed, Toast.LENGTH_LONG).show();
                finish();
            }
            return;
        }

        if (requestCode != IntentIntegrator.REQUEST_CODE) {
            // This is important, otherwise the result will not be passed to the fragment
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }

        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        String resultStr = result.getContents();
        if(resultStr == null) {
            Log.d("MainActivity", "Cancelled scan");
            Toast.makeText(this, "Scan cancelled", Toast.LENGTH_LONG).show();
        } else {
            Log.d("MainActivity", "Scanned");

            if (resultStr.startsWith("{")) {
                try {
                    JSONObject qrJson = new JSONObject(resultStr);
                    int action = qrJson.getInt("action");
                    if (action == GlobalConstants.SERVICE_SIGN_TX) {
                        Bundle bundle = new Bundle();
                        Iterator iter = qrJson.keys();
                        while(iter.hasNext()){
                            String key = (String)iter.next();
                            String value = qrJson.getString(key);
                            bundle.putString(key,value);
                        }
                        bundle.putString("extra", Constants.QR_CODE);
                        Message msgToSend = new Message();
                        msgToSend.what = Constants.SIGN_TX;
                        msgToSend.setData(bundle);
                        mHandle.sendMessageDelayed(msgToSend, 500);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    @Override
    protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bind to LocalService
        Intent intentNew = new Intent(this, KeyStorageService.class);
        bindService(intentNew, mConnection, Context.BIND_AUTO_CREATE);

        showPublicKeys();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        //((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(Constants.NOTIFICATION_ID);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unbind from the service
        if (mBound) {
            mKeyStorageService.stopForeground(true);
            unbindService(mConnection);
            mBound = false;
        }
    }

    public void loadEtherOfflineSigner(final String from, final String privateKey, final String to, final String value, final int chainId, final String nonce, final String gasPrice, final String gasLimits, final String dataStr, final String dataInfoStr, final String extra) {
        // custom dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.sign_dialog, null);
        builder.setTitle(R.string.confirm_transaction_title);
        BigDecimal val = Convert.fromWei(value, Convert.Unit.ETHER);
        BigDecimal gasPriceDec = Convert.fromWei(gasPrice, Convert.Unit.GWEI);
        String txInfo =
                "nonce: " + nonce + "\n" +
                "chainId: " + chainId + "\n" +
                "gasPrice: " + gasPriceDec.toString() + " GWEI\n" +
                "gasLimits: " + gasLimits;
        if (dataStr.length() > 0)
            txInfo += "\ndata:" + dataInfoStr;
        TextView text = dialogView.findViewById(R.id.twTo);
        text.setText(to);
        text = dialogView.findViewById(R.id.twValue);
        text.setText(val.toString() + " Ether");
        text = dialogView.findViewById(R.id.twOtherInfo);
        text.setText(txInfo);
        builder.setView(dialogView);
        final Dialog dialog = builder.create();
        Button btReject = dialogView.findViewById(R.id.btReject);
        btReject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                Message msg = new Message();
                msg.what = Constants.RETURN_TX;
                Bundle data = new Bundle();
                data.putString("txRaw", "error:rejected");
                data.putString("extra", extra);
                msg.setData(data);
                mHandle.sendMessage(msg);
            }
        });

        Button btConfirm = dialogView.findViewById(R.id.btConfirm);
        btConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();

                if (to.equals("SignMessageRaw")) {
                    Credentials creds = Credentials.create(privateKey);
                    Sign.SignatureData signed = Sign.signMessage(Numeric.hexStringToByteArray(dataStr), creds.getEcKeyPair(), false);
                    String encoded = Numeric.toHexString(signed.getR()) + Numeric.toHexString(signed.getS()).substring(2) + Integer.toHexString(signed.getV());

                    Message msg = new Message();
                    msg.what = Constants.RETURN_TX;
                    Bundle data = new Bundle();
                    data.putString("txRaw", encoded);
                    Log.e("keystorage", encoded);
                    data.putString("extra", extra);
                    msg.setData(data);
                    mHandle.sendMessage(msg);
                    return;
                }


                final WebView webView= Utils.initWebView((WebView)findViewById(R.id.webview2));
                JavaScriptInterfaceEtherOffSign jsInterface = new JavaScriptInterfaceEtherOffSign(main, webView);
                webView.addJavascriptInterface(jsInterface, "JSInterface");

                webView.setWebViewClient(new WebViewClient() {
                    public void onPageFinished(WebView view, String url) {
                        String scriptParam = "signTransactionOffline(" +
                                "'" + from +"'," +
                                "'" + privateKey +"'," +
                                "'" + to +"'," +
                                "'" + value +"'," +
                                "" + chainId +"," +
                                "" + nonce +"," +
                                "'" + gasPrice +"'," +
                                "'" + gasLimits +"'," +
                                "'" + dataStr +"'," +
                                "'" + extra +"'"
                                +");";
                        //Log.e("lcw", scriptParam);
                        webView.evaluateJavascript(scriptParam, new ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String s) {
                            }
                        });
                    }
                });

                webView.loadUrl("file:///android_res/raw/etheroffsign.html");
            }
        });

        dialog.show();
    }

    public class JavaScriptInterfaceEtherOffSign {
        private Context mContext;
        private WebView mWebView;

        public JavaScriptInterfaceEtherOffSign(Context c, WebView webView) {
            this.mContext = c;
            this.mWebView = webView;
        }

        @JavascriptInterface
        public void txReady(String txRaw, String extra) {
            Log.e("webview", "tx = " + txRaw);
            Message msg = new Message();
            msg.what = Constants.RETURN_TX;
            Bundle data = new Bundle();
            data.putString("txRaw", txRaw);
            data.putString("extra", extra);
            msg.setData(data);

            mHandle.sendMessage(msg);
        }

    }

    static public Handler mHandle = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case Constants.TOAST_MESSAGE:
                    String message = msg.getData().getString("message");
                    Toast.makeText(main, message, Toast.LENGTH_LONG).show();
                    break;

                case Constants.SIGN_TX:
                    Bundle data = msg.getData();
                    String from = data.getString("from");
                    String to = data.getString("to");
                    String value = data.getString("value");
                    int chainId = data.getInt("chainId");
                    String nonce = data.getString("nonce");
                    String gasPrice = data.getString("gasPrice");
                    String gasLimits = data.getString("gasLimits");
                    String dataStr = data.getString("data");
                    String extra = data.getString("extra");
                    Log.e("keystorage", "from=" + from + ", to=" + to);
                    String dataInfoStr = "";
                    if (dataStr == null)
                        dataStr = "";
                    else
                        dataInfoStr = data.getString("datainfo", "");
                    String privateKey = Utils.getPrivateKey(from);
                    main.loadEtherOfflineSigner(from, privateKey, to, value, chainId, nonce, gasPrice, gasLimits, dataStr, dataInfoStr, extra);
                    break;
                case Constants.RETURN_TX:
                    extra = msg.getData().getString("extra");
                    if (extra != null && extra.equals(Constants.QR_CODE)) {
                        data = msg.getData();
                        JSONObject json = new JSONObject();
                        Set<String> keys = data.keySet();
                        try {
                            for (String key : keys) {
                                json.put(key, JSONObject.wrap(data.get(key)));
                            }
                            json.put("type", "signedTransaction");
                            String jsonStr = json.toString();
                            showQRCode(jsonStr, "Signed Transaction");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                    main.mKeyStorageService.returnRawTxToWalletService(msg.getData());

                    final WebView webView = main.findViewById(R.id.webview2);
                    webView.setWebChromeClient(null);
                    webView.setWebViewClient(null);
                    webView.loadUrl("about:blank");
                    if (main.finishAfterSign)
                        main.finish();
                    break;
                case Constants.GENERATE_ADDRESS:
                    int hdCoinCode = msg.getData().getInt("hdCoinCode");
                    int index = msg.getData().getInt("addressIndex");
                    String owner = msg.getData().getString("owner");
                    main.generateAddress(hdCoinCode, index, owner, true);
                    break;
            }
        }
    };

    boolean masterSeedExist;
    private void generateAddress(final Integer hdCoinCode, final Integer keyIndex, final String owner, final boolean returnToWallet) {
        String masterSeed = Utils.decryptMasterSeed(main);
        masterSeedExist = !masterSeed.equals("");
        String passphrase = "";
        int passPhraseIndex = masterSeed.indexOf('|');
        if (passPhraseIndex > 0) {
            passphrase = masterSeed.substring(passPhraseIndex + 1);
            masterSeed = masterSeed.substring(0, passPhraseIndex - 1);
        }
        String wordCount = "12";
        if (masterSeedExist)
            wordCount = Utils.getWordCount(masterSeed).toString();
        SeedGenerationDialog dialog = new SeedGenerationDialog(this, masterSeed, passphrase, wordCount, hdCoinCode,  keyIndex, new SeedGenerationDialog.ReturnValueEvent() {
            @Override
            public void onReturnValue(String data, HashMap<Integer, Coin> _coinList) {
                coinList = _coinList;
                if(keyIndex == -2) {
                    showPublicKeys();
                    if (onStartAction != null && onStartAction.equals("return_to_wallet")) {
                        mKeyStorageService.returnAddressToWalletService(callPackage, callClass);
                    }
                    return;
                }
                if (data.startsWith("error")) {
                    Log.e("keystorage", "seed generation errer=" + data);
                    return;
                }
                JSONObject mainObject = null;
                try {
                    mainObject = new JSONObject(data);
                    String seed = mainObject.getString("seed");
                    String address = mainObject.getString("address");
                    String privateKey = mainObject.getString("privatekey");
                    if (!masterSeedExist) {
                        Utils.encryptMasterSeedAndSave(main, seed, "");
                    }
                    Utils.addAddressToDatabase(hdCoinCode, address, keyIndex, privateKey, owner);
                    showPublicKeys();
                    if (returnToWallet) {
                        mKeyStorageService.returnAddressToWalletService(address, true);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        //if(keyIndex != -2)
        dialog.show();
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            KeyStorageService.LocalBinder binder = (KeyStorageService.LocalBinder) service;
            mKeyStorageService = binder.getService();
            mBound = true;
            mKeyStorageService.setMainActivity(MainActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    PublicKeysAdapter publicKeysAdapter;

    private void showPublicKeys() {
        if (coinList == null)
            return;
        publicKeysAdapter = new PublicKeysAdapter(this, new ClickListener() {
            @Override
            public void onItemClicked(int position) {
                String publicKey = publicKeysAdapter.getItemPublicKey(position);

                JSONObject qrJson = new JSONObject();
                try {
                    qrJson.put("type", "publicKeyETH");
                    qrJson.put("publicKeyETH", publicKey);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                String qrText = qrJson.toString();

                showQRCode(qrText, getString(R.string.public_key));
            }

            @Override
            public void onItemLongClicked(int position) {

            }
        });
        ArrayList<AddressInfo> addressInfos = Utils.getAddressListForOwnerFromDatabase(null);

        for (AddressInfo ai : addressInfos) {
            String publicAddress = ai.getAddress();
            String coinName = coinList.get(ai.getHdCoindId()).name;
            String coinNameShort = coinName.replace(" ", "").replace("-", "_").toLowerCase();
            int imageId = getResources().getIdentifier(coinNameShort, "drawable", "co.smallet.keystorage");
            publicKeysAdapter.addPublicKey(coinName, ai.getKeyIndex(), publicAddress, imageId);
        }
        RecyclerView rvPublicKeys = findViewById(R.id.rvPublicKeys);
        rvPublicKeys.setAdapter(publicKeysAdapter);
        rvPublicKeys.setLayoutManager(new LinearLayoutManager(this));
    }

    private static  void showQRCode(String qrText, String title) {
        AlertDialog.Builder builder = new AlertDialog.Builder(main);
        LayoutInflater inflater = main.getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.qrcode_dialog, null);
        builder.setTitle(title);

        builder.setView(dialogView);
        final Dialog dialog = builder.create();

        QRCodeWriter writer = new QRCodeWriter();
        try {
            Display display = main.getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            int width = size.x;

            BitMatrix bitMatrix = writer.encode(qrText, BarcodeFormat.QR_CODE, width - 100, width - 100);
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.createBitmap(bitMatrix);
            ((ImageView) dialogView.findViewById(R.id.ivPublicAddressQR)).setImageBitmap(bitmap);

        } catch (WriterException e) {
            e.printStackTrace();
        }

        dialog.show();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(main, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();
        if (id == R.id.nav_master_seed) {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_scan_QR_code) {
            new IntentIntegrator(main)
                    .setBarcodeImageEnabled(true)
                    .initiateScan();
        } else  if (id == R.id.nav_add_account) {
            generateAddress(coinHdCodeETH, publicKeysAdapter.getItemCount(), getPackageName(), false);
        } else if (id == R.id.nav_remove_all_account) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppTheme_Dark_Dialog);
            builder.setTitle(R.string.remove_all_account_dialog_title)
                    .setMessage(R.string.remove_all_account_dialog_msg)
                    .setNegativeButton(R.string.dialog_button_cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                        }
                    })
                    .setPositiveButton(R.string.dialog_button_ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Utils.removeAllAccount(main);
                            showPublicKeys();
                        }
                    }).show();
        } else if (id == R.id.nav_share) {
        } else if (id == R.id.nav_send) {
            //Utils.getPref(this).edit().clear().commit();
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }
}
