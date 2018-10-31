package co.smallet.smalletandroidlibrary;

import java.io.Serializable;

public class AddressInfo implements Serializable {
    private Integer hdCoindId;
    private Integer keyIndex;
    private String address;
    private String type;

    public AddressInfo(Integer _hdCoinId, Integer _keyIndex, String _address) {
        hdCoindId = _hdCoinId;
        keyIndex = _keyIndex;
        address = _address;
        type = null;
    }

    public AddressInfo(Integer _hdCoinId, Integer _keyIndex, String _address, String _type) {
        hdCoindId = _hdCoinId;
        keyIndex = _keyIndex;
        address = _address;
        type = _type;
    }

    public Integer getHdCoindId() {
        return hdCoindId;
    }

    public Integer getKeyIndex() {
        return keyIndex;
    }

    public String getAddress() {
        return address;
    }

    public String getType() {
        return type;
    }
}
