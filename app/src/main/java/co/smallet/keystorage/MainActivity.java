package co.smallet.keystorage;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.MenuItem;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
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
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    static String address = null;
    static String privateKey;
    static MainActivity main;
    EditText etSeed;
    Spinner spCoins;

    private TextView mTextMessage;

    KeyStorageService mKeyStorageService;
    boolean mBound = false;
    Web3j web3j = null;

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
                    i.putExtra("action", Constants.SERVICE_SIGN_TX);
                    i.putExtras(msg.getData());
                    main.startService(i);

                    final WebView webView = main.findViewById(R.id.webview2);
                    webView.loadUrl("about:blank");
                    break;
            }
        }
    };

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        main = this;

        etSeed = (EditText) findViewById(R.id.etSeed);
        spCoins = (Spinner) findViewById(R.id.spCoins);
        webViewBIP39 = initWebView(R.id.webview);

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

        Button btImportSeed = (Button) findViewById(R.id.btImportSeed);
        btImportSeed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String seed = etSeed.getText().toString();
                main.loadKeyGenerator(seed);
            }
        });

        Button btGenerateNew = (Button) findViewById(R.id.btGenerateNew);
        btGenerateNew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                main.loadKeyGenerator("");
            }
        });

        loadSeed();
        loadKeyGenerator(currentSeed);
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

    boolean coinPopulated = false;
    private void populateCoins() {
        if (coinPopulated)
            return;
        webViewBIP39.evaluateJavascript("getNetworkList();", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String networkListStr) {
                try {
                    networkListStr = networkListStr.substring(1, networkListStr.length() - 1).replace("\\","");
                    JSONArray networkList = new JSONArray(networkListStr);
                    List<String> list = new ArrayList<String>();
                    for (int i = 0; i < networkList.length(); i++) {
                        list.add(((JSONObject)networkList.get(i)).get("name").toString());
                    }
                    ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(MainActivity.this,
                            android.R.layout.simple_spinner_item, list);
                    dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spCoins.setAdapter(dataAdapter);
                    spCoins.setOnItemSelectedListener(new CustomOnItemSelectedListener());
                    spCoins.setSelection(currentCoin);
                    coinPopulated = true;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public class CustomOnItemSelectedListener implements OnItemSelectedListener {
        public void onItemSelected(AdapterView<?> parent, View view, int pos,long id) {
            Toast.makeText(parent.getContext(), "Selected Coin : " + parent.getItemAtPosition(pos).toString(), Toast.LENGTH_SHORT).show();
            webViewBIP39.evaluateJavascript("callGenerateClick('"+ pos + "', '12', '" + currentSeed + "');", new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String s) {
                    Log.i("keystorage", s);
                    saveSeed();
                }
            });
        }

        @Override
        public void onNothingSelected(AdapterView<?> arg0) {
        }
    }
    private WebView initWebView(int id) {
        final WebView webView = (WebView) findViewById(id);
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

    // note globe between process will soon hello rain bone easily potato fragile;
    String currentSeed = null;
    int currentCoin = 36;
    WebView webViewBIP39;
    private void loadKeyGenerator(String seed) {
        Toast.makeText(this, "Seed Generating", Toast.LENGTH_SHORT).show();
        currentSeed = seed;

        JavaScriptInterfaceKeyStorage jsInterface = new JavaScriptInterfaceKeyStorage(this, webViewBIP39);
        webViewBIP39.addJavascriptInterface(jsInterface, "JSInterface");

        webViewBIP39.setWebViewClient(new WebViewClient() {
            public void onPageFinished(WebView view, String url) {
                webViewBIP39.evaluateJavascript("callGenerateClick('" + currentCoin + "', '12', '" + currentSeed + "');", new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String s) {
                        Log.i("keystorage", s);
                    }
                });
                populateCoins();
            }
        });

        webViewBIP39.loadUrl("file:///android_res/raw/bip39standalone.html");
    }

    public void getSeed() {
        webViewBIP39.evaluateJavascript("getSeed();", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String seed) {
                Log.i("keystorage", "seed=" + seed);
                currentSeed = seed.replace("\"", "");
                etSeed.post(new Runnable() {
                    @Override
                    public void run() {
                        etSeed.setText(currentSeed, TextView.BufferType.EDITABLE);
                        saveSeed();
                    }
                });
            }
        });
        //webViewBIP39.loadUrl("about:blank");
    }

    private void saveSeed() {
        SharedPreferences.Editor editor = getSharedPreferences(Constants.MY_PREFS_NAME, MODE_PRIVATE).edit();
        editor.putString("seed", currentSeed);
        editor.putInt("coin", currentCoin);
        editor.apply();
    }

    private void loadSeed() {
        SharedPreferences prefs = getSharedPreferences(Constants.MY_PREFS_NAME, MODE_PRIVATE);
        currentSeed = prefs.getString("seed", "");
        currentCoin = prefs.getInt("coin", 36);
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
                final WebView webView= initWebView(R.id.webview2);
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

    TextView twHello;
    public class JavaScriptInterfaceKeyStorage {
        private Context mContext;
        private WebView mWebView;

        public JavaScriptInterfaceKeyStorage(Context c, WebView webView) {
            this.mContext = c;
            this.mWebView = webView;
        }

        @JavascriptInterface
        public void walletAddressReady(String ready) {
            Log.e("webview", "wallet ready = " + ready);

            mWebView.post(new Runnable() {
                @Override
                public void run() {
                mWebView.evaluateJavascript("getAddress(0);", new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String s) {
                    Log.i("webview", s);
                    Toast.makeText(mContext, "addr=" + s, Toast.LENGTH_SHORT).show();
                    try {
                        JSONObject mainObject = new JSONObject(s);
                        address = mainObject.getString("addr");
                        privateKey = mainObject.getString("pk");
                        twHello = (TextView) findViewById(R.id.text);
                        twHello.post(new Runnable() {
                            public void run() {
                            twHello.setText(address);
                            }
                        });

                    } catch (Exception ex) {};
                    mKeyStorageService.setPublicAddress(address);
                    getSeed();
                    /*
                    loadEtherOfflineSigner(
                            privateKey,
                            "0xF6791CB4A2037Ddb58221b84678a6ba992cda11d",
                            "1000000000",
                            3,
                            4,
                            "1000000000",
                            "300000"
                    );
                    */
                    }
                });
                }
            });
        }
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

    public static Intent buildWalletIntent(String resultType) {
        Intent walletIntent = new Intent("co.smallet.wallet.RESULT_DATA");
        //walletIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        //walletIntent.setComponent(new ComponentName("co.smallet.wallet","co.smallet.wallet.MainActivity"));
        walletIntent.putExtra("resultType", resultType);
        return walletIntent;
    }

}
