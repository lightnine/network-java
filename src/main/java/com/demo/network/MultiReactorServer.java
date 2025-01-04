package com.demo.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @date 2025/1/4 18:35
 * @description: 主从的Reactor，主Reactor负责监听accept事件，而从reactor负责监听read事件并进行业务处理
 */
public class MultiReactorServer {
    public static void main(String[] args) throws IOException {
        MainReactor.start();
    }
}

// 主反应器，主要负责接收客户端的连接请求
class MainReactor {
    static void start() throws IOException {
        Selector selector = Selector.open();
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.socket().bind(new InetSocketAddress(8089));
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        int coreNum = Runtime.getRuntime().availableProcessors();
        FollowerReactor[] followers = new FollowerReactor[coreNum];
        for (int i = 0; i < coreNum; i++) {
            followers[i] = new FollowerReactor();
        }

        int index = 0;
        while (selector.select() > 0) {
            Set<SelectionKey> keys = selector.selectedKeys();
            for (SelectionKey key : keys) {
                keys.remove(key);
                if (key.isValid() && key.isAcceptable()) {
                    ServerSocketChannel serverSocketChannel1 = (ServerSocketChannel) key.channel();
                    SocketChannel socketChannel = serverSocketChannel1.accept();
                    socketChannel.configureBlocking(false);
                    System.out.println("Accept request: " + socketChannel.socket().getInetAddress()
                            + ":" + socketChannel.socket().getPort());
                    FollowerReactor follower = followers[++index % coreNum];
                    // ！！!这里是将socketChannel的Read事件注册到了FollowerReactor的Selector上，而不是主反应器的Selector上。
                    follower.register(socketChannel);
                }
            }
        }

    }
}

class FollowerReactor {
    private static final ExecutorService service = Executors.newFixedThreadPool(
            2 * Runtime.getRuntime().availableProcessors());
    private final Selector selector;

    FollowerReactor() throws IOException {
        this.selector = Selector.open();
        this.select();
    }

    void register(SocketChannel socketChannel) throws ClosedChannelException {
        socketChannel.register(this.selector, SelectionKey.OP_READ);
    }

    private void select() {
        service.submit(() -> {
            while (true) {
                if (this.selector.select(500) <= 0) {
                    continue;
                }
                Set<SelectionKey> keys = this.selector.selectedKeys();
                Iterator<SelectionKey> iterator = keys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    if (key.isValid() && key.isReadable()) {
                        Object attachment = key.attachment();
                        if (attachment instanceof Boolean && (Boolean) attachment) {
                            continue;
                        }
                        // 标记当前key已经被处理过，防止重复处理同一个事件
                        key.attach(true);
                        ByteBuffer buffer = ByteBuffer.allocate(1024);
                        SocketChannel channel = (SocketChannel) key.channel();
                        try {
                            int count = channel.read(buffer);
                            if (count < 0) {
                                channel.close();
                                key.cancel();
                                System.out.println(channel + "->red end !");
                            } else if (count == 0) {
                                System.out.println(channel + ",size is 0 !");
                            } else {
                                String receivedMessage = new String(buffer.array());
                                System.out.println(channel + ", message is :" + receivedMessage);
                                // 将响应数据写回客户端
                                String responseMessage = "echo: " + receivedMessage;
                                ByteBuffer responseBuffer = ByteBuffer.wrap(responseMessage.getBytes());
                                channel.write(responseBuffer);
                            }
                        } catch (IOException e) {
                            key.cancel();
                            channel.close();
//                            e.printStackTrace();
                            System.out.println("read error, close the connection");
                        }
                        // 重置标记，以便下次处理该事件
                        key.attach(null);
                    }
                }
            }
        });
    }
}


