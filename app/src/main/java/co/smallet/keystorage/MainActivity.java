package co.smallet.keystorage;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.webkit.WebView;
import android.widget.Toast;

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

// note globe between process will soon hello rain bone easily potato fragile
public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    public static MainActivity main;
    Spinner spCoins;
    HashMap<Integer, Coin> coinList = null;

    private TextView mTextMessage;

    KeyStorageService mKeyStorageService;
    boolean mBound = false;
    Web3j web3j = null;

    String onStartAction;
    String callPackage;
    String callClass;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        main = this;

        Intent intent = getIntent();
        if (intent != null) {
            onStartAction = intent.getAction();
            Log.e("keystorage", "action=========================" + onStartAction);
            if (onStartAction != null && onStartAction.equals("return_to_wallet")) {
                callPackage = intent.getStringExtra("call_package");
                callClass = intent.getStringExtra("call_class");
            }
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        spCoins = (Spinner) findViewById(R.id.spCoins);

        web3j = Web3jFactory.build(new HttpService("https://ropsten.infura.io/du9Plyu1xJErXebTWjsn"));

        mTextMessage = (TextView) findViewById(R.id.message);

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

    public void loadEtherOfflineSigner(final String privateKey, final String to, final String value, final int chainId, final String nonce, final String gasPrice, final String gasLimits, final String dataStr, final String dataInfoStr) {
        // custom dialog
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.sign_dialog);
        dialog.setTitle("Confirm Transaction");

        // set the custom dialog components - text, image and button
        BigDecimal val = Convert.fromWei(value, Convert.Unit.ETHER);
        BigDecimal gasPriceDec = Convert.fromWei(gasPrice, Convert.Unit.GWEI);
        String txInfo =
                "nonce: " + nonce + "\n" +
                "chainId: " + chainId + "\n" +
                "gasPrice: " + gasPriceDec.toString() + " GWEI\n" +
                "gasLimits: " + gasLimits;
        if (dataStr.length() > 0)
            txInfo += "\ndata:" + dataInfoStr;
        TextView text = (TextView) dialog.findViewById(R.id.twTo);
        text.setText(to);
        text = (TextView) dialog.findViewById(R.id.twValue);
        text.setText(val.toString() + " Ether");
        text = (TextView) dialog.findViewById(R.id.twOtherInfo);
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
                    Log.e("keystorage", "from=" + from + ", to=" + to);
                    String dataInfoStr = "";
                    if (dataStr == null)
                        dataStr = "";
                    else
                        dataInfoStr = data.getString("datainfo", "");
                    String privateKey = Utils.getPrivateKey(main, from);
                    main.loadEtherOfflineSigner(privateKey, to, value, chainId, nonce, gasPrice, gasLimits, dataStr, dataInfoStr);
                    break;
                case Constants.RETURN_TX:
                    main.mKeyStorageService.returnRawTxToWalletService(msg.getData());

                    final WebView webView = main.findViewById(R.id.webview2);
                    webView.setWebChromeClient(null);
                    webView.setWebViewClient(null);
                    webView.loadUrl("about:blank");
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
                    Utils.addAddressToPref(main, hdCoinCode, address, keyIndex, privateKey, owner);
                    showPublicKeys();
                    String ownerAddressList = Utils.getAddressListForOwnerFromPrefEncoded(main, owner);
                    if (returnToWallet) {
                        Log.e("keystorage", "current owner address size=" + Utils.getAddressListForOwnerFromPref(main, owner).size());
                        mKeyStorageService.returnAddressToWalletService(address, ownerAddressList, true);
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

    private void showPublicKeys() {
        if (coinList == null)
            return;
        PublicKeysAdapter publicKeysAdapter = new PublicKeysAdapter(this);
        ArrayList<Integer> issuedCoins =  Utils.getIssuedCoinsFromPref(this);
        for (Integer hdCoinId : issuedCoins) {
            HashMap<Integer, String> publicAddressList = Utils.getAddressListFromPref(this, "publickey", hdCoinId);
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

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
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
        } else if (id == R.id.nav_remove_all_account) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
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

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }
}
