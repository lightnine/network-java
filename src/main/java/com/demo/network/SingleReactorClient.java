package com.demo.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
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
            System.out.println("Sent message to server: " + str);
            // 接收服务器的响应
            ByteBuffer responseBuffer = ByteBuffer.allocate(1024);
            int bytesRead = socketChannel.read(responseBuffer);
            if (bytesRead > 0) {
                responseBuffer.flip();
                byte[] responseData = new byte[responseBuffer.limit()];
                responseBuffer.get(responseData);
                String responseMessage = new String(responseData).trim();
                System.out.println("Received response from server: " + responseMessage);
            } else {
                System.out.println("No response received from server.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            socketChannel.close();
        }
    }
}
