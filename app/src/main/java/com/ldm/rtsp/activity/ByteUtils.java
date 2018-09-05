package com.ldm.rtsp.activity;

import android.media.MediaCodec;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Description
 * Author lizheng
 * Create Data  2018\9\4 0004
 */
public class ByteUtils {

  private DataInputStream mInputStream;
  private MediaCodec mCodec;
  private boolean mStopFlag = false;
  private Thread mDecodeThread;

  private static final String TAG = "ByteUtils";

  public ByteUtils(MediaCodec codes,String filePath){
    this.mCodec = codes;
    File f = new File(filePath);
    if (null == f || !f.exists() || f.length() == 0) {
      Log.e(TAG,"视频文件不存在");
      return;
    }
    try {
      //获取文件输入流
      mInputStream = new DataInputStream(new FileInputStream(new File(filePath)));
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      try {
        mInputStream.close();
      } catch (IOException e1) {
        e1.printStackTrace();
      }
    }
  }

  public void startDecodingThread() {
    mCodec.start();
    mDecodeThread = new Thread(new decodeThread());
    mDecodeThread.start();
  }

  /**
   * @author ldm
   * @description 解码线程
   * @time 2016/12/19 16:36
   */
  private class decodeThread implements Runnable {
    @Override
    public void run() {
      try {
        decodeLoop();
      } catch (Exception e) {
      }
    }

    private void decodeLoop() {
      //存放目标文件的数据
      ByteBuffer[] inputBuffers = mCodec.getInputBuffers();
      //解码后的数据，包含每一个buffer的元数据信息，例如偏差，在相关解码器中有效的数据大小
      MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
      long startMs = System.currentTimeMillis();
      long timeoutUs = 10000;
      byte[] marker0 = new byte[]{0, 0, 0, 1};
      byte[] dummyFrame = new byte[]{0x00, 0x00, 0x01, 0x20};
      byte[] streamBuffer = null;
      try {
        streamBuffer = getBytes(mInputStream);
      } catch (IOException e) {
        e.printStackTrace();
      }
      int bytes_cnt = 0;
      while (!mStopFlag) {
        bytes_cnt = streamBuffer.length;
        if (bytes_cnt == 0) {
          streamBuffer = dummyFrame;
        }

        int startIndex = 0;
        int remaining = bytes_cnt;
        while (true) {
          if (remaining == 0 || startIndex >= remaining) {
            break;
          }
          int nextFrameStart = KMPMatch(marker0, streamBuffer, startIndex + 2, remaining);
          if (nextFrameStart == -1) {
            nextFrameStart = remaining;
          } else {
          }

          long decode_ms = 0; //= System.currentTimeMillis();
          int inIndex = mCodec.dequeueInputBuffer(timeoutUs);
          if (inIndex >= 0) {
            ByteBuffer byteBuffer = inputBuffers[inIndex];
            byteBuffer.clear();
            byteBuffer.put(streamBuffer, startIndex, nextFrameStart - startIndex);
            //在给指定Index的inputbuffer[]填充数据后，调用这个函数把数据传给解码器
            decode_ms = System.currentTimeMillis();
            mCodec.queueInputBuffer(inIndex, 0, nextFrameStart - startIndex, 0, 0);
            startIndex = nextFrameStart;
          } else {
            continue;
          }

          int outIndex = mCodec.dequeueOutputBuffer(info, timeoutUs);
          if (outIndex >= 0) {
            long last_ms= System.currentTimeMillis()- decode_ms;
            Log.d("mCodec--", "mCodec--time: "+ last_ms);
            //帧控制是不在这种情况下工作，因为没有PTS H264是可用的
            while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
              try {
                Thread.sleep(100);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
            }
            boolean doRender = (info.size != 0);
            //对outputbuffer的处理完后，调用这个函数把buffer重新返回给codec类。
            mCodec.releaseOutputBuffer(outIndex, doRender);
          } else {
          }
        }
        mStopFlag = true;
      }
    }
  }


  public byte[] getBytes(Byte[] streamBytes){
    return null;
  }


  public static byte[] getBytes(InputStream is) throws IOException {
    int len;
    int size = 1024;
    byte[] buf;
    if (is instanceof ByteArrayInputStream) {
      size = is.available();
      buf = new byte[size];
      len = is.read(buf, 0, size);
    } else {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      buf = new byte[size];
      while ((len = is.read(buf, 0, size)) != -1)
        bos.write(buf, 0, len);
      buf = bos.toByteArray();
    }
    return buf;
  }

  int KMPMatch(byte[] pattern, byte[] bytes, int start, int remain) {
    try {
      Thread.sleep(30);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    int[] lsp = computeLspTable(pattern);

    int j = 0;  // Number of chars matched in pattern
    for (int i = start; i < remain; i++) {
      while (j > 0 && bytes[i] != pattern[j]) {
        // Fall back in the pattern
        j = lsp[j - 1];  // Strictly decreasing
      }
      if (bytes[i] == pattern[j]) {
        // Next char matched, increment position
        j++;
        if (j == pattern.length)
          return i - (j - 1);
      }
    }

    return -1;  // Not found
  }

  int[] computeLspTable(byte[] pattern) {
    int[] lsp = new int[pattern.length];
    lsp[0] = 0;  // Base case
    for (int i = 1; i < pattern.length; i++) {
      // Start by assuming we're extending the previous LSP
      int j = lsp[i - 1];
      while (j > 0 && pattern[i] != pattern[j])
        j = lsp[j - 1];
      if (pattern[i] == pattern[j])
        j++;
      lsp[i] = j;
    }
    return lsp;
  }

}
