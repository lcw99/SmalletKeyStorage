package co.smallet.keystorage;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.hash.Hashing;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

public class SignupActivity extends AppCompatActivity {
    private static final String TAG = "SignupActivity";

    @BindView(R.id.ck_have_seed) CheckBox _haveSeedCheckbox;
    @BindView(R.id.input_seed) EditText _seedText;
    @BindView(R.id.input_pass_phrase) EditText _passphraseText;
    @BindView(R.id.input_password) EditText _passwordText;
    @BindView(R.id.input_reEnterPassword) EditText _reEnterPasswordText;
    @BindView(R.id.btn_signup) Button _signupButton;
    @BindView(R.id.link_login) TextView _loginLink;
    @BindView(R.id.text_backup_seed) TextView _backupSeedInfo;
    @BindView(R.id.current_seed) TextView _currentSeed;

    String password;
    String seedGenerated = "";
    String wordCount = "12";
    String passphrase = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);
        ButterKnife.bind(this);

        _signupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signup();
            }
        });

        _loginLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Finish the registration screen and return to the Login activity
                Intent intent = new Intent(getApplicationContext(),LoginActivity.class);
                startActivity(intent);
                finish();
                overridePendingTransition(R.anim.push_left_in, R.anim.push_left_out);
            }
        });

        _haveSeedCheckbox.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (isChecked) {
                    _seedText.setVisibility(View.VISIBLE);
                    _passphraseText.setVisibility(View.VISIBLE);
                    _seedText.requestFocus();
                    _signupButton.setText("Import Seed");
                } else {
                    _seedText.setVisibility(View.GONE);
                    _passphraseText.setVisibility(View.GONE);
                    _signupButton.setText("Create Account");
                }
            }
        });

        if (Utils.isMasterKeyExist(this)) {
            _currentSeed.setText(Utils.decryptMasterSeed(this));
        }
    }

    public void signup() {
        Log.d(TAG, "Signup");
        if (_signupButton.getText().equals(getResources().getString(R.string.done_seed_backup)) || _signupButton.getText().equals("Login")) {
            Utils.encryptMasterSeedAndSave(this, seedGenerated, passphrase);

            final String passwordHash = Hashing.sha256().hashString(password, Charset.defaultCharset()).toString();
            SharedPreferences.Editor editor = Utils.getPref(this).edit();
            editor.putString(getString(R.string.passwordHash), passwordHash);
            editor.commit();

            finish();
            return;
        }

        _backupSeedInfo.setVisibility(View.GONE);

        if (!validate()) {
            onSignupFailed();
            return;
        }

        final String seedText = _seedText.getText().toString();
        passphrase = _passphraseText.getText().toString();
        SeedGenerationDialog dialog = new SeedGenerationDialog(this, seedText, passphrase, wordCount, 60, 0,  new SeedGenerationDialog.ReturnValueEvent() {
            @Override
            public void onReturnValue(String data, HashMap<Integer, Coin> coinList) {
                if (data.startsWith("error")) {
                    _backupSeedInfo.setText("Import failed - " + data.replace("error=", ""));
                    _backupSeedInfo.setVisibility(View.VISIBLE);
                    return;
                }
                JSONObject mainObject = null;
                String seed = "";
                try {
                    mainObject = new JSONObject(data);
                    seed = mainObject.getString("seed");
                } catch (JSONException e) {
                    e.printStackTrace();
                    return;
                }

                seedGenerated = seed;
                _seedText.setVisibility(View.VISIBLE);
                _seedText.setText(seed, TextView.BufferType.EDITABLE);
                _seedText.setKeyListener(null);
                _haveSeedCheckbox.setVisibility(View.GONE);
                if (!seedText.equals("")) {
                    _backupSeedInfo.setText("Master seed Import successful.");
                    _signupButton.setText("Login");
                } else {
                    _signupButton.setText(R.string.done_seed_backup);
                }
                if (passphrase.equals(""))
                    _passphraseText.setVisibility(View.GONE);
                _backupSeedInfo.setVisibility(View.VISIBLE);
                password = _passwordText.getText().toString();
                _passwordText.setText("", TextView.BufferType.EDITABLE);
                _passwordText.setVisibility(View.GONE);
                _reEnterPasswordText.setText("", TextView.BufferType.EDITABLE);
                _reEnterPasswordText.setVisibility(View.GONE);
            }
        });
        dialog.show();
    }


    public void onSignupFailed() {
        Toast.makeText(getBaseContext(), "Account creation failed", Toast.LENGTH_LONG).show();

        _signupButton.setEnabled(true);
    }

    public boolean validate() {
        boolean valid = true;

        String seed = _seedText.getText().toString();
        String passphrase = _passphraseText.getText().toString();
        String password = _passwordText.getText().toString();
        String reEnterPassword = _reEnterPasswordText.getText().toString();

        if (_seedText.getVisibility() == View.VISIBLE && seed.isEmpty()) {
            _seedText.setError("enter valid seed words");
            valid = false;
        } else if (_seedText.getVisibility() == View.VISIBLE) {
            wordCount = Utils.getWordCount(seed).toString();
            List<String> strengthList = Arrays.asList("12", "15", "18", "21", "24");
            if (!strengthList.contains(wordCount)) {
                _seedText.setError("number of words in the seed word must be 12, 15, 18, 21 or 24.");
                valid = false;
            } else {
                _seedText.setError(null);
            }
        }

        if (password.isEmpty() || password.length() < 4) {
            _passwordText.setError(getString(R.string.password_input_error));
            valid = false;
        } else {
            _passwordText.setError(null);
        }

        if (reEnterPassword.isEmpty() || reEnterPassword.length() < 4 || !(reEnterPassword.equals(password))) {
            _reEnterPasswordText.setError("Password Do not match");
            valid = false;
        } else {
            _reEnterPasswordText.setError(null);
        }

        return valid;
    }
}