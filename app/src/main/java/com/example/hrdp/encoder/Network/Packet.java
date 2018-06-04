package com.example.hrdp.encoder.Network;

import java.net.InetAddress;

/**
 * Created by icns on 2018-02-05.
 */

public class Packet {
    private byte[] data;
    private InetAddress address;
    private int port;

    public Packet(byte[] data, InetAddress address, int port) {
        this.data = data;
        this.address = address;
        this.port = port;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public InetAddress getAddress() {
        return address;
    }

    public void setAddress(InetAddress address) {
        this.address = address;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
