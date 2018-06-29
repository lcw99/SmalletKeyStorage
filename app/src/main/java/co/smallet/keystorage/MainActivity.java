package co.smallet.keystorage;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.liquidplayer.service.MicroService;
import org.liquidplayer.service.MicroService.ServiceStartListener;
import org.liquidplayer.service.MicroService.EventListener;
import org.liquidplayer.service.Synchronizer;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jFactory;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import co.smallet.smalletlib.GlobalConstants;

public class MainActivity extends AppCompatActivity {
    static String address = null;
    static String privateKey;
    static MainActivity main;
    EditText etSeed;
    Spinner spCoins;
    HashMap<Integer, Coin> coinList = null;

    private TextView mTextMessage;

    KeyStorageService mKeyStorageService;
    boolean mBound = false;
    Web3j web3j = null;

    private String passwordHash;
    private String encSeed;
    private String ivSeed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        main = this;

        spCoins = (Spinner) findViewById(R.id.spCoins);

        web3j = Web3jFactory.build(new HttpService("https://ropsten.infura.io/du9Plyu1xJErXebTWjsn"));

        mTextMessage = (TextView) findViewById(R.id.message);
        BottomNavigationView navigation = (BottomNavigationView) findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        final TextView textView = (TextView) findViewById(R.id.text);
        final Button button = (Button) findViewById(R.id.button);

        // Our 'ready' listener will wait for a ready event from the micro service.  Once
        // the micro service is ready, we'll ping it by emitting a "ping" event to the
        // service.
        final EventListener readyListener = new EventListener() {
            @Override
            public void onEvent(MicroService service, String event, JSONObject payload) {
                service.emit("ping");
            }
        };

