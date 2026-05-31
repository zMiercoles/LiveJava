package livejava;

import java.util.HashMap;
import java.util.Map;

public class Lang {

    public enum Key {
        HELP_TITLE,
        HELP_EDITOR, HELP_EDITOR_REMOVE, HELP_EDITOR_RELOAD,
        HELP_BUILD, HELP_UNLOAD, HELP_RELOAD, HELP_DEBUG, HELP_LANGUAGE,

        EDITOR_USAGE_REMOVE, EDITOR_SESSION_CLOSED, EDITOR_KEY_NOT_FOUND,
        EDITOR_RESTARTED, EDITOR_READY, EDITOR_IP_LOCK,
        EDITOR_MAGMANODE_1, EDITOR_MAGMANODE_2, EDITOR_SESSION_CLOSE_HINT,

        BUILD_USAGE,
        BUILD_BUILDING, BUILD_BUILDING_DESC,
        BUILD_SUCCESS, BUILD_SUCCESS_DESC,
        BUILD_LOAD_ERROR, BUILD_LOAD_ERROR_DESC1, BUILD_LOAD_ERROR_DESC2,
        BUILD_COMPILE_ERROR, BUILD_COMPILE_ERROR_DESC1, BUILD_COMPILE_ERROR_DESC2,
        BUILD_NOT_FOUND,

        UNLOAD_USAGE, UNLOAD_ALL, UNLOAD_SINGLE,
        RELOAD_SUCCESS,
        LOAD_REMOVED,

        LANG_CHANGED, LANG_UNKNOWN, LANG_USAGE,

        NO_PERMISSION,
        ERROR_UNKNOWN, ERROR_HINT,

        DEBUG_TITLE, DEBUG_RAM, DEBUG_HEAP_USAGE, DEBUG_NONHEAP,
        DEBUG_METASPACE, DEBUG_THREAD, DEBUG_TOTAL_THREAD, DEBUG_PEAK_THREAD,
        DEBUG_DEADLOCK_FOUND, DEBUG_NO_DEADLOCK, DEBUG_HIGH_CPU,
        DEBUG_PROJECTS, DEBUG_NO_PROJECTS,
        DEBUG_WARNINGS, DEBUG_WARN_HEAP, DEBUG_WARN_DEADLOCK,
        DEBUG_WARN_ERROR_PROJECTS, DEBUG_WARN_NONHEAP, DEBUG_CLEAN
    }

    private static final Map<String, Map<Key, String>> LANGS = new HashMap<>();

