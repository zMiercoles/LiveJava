package livejava;

import livejava.compiler.RuntimeCompiler;
import livejava.manager.ScriptManager;
import livejava.web.WebIDEServer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class LiveJavaPlugin extends JavaPlugin implements TabCompleter {

    private ScriptManager scriptManager;
    private RuntimeCompiler compiler;
    private WebIDEServer webServer;
    private Lang lang;

    private String publicIp = "127.0.0.1";

    private final java.util.Map<String, String> projectStatuses = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, String> projectLogs     = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<CommandSender> uncensoredUsers = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public java.util.Map<String, String> getProjectStatuses() { return projectStatuses; }
    public java.util.Map<String, String> getProjectLogs() { return projectLogs; }
    public RuntimeCompiler getCompiler() { return compiler; }

    public void broadcastIdeLog(String action, String ip) {
        String censored = censorIp(ip);
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            if (p.isOp() || p.hasPermission("livejava.logs")) {
                String displayIp = uncensoredUsers.contains(p) ? ip : censored;
                p.sendMessage("§8[§eLiveJava§8] §7" + action + " §8(IP: §f" + displayIp + "§8)");
            }
        }
    }

    private String censorIp(String ip) {
        if (ip == null) return "***.***.***.***";
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return "***.***.***." + parts[3];
        }
        return "***.***.***.***";
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.lang = new Lang();
        this.lang.setLang(getConfig().getString("language", "en"));

        this.scriptManager = new ScriptManager(this);
        this.compiler      = new RuntimeCompiler();

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                java.util.Scanner s = new java.util.Scanner(
                        new java.net.URL("https://api.ipify.org").openStream(), "UTF-8").useDelimiter("\\A");
                if (s.hasNext()) publicIp = s.next();
            } catch (Exception ignored) {
                publicIp = Bukkit.getIp().isEmpty() || Bukkit.getIp().equals("0.0.0.0")
                        ? "localhost" : Bukkit.getIp();
            }
            getLogger().info("LiveJava Public IP: " + publicIp);
        });

        int webPort = getConfig().getInt("web-ide-port", 8080);

        File tempClassesDir = new File(getDataFolder(), "temp_classes");
        if (!tempClassesDir.exists()) tempClassesDir.mkdirs();

        File scriptsDir = new File(getDataFolder(), "scripts");
        if (!scriptsDir.exists()) {
            scriptsDir.mkdirs();
            createDemoScript(scriptsDir);
        }

        File editorHtml = new File(getDataFolder(), "editor.html");
        if (!editorHtml.exists()) {
            try (InputStream in = getResource("editor.html")) {
                if (in != null) Files.copy(in, editorHtml.toPath());
            } catch (IOException e) {
                getLogger().severe("editor.html could not be written to disk!");
            }
        }

        this.webServer = new WebIDEServer(this, webPort);
        this.webServer.start();

        getCommand("livejava").setTabCompleter(this);

        autoRestoreProjects();

        getLogger().info("LiveJava enabled! (Web Port: " + webPort + ")");
    }

    @Override
    public void onDisable() {
        saveRunningProjects();
        if (scriptManager != null) scriptManager.stopAll();
        if (webServer != null) webServer.stop();
        getLogger().info("LiveJava disabled.");
    }

    // ── Auto-restart ────────────────────────────────────────────────────────

    private File getRunningProjectsFile() {
        return new File(getDataFolder(), "running_projects.txt");
    }

    private void saveRunningProjects() {
        List<String> running = new ArrayList<>();
        for (java.util.Map.Entry<String, String> e : projectStatuses.entrySet()) {
            if ("running".equals(e.getValue())) running.add(e.getKey());
        }
        try {
            Files.write(getRunningProjectsFile().toPath(), running, StandardCharsets.UTF_8);
        } catch (IOException e) {
            getLogger().warning("Could not save running projects: " + e.getMessage());
        }
    }

    private void autoRestoreProjects() {
        File file = getRunningProjectsFile();
        if (!file.exists()) return;
        try {
            List<String> projects = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            if (projects.isEmpty()) return;
            getLogger().info("[LiveJava] Auto-restart: restoring " + projects.size() + " project(s)...");
            Bukkit.getScheduler().runTaskLater(this, () -> {
                for (String name : projects) {
                    name = name.trim();
                    if (name.isEmpty()) continue;
                    getLogger().info("[LiveJava] Auto-restart: building " + name + "...");
                    buildProject(name, null);
                }
            }, 40L);
        } catch (IOException e) {
            getLogger().warning("Could not read auto-restart file: " + e.getMessage());
        }
    }

    // ── UI helpers ──────────────────────────────────────────────────────────

    private static final String PREFIX = "§8║ ";
    private static final String TOP    = "§8╔══════════════════════════════════════════════╗";
    private static final String MID    = "§8╠══════════════════════════════════════════════╣";
    private static final String BOT    = "§8╚══════════════════════════════════════════════╝";

    private void sendBox(CommandSender sender, String title, String... lines) {
        sender.sendMessage(TOP);
        sender.sendMessage(PREFIX + title);
        sender.sendMessage(MID);
        for (String line : lines) sender.sendMessage(PREFIX + line);
        sender.sendMessage(BOT);
    }

    // ── Commands ────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("livejava")) return false;

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendBox(sender, lang.get(Lang.Key.HELP_TITLE),
                "",
                lang.get(Lang.Key.HELP_EDITOR),
                lang.get(Lang.Key.HELP_EDITOR_REMOVE),
                lang.get(Lang.Key.HELP_EDITOR_RELOAD),
                "",
                lang.get(Lang.Key.HELP_BUILD),
                lang.get(Lang.Key.HELP_UNLOAD),
                lang.get(Lang.Key.HELP_RELOAD),
                lang.get(Lang.Key.HELP_DEBUG),
                lang.get(Lang.Key.HELP_LANGUAGE),
                ""
            );
            return true;
        }

        String sub = args[0].toLowerCase();

        // ── permission check helper ─────────────────────────────────────────
        String perm = switch (sub) {
            case "editor" -> "livejava.editor";
            case "build" -> "livejava.build";
            case "unload" -> "livejava.unload";
            case "reload" -> "livejava.reload";
            case "debug" -> "livejava.debug";
            case "lang", "language" -> "livejava.lang";
            default -> null;
        };
        if (perm != null && !sender.hasPermission(perm)) {
            sendBox(sender, "§c§lERROR", lang.get(Lang.Key.NO_PERMISSION));
            return true;
        }

        // ── editor ──────────────────────────────────────────────────────────
        if (sub.equals("editor")) {
            if (args.length > 1) {
                if (args[1].equalsIgnoreCase("remove")) {
                    if (args.length < 3) {
                        sendBox(sender, "§c§lERROR", lang.get(Lang.Key.EDITOR_USAGE_REMOVE));
                        return true;
                    }
                    String key = args[2];
                    if (webServer.removeEditorKey(key)) {
                        sendBox(sender, "§a§lOK",
                            lang.get(Lang.Key.EDITOR_SESSION_CLOSED),
                            "§8Key: §7" + key
                        );
                    } else {
                        sendBox(sender, "§c§lERROR", lang.get(Lang.Key.EDITOR_KEY_NOT_FOUND));
                    }
                    return true;
                }

                if (args[1].equalsIgnoreCase("reload")) {
                    if (webServer != null) webServer.stop();
                    reloadConfig();
                    lang.setLang(getConfig().getString("language", "en"));
                    int newPort = getConfig().getInt("web-ide-port", 8080);
                    this.webServer = new WebIDEServer(this, newPort);
                    this.webServer.start();
                    sendBox(sender, lang.get(Lang.Key.EDITOR_RESTARTED), "§8Port: §b" + newPort);
                    return true;
                }
            }

            String playerIp = "127.0.0.1";
            if (sender instanceof org.bukkit.entity.Player) {
                playerIp = ((org.bukkit.entity.Player) sender).getAddress().getAddress().getHostAddress();
            }

            String token = webServer.generateEditorKey(playerIp);
            int    port  = webServer.getPort();
            String url   = "http://" + publicIp + ":" + port + "/?key=" + token;

            sender.sendMessage(TOP);
            sender.sendMessage(PREFIX + "§b§lLiveJava IDE");
            sender.sendMessage(MID);
            sender.sendMessage(PREFIX + lang.get(Lang.Key.EDITOR_READY));
            sender.sendMessage(PREFIX + lang.get(Lang.Key.EDITOR_IP_LOCK) + playerIp);
            sender.sendMessage(PREFIX);

            net.md_5.bungee.api.chat.TextComponent comp = new net.md_5.bungee.api.chat.TextComponent("§8║ ");
            net.md_5.bungee.api.chat.TextComponent link = new net.md_5.bungee.api.chat.TextComponent("§e§n" + url);
            link.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, url));
            link.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.ComponentBuilder("§aClick to open IDE!").create()));
            comp.addExtra(link);

            if (sender instanceof org.bukkit.entity.Player) {
                ((org.bukkit.entity.Player) sender).spigot().sendMessage(comp);
            } else {
                sender.sendMessage(PREFIX + url);
            }

            sender.sendMessage(PREFIX);
            sender.sendMessage(MID);
            sender.sendMessage(PREFIX + lang.get(Lang.Key.EDITOR_MAGMANODE_1));
            sender.sendMessage(PREFIX + lang.get(Lang.Key.EDITOR_MAGMANODE_2));
            sender.sendMessage(MID);
            sender.sendMessage(PREFIX + lang.get(Lang.Key.EDITOR_SESSION_CLOSE_HINT) + token);
            sender.sendMessage(BOT);
            return true;
        }

        // ── lang / language ─────────────────────────────────────────────────
        if (sub.equals("lang") || sub.equals("language")) {
            if (args.length < 2) {
                sendBox(sender, "§c§lERROR", lang.get(Lang.Key.LANG_USAGE));
                return true;
            }
            String newLang = args[1].toLowerCase();
            if (!lang.isValid(newLang)) {
                sendBox(sender, "§c§lERROR", lang.get(Lang.Key.LANG_UNKNOWN));
                return true;
            }
            lang.setLang(newLang);
            getConfig().set("language", newLang);
            saveConfig();
            sendBox(sender, "§a§lOK", lang.get(Lang.Key.LANG_CHANGED) + newLang.toUpperCase());
            return true;
        }

        // ── uncensorip ──────────────────────────────────────────────────────
        if (sub.equals("uncensorip")) {
            if (!sender.hasPermission("livejava.logs")) {
                sendBox(sender, "§c§lERROR", lang.get(Lang.Key.NO_PERMISSION));
                return true;
            }
            if (uncensoredUsers.contains(sender)) {
                uncensoredUsers.remove(sender);
                sendBox(sender, "§a§lOK", "§7IP addresses are now §ccensored §7in IDE logs.");
            } else {
                uncensoredUsers.add(sender);
                sendBox(sender, "§a§lOK", "§7IP addresses are now §avisible §7in IDE logs.");
            }
            return true;
        }

        // ── reload ───────────────────────────────────────────────────────────
        if (sub.equals("reload")) {
            reloadConfig();
            lang.setLang(getConfig().getString("language", "en"));
            sendBox(sender, "§a§lOK", lang.get(Lang.Key.RELOAD_SUCCESS));
            return true;
        }

        // ── debug ────────────────────────────────────────────────────────────
        if (sub.equals("debug")) {
            runDebug(sender);
            return true;
        }

        // ── load (removed) ───────────────────────────────────────────────────
        if (sub.equals("load")) {
            sendBox(sender, "§e§lInfo", lang.get(Lang.Key.LOAD_REMOVED));
            return true;
        }

        // ── unload ───────────────────────────────────────────────────────────
        if (sub.equals("unload")) {
            if (args.length < 2) {
                sendBox(sender, "§c§lERROR", lang.get(Lang.Key.UNLOAD_USAGE));
                return true;
            }
            if (args[1].equalsIgnoreCase("all")) {
                scriptManager.stopAll();
                projectStatuses.replaceAll((k, v) -> "idle");
                sendBox(sender, "§c§lSTOP", lang.get(Lang.Key.UNLOAD_ALL));
            } else {
                scriptManager.stop(args[1]);
                projectStatuses.put(args[1], "idle");
                sendBox(sender, "§c§lSTOP",
                    "§8Project: §7" + args[1],
                    lang.get(Lang.Key.UNLOAD_SINGLE)
                );
            }
            return true;
        }

        // ── build ────────────────────────────────────────────────────────────
        if (sub.equals("build")) {
            if (args.length < 2) {
                sendBox(sender, "§c§lERROR", lang.get(Lang.Key.BUILD_USAGE));
                return true;
            }
            if (args[1].equalsIgnoreCase("all")) {
                File scriptsDir = new File(getDataFolder(), "scripts");
                File[] projects = scriptsDir.listFiles(File::isDirectory);
                if (projects != null) {
                    for (File p : projects) buildProject(p.getName(), sender);
                }
            } else {
                buildProject(args[1], sender);
            }
            return true;
        }

        sendBox(sender, "§c§lERROR",
            lang.get(Lang.Key.ERROR_UNKNOWN),
            lang.get(Lang.Key.ERROR_HINT)
        );
        return true;
    }

    // ── Tab completer ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!command.getName().equalsIgnoreCase("livejava")) return completions;

        if (args.length == 1) {
            completions.add("editor");
            completions.add("build");
            completions.add("unload");
            completions.add("reload");
            completions.add("debug");
            completions.add("uncensorip");
            completions.add("lang");
            completions.add("help");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("editor")) {
            completions.add("remove");
            completions.add("reload");
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("build") || args[0].equalsIgnoreCase("unload"))) {
            completions.add("all");
            File scriptsDir = new File(getDataFolder(), "scripts");
            File[] projects = scriptsDir.listFiles(File::isDirectory);
            if (projects != null) for (File p : projects) completions.add(p.getName());
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("lang") || args[0].equalsIgnoreCase("language"))) {
            completions.add("en");
            completions.add("tr");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("editor") && args[1].equalsIgnoreCase("remove")) {
            if (webServer != null) completions.addAll(webServer.getActiveKeys());
        }

        return filterCompletions(args[args.length - 1], completions);
    }

    private List<String> filterCompletions(String prefix, List<String> list) {
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(prefix.toLowerCase())) result.add(s);
        }
        return result;
    }

    // ── Build ────────────────────────────────────────────────────────────────

    public ScriptManager getScriptManager() { return scriptManager; }

    public void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    public void buildProject(String projectName, CommandSender sender) {
        File projectDir = new File(getDataFolder(), "scripts/" + projectName);
        if (!projectDir.exists()) {
            if (sender != null) sendBox(sender, "§c§lERROR", lang.get(Lang.Key.BUILD_NOT_FOUND) + projectName);
            return;
        }

        File tempDir = new File(getDataFolder(), "temp_classes/" + projectName);

        if (tempDir.exists()) {
            deleteRecursively(tempDir);
        }

        projectStatuses.put(projectName, "building");
        projectLogs.put(projectName, "[BUILD] Building: " + projectName + "\n");

        if (sender != null) sendBox(sender, lang.get(Lang.Key.BUILD_BUILDING),
            "§8Project: §7" + projectName,
            lang.get(Lang.Key.BUILD_BUILDING_DESC)
        );

        File pluginsDir = getDataFolder().getParentFile();

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            RuntimeCompiler.CompilationResult result =
                    compiler.compileProject(projectDir, tempDir, pluginsDir);

            Bukkit.getScheduler().runTask(this, () -> {
                if (result.success) {
                    boolean loaded = scriptManager.loadAndStartProject(projectName, result.outputDir);
                    if (loaded) {
                        if (sender != null) sendBox(sender, lang.get(Lang.Key.BUILD_SUCCESS),
                            "§8Project: §7" + projectName,
                            lang.get(Lang.Key.BUILD_SUCCESS_DESC)
                        );
                        projectStatuses.put(projectName, "running");
                        projectLogs.put(projectName, projectLogs.getOrDefault(projectName, "") + "[SUCCESS] Active.\n");
                    } else {
                        if (sender != null) sendBox(sender, lang.get(Lang.Key.BUILD_LOAD_ERROR),
                            "§8Project: §7" + projectName,
                            lang.get(Lang.Key.BUILD_LOAD_ERROR_DESC1),
                            lang.get(Lang.Key.BUILD_LOAD_ERROR_DESC2)
                        );
                        projectStatuses.put(projectName, "error");
                        projectLogs.put(projectName, projectLogs.getOrDefault(projectName, "") + "[ERROR] ClassLoader failed.\n");
                    }
                } else {
                    if (sender != null) sendBox(sender, lang.get(Lang.Key.BUILD_COMPILE_ERROR),
                        "§8Project: §7" + projectName,
                        lang.get(Lang.Key.BUILD_COMPILE_ERROR_DESC1),
                        lang.get(Lang.Key.BUILD_COMPILE_ERROR_DESC2)
                    );
                    projectStatuses.put(projectName, "error");
                    projectLogs.put(projectName, projectLogs.getOrDefault(projectName, "")
                            + "--- COMPILE ERROR ---\n" + result.errorMessage);
                }
            });
        });
    }

    // ── Debug ────────────────────────────────────────────────────────────────

    private void runDebug(CommandSender sender) {
        sender.sendMessage(TOP);
        sender.sendMessage(PREFIX + lang.get(Lang.Key.DEBUG_TITLE));
        sender.sendMessage(MID);

        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        long heapUsed    = memBean.getHeapMemoryUsage().getUsed()    / 1024 / 1024;
        long heapMax     = memBean.getHeapMemoryUsage().getMax()     / 1024 / 1024;
        long nonHeapUsed = memBean.getNonHeapMemoryUsage().getUsed() / 1024 / 1024;

        sender.sendMessage(PREFIX + lang.get(Lang.Key.DEBUG_RAM));
        sender.sendMessage(PREFIX + lang.get(Lang.Key.DEBUG_HEAP_USAGE, String.valueOf(heapUsed), String.valueOf(heapMax)));
        sender.sendMessage(PREFIX + lang.get(Lang.Key.DEBUG_NONHEAP, String.valueOf(nonHeapUsed)));

        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getName().toLowerCase().contains("metaspace")) {
                long metaUsed = pool.getUsage().getUsed() / 1024 / 1024;
                long metaMax  = pool.getUsage().getMax();
                String maxStr = metaMax < 0 ? "unlimited" : (metaMax / 1024 / 1024) + " MB";
                sender.sendMessage(PREFIX + lang.get(Lang.Key.DEBUG_METASPACE, String.valueOf(metaUsed), maxStr));
            }
        }

        sender.sendMessage(MID);

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        sender.sendMessage(PREFIX + lang.get(Lang.Key.DEBUG_THREAD));
        sender.sendMessage(PREFIX + lang.get(Lang.Key.DEBUG_TOTAL_THREAD, String.valueOf(threadBean.getThreadCount())));
        sender.sendMessage(PREFIX + lang.get(Lang.Key.DEBUG_PEAK_THREAD,  String.valueOf(threadBean.getPeakThreadCount())));

        long[] deadlocked = threadBean.findDeadlockedThreads();
        if (deadlocked != null && deadlocked.length > 0) {
            sender.sendMessage(PREFIX + lang.get(Lang.Key.DEBUG_DEADLOCK_FOUND, String.valueOf(deadlocked.length)));
        } else {
            sender.sendMessage(PREFIX + lang.get(Lang.Key.DEBUG_NO_DEADLOCK));
        }

        sender.sendMessage(PREFIX + lang.get(Lang.Key.DEBUG_HIGH_CPU));
        long[] threadIds = threadBean.getAllThreadIds();
        java.util.List<long[]> topThreads = new java.util.ArrayList<>();
        for (long tid : threadIds) {
            long cpu = threadBean.getThreadCpuTime(tid);
            if (cpu > 0) topThreads.add(new long[]{tid, cpu});
        }
        topThreads.sort((a, b) -> Long.compare(b[1], a[1]));
        int shown = 0;
        for (long[] entry : topThreads) {
            if (shown++ >= 5) break;
            java.lang.management.ThreadInfo info = threadBean.getThreadInfo(entry[0]);
            if (info == null) continue;
            long cpuMs = entry[1] / 1_000_000;
            String tName = info.getThreadName();
            String tag = "";
            for (String projectName : projectStatuses.keySet()) {
                if (tName.toLowerCase().contains(projectName.toLowerCase())) {
                    tag = " §c[LiveJava:" + projectName + "]";
                    break;
                }
            }
            sender.sendMessage(PREFIX + "§8- §7" + tName + " §8(§e" + cpuMs + "ms§8)" + tag);
        }

        sender.sendMessage(MID);

        sender.sendMessage(PREFIX + lang.get(Lang.Key.DEBUG_PROJECTS));
        if (projectStatuses.isEmpty()) {
            sender.sendMessage(PREFIX + lang.get(Lang.Key.DEBUG_NO_PROJECTS));
        } else {
            for (java.util.Map.Entry<String, String> entry : projectStatuses.entrySet()) {
                String status = entry.getValue();
                String color  = "running".equals(status) ? "§a" : "error".equals(status) ? "§c" : "§7";
                sender.sendMessage(PREFIX + "§8» " + color + entry.getKey() + " §8[" + status + "]");
            }
        }

        sender.sendMessage(MID);

        sender.sendMessage(PREFIX + lang.get(Lang.Key.DEBUG_WARNINGS));
        boolean clean = true;
        if (heapUsed > heapMax * 0.85) {
            sender.sendMessage(PREFIX + lang.get(Lang.Key.DEBUG_WARN_HEAP));
            clean = false;
        }
        if (deadlocked != null && deadlocked.length > 0) {
            sender.sendMessage(PREFIX + lang.get(Lang.Key.DEBUG_WARN_DEADLOCK));
            clean = false;
        }
        long errorCount = projectStatuses.values().stream().filter("error"::equals).count();
        if (errorCount > 0) {
            sender.sendMessage(PREFIX + lang.get(Lang.Key.DEBUG_WARN_ERROR_PROJECTS, String.valueOf(errorCount)));
            clean = false;
        }
        if (nonHeapUsed > 256) {
            sender.sendMessage(PREFIX + lang.get(Lang.Key.DEBUG_WARN_NONHEAP, String.valueOf(nonHeapUsed)));
            clean = false;
        }
        if (clean) sender.sendMessage(PREFIX + lang.get(Lang.Key.DEBUG_CLEAN));

        sender.sendMessage(BOT);
    }

    // ── Demo script ──────────────────────────────────────────────────────────

    private void createDemoScript(File folder) {
        File projectFolder = new File(folder, "DemoProject");
        File packageFolder = new File(projectFolder, "miercoles");
        packageFolder.mkdirs();

        File demoFile = new File(packageFolder, "DemoScript.java");
        String demoContent =
            "package miercoles;\n\n" +
            "import livejava.api.LiveScript;\n" +
            "import org.bukkit.Bukkit;\n" +
            "import org.bukkit.entity.Player;\n" +
            "import org.bukkit.event.EventHandler;\n" +
            "import org.bukkit.event.Listener;\n" +
            "import org.bukkit.event.player.PlayerJoinEvent;\n" +
            "import org.bukkit.event.HandlerList;\n" +
            "import org.bukkit.plugin.java.JavaPlugin;\n" +
            "import org.bukkit.scheduler.BukkitTask;\n" +
            "import java.util.ArrayList;\n" +
            "import java.util.List;\n\n" +
            "public class DemoScript extends LiveScript implements Listener {\n\n" +
            "    private BukkitTask task;\n" +
            "    private JavaPlugin plugin;\n\n" +
            "    @Override\n" +
            "    public void onStart() {\n" +
            "        plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin(\"LiveJava\");\n\n" +
            "        Bukkit.broadcastMessage(\"§a[DemoScript] Successfully started via LiveJava IDE!\");\n\n" +
            "        Bukkit.getPluginManager().registerEvents(this, plugin);\n\n" +
            "        command(\"livejavatest\", \"LiveJava Test Komutu\")\n" +
            "            .aliases(\"ljt\")\n" +
            "            .execute((sender, args) -> {\n" +
            "                if (args.length == 0) {\n" +
            "                    sender.sendMessage(\"§cUsage: /livejavatest [heal/smite]\");\n" +
            "                } else if (sender instanceof Player p) {\n" +
            "                    if (args[0].equalsIgnoreCase(\"heal\")) { p.setHealth(20); p.sendMessage(\"§aHealth restored!\"); }\n" +
            "                    if (args[0].equalsIgnoreCase(\"smite\")) { p.getWorld().strikeLightning(p.getLocation()); }\n" +
            "                }\n" +
            "            })\n" +
            "            .tabComplete((sender, args) -> {\n" +
            "                List<String> list = new ArrayList<>();\n" +
            "                if (args.length == 1) {\n" +
            "                    if (\"heal\".startsWith(args[0].toLowerCase())) list.add(\"heal\");\n" +
            "                    if (\"smite\".startsWith(args[0].toLowerCase())) list.add(\"smite\");\n" +
            "                }\n" +
            "                return list;\n" +
            "            })\n" +
            "            .register();\n\n" +
            "        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {\n" +
            "            for (Player p : Bukkit.getOnlinePlayers()) {\n" +
            "                double health = p.getHealth();\n" +
            "                if (health > 0 && health < 20.0) p.setHealth(Math.min(20.0, health + 1.0));\n" +
            "            }\n" +
            "        }, 0L, 200L);\n" +
            "    }\n\n" +
            "    @Override\n" +
            "    public void onStop() {\n" +
            "        HandlerList.unregisterAll(this);\n" +
            "        if (task != null) task.cancel();\n" +
            "        Bukkit.broadcastMessage(\"§c[DemoScript] Safely removed from memory.\");\n" +
            "    }\n\n" +
            "    @EventHandler\n" +
            "    public void onJoin(PlayerJoinEvent e) {\n" +
            "        e.setJoinMessage(\"§e[LiveJava] §a\" + e.getPlayer().getName() + \" joined and will feel LiveJava's power!\");\n" +
            "    }\n" +
            "}\n";

        try {
            java.nio.file.Files.write(demoFile.toPath(),
                    demoContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            getLogger().warning("Could not write demo script: " + e.getMessage());
        }
    }
}
