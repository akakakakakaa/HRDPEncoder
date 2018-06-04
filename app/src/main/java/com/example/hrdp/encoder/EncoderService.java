package com.example.hrdp.encoder;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;

import com.example.hrdp.encoder.Network.ClientInfo;
import com.example.hrdp.encoder.Network.NonReliableSock;
import com.example.hrdp.encoder.Network.Packet;
import com.example.hrdp.encoder.Network.ReliableSock;
import com.example.hrdp.encoder.Protocol.Protocol;
import com.example.hrdp.encoder.Protocol.RequestImgFunc;
import com.example.hrdp.encoder.Protocol.RequestTsFunc;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by Mansu on 2017-02-13.
 */

public class EncoderService extends Service {
    public static final String TAG = "EncoderService";
    private final IBinder binder = new LocalBinder();

    private Thread receiverThread = null;

    //Socket
    private String serverIp = "192.168.0.27";
    private Map<String, ClientInfo> clientInfos = Collections.synchronizedMap(new HashMap<String, ClientInfo>());
    private NonReliableSock nonReliableSock = null;
    private ReliableSock reliableSock = null;
    private int serverReliablePort = 3001;
    private int serverReliableTimeout = 2000;
    private int serverReliableSleepTime = 30;
    private int serverReliableSegSize = 200;

    private int serverNonReliablePort = 3002;
    private int serverNonReliableTimeout = 1000;
    private int serverNonReliableSleepTime = 30;
    private int serverNonReliableSegSize = 200;

    //video
    private int videoWidth = 640;
    private int videoHeight = 480;
    private int videoResizeWidth = 160;
    private int videoResizeHeight = 120;
    private int bitRate = 50*1024;
    private int frameRate = 10;
    private int keyFrameRate = 20;

    //global flag
    private boolean isSet = false;
    private boolean isPause = true;

    //processThread
    private ProcessThread processThread = new ProcessThread();

    //frActivity
    private String frPath = Environment.getExternalStorageDirectory().getAbsolutePath()+"/frpath/";
    private int maxNode = 200;
    private int savePerFrame = 5;

    //timestamps
    private LinkedList<Long> timestamps = new LinkedList<>();

    //check timestamp
    private long prevTimestamp = System.currentTimeMillis();

    public class LocalBinder extends Binder {
        EncoderService getService() {
            return EncoderService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");

        super.onCreate();
    }

    public void sendFrame(byte[] frame) {
        long currentTimestamp = System.currentTimeMillis();
        if(!isPause && currentTimestamp - prevTimestamp >= 1000.0/frameRate) {
            prevTimestamp = currentTimestamp;
            processThread.putData(frame);
        }
    }

    private void settingReliableUDP() throws IOException {
        reliableSock = new ReliableSock(serverReliablePort, InetAddress.getByName(serverIp), serverReliableSegSize, serverReliableSleepTime);
        reliableSock.setSoTimeout(serverReliableTimeout);
    }

    private void settingNonReliableUDP() throws IOException {
        nonReliableSock = new NonReliableSock(serverNonReliablePort, InetAddress.getByName(serverIp), serverNonReliableSegSize, serverNonReliableSleepTime);
        nonReliableSock.setSoTimeout(serverNonReliableTimeout);
    }

    private void closeReliableUDP() {
        if(reliableSock != null) {
            reliableSock.close();
            reliableSock = null;
        }
    }

