package co.smallet.keystorage;

import android.app.Dialog;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SeedGenerationDialog extends Dialog {

    Context dialog;
    WebView webViewBIP39;
    ReturnValueEvent mReturnValue;
    HashMap<Integer, Coin> coinList = new HashMap<Integer, Coin>();
    Integer coinIndex;
    private Integer coinHdCode;
    private Integer keyIndex;

    public interface ReturnValueEvent {
        void onReturnValue(String value, HashMap<Integer, Coin> _coinList);
    }

    public SeedGenerationDialog(@NonNull Context context, String seed, String passPhrase, String strength, Integer _coinHdCode, Integer _keyIndex, ReturnValueEvent event) {
        super(context);
        setContentView(R.layout.dialog_seed_generate);

        mReturnValue = event;
        dialog = context;
        coinHdCode = _coinHdCode;
        keyIndex = _keyIndex;

        if (keyIndex == -1) {
            startKeyGeneration(seed, passPhrase, strength, -2);
        } else if (coinList.size() == 0)
            startKeyGeneration(seed, passPhrase, strength, -1);
        else {
            coinIndex = coinList.get(_coinHdCode).optionsIndex;
            startKeyGeneration(seed, passPhrase, strength, coinIndex);
        }
    }

    private void startKeyGeneration(final String seed, final String passPhrase, final String strength, final Integer coinIndex) {
        webViewBIP39 = findViewById(R.id.webviewBIP39);
        Utils.initWebView(webViewBIP39);

        new android.os.Handler().post(new Runnable() {
            @Override
            public void run() {
                loadKeyGenerator(dialog, seed, passPhrase, strength, coinIndex);
            }
        });
    }

    private void loadKeyGenerator(final Context context, final String seed, final String passPhrase, final String strength, final Integer coinIndex) {
        JavaScriptInterfaceSeedGenerating jsInterface = new JavaScriptInterfaceSeedGenerating(context, webViewBIP39);
        webViewBIP39.addJavascriptInterface(jsInterface, "JSInterface");

        webViewBIP39.setWebViewClient(new WebViewClient() {
            public void onPageFinished(WebView view, String url) {
                if (coinIndex == -1 || coinIndex == -2) {
                    webViewBIP39.evaluateJavascript("getNetworkList();", new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String networkListStr) {
                            try {
                                networkListStr = networkListStr.substring(1, networkListStr.length() - 1).replace("\\","");
                                JSONArray networkList = new JSONArray(networkListStr);
                                for (int i = 0; i < networkList.length(); i++) {
                                    String name = ((JSONObject)networkList.get(i)).get("name").toString();
                                    int hdCoin = Integer.parseInt(((JSONObject)networkList.get(i)).get("hdCoin").toString());
                                    coinList.put(hdCoin, new Coin(i, name));
                                }
                                if (coinIndex != -2) {
                                    int _coinIndex = coinList.get(coinHdCode).optionsIndex;
                                    startKeyGeneration(seed, passPhrase, strength, _coinIndex);
                                } else {
                                    mReturnValue.onReturnValue("", coinList);
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                } else {
                    String script = "callGenerateClick('" + coinIndex + "','" + strength + "','" + seed + "','" + passPhrase + "');";
                    Log.e("keystorage", script);
                    webViewBIP39.evaluateJavascript(script, new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String s) {
                            Log.i("keystorage", s);
                        }
                    });
                }
            }
        });

        webViewBIP39.loadUrl("file:///android_res/raw/bip39standalone.html");
    }


    public class JavaScriptInterfaceSeedGenerating {
        private Context mContext;
        private WebView mWebView;

        public JavaScriptInterfaceSeedGenerating(Context c, WebView webView) {
            this.mContext = c;
            this.mWebView = webView;
        }

        @JavascriptInterface
        public void walletAddressReady(final String ready) {
            Log.e("webview", "wallet ready = " + ready);

            if (ready.startsWith("error")) {
                if (ready.startsWith("error=Invalid root key"))
                    return;
                mWebView.post(new Runnable() {
                    @Override
                    public void run() {
                        mReturnValue.onReturnValue(ready, coinList);
                        SeedGenerationDialog.this.dismiss();
                    }
                });
                return;
            }
            mWebView.post(new Runnable() {
                @Override
                public void run() {
                    webViewBIP39.evaluateJavascript("getSeedAndAddress(" + keyIndex + ");", new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String data) {
                            Log.e("keystorage", "data=" + data);
                            mReturnValue.onReturnValue(data, coinList);
                            SeedGenerationDialog.this.dismiss();
                        }
                    });
                }
            });
        }
    }

}
