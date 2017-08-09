package com.receive.decode;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;

/**
 * Created by xmc on 2017/7/15.
 */
public class ClientTextureView extends TextureView implements  TextureView.SurfaceTextureListener{

    private static  final String MIME_TYPE = "video/avc";
    private static final String TAG = "ClientTextureView" ;
    private MediaCodec decode;

    byte[] rtpData =  new byte[80000];
    byte[] h264Data = new byte[80000];

    int timestamp = 0;

    DatagramSocket socket;

    public ClientTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setSurfaceTextureListener(this);
        try {
            socket = new DatagramSocket(5004);//端口号
            socket.setReuseAddress(true);
            socket.setBroadcast(true);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        new PreviewThread(new Surface(surface),800,480);//手机的分辨率
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (socket != null){
            socket.close();
            socket = null;
        }
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    private  class  PreviewThread extends  Thread {
        DatagramPacket datagramPacket = null;
        public PreviewThread(Surface surface, int width , int height){
            Log.e(TAG, "PreviewThread: gou zhao");
            decode = MediaCodec.createDecoderByType(MIME_TYPE);

            final MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE,width,height);
            format.setInteger(MediaFormat.KEY_BIT_RATE,  40000);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 20);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            byte[] header_sps = {0, 0, 0, 1, 103, 66, 0 , 41, -115, -115, 64, 80 , 30 , -48 , 15 ,8,-124, 83, -128};

            byte[] header_pps = {0,0 ,0, 1, 104, -54, 67, -56};

            format.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
            format.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));

            decode.configure(format,surface,null,0);
            decode.start();
            start();
        }


        @Override
        public void run() {
            byte[] data = new byte[80000];
            int h264Length = 0;
            while (true){
                if (socket != null){
                    try {
                        datagramPacket = new DatagramPacket(data,data.length);
                        socket.receive(datagramPacket);//接收数据
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                rtpData =  datagramPacket.getData();
                if (rtpData != null ){
                    if (rtpData[0] == -128 && rtpData[1] == 96){
                        Log.e(TAG, "run:xxx");
                        int l1 = (rtpData[12]<<24)& 0xff000000;
                        int l2 = (rtpData[13]<<16)& 0x00ff0000;
                        int l3 = (rtpData[14]<<8) & 0x0000ff00;
                        int l4 = rtpData[15]&0x000000FF;
                        h264Length = l1+l2+l3+l4;
                        Log.e(TAG, "run: h264Length="+h264Length);
                        System.arraycopy(rtpData,16, h264Data,0,h264Length);
                        Log.e(TAG, "run:h264Data[0]="+h264Data[0]+","+h264Data[1]+","+h264Data[2]+","+h264Data[3]
                                +","+h264Data[4]+","+h264Data[5]+","+h264Data[6]+","+h264Data[7]
                                +","+h264Data[8]+","+h264Data[9]+","+h264Data[10]
                                +","+h264Data[11]+","+h264Data[12]+","+h264Data[13]
                                +","+h264Data[14]+","+h264Data[15]+","+h264Data[16]
                                +","+h264Data[17]+","+h264Data[18]+","+h264Data[19]
                                +","+h264Data[20]+","+h264Data[21]+","+h264Data[22]);//打印sps、pps
                        offerDecoder(h264Data,h264Data.length);
                        Log.e(TAG, "run: offerDecoder=");
                    }
                }
            }
        }
    }

    //解码h264数据
    private void offerDecoder(byte[] input, int length) {
         Log.d(TAG, "offerDecoder: ");
        try {
            ByteBuffer[] inputBuffers = decode.getInputBuffers();
            int inputBufferIndex = decode.dequeueInputBuffer(0);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                try{
                    inputBuffer.put(input, 0, length);
                }catch (Exception e){
                    e.printStackTrace();
                }
                decode.queueInputBuffer(inputBufferIndex, 0, length, 0, 0);
            }
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            int outputBufferIndex = decode.dequeueOutputBuffer(bufferInfo, 0);
            while (outputBufferIndex >= 0) {
                //If a valid surface was specified when configuring the codec,
                //passing true renders this output buffer to the surface.
                decode.releaseOutputBuffer(outputBufferIndex, true);
                outputBufferIndex = decode.dequeueOutputBuffer(bufferInfo, 0);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
