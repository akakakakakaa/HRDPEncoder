package com.example.hrdp.encoder;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;

/**
 * Created by yj on 16. 9. 13.
 */
public class NonReliableSock {
    private String TAG = "ReliableSock";
    private DatagramSocket mSock = null;
    private boolean isTimeout = false;
    private int segSize = 0;
    private int sleepTime = 0;

    public NonReliableSock(int port, InetAddress ip, int m_segSize, int m_sleepTime) {
        try {
            mSock = new DatagramSocket(null);
            mSock.setReuseAddress(true);
            mSock.bind(new InetSocketAddress(ip ,port));
            segSize = m_segSize;
            sleepTime = m_sleepTime;
        } catch(SocketException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        mSock.close();
    }

    public void setSoTimeout(int timeout) {
        try {
            mSock.setSoTimeout(timeout);
            isTimeout = true;
        } catch(SocketException e) {
            e.printStackTrace();
        }
    }

    //if
    public void clear() {
        if(isTimeout == true) {
            try {
                byte[] seg = new byte[segSize];
                while (true) {
                    DatagramPacket pkt = new DatagramPacket(seg, seg.length);
                    mSock.receive(pkt);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean send(byte[] buf, InetAddress ip, int port) {
        int i;
        try {
            for(i=0; i<buf.length /segSize; i++) {
                DatagramPacket pkt = new DatagramPacket(buf, i * segSize, segSize, ip, port);
                mSock.send(pkt);
                Thread.sleep(sleepTime);
            }
            if(buf.length - i*segSize != 0) {
                DatagramPacket pkt = new DatagramPacket(buf, i * segSize, buf.length - i * segSize, ip, port);
                mSock.send(pkt);
                int delay = (int) (sleepTime * (buf.length - i * segSize) / (float) segSize);
                if(delay != 0)
                    Thread.sleep(delay);
            }

            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } catch(InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    public byte[] recv(int size, InetAddress ip) {
        byte[] recvBuf = new byte[size];

        int i;
        try {
            for(i=0; i<size/segSize; i++) {
                DatagramPacket pkt = new DatagramPacket(recvBuf, i*segSize, segSize);
                mSock.receive(pkt);
                if(!pkt.getAddress().equals(ip))
                    return new byte[0];
            }

            if(size - i*segSize != 0) {
                DatagramPacket pkt = new DatagramPacket(recvBuf, i * segSize, size - i * segSize);
                mSock.receive(pkt);
                if (!pkt.getAddress().equals(ip))
                    return new byte[0];
            }

            return recvBuf;
        } catch(IOException e) {
            e.printStackTrace();
            return new byte[0];
        }
    }
}