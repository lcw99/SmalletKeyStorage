package co.smallet.keystorage;

import android.app.Activity;
import android.app.Dialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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
import android.widget.Button;
import android.widget.TextView;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.liquidplayer.service.MicroService;
import org.liquidplayer.service.MicroService.ServiceStartListener;
import org.liquidplayer.service.MicroService.EventListener;
import org.liquidplayer.service.Synchronizer;

import java.net.URI;
import java.net.URISyntaxException;

public class MainActivity extends AppCompatActivity {
    static String address = null;
    static String privateKey;
    KeyStorageService mKeyStorageService;
    static MainActivity main;

    private TextView mTextMessage;

    boolean mBound = false;

    static public Handler mHandle = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == Constants.SIGN_TX){
                Bundle data = msg.getData();
                String to = data.getString("to");
                String value = data.getString("value");
                int chainId = data.getInt("chainId");
                int nonce = data.getInt("nonce");
                String gasPrice = data.getString("gasPrice");
                String gasLimits = data.getString("gasLimits");
                String dataStr = data.getString("data");
                if (dataStr == null)
                    dataStr = "";
                main.loadEtherOfflineSigner(privateKey, to, value, chainId, nonce, gasPrice, gasLimits, dataStr);
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
            if (address == null)
                loadKeyGenerator(null);
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

        Button btGenerate = (Button) findViewById(R.id.btGenerate);
        btGenerate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                main.loadKeyGenerator(null);
            }
        });

        Button btGenerate2 = (Button) findViewById(R.id.btGenerate2);
        btGenerate2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                main.loadKeyGenerator("");
            }
        });

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
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
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

    String currentSeed = null;
    WebView webViewBIP39;
    private void loadKeyGenerator(String seed) {
        currentSeed = seed;
        if (seed == null)
            currentSeed = "note globe between process will soon hello rain bone easily potato fragile";

        webViewBIP39 = initWebView(R.id.webview);
        JavaScriptInterfaceKeyStorage jsInterface = new JavaScriptInterfaceKeyStorage(this, webViewBIP39);
        webViewBIP39.addJavascriptInterface(jsInterface, "JSInterface");

        webViewBIP39.setWebViewClient(new WebViewClient() {
            public void onPageFinished(WebView view, String url) {
                webViewBIP39.evaluateJavascript("callGenerateClick('36', '12', '" + currentSeed + "');", new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String s) {
                        Log.i("keystorage", s);
                    }
                });
            }
        });

        webViewBIP39.loadUrl("file:///android_res/raw/bip39standalone.html");
    }

    TextView twSeed;
    public void getSeed() {
        twSeed = (TextView) findViewById(R.id.twSeed);
        webViewBIP39.evaluateJavascript("getSeed();", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String seed) {
                Log.i("keystorage", "seed=" + seed);
                currentSeed = seed;
                twSeed.post(new Runnable() {
                    @Override
                    public void run() {
                        twSeed.setText(currentSeed);
                    }
                });
            }
        });
    }

    public void loadEtherOfflineSigner(final String privateKey, final String to, final String value, final int chainId, final int nonce, final String gasPrice, final String gasLimits, final String dataStr) {
        // custom dialog
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.sign_dialog);
        dialog.setTitle("Confirm Transaction");

        // set the custom dialog components - text, image and button
        TextView text = (TextView) dialog.findViewById(R.id.text);
        String txInfo = "to: " + to + "\n" +
                "value: " + value + "\n" +
                "nonce: " + nonce + "\n" +
                "chainId: " + chainId + "\n" +
                "gasPrice: " + gasPrice + "\n" +
                "gasLimits: " + gasLimits + "\n" +
                "data:" + dataStr;
        text.setText(txInfo);

        Button dialogButton = (Button) dialog.findViewById(R.id.dialogButtonOK);
        // if button is clicked, close the custom dialog
        dialogButton.setOnClickListener(new View.OnClickListener() {
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
            Intent walletIntent = buildWalletIntent("TX_DATA");
            walletIntent.putExtra("txData", txRaw);
            sendBroadcast(walletIntent);
            //startActivityForResult(walletIntent, Constants.SIGN_TX);
        }

    }

    private Intent buildWalletIntent(String resultType) {
        Intent walletIntent = new Intent("co.smallet.wallet.RESULT_DATA");
        //walletIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        //walletIntent.setComponent(new ComponentName("co.smallet.wallet","co.smallet.wallet.MainActivity"));
        walletIntent.putExtra("resultType", resultType);
        return walletIntent;
    }

    public static class MyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d("keystorage", "onReceive===============================================");
            if (intent.getAction().equals("co.smallet.keystorage.ASK_DATA")) {
                String function = intent.getStringExtra("function");
                if (function.equals("GET_PUBLIC_ADDRESS")) {
                    Intent walletIntent = main.buildWalletIntent("PUBLIC_ADDRESS");
                    String address = KeyStorageService.getPublicAddress();
                    Log.e("keystorage", "addr=" + address);
                    walletIntent.putExtra("ADDRESS", address);
                    main.sendBroadcast(walletIntent);
                } else if(function.equals("SIGN_TX")) {
                    Intent i = new Intent(context, MainActivity.class);
                    context.startActivity(i);
                    Message msg = new Message();
                    msg.what = Constants.SIGN_TX;
                    msg.setData(intent.getBundleExtra("data"));
                    MainActivity.mHandle.sendMessage(msg);
                }
            }

        }
    }


}
