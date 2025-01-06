package com.demo.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author liliang21@baidu.com
 * @date 2025/1/4 18:27
 * @description: 采用线程池处理网络数据，而网络新建和可读事件是主线程中执行
 */
public class ThreadPoolReactorServer {
    private static final ExecutorService pool = Executors.newFixedThreadPool(100);

    public static void main(String[] args) throws IOException {
        // 1. 主线程中处理网络新建和可读事件，注：java中的selector只有水平触发，不支持边缘触发
        // 边缘触发是数据从无到有时才触发事件，水平触发是只要有数据就触发事件，所以在下面的处理中会设置attachment来避免重复处理同一个事件。
        Selector selector = Selector.open();
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.socket().bind(new InetSocketAddress(8089));
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        while (true) {
            if (selector.selectNow() < 0) {
                continue;
            }
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();
                // 这里要添加上key.isValid()判断，因为有可能在线程池中此key被取消
                if (key.isValid() && key.isAcceptable()) {
                    ServerSocketChannel Serverchannel = (ServerSocketChannel) key.channel();
                    SocketChannel channel = Serverchannel.accept();
                    channel.configureBlocking(false);
                    System.out.println("accept from: " + channel.socket().getInetAddress().toString()
                            + ":" + channel.socket().getPort());
                    // 2. 这里注册的可读事件是在selector上，由主线程负责监听
                    channel.register(selector, SelectionKey.OP_READ);
                } else if (key.isValid() && key.isReadable()) {
                    // 3. 防止同一个key被多次提交到线程池，这里用一个标记来判断是否已经处理过该事件，避免重复处理。
                    Object attachment = key.attachment();
                    if (attachment instanceof Boolean && (Boolean) attachment) {
                        continue;
                    }
                    key.attach(true);
                    Processor processor = new Processor(key);
                    System.out.println("key hashcode:" + key.hashCode() + ", processor hashcode:" + processor.hashCode());
                    // 将可读事件交给线程池处理
                    pool.submit(processor);
                }
            }
        }
    }
}

class Processor implements Callable {
    private final SelectionKey key;

    Processor(SelectionKey key) {
        this.key = key;
    }

    @Override
    public Object call() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        SocketChannel socketChannel = (SocketChannel) this.key.channel();

        try {
            int count = socketChannel.read(buffer);
            if (count < 0) {
                this.key.cancel();
                socketChannel.close();

                System.out.println("Received invalid data, close the connection");
                return null;
            } else if (count == 0) {
                System.out.println("Received nothing, do nothing, key hashcode: " + this.key.hashCode()
                        + ", current thread: " + Thread.currentThread());
                return null;
            }

            String receivedMessage = new String(buffer.array());
            System.out.println("Received message: " + receivedMessage);
            System.out.println("current thread：" + Thread.currentThread());

            // 将响应数据写回客户端
            String responseMessage = "echo: " + receivedMessage;
            ByteBuffer responseBuffer = ByteBuffer.wrap(responseMessage.getBytes());
            socketChannel.write(responseBuffer);
        } catch (IOException e) {
            this.key.cancel();
            socketChannel.close();
//            e.printStackTrace();
            System.out.println("Read data error, the connection maybe reset, so close the connection");
            return null;
        } finally {
            // 清除标记，以便下一次事件处理
            this.key.attach(null);
        }
        return null;
    }
}