    static {
        // ── ENGLISH ──────────────────────────────────────────────
        Map<Key, String> en = new HashMap<>();
        en.put(Key.HELP_TITLE,           "§b§lLiveJava §8§l| §7Command Guide");
        en.put(Key.HELP_EDITOR,          "§e/livejava editor         §8» §7Creates an IDE access link");
        en.put(Key.HELP_EDITOR_REMOVE,   "§e/livejava editor remove  §8» §7Terminates active session");
        en.put(Key.HELP_EDITOR_RELOAD,   "§e/livejava editor reload  §8» §7Restarts the web server");
        en.put(Key.HELP_BUILD,           "§a/livejava build <project> §8» §7Compiles and starts the project");
        en.put(Key.HELP_UNLOAD,          "§c/livejava unload <project>§8» §7Removes project from memory");
        en.put(Key.HELP_RELOAD,          "§b/livejava reload          §8» §7Reloads config.yml");
        en.put(Key.HELP_DEBUG,           "§d/livejava debug           §8» §7RAM/Thread/Crash analysis");
        en.put(Key.HELP_LANGUAGE,        "§e/livejava lang <en|tr>    §8» §7Change plugin language");

        en.put(Key.EDITOR_USAGE_REMOVE,  "§7Usage: §e/livejava editor remove <key>");
        en.put(Key.EDITOR_SESSION_CLOSED,"§7Key successfully revoked.");
        en.put(Key.EDITOR_KEY_NOT_FOUND, "§7No such key is registered.");
        en.put(Key.EDITOR_RESTARTED,     "§a§lIDE Restarted");
        en.put(Key.EDITOR_READY,         "§aYour editor link is ready! Click to open:");
        en.put(Key.EDITOR_IP_LOCK,       "§8IP Lock: §7");
        en.put(Key.EDITOR_MAGMANODE_1,   "§7If you use Magmanode, enter the company's");
        en.put(Key.EDITOR_MAGMANODE_2,   "§7IP address with port in your browser.");
        en.put(Key.EDITOR_SESSION_CLOSE_HINT, "§8Close session: §c/livejava editor remove ");

        en.put(Key.BUILD_USAGE,            "§7Usage: §e/livejava build <project | all>");
        en.put(Key.BUILD_BUILDING,       "§e§lBuilding...");
        en.put(Key.BUILD_BUILDING_DESC,  "§7Analyzing and compiling code...");
        en.put(Key.BUILD_SUCCESS,        "§a§lBuild Successful");
        en.put(Key.BUILD_SUCCESS_DESC,   "§7Project compiled and active on server!");
        en.put(Key.BUILD_LOAD_ERROR,     "§c§lLoad Error");
        en.put(Key.BUILD_LOAD_ERROR_DESC1,"§7Compiled but ClassLoader failed.");
        en.put(Key.BUILD_LOAD_ERROR_DESC2,"§7Check logs.");
        en.put(Key.BUILD_COMPILE_ERROR,  "§c§lCompile Error");
        en.put(Key.BUILD_COMPILE_ERROR_DESC1,"§7There are compilation errors in your code!");
        en.put(Key.BUILD_COMPILE_ERROR_DESC2,"§7Check the console for details.");
        en.put(Key.BUILD_NOT_FOUND,      "§7Project not found: §e");

        en.put(Key.UNLOAD_USAGE,         "§7Usage: §e/livejava unload <project | all>");
        en.put(Key.UNLOAD_ALL,           "§7All projects removed from memory.");
        en.put(Key.UNLOAD_SINGLE,        "§7Successfully removed from memory.");
        en.put(Key.RELOAD_SUCCESS,       "§7config.yml successfully reloaded.");
        en.put(Key.LOAD_REMOVED,         "§7This command is removed. Use §e/livejava build§7.");

        en.put(Key.LANG_CHANGED,         "§aLanguage changed to: §f");
        en.put(Key.LANG_UNKNOWN,         "§cUnknown language. Available: §fen, tr");
        en.put(Key.LANG_USAGE,           "§7Usage: §e/livejava lang <en|tr>");

        en.put(Key.NO_PERMISSION,        "§cYou don't have permission to use this command.");
        en.put(Key.ERROR_UNKNOWN,        "§7Unknown command. For help:");
        en.put(Key.ERROR_HINT,           "§e/livejava help");

        en.put(Key.DEBUG_TITLE,          "§d§lLiveJava Debug Report");
        en.put(Key.DEBUG_RAM,            "§e§lRAM (Heap)");
        en.put(Key.DEBUG_HEAP_USAGE,     "§7Used: §a%s MB §8/ §7Max: §c%s MB");
        en.put(Key.DEBUG_NONHEAP,        "§7Non-Heap (Metaspace etc.): §b%s MB");
        en.put(Key.DEBUG_METASPACE,      "§7Metaspace: §b%s MB §8/ §7Max: §c%s");
        en.put(Key.DEBUG_THREAD,         "§e§lThread Analysis");
        en.put(Key.DEBUG_TOTAL_THREAD,   "§7Total Threads: §f%s");
        en.put(Key.DEBUG_PEAK_THREAD,    "§7Peak Threads: §f%s");
        en.put(Key.DEBUG_DEADLOCK_FOUND, "§c§lDEADLOCK DETECTED! §7(%s threads locked)");
        en.put(Key.DEBUG_NO_DEADLOCK,    "§aNo deadlock detected.");
        en.put(Key.DEBUG_HIGH_CPU,       "§7High CPU threads:");
        en.put(Key.DEBUG_PROJECTS,       "§e§lActive LiveJava Projects");
        en.put(Key.DEBUG_NO_PROJECTS,    "§7No projects loaded.");
        en.put(Key.DEBUG_WARNINGS,       "§e§lPossible Issue Warnings");
        en.put(Key.DEBUG_WARN_HEAP,      "§c! Heap usage above 85%%, running out of RAM.");
        en.put(Key.DEBUG_WARN_DEADLOCK,  "§c! Deadlock present, server may freeze.");
        en.put(Key.DEBUG_WARN_ERROR_PROJECTS, "§c! §7%s project(s) in error state.");
        en.put(Key.DEBUG_WARN_NONHEAP,   "§e! Non-Heap memory high (%s MB). Possible ClassLoader leak.");
        en.put(Key.DEBUG_CLEAN,          "§aNo issues detected.");
        LANGS.put("en", en);

        // ── TÜRKÇE ───────────────────────────────────────────────
        Map<Key, String> tr = new HashMap<>();
        tr.put(Key.HELP_TITLE,           "§b§lLiveJava §8§l| §7Komut Rehberi");
        tr.put(Key.HELP_EDITOR,          "§e/livejava editor         §8» §7IDE giriş linki oluşturur");
        tr.put(Key.HELP_EDITOR_REMOVE,   "§e/livejava editor remove  §8» §7Aktif oturumu sonlandırır");
        tr.put(Key.HELP_EDITOR_RELOAD,   "§e/livejava editor reload  §8» §7Web portunu yeniden başlatır");
        tr.put(Key.HELP_BUILD,           "§a/livejava build <proje>  §8» §7Projeyi derler ve başlatır");
        tr.put(Key.HELP_UNLOAD,          "§c/livejava unload <proje> §8» §7Projeyi bellekten kaldırır");
        tr.put(Key.HELP_RELOAD,          "§b/livejava reload          §8» §7Config'i yeniden yükler");
        tr.put(Key.HELP_DEBUG,           "§d/livejava debug           §8» §7RAM/Thread/Crash analizi");
        tr.put(Key.HELP_LANGUAGE,        "§e/livejava lang <en|tr>   §8» §7Eklenti dilini değiştirir");

        tr.put(Key.EDITOR_USAGE_REMOVE,  "§7Kullanım: §e/livejava editor remove <key>");
        tr.put(Key.EDITOR_SESSION_CLOSED,"§7Anahtar başarıyla engellendi.");
        tr.put(Key.EDITOR_KEY_NOT_FOUND, "§7Böyle bir anahtar kayıtlı değil.");
        tr.put(Key.EDITOR_RESTARTED,     "§a§lIDE Yeniden Başlatıldı");
        tr.put(Key.EDITOR_READY,         "§aEditör linkiniz hazır! Tıklayarak açın:");
        tr.put(Key.EDITOR_IP_LOCK,       "§8IP Kilidi: §7");
        tr.put(Key.EDITOR_MAGMANODE_1,   "§7Magmanode kullanıyorsanız, firmanın IP");
        tr.put(Key.EDITOR_MAGMANODE_2,   "§7adresini port ile tarayıcıya girin.");
        tr.put(Key.EDITOR_SESSION_CLOSE_HINT, "§8Oturumu kapat: §c/livejava editor remove ");

        tr.put(Key.BUILD_USAGE,            "§7Kullanım: §e/livejava build <proje | all>");
        tr.put(Key.BUILD_BUILDING,       "§e§lDerleniyor...");
        tr.put(Key.BUILD_BUILDING_DESC,  "§7Kod analiz ediliyor ve derleniyor...");
        tr.put(Key.BUILD_SUCCESS,        "§a§lBuild Başarılı");
        tr.put(Key.BUILD_SUCCESS_DESC,   "§7Proje derlendi ve sunucuda aktif!");
        tr.put(Key.BUILD_LOAD_ERROR,     "§c§lYükleme Hatası");
        tr.put(Key.BUILD_LOAD_ERROR_DESC1,"§7Derleme başarılı fakat ClassLoader yüklenemedi.");
        tr.put(Key.BUILD_LOAD_ERROR_DESC2,"§7Logları kontrol edin.");
        tr.put(Key.BUILD_COMPILE_ERROR,  "§c§lDerleme Hatası");
        tr.put(Key.BUILD_COMPILE_ERROR_DESC1,"§7Kodunuzda derleme hatası var!");
        tr.put(Key.BUILD_COMPILE_ERROR_DESC2,"§7Detaylar için konsolu kontrol edin.");
        tr.put(Key.BUILD_NOT_FOUND,      "§7Proje bulunamadı: §e");

        tr.put(Key.UNLOAD_USAGE,         "§7Kullanım: §e/livejava unload <proje | all>");
        tr.put(Key.UNLOAD_ALL,           "§7Tüm projeler bellekten kaldırıldı.");
        tr.put(Key.UNLOAD_SINGLE,        "§7Bellekten başarıyla kaldırıldı.");
        tr.put(Key.RELOAD_SUCCESS,       "§7config.yml başarıyla yeniden okundu.");
        tr.put(Key.LOAD_REMOVED,         "§7Bu komut kaldırıldı. Lütfen §e/livejava build§7 kullanın.");

        tr.put(Key.LANG_CHANGED,         "§aDil değiştirildi: §f");
        tr.put(Key.LANG_UNKNOWN,         "§cBilinmeyen dil. Kullanılabilir: §fen, tr");
        tr.put(Key.LANG_USAGE,           "§7Kullanım: §e/livejava lang <en|tr>");

        tr.put(Key.NO_PERMISSION,        "§cBu komutu kullanma yetkiniz yok.");
        tr.put(Key.ERROR_UNKNOWN,        "§7Bilinmeyen komut. Yardım için:");
        tr.put(Key.ERROR_HINT,           "§e/livejava help");

        tr.put(Key.DEBUG_TITLE,          "§d§lLiveJava Debug Raporu");
        tr.put(Key.DEBUG_RAM,            "§e§lRAM (Heap)");
        tr.put(Key.DEBUG_HEAP_USAGE,     "§7Kullanılan: §a%s MB §8/ §7Max: §c%s MB");
        tr.put(Key.DEBUG_NONHEAP,        "§7Non-Heap (Metaspace vb.): §b%s MB");
        tr.put(Key.DEBUG_METASPACE,      "§7Metaspace: §b%s MB §8/ §7Max: §c%s");
        tr.put(Key.DEBUG_THREAD,         "§e§lThread Analizi");
        tr.put(Key.DEBUG_TOTAL_THREAD,   "§7Toplam Thread: §f%s");
        tr.put(Key.DEBUG_PEAK_THREAD,    "§7Peak Thread: §f%s");
        tr.put(Key.DEBUG_DEADLOCK_FOUND, "§c§lDEADLOCK TESPİT EDİLDİ! §7(%s thread kilitli)");
        tr.put(Key.DEBUG_NO_DEADLOCK,    "§aDeadlock tespit edilmedi.");
        tr.put(Key.DEBUG_HIGH_CPU,       "§7Yüksek CPU kullanan threadler:");
        tr.put(Key.DEBUG_PROJECTS,       "§e§lAktif LiveJava Projeleri");
        tr.put(Key.DEBUG_NO_PROJECTS,    "§7Hiç proje yüklü değil.");
        tr.put(Key.DEBUG_WARNINGS,       "§e§lOlası Sorun Uyarıları");
        tr.put(Key.DEBUG_WARN_HEAP,      "§c! Heap kullanımı %%85'in üzerinde, RAM dolmak üzere.");
        tr.put(Key.DEBUG_WARN_DEADLOCK,  "§c! Deadlock var, sunucu donabilir.");
        tr.put(Key.DEBUG_WARN_ERROR_PROJECTS, "§c! §7%s proje hata durumunda.");
        tr.put(Key.DEBUG_WARN_NONHEAP,   "§e! Non-Heap bellek yüksek (%s MB). ClassLoader sızıntısı olabilir.");
        tr.put(Key.DEBUG_CLEAN,          "§aTespit edilen bir sorun yok.");
        LANGS.put("tr", tr);
    }

    private String current = "en";

    public void setLang(String lang) {
        if (LANGS.containsKey(lang)) current = lang;
    }

    public String getLang() {
        return current;
    }

    public boolean isValid(String lang) {
        return LANGS.containsKey(lang);
    }

    public String get(Key key) {
        return LANGS.get(current).getOrDefault(key, LANGS.get("en").get(key));
    }

    public String get(Key key, Object... args) {
        return String.format(get(key), args);
    }
}
