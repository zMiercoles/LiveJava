package livejava.compiler;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class RuntimeCompiler {

    public static class CompilationResult {
        public final boolean success;
        public final String errorMessage;
        public final File outputDir;

        public CompilationResult(boolean success, String errorMessage, File outputDir) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.outputDir = outputDir;
        }
    }

    public CompilationResult compileProject(File srcDir, File outputDir, File pluginsDir) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return new CompilationResult(false, "JavaCompiler not found. Please start your server with JDK instead of JRE.", null);
        }

        java.util.List<File> javaFiles = new java.util.ArrayList<>();
        collectJavaFiles(srcDir, javaFiles);

        if (javaFiles.isEmpty()) {
            return new CompilationResult(false, "No .java files found to compile!", null);
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);

        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(javaFiles);

        if (!outputDir.exists()) outputDir.mkdirs();

        String fullClasspath = buildClasspath(pluginsDir);

        // Sistemin kendi Java sürümünü (örneğin 17 veya 21) otomatik al
        String javaVersion = System.getProperty("java.specification.version");

        Iterable<String> options = Arrays.asList(
                "-d", outputDir.getAbsolutePath(),
                "-target", javaVersion,
                "-source", javaVersion,
                "-classpath", fullClasspath);

        JavaCompiler.CompilationTask task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                options,
                null,
                compilationUnits
        );

        boolean success = task.call();
        String errorString = "";

        if (!success) {
            errorString = diagnostics.getDiagnostics().stream()
                    .map(d -> "File: " + d.getSource().getName() + " - Line " + d.getLineNumber() + " [" + d.getKind() + "]: " + d.getMessage(null))
                    .collect(Collectors.joining("\n"));
        }

        try {
            fileManager.close();
        } catch (IOException ignored) {}

        return new CompilationResult(success, errorString, outputDir);
    }

    private String buildClasspath(File pluginsDir) {
        Set<String> classPaths = new HashSet<>();
        classPaths.add(System.getProperty("java.class.path"));

        try { classPaths.add(new File(org.bukkit.Bukkit.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath()); } catch (Exception ignored) {}
        try { classPaths.add(new File(livejava.api.LiveScript.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath()); } catch (Exception ignored) {}
        try { classPaths.add(new File(Class.forName("net.kyori.adventure.audience.Audience").getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath()); } catch (Exception ignored) {}
        try { classPaths.add(new File(Class.forName("net.kyori.adventure.text.Component").getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath()); } catch (Exception ignored) {}

        if (pluginsDir != null && pluginsDir.exists()) {
            addJarsRecursively(pluginsDir, classPaths);
            File serverRoot = pluginsDir.getParentFile();
            for (String dirName : new String[]{"libraries", "bundler", "versions", "cache"}) {
                File dir = new File(serverRoot, dirName);
                if (dir.exists()) addJarsRecursively(dir, classPaths);
            }
        }

        return String.join(File.pathSeparator, classPaths);
    }

    private void collectJavaFiles(File dir, java.util.List<File> fileList) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectJavaFiles(f, fileList);
            } else if (f.getName().endsWith(".java")) {
                fileList.add(f);
            }
        }
    }

    private void addJarsRecursively(File dir, Set<String> classPaths) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                addJarsRecursively(f, classPaths);
            } else if (f.getName().toLowerCase().endsWith(".jar")) {
                classPaths.add(f.getAbsolutePath());
            }
        }
    }
}
