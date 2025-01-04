package com.demo.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author liliang21@baidu.com
 * @date 2025/1/3 21:47
 * @description:
 */
public class SingleReactorClient {
    public static void main(String[] args) throws IOException {
        SocketChannel socketChannel;
        socketChannel = SocketChannel.open();
        socketChannel.connect(new InetSocketAddress("localhost", 8089));
        Date now = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        String str = dateFormat.format(now);
        byte[] request = str.getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(request.length);
        buffer.put(request);
        buffer.flip();
        try {
            while (buffer.hasRemaining()) {
                socketChannel.write(buffer);
            }
        }catch (IOException e) {
            e.printStackTrace();
        }
        socketChannel.close();
    }
}
