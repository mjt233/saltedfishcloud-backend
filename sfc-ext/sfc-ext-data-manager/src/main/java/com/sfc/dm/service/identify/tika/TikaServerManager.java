package com.sfc.dm.service.identify.tika;

import com.xiaotao.saltedfishcloud.utils.OSInfo;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tika Server 子进程生命周期管理器。
 * <p>
 * 首次调用时从 resources 提取 tika-server.jar 到临时目录并启动子进程，
 * 通过 HTTP REST API 提供文件类型检测和元数据提取服务。
 * 空闲超过指定时间后自动销毁子进程。
 */
@Slf4j
public class TikaServerManager {
    private static final String TIKA_JAR_RESOURCE = "tika/tika-server.jar";
    private static final String TIKA_JAR_NAME = "tika-server.jar";
    private static final long IDLE_TIMEOUT_MS = 300_000L;
    private static final int PORT_RANGE_START = 9980;
    private static final int PORT_RANGE_END = 9999;
    private static final int HEALTH_CHECK_TIMEOUT_MS = 15_000;
    private static final int IDLE_CHECK_INTERVAL_S = 60;

    private static final Set<String> SUPPORTED_METADATA_KEYS = Set.of(
            "title", "creator", "xmpTPg:NPages", "dcterms:created"
    );

    private Process process;
    private int port;
    private String baseUrl;
    private volatile long lastAccessTime;
    private volatile boolean running = false;
    private final Object lock = new Object();
    private ScheduledExecutorService idleChecker;
    private Path extractedJarPath;
    private HttpClient httpClient;

