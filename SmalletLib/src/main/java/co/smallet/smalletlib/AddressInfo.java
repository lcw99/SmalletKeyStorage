package co.smallet.smalletlib;

import java.io.Serializable;

public class AddressInfo implements Serializable {
    private Integer hdCoindId;
    private Integer keyIndex;
    private String address;

    public AddressInfo(Integer _hdCoinId, Integer _keyIndex, String _address) {
        hdCoindId = _hdCoinId;
        keyIndex = _keyIndex;
        address = _address;
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
}
