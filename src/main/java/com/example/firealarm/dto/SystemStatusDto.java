package com.example.firealarm.dto;

/**
 * 首页系统状态汇总（与活跃事件表一致）
 */
public class SystemStatusDto {

    private long totalAlarms;
    private long fireAlarms;
    private long faults;
    private long others;
    private String status;

    public SystemStatusDto() {
    }

    public SystemStatusDto(long totalAlarms, long fireAlarms, long faults, long others, String status) {
        this.totalAlarms = totalAlarms;
        this.fireAlarms = fireAlarms;
        this.faults = faults;
        this.others = others;
        this.status = status;
    }

    public long getTotalAlarms() {
        return totalAlarms;
    }

    public void setTotalAlarms(long totalAlarms) {
        this.totalAlarms = totalAlarms;
    }

    public long getFireAlarms() {
        return fireAlarms;
    }

    public void setFireAlarms(long fireAlarms) {
        this.fireAlarms = fireAlarms;
    }

    public long getFaults() {
        return faults;
    }

    public void setFaults(long faults) {
        this.faults = faults;
    }

    public long getOthers() {
        return others;
    }

    public void setOthers(long others) {
        this.others = others;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
