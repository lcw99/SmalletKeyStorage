package co.smallet.keystorage;

import android.media.Image;
import android.provider.ContactsContract;

public class PublicKey {
    private String coinType;
    private Integer keyIndex;
    private String publicKey;
    private Integer image;

    public PublicKey(String _coinType, Integer _keyIndex, String _publicKey, Integer _image) {
        coinType = _coinType;
        keyIndex = _keyIndex;
        publicKey = _publicKey;
        image = _image;
    }

    public String getCoinType() {
        return coinType;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public Integer getKeyIndex() {
        return keyIndex;
    }

    public Integer getImage() {
        return image;
    }
}
