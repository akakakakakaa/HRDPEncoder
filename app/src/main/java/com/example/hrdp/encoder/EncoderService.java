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

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

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
import java.nio.LongBuffer;
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
            //serverIp = getIPAddress(true);
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
                                Packet packet = reliableSock.recv(36);
                                byte[] data = packet.getData();
                                if (data.length != 0) {
                                    Log.d(TAG, "data[0]: " + data[0] + " data[1]: " + data[1] + " data[2]: " + data[2] + " data[3]: " + data[3]);
                                    if (data[0] == 0x12 && data[1] == 0x34 && data[2] == 0x56) {
                                        switch (data[3]) {
                                            case 0x00:
                                                recvConfig(packet);
                                                break;
                                            case 0x01:
                                                Log.d(TAG, "isPause");
                                                isPause = true;
                                                processThread.clear();
                                                break;
                                            case 0x02:
                                                Log.d(TAG, "start");
                                                isPause = false;
                                                processThread.run();
                                                break;
                                        }
                                    } else if (data[0] == 0x34 && data[1] == 0x56 && data[2] == 0x78 && data[3] == 0x12) {
                                        switch (data[4]) {
                                            case 0x01:
                                                sendTimestampList(packet);
                                                break;
                                            case 0x02:
                                                sendWantedImg();
                                                break;
                                        }
                                    }
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
    public static String getIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        //boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                        boolean isIPv4 = sAddr.indexOf(':')<0;

                        if (useIPv4) {
                            if (isIPv4)
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 zone suffix
                                return delim<0 ? sAddr.toUpperCase() : sAddr.substring(0, delim).toUpperCase();
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) { } // for now eat exceptions
        return "";
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
        videoResizeWidth = toInt(config, 4);
        videoResizeHeight = toInt(config, 8);
        bitRate = toInt(config, 12);
        frameRate = toInt(config, 16);
        keyFrameRate = toInt(config, 20);
        int port = toInt(config, 24);
        serverReliableSegSize = toInt(config, 28);
        serverNonReliableSegSize = toInt(config, 32);
        try {
            //serverIp = getIPAddress(true);
            settingReliableUDP();
            settingNonReliableUDP();
        } catch(IOException e) {
            e.printStackTrace();
        }

        fileInitialize();
        codecInitialize();
        processThread.run();

        System.out.println("Test: " + packet.getAddress() + " " + packet.getPort());
        clientInfos.put(packet.getAddress().toString(), new ClientInfo(packet.getAddress(), packet.getPort(), port));
        isPause = false;
    }

    private void codecInitialize() {
        Log.d(TAG, "codecInitialize");

        if(isSet)
            Codec.encode_release();

        int crf = 40;
        switch (bitRate) {
            case 16384:
                serverNonReliableSleepTime = 60;
                serverReliableSleepTime = 60;
                crf = 47;
                break;
            case 24576:
                serverNonReliableSleepTime = 45;
                serverReliableSleepTime = 45;
                crf = 44;
                break;
            case 32768:
                serverNonReliableSleepTime = 30;
                serverReliableSleepTime = 30;
                crf = 40;
                break;
            case 40960:
                serverNonReliableSleepTime = 26;
                serverReliableSleepTime = 26;
                crf = 39;
                break;
            case 49152:
                serverNonReliableSleepTime = 22;
                serverReliableSleepTime = 22;
                crf = 38;
                break;
            case 57344:
                serverNonReliableSleepTime = 17;
                serverReliableSleepTime = 17;
                crf = 37;
                break;
            case 65536:
                serverNonReliableSleepTime = 15;
                serverReliableSleepTime = 15;
                crf = 35;
                break;
            default:
                crf = 40;
                break;
        }
        Codec.encode_init(videoResizeWidth, videoResizeHeight, bitRate, frameRate, 0, keyFrameRate, crf, Codec.encode_get_h264_identifier());
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

    private boolean sendTimestampList(Packet packet) {
        Log.d(TAG, "sendTimestampList");

        byte[] timestampsInfo = toBytes(timestamps.size());
        reliableSock.send(timestampsInfo, packet.getAddress(), packet.getPort());

        byte[] timestampList = new byte[8*timestamps.size()];
        for(int i=0; i<timestamps.size(); i++)
            System.arraycopy(ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(timestamps.get(i)).array(), 0, timestampList, i * 8, 8);

        reliableSock.send(timestampList, packet.getAddress(), packet.getPort());

        return true;
    }

    private boolean sendWantedImg() {
        Log.d(TAG, "sendWantedImg");

        //System.out.pri ntln("sub Image request");
        try {
            Packet packet = reliableSock.recv(24);
            byte[] rect = packet.getData();
            ByteBuffer buf = ByteBuffer.wrap(rect);
            LongBuffer longBuf = buf.asLongBuffer();
            long[] l = new long[longBuf.capacity()];
            longBuf.get(l);
            //System.out.println("timestamp: "+l[0]+" preX: "+Config.toInt(rect, 8)+" preY: "+Config.toInt(rect, 12)+" postX:"+Config.toInt(rect, 16)+" postY:"+Config.toInt(rect, 20));
            File file = new File(frPath+l[0]+".jpg");
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

                //System.out.println("read jpeg data");
                Bitmap jpg = BitmapFactory.decodeByteArray(wantedImg, 0, wantedImg.length);
                //System.out.println("decode jpeg data");

                int leftX = (toInt(rect, 8) > toInt(rect, 16)) ? toInt(rect, 16) : toInt(rect, 8);
                int leftY = (toInt(rect, 12) > toInt(rect, 20)) ? toInt(rect, 20) : toInt(rect, 12);
                int postX = (toInt(rect, 8) <= toInt(rect, 16)) ? toInt(rect, 16) : toInt(rect, 8);
                int postY = (toInt(rect, 12) <= toInt(rect, 20)) ? toInt(rect, 20) : toInt(rect, 12);

                int width = postX - leftX;
                int height = postY - leftY;

                Bitmap bmp = Bitmap.createBitmap(jpg, leftX, leftY, width, height);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.JPEG, 70, baos);
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
                reliableSock.send(toBytes(base64Data.length), packet.getAddress(), packet.getPort());
                Log.d(TAG, "base64data size is "+base64Data.length);
                reliableSock.send(base64Data, packet.getAddress(), packet.getPort());

                return true;
                //System.out.println("write over");
            }
            else {
                Log.d(TAG, "file not exists!!");
                return false;
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return false;
    }

    private class ProcessThread {
        //thread
        private MotionDetectThread motionDetectThread;
        private ResizeThread resizeThread;
        private FrameThread frameThread;
        private SendThread sendThread;
        private SaveImageThread saveImageThread;
        private boolean isRun = false;

        public synchronized void run() {
            if (!isRun) {
                isRun = true;

                motionDetectThread = new MotionDetectThread();
                resizeThread = new ResizeThread();
                frameThread = new FrameThread();
                sendThread = new SendThread();
                saveImageThread = new SaveImageThread();

                motionDetectThread.start();
                resizeThread.start();
                frameThread.start();
                sendThread.start();
                saveImageThread.start();
            }
        }

        public synchronized void release() {
            if (isRun) {
                isRun = false;

                motionDetectThread.release();
                resizeThread.release();
                frameThread.release();
                sendThread.release();
                saveImageThread.release();
            }
        }

        public synchronized void clear() {
            if (isRun) {
                isRun = false;

                motionDetectThread.clear();
                motionDetectThread.init();
                resizeThread.clear();
                frameThread.clear();
                sendThread.clear();
                saveImageThread.clear();
            }
        }

        public synchronized void putData(byte[] data) {
            motionDetectThread.putData(data);
            resizeThread.putData(data);
        }

        private class MotionDetectThread extends AsyncThread<byte[]> {
            private boolean isInit = false;
            @Override
            protected synchronized void process(byte[] data) {
                if(isRun) {
                    long start = System.currentTimeMillis();

                    if(!isInit) {
                        isInit = true;
                        BgfgVibe.init(data, videoWidth, videoHeight);
                    }
                    else {
                        Mat resultMat = new Mat();
                        BgfgVibe.detect(data, videoWidth, videoHeight, resultMat.getNativeObjAddr());
                        List<Rect> rectList = new ArrayList<>();
                        if(resultMat.rows() >= 1) {
                            Converters.Mat_to_vector_Rect(resultMat, rectList);
                            byte[] rectResult = new byte[6 + ((rectList.size() > 3) ? 3 : rectList.size()) * 8];
                            rectResult[0] = 0x78;
                            rectResult[1] = 0x56;
                            rectResult[2] = 0x34;
                            rectResult[3] = 0x11;
                            rectResult[4] = (byte) (((short) ((rectList.size() > 3) ? 3 : rectList.size()) >> 8) & 0xff);
                            rectResult[5] = (byte) (((short) ((rectList.size() > 3) ? 3 : rectList.size())) & 0xff);

                            for (int i = 0; i < ((rectList.size() > 3) ? 3 : rectList.size()); i++) {
                                rectResult[6 + i * 8] = (byte) ((((short) rectList.get(i).tl().x) >> 8) & 0xff);
                                rectResult[7 + i * 8] = (byte) (((short) rectList.get(i).tl().x) & 0xff);
                                rectResult[8 + i * 8] = (byte) ((((short) rectList.get(i).tl().y) >> 8) & 0xff);
                                rectResult[9 + i * 8] = (byte) (((short) rectList.get(i).tl().y) & 0xff);
                                rectResult[10 + i * 8] = (byte) ((((short) rectList.get(i).br().x) >> 8) & 0xff);
                                rectResult[11 + i * 8] = (byte) (((short) rectList.get(i).br().x) & 0xff);
                                rectResult[12 + i * 8] = (byte) ((((short) rectList.get(i).br().y) >>8) & 0xff);
                                rectResult[13 + i * 8] = (byte) (((short) rectList.get(i).br().y) & 0xff);
                            }
                            sendThread.putData(rectResult);
                        }
                    }

                    long end = System.currentTimeMillis();
                    System.out.println("MotionDetectThread : " + (end - start) + "ms");
                }
            }

            public void init() {
                isInit = false;
            }
       }

        private class Packet {

            public byte[] data;
            public byte[] changed;

            public Packet(byte[] data, byte[] changed) {
                this.data = data;
                this.changed = changed;
            }
        }

        private class ResizeThread extends AsyncThread<byte[]> {
            @Override
            protected synchronized void process(byte[] data) {
                if(isRun) {
                    long start = System.currentTimeMillis();

                    byte[] resize = resizeEncodeImage(data, videoWidth, videoHeight, videoResizeWidth, videoResizeHeight);
                    frameThread.putData(new Packet(data, resize));

                    long end = System.currentTimeMillis();
                    System.out.println("ResizeThread : " + (end - start) + "ms");
                }
            }

            /*
            public byte[] resizeEncodeImage(byte[] data, int srcWidth, int srcHeight, int dstWidth, int dstHeight) {
                Mat mat = new Mat(srcHeight * 3 / 2, srcWidth, CvType.CV_8UC1);
                mat.put(0, 0, data);

                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_YUV2BGR_NV21);
                Imgproc.resize(mat, mat, new Size(), ((double) dstWidth / srcWidth), ((double) dstHeight / srcHeight), Imgproc.INTER_CUBIC);
                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2YUV_YV12);

                byte[] result = new byte[dstWidth * dstHeight * 3 / 2];
                mat.get(0, 0, result);

                return result;
            }
            */
            public byte[] resizeEncodeImage(byte[] data, int srcWidth, int srcHeight, int dstWidth, int dstHeight) {
                Mat mat = new Mat(srcHeight * 3 / 2, srcWidth, CvType.CV_8UC1);
                mat.put(0, 0, data);
                Imgproc.resize(mat, mat, new Size(), ((double) dstWidth / srcWidth), ((double) dstHeight / srcHeight), Imgproc.INTER_CUBIC);

                byte[] result = new byte[dstWidth * dstHeight * 3 / 2];
                mat.get(0, 0, result);

                return result;
            }
        }

        private class SaveImage {
            public byte[] data;
            public long timestamp;
            SaveImage(byte[] data, long timestamp) {
                this.data = data;
                this.timestamp = timestamp;
            }
        }

        private class FrameThread extends AsyncThread<Packet> {
            private boolean isSave = false;
            private int saveCount = 0;

            @Override
            protected synchronized void process(Packet packet) {
                if(isRun) {
                    long start = System.currentTimeMillis();

                    final byte[] encoded = Codec.encode_frame(packet.changed);
                    if(encoded != null && encoded.length != 0) {
                        encoded[0] = 0x78;
                        encoded[1] = 0x56;
                        encoded[2] = 0x34;
                        encoded[3] = 0x12;
                        System.arraycopy(toBytes(encoded.length), 0, encoded, 4, 4);
                        System.arraycopy(ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(start).array(), 0, encoded, 8, 8);
                        saveCount++;
                        if (saveCount % savePerFrame == 0) {
                            saveImageThread.putData(new SaveImage(packet.data, start));
                            isSave = true;
                        }
                        else
                            isSave = false;
                        encoded[16] = (byte) (isSave ? 1 : 0);

                        System.out.println("frameSize: " + encoded.length);
                        sendThread.putData(encoded);
                    }

                    long end = System.currentTimeMillis();
                    System.out.println("frameThread : " + (end - start) + "ms");
                }
            }
        }

        private class SendThread extends AsyncThread<byte[]> {

            @Override
            protected synchronized void process(byte[] data) {
                if(isRun) {
                    long start = System.currentTimeMillis();
                    for( Map.Entry<String, ClientInfo> elem : clientInfos.entrySet() ) {
                        ClientInfo clientInfo = elem.getValue();
                        nonReliableSock.send(data, clientInfo.getAddress(), clientInfo.getNonReliablePort());
                    }

                    long end = System.currentTimeMillis();
                    System.out.println("SendThread : " + (end - start) + "ms");
                }
            }
        }

        private class SaveImageThread extends AsyncThread<SaveImage> {
            @Override
            protected synchronized void process(SaveImage image) {
                if(isRun) {
                    long start = System.currentTimeMillis();

                    saveFRObject(image.data, image.timestamp, videoWidth, videoHeight);

                    long end = System.currentTimeMillis();
                    System.out.println("SaveImageThread : " + (end - start) + "ms");
                }
            }

            private void saveFRObject(byte[] data, long timestamp, int width, int height) {
                if (timestamps.size() == maxNode) {
                    File firstImg = new File(frPath + timestamps.get(0) + ".jpg");
                    boolean deleted = firstImg.delete();
                    if (!deleted)
                        System.out.println("file delete error occured!!");

                    timestamps.remove(0);
                }
                timestamps.add(timestamp);
                Mat lastImg = new Mat(height * 3 / 2, width, CvType.CV_8UC1);
                lastImg.put(0, 0, data);
                Imgproc.cvtColor(lastImg, lastImg, Imgproc.COLOR_YUV2RGB_I420);
                Highgui.imwrite(frPath + timestamp + ".jpg", lastImg);

                //System.out.println("jpg Saved maxCount="+maxCount+" timestamps size:"+timestamps.size());
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
