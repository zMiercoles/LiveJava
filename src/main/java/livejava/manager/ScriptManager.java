package livejava.manager;

import livejava.api.LiveScript;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ScriptManager {

    private final Plugin activePlugin;
    private final Logger logger;

    // Çalışan projeleri (Workspace) System Loader ile tutuyoruz
    private final Map<String, ScriptContext> activeScripts = new ConcurrentHashMap<>();

    public ScriptManager(Plugin plugin) {
        this.activePlugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Projenin sahip olduğu TÜM ScriptInstance'ları listesinde tutar.
     */
    private static class ScriptContext {
        final List<LiveScript> scriptInstances;
        final URLClassLoader classLoader;

        ScriptContext(List<LiveScript> scriptInstances, URLClassLoader classLoader) {
            this.scriptInstances = scriptInstances;
            this.classLoader = classLoader;
        }
    }

    /**
     * Koca bir klasördeki (projedeki) derlenmiş classları tarayıp, LiveScript olanları tespit edip başlatır.
     */
    public boolean loadAndStartProject(String projectId, File classDir) {
        // Eğer zaten bir Workspace çalışıyorsa, yenisini yüklemeden önce tamamını temizle
        if (activeScripts.containsKey(projectId)) {
            stop(projectId);
        }

        try {
            URL[] urls = new URL[]{classDir.toURI().toURL()};
            URLClassLoader projectClassLoader = new URLClassLoader(urls, activePlugin.getClass().getClassLoader());

            List<LiveScript> instances = new ArrayList<>();
            List<File> classFiles = new ArrayList<>();
            collectClassFiles(classDir, classFiles);

            for (File f : classFiles) {
                String className = getClassNameFromFile(classDir, f);
                Class<?> clazz = projectClassLoader.loadClass(className);

                // Eğer LiveScript implement edilmişse, interface değilse ve abstract değilse tespit et
                if (LiveScript.class.isAssignableFrom(clazz) && !clazz.isInterface() && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                    LiveScript instance = (LiveScript) clazz.getDeclaredConstructor().newInstance();
                    instances.add(instance);
                }
            }

            if (instances.isEmpty()) {
                logger.warning("[LiveJava] Projede LiveScript arayüzünü uygulayan(implement) hiçbir ana sınıf bulunamadı. Sadece yan sınıflar derlendi.");
            }

            activeScripts.put(projectId, new ScriptContext(instances, projectClassLoader));

            // Tespit edilen bütün ana class'ların onStart'ını çalıştır
            for (LiveScript script : instances) {
                try {
                    script.onStart();
                } catch (Exception e) {
                    logger.severe("[LiveJava] '" + script.getClass().getSimpleName() + "' sınıfının onStart işlemi patladı!");
                    e.printStackTrace();
                }
            }
            logger.info("§a[LiveJava] Proje '" + projectId + "' (" + instances.size() + " ana eklenti scripti ile) başarıyla başlatıldı.");
            return true;

        } catch (Exception e) {
            logger.severe("Proje '" + projectId + "' yüklenirken hata oluştu!");
            e.printStackTrace();
            return false;
        }
    }

    public void stop(String projectId) {
        ScriptContext context = activeScripts.remove(projectId);

        if (context == null) return;

        for (LiveScript scriptInstance : context.scriptInstances) {
            try {
                scriptInstance.onStop();
                scriptInstance.cleanupBase(); // Burası harika sistemin otomatik çöpe atıcısı (komutları siler)
            } catch (Exception e) {
                logger.severe("LiveScript stoplama hatası! Sınıf: " + scriptInstance.getClass().getSimpleName());
                e.printStackTrace();
            }
        }

        try {
            context.classLoader.close();
            logger.info("§c[LiveJava] Proje '" + projectId + "' tamamen durduruldu ve bellek serbest bırakıldı.");
        } catch (Exception e) {
            logger.severe("ClassLoader kapatılamadı: " + projectId);
            e.printStackTrace();
        }
    }

    public void stopAll() {
        String[] keys = activeScripts.keySet().toArray(new String[0]);
        for (String scriptId : keys) {
            stop(scriptId);
        }
    }

    private void collectClassFiles(File dir, List<File> list) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectClassFiles(f, list);
            } else if (f.getName().endsWith(".class")) {
                list.add(f);
            }
        }
    }

    private String getClassNameFromFile(File baseDir, File classFile) {
        String basePath = baseDir.getAbsolutePath();
        String classPath = classFile.getAbsolutePath();
        String relativePath = classPath.substring(basePath.length() + 1);
        return relativePath.replace(File.separatorChar, '.').replace(".class", "");
    }
}