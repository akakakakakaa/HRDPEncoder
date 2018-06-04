package com.example.hrdp.encoder.Network;

import java.net.InetAddress;

/**
 * Created by icns on 2018-02-05.
 */

public class ClientInfo {
    private InetAddress address;
    private int reliablePort;
    private int nonReliablePort;

    public ClientInfo(InetAddress address, int reliablePort, int nonReliablePort) {
        this.address = address;
        this.reliablePort = reliablePort;
        this.nonReliablePort = nonReliablePort;
    }

    public InetAddress getAddress() {
        return address;
    }

    public void setAddress(InetAddress address) {
        this.address = address;
    }

    public int getReliablePort() {
        return reliablePort;
    }

    public void setReliablePort(int reliablePort) {
        this.reliablePort = reliablePort;
    }

    public int getNonReliablePort() {
        return nonReliablePort;
    }

    public void setNonReliablePort(int nonReliablePort) {
        this.nonReliablePort = nonReliablePort;
    }
}
