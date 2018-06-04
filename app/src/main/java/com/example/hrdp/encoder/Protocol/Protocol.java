package com.example.hrdp.encoder.Protocol;

/**
 * Created by icns on 2018-04-23.
 */

public class Protocol {
    public enum Type {
            REQUEST_TS, REQUEST_IMG
    }

    public static final int PROTOCOL_MAX_SIZE = 36;
    private Type type;
    private ProtocolFuncBase protocolFunc;

    public Protocol(byte[] data) {
        switch(data[0]) {
            case 0:
                type = Type.REQUEST_TS;
                protocolFunc = new RequestTsFunc();
                break;
            case 1:
                type = Type.REQUEST_IMG;
                protocolFunc = new RequestImgFunc(data);
                break;
        }
    }

    public Type getType() {
        return type;
    }

    public ProtocolFuncBase getProtocolFunc() {
        return protocolFunc;
    }
}