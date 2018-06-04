package com.example.hrdp.encoder.Protocol;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

/**
 * Created by icns on 2018-04-23.
 */

public class RequestImgFunc extends ProtocolFuncBase {
    private byte[] data;

    public RequestImgFunc(byte[] data) {
        this.data = data;
    }

    public long getTimestamp() {
        ByteBuffer buf = ByteBuffer.wrap(data, 1, 8);
        LongBuffer longBuf = buf.asLongBuffer();
        long[] l = new long[longBuf.capacity()];
        longBuf.get(l);
        return l[0];
    }
}
