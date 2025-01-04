package com.demo.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * @date 2025/1/3 18:12
 * @description: 单Reactor模型，只有一个线程处理accept事件和read事件
 */
public class SingleReactorServer {
    public static void main(String[] args) throws IOException {
        // 1. 在8089端口上，监听accept事件
        Selector selector = Selector.open();
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.socket().bind(new InetSocketAddress(8089));
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        // 2. select()函数会阻塞，直到有一个关注的事件到来
        while (selector.select() > 0) {
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = keys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                if (key.isValid() && key.isAcceptable()) {
                    ServerSocketChannel acceptServerSocketChannel = (ServerSocketChannel) key.channel();
                    SocketChannel socketChannel = acceptServerSocketChannel.accept();
                    socketChannel.configureBlocking(false);
                    System.out.println("accept from： " + socketChannel.socket().getInetAddress().toString() + ":"
                            + socketChannel.socket().getPort());
                    // 将新连接的socketChannel注册到selector上，并关注read事件
                    socketChannel.register(selector, SelectionKey.OP_READ);
                } else if (key.isReadable() && key.isValid()) {
                    SocketChannel socketChannel = (SocketChannel) key.channel();
                    ByteBuffer buffer = ByteBuffer.allocate(1024);
                    try {
                        int count = socketChannel.read(buffer);
                        if (count <= 0) {
                            socketChannel.close();
                            key.cancel();
                            System.out.println("Received invalid data, close the connection");
                            continue;
                        }
                        String receivedMessage = new String(buffer.array());
                        System.out.println("Received message: " + receivedMessage);
                        System.out.println("current thread：" + Thread.currentThread());

                        // 将响应数据写回客户端
                        String responseMessage = "echo: " + receivedMessage;
                        ByteBuffer responseBuffer = ByteBuffer.wrap(responseMessage.getBytes());
                        socketChannel.write(responseBuffer);
                        // 处理完成后，关闭连接，不过在实际应用中，通常不会主动关闭。因为一般会有keep-alive
//                    socketChannel.close();
//                    key.cancel();
                    } catch (IOException e) {
                        key.cancel();
                        socketChannel.close();
//                        e.printStackTrace();
                        System.out.println("Received invalid data, close the connection");
                    }
                }
                // 将已经处理完成的key删除
                keys.remove(key);
            }
        }
    }
}