    /**
     * 从 classpath 中提取 tika-server.jar 到临时目录
     * @return 提取后的 jar 文件路径
     * @throws IOException 提取失败时抛出
     */
    private Path extractJar() throws IOException {
        if (extractedJarPath != null && Files.exists(extractedJarPath)) {
            return extractedJarPath;
        }
        Path tempDir = Files.createTempDirectory("sfc-tika-");
        tempDir.toFile().deleteOnExit();
        Path jarPath = tempDir.resolve(TIKA_JAR_NAME);
        jarPath.toFile().deleteOnExit();

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(TIKA_JAR_RESOURCE)) {
            if (is == null) {
                throw new FileNotFoundException("Tika server jar not found in resources: " + TIKA_JAR_RESOURCE);
            }
            Files.copy(is, jarPath, StandardCopyOption.REPLACE_EXISTING);
        }
        extractedJarPath = jarPath;
        log.info("Tika server jar 已提取到: {}", jarPath);
        return jarPath;
    }

    /**
     * 确保 Tika Server 正在运行，返回 base URL
     * @return Tika Server 的 base URL (如 http://127.0.0.1:9980)
     */
    private String ensureRunning() {
        if (running && process != null && process.isAlive()) {
            lastAccessTime = System.currentTimeMillis();
            return baseUrl;
        }
        synchronized (lock) {
            if (running && process != null && process.isAlive()) {
                lastAccessTime = System.currentTimeMillis();
                return baseUrl;
            }
            try {
                startServer();
            } catch (Exception e) {
                log.error("Tika Server 启动失败", e);
                throw new RuntimeException("Tika Server 启动失败", e);
            }
            return baseUrl;
        }
    }

    /**
     * 启动 Tika Server 子进程
     */
    private void startServer() throws Exception {
        Path jarPath = extractJar();
        port = findAvailablePort();
        baseUrl = "http://127.0.0.1:" + port;

        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + (OSInfo.isWindows() ? "java.exe" : "java");

        ProcessBuilder pb = new ProcessBuilder(
                javaBin,
                "-jar", jarPath.toString(),
                "--port", String.valueOf(port),
                "--host", "127.0.0.1"
        );
        pb.redirectErrorStream(true);

        log.info("正在启动 Tika Server: port={}", port);
        process = pb.start();
        running = true;
        lastAccessTime = System.currentTimeMillis();

        // 后台消费进程输出，防止缓冲区满导致阻塞
        Thread outputDrainer = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.trace("[Tika] {}", line);
                }
            } catch (IOException ignored) {}
        }, "tika-output-drainer");
        outputDrainer.setDaemon(true);
        outputDrainer.start();

        // 注册 JVM 关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "tika-shutdown"));

        waitForReady();
        startIdleChecker();

        log.info("Tika Server 已启动: {}", baseUrl);
    }

    /**
     * 轮询健康检查直到 Tika Server 就绪
     */
    private void waitForReady() throws InterruptedException {
        long deadline = System.currentTimeMillis() + HEALTH_CHECK_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpClient client = getHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/version"))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    log.info("Tika Server 就绪, version: {}", response.body().trim());
                    return;
                }
            } catch (Exception ignored) {
            }
            Thread.sleep(500);
        }
        throw new RuntimeException("Tika Server 启动超时（" + HEALTH_CHECK_TIMEOUT_MS + "ms）");
    }

    /**
     * 启动空闲检查定时任务
     */
    private void startIdleChecker() {
        if (idleChecker != null) {
            idleChecker.shutdownNow();
        }
        idleChecker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tika-idle-checker");
            t.setDaemon(true);
            return t;
        });
        idleChecker.scheduleWithFixedDelay(this::checkIdle, IDLE_CHECK_INTERVAL_S, IDLE_CHECK_INTERVAL_S, TimeUnit.SECONDS);
    }

    /**
     * 检查是否空闲超时，超时则停止子进程
     */
    private void checkIdle() {
        if (!running) return;
        long idle = System.currentTimeMillis() - lastAccessTime;
        if (idle > IDLE_TIMEOUT_MS) {
            log.info("Tika Server 空闲超时 ({}ms)，自动关闭", idle);
            stop();
        }
    }

    /**
     * 停止 Tika Server 子进程
     */
    public synchronized void stop() {
        running = false;
        if (idleChecker != null) {
            idleChecker.shutdownNow();
            idleChecker = null;
        }
        if (process != null) {
            process.destroyForcibly();
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            process = null;
            log.info("Tika Server 已停止");
        }
        // 清理临时文件
        if (extractedJarPath != null) {
            try {
                Files.deleteIfExists(extractedJarPath);
                Files.deleteIfExists(extractedJarPath.getParent());
            } catch (IOException ignored) {}
            extractedJarPath = null;
        }
    }

    /**
     * 检测文件的 MIME 类型
     * @param file 待检测的文件
     * @return MIME 类型字符串，失败时返回 null
     */
    public String detect(File file) {
        try {
            String url = ensureRunning() + "/detect/stream";
            HttpClient client = getHttpClient();
            // MIME 检测仅需文件头 magic bytes，只发送前 8KB 避免完整读取大文件
            byte[] headerBytes = readFirstBytes(file, 8192);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/octet-stream")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(headerBytes))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body().trim();
            }
            log.debug("Tika detect 返回非200: status={}, body={}", response.statusCode(), response.body());
            return null;
        } catch (Exception e) {
            log.debug("Tika detect 失败: {}", file.getName(), e);
            return null;
        }
    }

    /**
     * 提取文件的元数据
     * @param file 待提取的文件
     * @param fields 需要提取的 Tika 元数据字段名集合
     * @return 元数据键值对，失败时返回 null
     */
    public Map<String, String> extractMetadata(File file, Set<String> fields) {
        try {
            String url = ensureRunning() + "/meta";
            HttpClient client = getHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/octet-stream")
                    .header("Accept", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofFile(file.toPath()))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.debug("Tika meta 返回非200: status={}", response.statusCode());
                return null;
            }
            return parseMetadataJson(response.body(), fields);
        } catch (Exception e) {
            log.debug("Tika extractMetadata 失败: {}", file.getName(), e);
            return null;
        }
    }

    /**
     * 解析 Tika 返回的 JSON 元数据
     * Tika /meta 返回格式: [{"title":"...","creator":"...","xmpTPg:NPages":"..."}, ...]
     * 或单个对象 {"title":"...","creator":"..."}
     */
    private Map<String, String> parseMetadataJson(String json, Set<String> fields) {
        Map<String, String> result = new HashMap<>();
        // 简单 JSON 解析，避免引入额外依赖
        String content = json.trim();
        // 如果是数组，取第一个元素
        if (content.startsWith("[")) {
            int end = content.indexOf(']');
            if (end > 0) {
                content = content.substring(1, end).trim();
            }
        }
        if (!content.startsWith("{")) {
            return result;
        }
        // 去掉首尾大括号
        content = content.substring(1, content.lastIndexOf('}')).trim();

        // 按逗号分割，处理嵌套引号
        int depth = 0;
        int start = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '"') {
                // 跳过引号内的内容
                int endQuote = content.indexOf('"', i + 1);
                if (endQuote < 0) break;
                i = endQuote;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
            } else if (c == ',' && depth == 0) {
                extractKeyValue(content.substring(start, i), fields, result);
                start = i + 1;
            }
        }
        if (start < content.length()) {
            extractKeyValue(content.substring(start), fields, result);
        }

        return result.isEmpty() ? null : result;
    }

    /**
     * 从单个 "key":"value" 片段中提取键值对
     */
    private void extractKeyValue(String pair, Set<String> fields, Map<String, String> result) {
        pair = pair.trim();
        int colonIdx = pair.indexOf(':');
        if (colonIdx < 0) return;

        String key = pair.substring(0, colonIdx).trim().replace("\"", "");
        String value = pair.substring(colonIdx + 1).trim();
        // 去掉值两端的引号
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        // 反转义
        value = value.replace("\\\"", "\"").replace("\\\\", "\\");

        if (fields.contains(key) && !value.isEmpty()) {
            result.put(key, value);
        }
    }

    /**
     * 获取或创建 HttpClient 实例
     */
    private HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
        }
        return httpClient;
    }

    /**
     * 读取文件前 {@code maxBytes} 字节，用于 MIME 检测等仅需头部数据的场景
     * @param file     目标文件
     * @param maxBytes 最大读取字节数
     * @return 实际读取到的字节数组（文件不足时为实际长度）
     * @throws IOException 读取失败时抛出
     */
    private static byte[] readFirstBytes(File file, int maxBytes) throws IOException {
        byte[] buf = new byte[maxBytes];
        try (FileInputStream fis = new FileInputStream(file)) {
            int read = fis.read(buf);
            return (read < maxBytes) ? Arrays.copyOf(buf, read) : buf;
        }
    }

    /**
     * 查找一个可用端口
     * @return 可用端口号
     * @throws IllegalStateException 没有可用端口时抛出
     */
    private int findAvailablePort() {
        for (int p = PORT_RANGE_START; p <= PORT_RANGE_END; p++) {
            try (ServerSocket ss = new ServerSocket(p)) {
                return p;
            } catch (IOException ignored) {
            }
        }
        throw new IllegalStateException("没有可用端口 (" + PORT_RANGE_START + "-" + PORT_RANGE_END + ")");
    }
}
