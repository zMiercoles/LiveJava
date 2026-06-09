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

    // We store running projects (Workspace) with the System Loader
    private final Map<String, ScriptContext> activeScripts = new ConcurrentHashMap<>();

    public ScriptManager(Plugin plugin) {
        this.activePlugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Holds all ScriptInstances owned by a project.
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
     * Scans a full project folder for compiled classes, detects LiveScript implementations, and starts them.
     */
    public boolean loadAndStartProject(String projectId, File classDir) {
        // If a workspace is already running for this project, correctly clear it entirely before loading the new one
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

                // Detect if LiveScript is implemented, is not an interface, and is not abstract
                if (LiveScript.class.isAssignableFrom(clazz) && !clazz.isInterface() && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                    LiveScript instance = (LiveScript) clazz.getDeclaredConstructor().newInstance();
                    instances.add(instance);
                }
            }

            if (instances.isEmpty()) {
                logger.warning("[LiveJava] No main class implementing LiveScript interface found in the project. Only side classes were compiled.");
            }

            activeScripts.put(projectId, new ScriptContext(instances, projectClassLoader));

            // Execute onStart for all detected main classes
            for (LiveScript script : instances) {
                try {
                    script.onStart();
                } catch (Exception e) {
                    logger.severe("[LiveJava] The onStart process of class '" + script.getClass().getSimpleName() + "' has failed!");
                    e.printStackTrace();
                }
            }
            logger.info("§a[LiveJava] Project '" + projectId + "' (with " + instances.size() + " main plugin scripts) started successfully.");
            return true;

        } catch (Exception e) {
            logger.severe("An error occurred while loading project '" + projectId + "'!");
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
                scriptInstance.cleanupBase(); // Automatic garbage collection base (removes commands/events safely)
            } catch (Exception e) {
                logger.severe("LiveScript stop error! Class: " + scriptInstance.getClass().getSimpleName());
                e.printStackTrace();
            }
        }

        try {
            context.classLoader.close();
            logger.info("§c[LiveJava] Project '" + projectId + "' stopped entirely and memory freed.");
        } catch (Exception e) {
            logger.severe("ClassLoader could not be closed: " + projectId);
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