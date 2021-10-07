package com.damocode.iot.starter.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import org.damocode.iot.core.device.DeviceOperator;
import org.damocode.iot.core.device.DeviceState;
import org.damocode.iot.core.server.session.DeviceSession;
import org.damocode.iot.core.server.session.DeviceSessionManager;
import rx.subjects.PublishSubject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @Description: 默认设备会话管理器
 * @Author: zzg
 * @Date: 2021/10/7 16:41
 * @Version: 1.0.0
 */
@Slf4j
public class DefaultDeviceSessionManager implements DeviceSessionManager {

    private final String serverId;

    private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);

    private final Map<String, DeviceSession> repository = new ConcurrentHashMap<>(4096);

    private PublishSubject<DeviceSession> unregisterHandler = PublishSubject.create();

    private final PublishSubject<DeviceSession> unregisterListener = PublishSubject.create();

    private final PublishSubject<DeviceSession> registerListener = PublishSubject.create();

    private final Queue<Runnable> scheduleJobQueue = new ArrayDeque<>();

    public DefaultDeviceSessionManager(String serverId) {
        this.serverId = serverId;
    }

    public void shutdown() {
        repository.values()
                .stream()
                .map(DeviceSession::getId)
                .forEach(this::unregister);
    }

    public void init() {
        executor.scheduleAtFixedRate(() -> checkSession(),10,60, TimeUnit.SECONDS);
        unregisterHandler.subscribe(session -> {
            session.getOperator().offline();
            if(unregisterListener.hasObservers()){
                unregisterListener.onNext(session);
            }
        });
        // 执行任务
        for(Runnable runnable = scheduleJobQueue.poll(); runnable != null; runnable = scheduleJobQueue.poll()) {
            try {
                runnable.run();
            }catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public DeviceSession getSession(String idOrDeviceId) {
        DeviceSession session = repository.get(idOrDeviceId);
        if (session == null || !session.isAlive()) {
            return null;
        }
        return session;
    }

    @Override
    public DeviceSession register(DeviceSession session) {
        DeviceSession old = repository.put(session.getDeviceId(), session);
        if (old != null) {
            //清空sessionId不同
            if (!old.getId().equals(old.getDeviceId())) {
                repository.remove(old.getId());
            }
        }
        if (!session.getId().equals(session.getDeviceId())) {
            repository.put(session.getId(), session);
        }
        if (null != old) {
            if (!old.equals(session)) {
                log.warn("device[{}] session exists,disconnect old session:{}", old.getDeviceId(), session);
                //加入关闭连接队列
                scheduleJobQueue.add(old::close);
            }
        }

        //注册中心上线
        session.getOperator()
                .online(Optional.ofNullable(session.getServerId()).orElse(serverId),session.getId(),session.getClientAddress().map(String::valueOf).orElse(null));

        if (registerListener.hasObservers()) {
            registerListener.onNext(session);
        }
        return old;
    }

    @Override
    public DeviceSession replace(DeviceSession oldSession, DeviceSession newSession) {
        DeviceSession old = repository.put(oldSession.getDeviceId(), newSession);
        if (old != null) {
            //清空sessionId不同
            if (!old.getId().equals(old.getDeviceId())) {
                repository.put(oldSession.getId(), newSession);
            }
        }
        return newSession;
    }

    @Override
    public PublishSubject<DeviceSession> onRegister() {
        return registerListener;
    }

    @Override
    public PublishSubject<DeviceSession> onUnRegister() {
        return unregisterListener;
    }

    @Override
    public DeviceSession unregister(String idOrDeviceId) {
        DeviceSession session = repository.remove(idOrDeviceId);
        if (null != session) {
            if (!session.getId().equals(session.getDeviceId())) {
                repository.remove(session.getId().equals(idOrDeviceId) ? session.getDeviceId() : session.getId());
            }
            //通知
            unregisterHandler.onNext(session);
            //加入关闭连接队列
            scheduleJobQueue.add(session::close);
        }
        return session;
    }

    @Override
    public boolean sessionIsAlive(String deviceId) {
        return getSession(deviceId) != null;
    }

    @Override
    public List<DeviceSession> getAllSession() {
        return repository.values().parallelStream().filter(deviceSession -> {
            Set<Object> seen = ConcurrentHashMap.newKeySet();
            return seen.add(deviceSession.getDeviceId());
        }).collect(Collectors.toList());
    }

    private Long checkSession() {
        log.debug("check session");
        Long result = repository.values().stream().distinct()
                .filter(session -> {
                    if (!session.isAlive() || session.getOperator() == null) {
                        return true;
                    }
                    DeviceOperator operator = session.getOperator();
                    Byte state = Optional.ofNullable(operator.getState()).orElse(DeviceState.offline);
                    String connectServerId = Optional.ofNullable(operator.getConnectionServerId()).orElse("");
                    if(!state.equals(DeviceState.online) || !connectServerId.equals(serverId)){
                        //设备设备状态为在线
                        operator.online(serverId,session.getId());
                        registerListener.onNext(session);
                    }
                    return false;
                }).map(DeviceSession::getId)
                .map(this::unregister)
                .count();
        return result;
    }
}