        // Our micro service will respond to us with a "pong" event.  Embedded in that
        // event is our message.  We'll update the textView with the message from the
        // micro service.
        final EventListener pongListener = new EventListener() {
            @Override
            public void onEvent(MicroService service, String event, final JSONObject payload) {
                // NOTE: This event is typically called inside of the micro service's thread, not
                // the main UI thread.  To update the UI, run this on the main thread.
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            textView.setText(payload.getString("message"));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        };

        // Our start listener will set up our event listeners once the micro service Node.js
        // environment is set up
        final ServiceStartListener startListener = new ServiceStartListener() {
            @Override
            public void onStart(MicroService service, Synchronizer synchronizer) {
                service.addEventListener("ready", readyListener);
                service.addEventListener("pong", pongListener);
            }
        };

        // When our button is clicked, we will launch a new instance of our micro service.
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    URI uri = new URI("android.resource://co.smallet.keystorage/raw/service");
                    MicroService service = new MicroService(MainActivity.this, uri, startListener);
                    service.start();
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
            }
        });

        Intent intent = new Intent(this, LoginActivity.class);
        //startActivity(intent);

        if (coinList == null)
            generateAddress(60, -2, false);
        else
            showPublicKeys();
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
        Intent intent = new Intent(this, KeyStorageService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
    }

    public void loadEtherOfflineSigner(final String privateKey, final String to, final String value, final int chainId, final String nonce, final String gasPrice, final String gasLimits, final String dataStr) {
        // custom dialog
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.sign_dialog);
        dialog.setTitle("Confirm Transaction");

        // set the custom dialog components - text, image and button
        TextView text = (TextView) dialog.findViewById(R.id.text);
        BigDecimal val = Convert.fromWei(value, Convert.Unit.ETHER);
        BigDecimal gasPriceDec = Convert.fromWei(gasPrice, Convert.Unit.GWEI);
        String txInfo = "to: " + to + "\n" +
                "value: " + val.toString() + " Ether\n" +
                "nonce: " + nonce + "\n" +
                "chainId: " + chainId + "\n" +
                "gasPrice: " + gasPriceDec.toString() + " GWEI\n" +
                "gasLimits: " + gasLimits + "\n" +
                "data:" + dataStr;
        text.setText(txInfo);

        Button btReject = (Button) dialog.findViewById(R.id.btReject);
        btReject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                Message msg = new Message();
                msg.what = Constants.RETURN_TX;
                Bundle data = new Bundle();
                data.putString("txRaw", "rejected");
                msg.setData(data);
                mHandle.sendMessage(msg);
            }
        });

        Button btConfirm = (Button) dialog.findViewById(R.id.btConfirm);
        btConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                final WebView webView= Utils.initWebView((WebView)findViewById(R.id.webview2));
                JavaScriptInterfaceEtherOffSign jsInterface = new JavaScriptInterfaceEtherOffSign(main, webView);
                webView.addJavascriptInterface(jsInterface, "JSInterface");

                webView.setWebViewClient(new WebViewClient() {
                    public void onPageFinished(WebView view, String url) {
                        String scriptParam = "signTransactionOffline(" +
                                "'" + privateKey +"'," +
                                "'" + to +"'," +
                                "'" + value +"'," +
                                "" + chainId +"," +
                                "" + nonce +"," +
                                "'" + gasPrice +"'," +
                                "'" + gasLimits +"'," +
                                "'" + dataStr +"'"
                                +");";
                        Log.e("lcw", scriptParam);
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
        public void txReady(String txRaw) {
            Log.e("webview", "tx = " + txRaw);
            Message msg = new Message();
            msg.what = Constants.RETURN_TX;
            Bundle data = new Bundle();
            data.putString("txRaw", txRaw);
            msg.setData(data);

            mHandle.sendMessage(msg);
        }

    }

    static public Handler mHandle = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case Constants.SIGN_TX:
                    Bundle data = msg.getData();
                    String to = data.getString("to");
                    String value = data.getString("value");
                    int chainId = data.getInt("chainId");
                    String nonce = data.getString("nonce");
                    String gasPrice = data.getString("gasPrice");
                    String gasLimits = data.getString("gasLimits");
                    String dataStr = data.getString("data");
                    if (dataStr == null)
                        dataStr = "";
                    main.loadEtherOfflineSigner(privateKey, to, value, chainId, nonce, gasPrice, gasLimits, dataStr);
                    break;
                case Constants.RETURN_TX:
                    Intent i = new Intent();
                    i.setComponent(new ComponentName("co.smallet.wallet", "co.smallet.wallet.WalletService"));
                    i.putExtra("action", GlobalConstants.SERVICE_SIGN_TX);
                    i.putExtras(msg.getData());
                    main.startService(i);

                    final WebView webView = main.findViewById(R.id.webview2);
                    webView.loadUrl("about:blank");
                    break;
                case Constants.GENERATE_ADDRESS:
                    int hdCoinCode = msg.getData().getInt("hdCoinCode");
                    int index = msg.getData().getInt("addressIndex");
                    main.generateAddress(hdCoinCode, index, true);
                    break;
            }
        }
    };

    boolean masterSeedExist;
    private void generateAddress(final Integer hdCoinCode, final Integer keyIndex, final boolean returnToWallet) {
        String masterSeed = Utils.decryptMasterSeed(main);
        masterSeedExist = false;
        if (!masterSeed.equals(""))
            masterSeedExist = true;
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
                    Utils.addPublicAddress(main, hdCoinCode, address, keyIndex);
                    if (returnToWallet)
                        mKeyStorageService.returnAddressToWalletService(address);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
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

    private void showPublicKeys() {
        PublicKeysAdapter publicKeysAdapter = new PublicKeysAdapter(this);
        ArrayList<Integer> issuedCoins =  Utils.getIssuedCoinsFromPref(this);
        for (Integer hdCoinId : issuedCoins) {
            HashMap<Integer, String> publicAddressList = Utils.getPublicAddressListFromPref(this, hdCoinId);
            for (Integer keyIndex : publicAddressList.keySet()) {
                String publicAddress = publicAddressList.get(keyIndex);
                String coinName = coinList.get(hdCoinId).name;
                String coinNameShort = coinName.replace(" ", "").replace("-", "_").toLowerCase();
                int imageId = getResources().getIdentifier(coinNameShort, "drawable", "co.smallet.keystorage");
                publicKeysAdapter.addPublicKey(coinName, keyIndex, publicAddress, imageId);
            }
        }
        RecyclerView rvPublicKeys = findViewById(R.id.rvPublicKeys);
        rvPublicKeys.setAdapter(publicKeysAdapter);
        rvPublicKeys.setLayoutManager(new LinearLayoutManager(this));
    }

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_home:
                    mTextMessage.setText(R.string.title_home);
                    return true;
                case R.id.navigation_dashboard:
                    mTextMessage.setText(R.string.title_dashboard);
                    return true;
                case R.id.navigation_notifications:
                    mTextMessage.setText(R.string.title_notifications);
                    return true;
            }
            return false;
        }
    };


}
