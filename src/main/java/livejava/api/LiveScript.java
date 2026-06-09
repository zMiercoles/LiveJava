package livejava.api;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * All scripts to be compiled and executed at runtime via LiveJava
 * must extend this abstract class.
 */
public abstract class LiveScript {

    // List of commands loaded into memory when this script is executed
    // When the script is stopped (or reloaded), these commands are automatically garbage collected.
    private final List<Command> registeredCommands = new ArrayList<>();

    /**
     * Triggered when the script is loaded into memory and started.
     * Event registrations, command creations, or tasks should be initialized here.
     */
    public abstract void onStart();

    /**
     * Triggered when the script is stopped or when the LiveJava plugin is disabled.
     */
    public abstract void onStop();

    /**
     * Background (automatic) cleanup mechanism called by LiveJava when the script stops.
     */
    public final void cleanupBase() {
        for (Command cmd : registeredCommands) {
            CommandAPI.unregister(cmd);
        }
        registeredCommands.clear();

        // Automatic Listener Cleanup (Deletes ghost events if the script implements Listener)
        if (this instanceof org.bukkit.event.Listener) {
            org.bukkit.event.HandlerList.unregisterAll((org.bukkit.event.Listener) this);
        }
    }

    /**
     * Skript-like beautifully easy command creation method.
     * Usage:
     * command("hello", "A great command")
     *      .execute((sender, args) -> sender.sendMessage("Hi!"))
     *      .register();
     */
    public LiveCommandBuilder command(String name, String description) {
        return new LiveCommandBuilder(name, description, this);
    }

    // Builder Class Architecture
    public class LiveCommandBuilder {
        private final String name;
        private final String description;
        private List<String> aliases = new ArrayList<>();
        private BiConsumer<CommandSender, String[]> executeLogic;
        private BiFunction<CommandSender, String[], List<String>> tabLogic;
        private final LiveScript owner;

        public LiveCommandBuilder(String name, String description, LiveScript owner) {
            this.name = name;
            this.description = description;
            this.owner = owner;
        }

        public LiveCommandBuilder aliases(String... aliases) {
            this.aliases.addAll(Arrays.asList(aliases));
            return this;
        }

        public LiveCommandBuilder execute(BiConsumer<CommandSender, String[]> logic) {
            this.executeLogic = logic;
            return this;
        }

        public LiveCommandBuilder tabComplete(BiFunction<CommandSender, String[], List<String>> logic) {
            this.tabLogic = logic;
            return this;
        }

        public void register() {
            Command dynamicCommand = new Command(name, description, "/" + name, aliases) {
                @Override
                public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                    if (executeLogic != null) {
                        try {
                            executeLogic.accept(sender, args);
                        } catch (Exception e) {
                            sender.sendMessage("§cAn error occurred while executing the command!");
                            e.printStackTrace();
                        }
                        return true;
                    }
                    return false;
                }

                @Override
                public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
                    if (tabLogic != null) {
                        return tabLogic.apply(sender, args);
                    }
                    return super.tabComplete(sender, alias, args);
                }
            };

            // Inject to system and send the packet that will remove the red line indicator!
            CommandAPI.register(dynamicCommand);

            // Register the generated command into the garbage collector (to clean up upon reload)
            owner.registeredCommands.add(dynamicCommand);
        }
    }
}