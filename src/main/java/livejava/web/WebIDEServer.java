package livejava.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import livejava.LiveJavaPlugin;
import org.bukkit.Bukkit;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class WebIDEServer {

    private HttpServer server;
    private final LiveJavaPlugin plugin;
    private final int port;
    private final Logger logger;

    /** Aktif oturumlar: token -> oturum bilgisi (IP + son aktivite zamani) */
    private final Map<String, Session> activeKeys = new ConcurrentHashMap<>();

    /** Bir editor oturumunu temsil eder (IP kilidi + zaman asimi takibi). */
    private static final class Session {
        final String ip;
        volatile long lastActivity;

        Session(String ip) {
            this.ip = ip;
            this.lastActivity = System.currentTimeMillis();
        }
    }

    public WebIDEServer(LiveJavaPlugin plugin, int port) {
        this.plugin = plugin;
        this.port = port;
        this.logger = plugin.getLogger();
    }

    public String generateEditorKey(String playerIp) {
        String key = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        activeKeys.put(key, new Session(playerIp));
        logger.info("[LiveJava] Generated new editor token. Connected IP: " + playerIp);
        return key;
    }

    public boolean removeEditorKey(String key) {
        return activeKeys.remove(key) != null;
    }

    public Set<String> getActiveKeys() {
        return activeKeys.keySet();
    }

    private boolean isAuthorized(HttpExchange exchange) throws IOException {
        String token = null;

        // 1. Check Authorization Header
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        // 2. Fallback to URL query, SADECE ilk HTML sayfa yuklemesi icin.
        //    /api/* uclari Authorization basligi kullanmak zorundadir; boylece
        //    token URL'lerde, erisim loglarinda ve tarayici gecmisinde kalmaz.
        if (token == null && !exchange.getRequestURI().getPath().startsWith("/api/")) {
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                for (String p : query.split("&")) {
                    if (p.startsWith("key=") && p.length() > 4) {
                        token = p.substring(4);
                        break;
                    }
                }
            }
        }

        if (token == null) return false;

        Session session = activeKeys.get(token);
        if (session == null) return false;

        // Oturum zaman asimi kontrolu (0 = devre disi)
        long timeoutMinutes = plugin.getConfig().getLong("session-timeout-minutes", 60);
        if (timeoutMinutes > 0 && System.currentTimeMillis() - session.lastActivity > timeoutMinutes * 60_000L) {
            activeKeys.remove(token);
            logger.info("[LiveJava] Editor session expired due to inactivity.");
            return false;
        }

        String requestIp = getClientIp(exchange);

        // IP kilidi: token sadece olusturuldugu IP'den kullanilabilir
        if (session.ip != null && session.ip.equals(requestIp)) {
            session.lastActivity = System.currentTimeMillis();
            return true;
        }

        // Config'deki whitelist kontrolü
        List<String> allowedIps = plugin.getConfig().getStringList("allowed-ips");
        if (allowedIps.contains(requestIp)) {
            session.lastActivity = System.currentTimeMillis();
            return true;
        }

        logger.warning("[LiveJava] Unauthorized IDE access blocked! IP: " + requestIp);
        return false;
    }

    public int getPort() {
        return port;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new EditorFrontendHandler());
            server.createContext("/api/tree", new TreeApiHandler());
            server.createContext("/api/file", new FileApiHandler());
            server.createContext("/api/build", new BuildApiHandler());
            server.createContext("/api/fs", new FsApiHandler()); // Dosya Silme/Yeniden adlandırma işleri
            server.createContext("/api/logs", new LogApiHandler()); // Log Görüntüleyici
            server.setExecutor(null);
            server.start();
        } catch (Exception e) {
            logger.severe("§cIDE başlatılamadı, port hatası!");
        }
    }

    public void stop() {
        if (server != null) server.stop(0);
        activeKeys.clear();
    }

    private class EditorFrontendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) { sendResponse(exchange, 401, "Yetkisiz Erisim! Lutfen oyundan /livejava editor yazip link aliniz."); return; } // ORROSPU ÇOCUĞU ÖNLEYİCİ

            File editorFile = new File(plugin.getDataFolder(), "editor.html");
            if (!editorFile.exists()) { sendResponse(exchange, 404, "editor.html bulunamadi!"); return; } // editor yok nereye gidiyon aptal oc

            String html = new String(Files.readAllBytes(editorFile.toPath()), StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");

            String requestIp = getClientIp(exchange);
            plugin.broadcastIdeLog("Web IDE Opened", requestIp);

            sendResponse(exchange, 200, html);
        }
    }

    private class TreeApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) { sendResponse(exchange, 401, "[]"); return; }

            File scriptsDir = new File(plugin.getDataFolder(), "scripts");
            String jsonTree = buildFileTreeJson(scriptsDir, scriptsDir.getAbsolutePath());
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            sendResponse(exchange, 200, jsonTree);
        }

        private String buildFileTreeJson(File dir, String basePath) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            File[] files = dir.listFiles();
            if (files != null) {
                boolean first = true;
                for (File f : files) {
                    if (!first) sb.append(",");
                    first = false;
                    String relPath = f.getAbsolutePath().substring(basePath.length() + 1).replace("\\", "/");
                    sb.append("{");
                    sb.append("\"name\":\"").append(escapeJson(f.getName())).append("\",");
                    sb.append("\"path\":\"").append(escapeJson(relPath)).append("\",");
                    sb.append("\"isDir\":").append(f.isDirectory());

                    // Sadece root dizindeyse (Yani projenin kendisiyse) status bilgisini ver
                    if (f.getParentFile().getName().equals("scripts")) {
                        String status = plugin.getProjectStatuses().getOrDefault(f.getName(), "idle");
                        sb.append(",\"status\":\"").append(escapeJson(status)).append("\"");
                    }

                    if (f.isDirectory()) { sb.append(",\"children\":").append(buildFileTreeJson(f, basePath)); }
                    sb.append("}");
                }
            }
            sb.append("]");
            return sb.toString();
        }
    }

    private class FileApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) { sendResponse(exchange, 401, "Error"); return; }

            String query = exchange.getRequestURI().getQuery();
            String relativePath = "";
            if (query != null) {
                for(String arg : query.split("&")) { if(arg.startsWith("path=")) relativePath = arg.replace("path=", ""); }
            }
            relativePath = URLDecoder.decode(relativePath, StandardCharsets.UTF_8);
            File scriptsDir = new File(plugin.getDataFolder(), "scripts");
            File targetFile = new File(scriptsDir, relativePath);

            // Path Traversal koruması (NIO Path strict checks)
            java.nio.file.Path targetPathObj = targetFile.getCanonicalFile().toPath();
            java.nio.file.Path scriptsPathObj = scriptsDir.getCanonicalFile().toPath();

            if (!targetPathObj.startsWith(scriptsPathObj)) {
                sendResponse(exchange, 403, "Erişim engellendi: Geçersiz dosya yolu.");
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                if (targetFile.exists() && !targetFile.isDirectory()) {
                    String content = new String(Files.readAllBytes(targetFile.toPath()), StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                    sendResponse(exchange, 200, content);
                } else sendResponse(exchange, 404, "Dosya bulunamadi.");
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                if (!targetFile.getParentFile().exists()) targetFile.getParentFile().mkdirs();
                String newCode = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Files.write(targetFile.toPath(), newCode.getBytes(StandardCharsets.UTF_8));

                String requestIp = getClientIp(exchange);
                plugin.broadcastIdeLog("File Saved: " + targetFile.getName(), requestIp);

                sendResponse(exchange, 200, "Kayıt Başarılı.");
            } else sendResponse(exchange, 405, "Method Not Allowed");
        }
    }

    // Sağ Tık İşlemleri için Dosya Sistemi Yoneticisi (FS)
    private class FsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) { sendResponse(exchange, 401, "Error"); return; }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    JSONParser parser = new JSONParser();
                    JSONObject obj = (JSONObject) parser.parse(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
                    String action = (String) obj.get("action");
                    String path = (String) obj.get("path");
                    File scriptsDir = new File(plugin.getDataFolder(), "scripts");
                    File target = new File(scriptsDir, path);

                    // Path Traversal koruması (NIO)
                    java.nio.file.Path targetPathObj = target.getCanonicalFile().toPath();
                    java.nio.file.Path scriptsPathObj = scriptsDir.getCanonicalFile().toPath();

                    if (!targetPathObj.startsWith(scriptsPathObj)) {
                        sendResponse(exchange, 403, "Erişim engellendi: Geçersiz dosya yolu.");
                        return;
                    }

                    if ("delete".equals(action)) {
                        deleteRecursively(target);
                        sendResponse(exchange, 200, "Silindi.");
                    } else if ("rename".equals(action)) {
                        String newPath = (String) obj.get("newPath");
                        File dest = new File(scriptsDir, newPath);

                        // Hedef yol için de Path Traversal koruması (NIO)
                        java.nio.file.Path destPathObj = dest.getCanonicalFile().toPath();
                        if (!destPathObj.startsWith(scriptsPathObj)) {
                            sendResponse(exchange, 403, "Erişim engellendi: Geçersiz hedef yolu.");
                            return;
                        }

                        Files.move(target.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        sendResponse(exchange, 200, "Ad Degistirildi.");
                    } else if ("mkdir".equals(action)) {
                        if (!target.exists()) target.mkdirs();
                        sendResponse(exchange, 200, "Klasör oluşturuldu.");
                    } else sendResponse(exchange, 400, "Bilinmeyen eylem");

                } catch (Exception e) {
                    sendResponse(exchange, 500, "Sunucu Hatası: " + e.getMessage());
                }
            } else sendResponse(exchange, 405, "Method Not Allowed");
        }

        private void deleteRecursively(File file) {
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) for (File child : children) deleteRecursively(child);
            }
            file.delete();
        }
    }

    private class BuildApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) { sendResponse(exchange, 401, "Error"); return; }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery();
                String projectName = "all";
                if (query != null) {
                    for(String arg : query.split("&")) { if(arg.startsWith("project=")) projectName = arg.replace("project=", ""); }
                }
                final String targetProject = projectName;

                // Path traversal korumasi: proje adi sadece guvenli karakterler icerebilir
                if (!targetProject.equals("all") && !isSafeProjectName(targetProject)) {
                    sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Gecersiz proje adi.\"}");
                    return;
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (targetProject.equals("all")) {
                        File scriptsDir = new File(plugin.getDataFolder(), "scripts");
                        File[] projects = scriptsDir.listFiles(File::isDirectory);
                        if (projects != null) {
                            for (File p : projects) plugin.buildProject(p.getName(), Bukkit.getConsoleSender());
                        }
                    } else {
                        plugin.buildProject(targetProject, Bukkit.getConsoleSender());
                    }
                });
                sendResponse(exchange, 200, "{\"status\": \"building\"}");
            } else sendResponse(exchange, 405, "Method Not Allowed");
        }
    }

    private class LogApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) { sendResponse(exchange, 401, "Error"); return; }

            String query = exchange.getRequestURI().getQuery();
            String projectName = "";
            if (query != null) {
                for(String arg : query.split("&")) { if(arg.startsWith("project=")) projectName = arg.replace("project=", ""); }
            }

            String logs = plugin.getProjectLogs().getOrDefault(projectName, "Bu proje için henüz bir log kaydı bulunamadı.");
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            sendResponse(exchange, 200, logs);
        }
    }

    /**
     * Proje adinin path traversal icin kullanilamayacagini garanti eder.
     * Sadece harf, rakam, alt cizgi ve tire kabul edilir.
     */
    static boolean isSafeProjectName(String name) {
        return name != null && !name.isEmpty() && name.matches("[A-Za-z0-9_\\-]+");
    }

    /**
     * JSON injection / XSS onlemek icin string degerleri JSON'a gomulmeden once kacislar.
     */
    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"'  -> sb.append("\\\"");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    /**
     * Istemci IP'sini dondurur. HTTPS reverse proxy (Nginx/Caddy vb.) arkasinda calisirken
     * config'de 'behind-reverse-proxy: true' ayarlanirsa X-Forwarded-For basligindaki
     * gercek istemci IP'si kullanilir; aksi halde dogrudan baglanti IP'si kullanilir.
     */
    private String getClientIp(HttpExchange exchange) {
        if (plugin.getConfig().getBoolean("behind-reverse-proxy", false)) {
            String xff = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
            if (xff != null && !xff.isEmpty()) {
                return xff.split(",")[0].trim();
            }
        }
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}
