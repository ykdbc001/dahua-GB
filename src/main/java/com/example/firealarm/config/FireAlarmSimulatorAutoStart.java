package com.example.firealarm.config;

import com.example.firealarm.service.FireAlarmSimulatorService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * 可选：启动完成后自动开启模拟器（默认关闭，由首页开关控制）。
 */
@Component
@ConditionalOnProperty(prefix = "fire-alarm.simulator", name = "auto-start", havingValue = "true")
public class FireAlarmSimulatorAutoStart implements ApplicationListener<ApplicationReadyEvent> {

    private final FireAlarmSimulatorService simulatorService;

    public FireAlarmSimulatorAutoStart(FireAlarmSimulatorService simulatorService) {
        this.simulatorService = simulatorService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        simulatorService.start();
    }
}
