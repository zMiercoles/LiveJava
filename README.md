# LiveJava: The Native Java IDE for your Server

**Say goodbye to restarts, reloading times, and limited scripting languages. Meet LiveJava—the ultimate bridge between professional development and server management.**

LiveJava embeds a **secure, VS Code-powered Web IDE** directly into your Minecraft server. It allows you to write, compile, and execute **Native Java code** in real-time. No more `reload` commands, no more heavy third-party scripting engines—just pure, high-performance Java.

---

### 🔥 Key Features

* **Zero-Restart Workflow:** Modify your code, hit "Build", and see the changes active instantly.
* **Native Performance:** Unlike Skript or other parsers, your code runs directly on the JVM, delivering maximum server performance.
* **Embedded Web IDE:** A clean, VS Code (Monaco) based environment with auto-completion, syntax highlighting, and error tracking—right in your browser.
* **Auto-Classpath Injection:** LiveJava automatically detects all installed plugins on your server. Need `Vault` or `WorldEdit`? Just import them and start using their APIs immediately!
* **Multi-Class Project Support:** Stop cramming code into a single file. Build structured, professional projects with multiple classes.
* **High Security:** Your IDE is accessible via a custom port, locked with a unique IP filter, and protected by a secret key. Only you have the power.
* **Persistent Storage:** All your code is saved directly to your disk. No data loss on server restarts.

---

### 🚀 Why choose LiveJava over Skript?

| Feature       |          Skript    |    LiveJava    |
| ---           |           ---      |      ---       |
| **Execution** | Interpreted (Slow) | Compiled (Native JVM Speed) |
| **Language** | Limited Custom Syntax | Pure, Standard Java |
| **IDE Experience** | Notepad / Basic Text | Advanced Web IDE (VS Code) |
| **Dependencies** | Requires Add-ons | Auto-links all local plugins |

---

### 🛡️ Security First

We know "remote code execution" sounds scary. That's why LiveJava is built with a **"Loopback-Only"** policy. The editor is isolated and accessible only via the specific IP and secure key combination you define in the configuration.

---

### 📥 Getting Started

1. Install the plugin.
2. Set your custom port and secret key in `config.yml`.
3. Open your browser to `http://your-server-ip:port/your-secret-key`.
4. Start coding and save to deploy!

---

**LiveJava is 100% Free and Open Source.** If you love the project, consider leaving a star on GitHub!
