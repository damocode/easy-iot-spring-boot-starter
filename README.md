### 使用方法：
- 导入依赖

```
<dependency>
   <groupId>org.damocode</groupId>
   <artifactId>easy-iot-spring-boot-starter</artifactId>
   <version>1.0-SNAPSHOT</version>
</dependency>
```

- 注入相关类


```java
    // TCP网络组件提供者
    @Resource
    private TcpServerProvider tcpServerProvider;
    
    //设备会话管理器
    @Resource
    private DefaultDeviceSessionManager deviceSessionManager;
    
    //设备操作管理器
    @Resource
    private DeviceOperatorManager deviceOperatorManager;
```

- 实现数据解码及编码接口 DeviceMessageCodec

- 实现数据操作保存接口 DecodedClientMessageHandler
