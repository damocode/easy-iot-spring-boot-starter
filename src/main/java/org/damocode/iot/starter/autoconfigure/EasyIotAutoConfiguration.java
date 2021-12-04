package org.damocode.iot.starter.autoconfigure;

import cn.hutool.core.util.IdUtil;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.damocode.iot.core.device.DeviceOperationBroker;
import org.damocode.iot.core.device.DeviceOperatorManager;
import org.damocode.iot.core.device.IDeviceOperatorService;
import org.damocode.iot.core.device.StandaloneDeviceMessageBroker;
import org.damocode.iot.core.server.DecodedClientMessageHandler;
import org.damocode.iot.core.server.MessageHandler;
import org.damocode.iot.core.server.session.DeviceSessionManager;
import org.damocode.iot.device.message.DeviceMessageConnector;
import org.damocode.iot.device.message.IDeviceDataService;
import org.damocode.iot.network.http.server.vertx.HttpServerProvider;
import org.damocode.iot.network.mqtt.client.MqttClientProvider;
import org.damocode.iot.network.mqtt.server.vertx.VertxMqttServerProvider;
import org.damocode.iot.network.tcp.server.TcpServerProvider;
import org.damocode.iot.supports.server.DefaultSendToDeviceMessageHandler;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.util.List;

/**
 * @Author: zzg
 * @Date: 2021/10/7 16:24
 * @Version: 1.0.0
 */
@Configuration
public class EasyIotAutoConfiguration {

    private static final String serverId = IdUtil.fastUUID();

    @Resource
    private IDeviceOperatorService deviceOperatorService;

    @Resource
    private List<IDeviceDataService> deviceDataServices;

    @Bean
    @ConfigurationProperties(prefix = "vertx")
    public VertxOptions vertxOptions() {
        return new VertxOptions();
    }

    @Bean
    public Vertx vertx(VertxOptions vertxOptions) {
        return Vertx.vertx(vertxOptions);
    }

    @Bean
    public StandaloneDeviceMessageBroker standaloneDeviceMessageBroker() {
        return new StandaloneDeviceMessageBroker();
    }

    @Bean(destroyMethod = "shutdown",initMethod = "init")
    public DefaultDeviceSessionManager deviceSessionManager(){
        DefaultDeviceSessionManager sessionManager = new DefaultDeviceSessionManager(serverId);
        return sessionManager;
    }

    @Bean
    public DeviceMessageConnector deviceMessageConnector() {
        return new DeviceMessageConnector(deviceDataServices);
    }

    @Bean(initMethod = "startup")
    public DefaultSendToDeviceMessageHandler defaultSendToDeviceMessageHandler(DeviceSessionManager sessionManager, MessageHandler messageHandler,DecodedClientMessageHandler clientMessageHandler){
        return new DefaultSendToDeviceMessageHandler(serverId,sessionManager,messageHandler,clientMessageHandler);
    }

    @Bean
    public DeviceOperatorManager deviceOperatorManager(DeviceOperationBroker handler){
        return new DeviceOperatorManager(handler,deviceOperatorService);
    }

    @Bean
    public TcpServerProvider tcpServerProvider(Vertx vertx) {
        return new TcpServerProvider(vertx);
    }

    @Bean
    public MqttClientProvider mqttClientProvider(Vertx vertx) {
        return new MqttClientProvider(vertx);
    }

    @Bean
    public VertxMqttServerProvider mqttServerProvider(Vertx vertx) {
        return new VertxMqttServerProvider(vertx);
    }

    @Bean
    public HttpServerProvider httpServerProvider(Vertx vertx) {
        return new HttpServerProvider(vertx);
    }

}