    private void closeNonReliableUDP() {
        if(nonReliableSock != null) {
            nonReliableSock.close();
            nonReliableSock = null;
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");

        super.onDestroy();
        release();
    }

    public void start(int videoWidth, int videoHeight) {
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;

        try {
            settingReliableUDP();
            settingNonReliableUDP();
        } catch(IOException e) {
            e.printStackTrace();
        }

        if(receiverThread == null) {
            receiverThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            if(reliableSock != null) {
                                Packet packet = reliableSock.recv(Protocol.PROTOCOL_MAX_SIZE);
                                Protocol protocol = new Protocol(packet.getData());
                                switch(protocol.getType()) {
                                    case REQUEST_TS:
                                        sendTimestampList(protocol, packet.getAddress(), packet.getPort());
                                        break;
                                    case REQUEST_IMG:
                                        sendWantedImg(protocol, packet.getAddress(), packet.getPort());
                                        break;
                                }
                            }
                        } catch (SocketException e) {
                            e.printStackTrace();
                        }
                    }
                    closeReliableUDP();
                    closeNonReliableUDP();
                    Log.d(TAG, "receiverThread end");
                }
            });
            receiverThread.start();
        }
    }

    private void release() {
        if(receiverThread != null)
            receiverThread.interrupt();
        processThread.release();

        isSet = false;
        isPause = true;
    }

    private void recvConfig(Packet packet) {
        Log.d(TAG, "recvConfig");
        isPause = true;

        byte[] config = packet.getData();
        processThread.clear();
        //videoResizeWidth = toInt(config, 4);
        //videoResizeHeight = toInt(config, 8);
        //bitRate = toInt(config, 12);
        //frameRate = toInt(config, 16);
        //keyFrameRate = toInt(config, 20);
        int port = toInt(config, 24);
        serverReliableSegSize = toInt(config, 28);
        serverNonReliableSegSize = toInt(config, 32);
        try {
            settingReliableUDP();
            settingNonReliableUDP();
        } catch(IOException e) {
            e.printStackTrace();
        }

        fileInitialize();
        processThread.run();

        System.out.println("Test: " + packet.getAddress() + " " + packet.getPort());
        clientInfos.put(packet.getAddress().toString(), new ClientInfo(packet.getAddress(), packet.getPort(), port));
        isPause = false;
    }

    private void fileInitialize() {
        Log.d(TAG, "fileInitialize");

        File file = new File(frPath);
        timestamps.clear();

        if(file.exists()) {
            if (file.isDirectory()) {
                String[] children = file.list();
                for (int i = 0; i < children.length; i++)
                    new File(file, children[i]).delete();
            }
            file.delete();
        }

        if(!file.exists())
            file.mkdir();
    }

    private boolean sendTimestampList(Protocol protocol, InetAddress address, int port) {
        Log.d(TAG, "sendTimestampList");
        RequestTsFunc requestTsFunc = (RequestTsFunc)protocol.getProtocolFunc();

        byte[] timestampsInfo = toBytes(timestamps.size());
        reliableSock.send(timestampsInfo, address, port);

        byte[] timestampList = new byte[8*timestamps.size()];
        for(int i=0; i<timestamps.size(); i++)
            System.arraycopy(ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(timestamps.get(i)).array(), 0, timestampList, i * 8, 8);

        reliableSock.send(timestampList, address, port);

        return true;
    }

    private boolean sendWantedImg(Protocol protocol, InetAddress address, int port) {
        Log.d(TAG, "sendWantedImg");
        RequestImgFunc requestImgFunc = (RequestImgFunc)protocol.getProtocolFunc();

        File file = new File(frPath+requestImgFunc.getTimestamp()+".jpg");
        if(file.exists()) {
            byte[] wantedImg = null;
            try {
                FileInputStream fis = new FileInputStream(file);
                wantedImg = new byte[(int) file.length()];
                fis.read(wantedImg);
                fis.close();
            } catch(FileNotFoundException e) {
                e.printStackTrace();
                Log.d(TAG, "FileNotFoundException fail");
            } catch(IOException e) {
                e.printStackTrace();
                Log.d(TAG, "file IOException fail");
            }

            Bitmap jpg = BitmapFactory.decodeByteArray(wantedImg, 0, wantedImg.length);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            jpg.compress(Bitmap.CompressFormat.JPEG, 70, baos);
            byte[] writeData = baos.toByteArray();
            byte[] base64Data = Base64.encode(writeData, Base64.DEFAULT);
            try {
                FileOutputStream fout = new FileOutputStream(new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/tmp.txt"));
                fout.write(base64Data);
                fout.flush();
                fout.close();
            } catch(FileNotFoundException e) {
                e.printStackTrace();
            } catch(IOException e) {
                e.printStackTrace();
            }
            //System.out.println("sub Image size is " + baos.toByteArray().length);
            reliableSock.send(toBytes(base64Data.length), address, port);
            Log.d(TAG, "base64data size is "+base64Data.length);
            reliableSock.send(base64Data, address, port);

            return true;
            //System.out.println("write over");
        }
        else {
            Log.d(TAG, "file not exists!!");
            return false;
        }
    }

    private class ProcessThread {
        //thread
        private ArrayList<AsyncThread> asyncThreads = new ArrayList<>();
        class RectData {
            byte[] img;
            List<Rect> rects;

            public RectData(byte[] img, List<Rect> rects) {
                this.img = img;
                this.rects = rects;
            }
        }
        private AsyncThread<byte[], RectData> motionDetectThread = new AsyncThread<>(new AsyncThread.ProcessInterface<byte[], RectData>() {
            private boolean isInit = false;

            @Override
            public RectData process(byte[] img) {
                if(isRun) {
                    long start = System.currentTimeMillis();

                    if(!isInit) {
                        isInit = true;
                        BgfgVibe.init(img, videoWidth, videoHeight);
                    }
                    else {
                        Mat resultMat = new Mat();
                        BgfgVibe.detect(img, videoWidth, videoHeight, resultMat.getNativeObjAddr());
                        List<Rect> rectList = new ArrayList<>();

                        return new RectData(img, rectList);
                        /*
                        if(resultMat.rows() >= 1) {
                            Converters.Mat_to_vector_Rect(resultMat, rectList);
                            Mat mat = new Mat(videoHeight+videoHeight/2, videoWidth, CvType.CV_8UC1);
                            mat.put(0, 0, data);
                            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_YUV2RGB_YV12);
                            for(int i=0; i<(rectList.size() > 3) ? 3 : rectList.size(); i++)
                               Core.rectangle(mat, rectList.get(i).tl(), rectList.get(i).br(), new Scalar(255, 0, 0), 2);

                            return rectResult;
                        }
                        */
                    }

                    long end = System.currentTimeMillis();
                    System.out.println("MotionDetectThread : " + (end - start) + "ms");
                }

                return null;
            }

            @Override
            public void onClear() {
                isInit = false;
            }
        });


        private AsyncThread<RectData, byte[]> saveImageThread = new AsyncThread<>(new AsyncThread.ProcessInterface<RectData, byte[]>() {
            @Override
            public byte[] process(RectData rectData) {
                if(isRun) {
                    long start = System.currentTimeMillis();

                    if(rectData != null)
                        saveFRObject(rectData, System.currentTimeMillis(), videoWidth, videoHeight);

                    long end = System.currentTimeMillis();
                    System.out.println("SaveImageThread : " + (end - start) + "ms");
                }

                return new byte[0];
            }

            private void saveFRObject(RectData rectData, long timestamp, int width, int height) {
                if (timestamps.size() == maxNode) {
                    File firstImg = new File(frPath + timestamps.get(0) + ".jpg");
                    boolean deleted = firstImg.delete();
                    if (!deleted)
                        System.out.println("file delete error occured!!");

                    timestamps.remove(0);
                }
                timestamps.add(timestamp);
                Mat lastImg = new Mat(height * 3 / 2, width, CvType.CV_8UC1);
                lastImg.put(0, 0, rectData.img);
                Imgproc.cvtColor(lastImg, lastImg, Imgproc.COLOR_YUV2RGB_I420);
                for(int i=0; i<((rectData.rects.size() > 3) ? 3 : rectData.rects.size()); i++)
                    Core.rectangle(lastImg, rectData.rects.get(i).tl(), rectData.rects.get(i).br(), new Scalar(255, 0, 0), 2);
                Highgui.imwrite(frPath + timestamp + ".jpg", lastImg);

                //System.out.println("jpg Saved maxCount="+maxCount+" timestamps size:"+timestamps.size());
            }

            @Override
            public void onClear() {

            }
        });

        private boolean isRun = false;
        public ProcessThread() {
            asyncThreads.add(motionDetectThread);
            asyncThreads.add(saveImageThread);
        }

        public synchronized void run() {
            if (!isRun) {
                isRun = true;

                for(AsyncThread asyncThread : asyncThreads)
                    asyncThread.start();
            }
        }

        public synchronized void putData(byte[] data) {
            if(isRun)
                motionDetectThread.putData(data);
        }

        public synchronized void release() {
            if (isRun) {
                isRun = false;

                for(AsyncThread asyncThread : asyncThreads)
                    asyncThread.release();
            }
        }

        public synchronized void clear() {
            if (isRun) {
                isRun = false;

                for(AsyncThread asyncThread : asyncThreads)
                    asyncThread.clear();
            }
        }
    }


    private int toInt(byte[] data, int offset) {
        return (data[offset] & 0xff) << 24 | (data[1+offset] & 0xff) << 16 |
                (data[2+offset] & 0xff) << 8  | (data[3+offset] & 0xff);
    }

    private byte[] toBytes(int i) {
        byte[] result = new byte[4];

        result[0] = (byte) (i >> 24);
        result[1] = (byte) (i >> 16);
        result[2] = (byte) (i >> 8);
        result[3] = (byte) (i /*>> 0*/);

        return result;
    }
}
