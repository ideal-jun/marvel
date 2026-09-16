package com.marvel.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.marvel.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 服务监控：服务器 / JVM / 磁盘 / Redis 运行信息。
 *
 * <p>属于敏感信息，仅授予 {@code system:monitor:*} 权限的管理员可见（默认只有超管）。
 * 该端点已从 actuator 移出，避免开启 actuator 造成更大暴露面。
 */
@RestController
@RequestMapping("/system/monitor")
@RequiredArgsConstructor
public class SysMonitorController {

    private final RedisConnectionFactory redisConnectionFactory;

    @SaCheckPermission("system:monitor:list")
    @GetMapping("/server")
    public R<Map<String, Object>> server() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sys", sysInfo());
        result.put("cpu", cpuInfo());
        result.put("memory", memoryInfo());
        result.put("jvm", jvmInfo());
        result.put("disk", diskInfo());
        return R.ok(result);
    }

    @SaCheckPermission("system:monitor:redis")
    @GetMapping("/redis")
    public R<Map<String, Object>> redis() {
        Map<String, Object> info = new LinkedHashMap<>();
        RedisConnection connection = null;
        try {
            connection = redisConnectionFactory.getConnection();
            Properties props = connection.serverCommands().info();
            info.put("version", props.getProperty("redis_version"));
            info.put("mode", props.getProperty("redis_mode"));
            info.put("uptimeDays", props.getProperty("uptime_in_days"));
            info.put("connectedClients", props.getProperty("connected_clients"));
            info.put("usedMemory", props.getProperty("used_memory_human"));
            info.put("maxMemory", props.getProperty("maxmemory_human"));
            info.put("totalCommands", props.getProperty("total_commands_processed"));
            info.put("expiredKeys", props.getProperty("expired_keys"));
            info.put("keyspaceHits", props.getProperty("keyspace_hits"));
            info.put("keyspaceMisses", props.getProperty("keyspace_misses"));
            info.put("keyspace", props.getProperty("db0"));
        } catch (Exception e) {
            info.put("error", "Redis 信息获取失败: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
        return R.ok(info);
    }

    private Map<String, Object> sysInfo() {
        Map<String, Object> sys = new LinkedHashMap<>();
        sys.put("osName", System.getProperty("os.name"));
        sys.put("osArch", System.getProperty("os.arch"));
        sys.put("osVersion", System.getProperty("os.version"));
        sys.put("hostName", hostName());
        sys.put("userDir", System.getProperty("user.dir"));
        sys.put("currentTime", LocalDateTime.now().toString());
        return sys;
    }

    private Map<String, Object> cpuInfo() {
        com.sun.management.OperatingSystemMXBean os = osBean();
        Map<String, Object> cpu = new LinkedHashMap<>();
        cpu.put("cores", Runtime.getRuntime().availableProcessors());
        cpu.put("systemLoadAverage", String.format("%.2f", os.getSystemLoadAverage()));
        cpu.put("cpuLoad", pct(os.getCpuLoad()));
        cpu.put("processCpuLoad", pct(os.getProcessCpuLoad()));
        return cpu;
    }

    private Map<String, Object> memoryInfo() {
        com.sun.management.OperatingSystemMXBean os = osBean();
        long total = os.getTotalMemorySize();
        long free = os.getFreeMemorySize();
        long used = Math.max(0, total - free);
        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("total", human(total));
        memory.put("used", human(used));
        memory.put("free", human(free));
        memory.put("usage", ratio(used, total));
        return memory;
    }

    private Map<String, Object> jvmInfo() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("javaVersion", System.getProperty("java.version"));
        jvm.put("javaVendor", System.getProperty("java.vendor"));
        jvm.put("jvmName", runtime.getVmName());
        jvm.put("startTime", LocalDateTime.ofInstant(
                Instant.ofEpochMilli(runtime.getStartTime()), ZoneId.systemDefault()).toString());
        jvm.put("uptime", humanSeconds(runtime.getUptime()));
        jvm.put("heapUsed", human(heap.getUsed()));
        jvm.put("heapMax", human(heap.getMax()));
        jvm.put("heapUsage", ratio(heap.getUsed(), heap.getMax()));
        jvm.put("nonHeapUsed", human(memory.getNonHeapMemoryUsage().getUsed()));
        jvm.put("threadCount", threads.getThreadCount());
        jvm.put("peakThreadCount", threads.getPeakThreadCount());
        return jvm;
    }

    private List<Map<String, Object>> diskInfo() {
        List<Map<String, Object>> disks = new ArrayList<>();
        for (File root : File.listRoots()) {
            long total = root.getTotalSpace();
            if (total <= 0) {
                continue;
            }
            long free = root.getFreeSpace();
            Map<String, Object> disk = new LinkedHashMap<>();
            disk.put("path", root.getAbsolutePath());
            disk.put("total", human(total));
            disk.put("used", human(total - free));
            disk.put("usable", human(root.getUsableSpace()));
            disk.put("usage", ratio(total - free, total));
            disks.add(disk);
        }
        return disks;
    }

    private com.sun.management.OperatingSystemMXBean osBean() {
        return (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    }

    private String pct(double value) {
        return value < 0 ? "N/A" : String.format("%.2f%%", value * 100);
    }

    private String ratio(long used, long total) {
        return total <= 0 ? "N/A" : String.format("%.2f%%", used * 100.0 / total);
    }

    /** 字节数转可读大小 */
    static String human(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB", "PB"};
        double value = bytes;
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format("%.2f %s", value, units[unit]);
    }

    private String humanSeconds(long millis) {
        long seconds = millis / 1000;
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        return days + "天 " + hours + "小时 " + minutes + "分 " + (seconds % 60) + "秒";
    }

    private String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
