package com.example.hrdp.encoder;

import android.support.annotation.NonNull;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by Mansu on 2017-03-08.
 */

public class AsyncThread<T,T2> extends Thread {
    protected List<T> dataList = Collections.synchronizedList(new ArrayList());
    private List<AsyncThread> nextThreads;
    private ProcessInterface<T,T2> processInterface;
    private boolean isClear = false;


    public AsyncThread(@NonNull ProcessInterface<T,T2> processInterface) {
        this.processInterface = processInterface;
    }

    public void setNextThreads(@NonNull List<AsyncThread> nextThreads) {
        this.nextThreads = nextThreads;
    }

    @Override
    public void run() {
        while(!Thread.currentThread().isInterrupted()) {
            T data = null;
            synchronized (dataList) {
                if(dataList.size() == 0)
                    try {
                        dataList.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                if(Thread.currentThread().isInterrupted())
                    break;
                if(dataList.size() != 0) {
                    data = dataList.get(0);
                    dataList.remove(0);
                }
            }

            if(data != null) {
                T2 processedData = processInterface.process(data);
                for(AsyncThread asyncThread : nextThreads)
                    asyncThread.putData(processedData);
            }
        }
        Log.d("AsyncThread", "AsyncThread end");
    }

    public void putData(@NonNull T data) {
        synchronized (dataList) {
            if(!isClear) {
                dataList.add(data);
                dataList.notify();
            }
            else
                isClear = false;
        }
    }

    public synchronized void clear() {
        synchronized (dataList) {
            dataList.clear();
            isClear = true;
            processInterface.onClear();
        }
    }

    public synchronized void release() {
        clear();
        interrupt();
        synchronized (dataList) {
            dataList.notify();
        }
    }

    interface ProcessInterface<T,T2> {
        T2 process(T data);
        void onClear();
    }
}