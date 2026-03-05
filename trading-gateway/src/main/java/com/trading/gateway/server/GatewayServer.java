package com.trading.gateway.server;

import com.trading.gateway.config.GatewayConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;

/**
 * 交易网关 Netty 服务端：按行接收 JSON，转发到 Core 后回写客户端。
 */
@Component
public class GatewayServer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(GatewayServer.class);

    private final GatewayConfig config;
    private final GatewayHandler gatewayHandler;
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;

    public GatewayServer(GatewayConfig config, GatewayHandler gatewayHandler) {
        this.config = config;
        this.gatewayHandler = gatewayHandler;
    }

    @Override
    public void run(String... args) {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new LineBasedFrameDecoder(65536))
                                    .addLast(new StringDecoder(CharsetUtil.UTF_8))
                                    .addLast(new StringEncoder(CharsetUtil.UTF_8))
                                    .addLast(gatewayHandler);
                        }
                    });
            b.bind(config.getPort()).sync();
            log.info("Gateway listening on port {}", config.getPort());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Gateway start interrupted", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
    }
}
