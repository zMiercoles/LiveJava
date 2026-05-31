package livejava.api;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * LiveJava üzerinden runtime'da derlenecek ve çalıştırılacak tüm betikler
 * bu abstract sınıfı (soyut sınıfı) kullanmak (extends) zorundadır.
 */
public abstract class LiveScript {

    // Bu script oluşturulduğunda hafızaya alınan komutların listesi
    // Script durdurulunca (reload atılınca) bu komutlar otomatik çöpe atılır (silinir).
    private final List<Command> registeredCommands = new ArrayList<>();

    /**
     * Script hafızaya yüklenip başlatıldığında tetiklenir.
     * Event kayıtları, komut oluşturma veya zamanlayıcı (task) başlatma buralarda yapılır.
     */
    public abstract void onStart();

    /**
     * Script durdurulduğunda veya LiveJava plugin'i kapatıldığında tetiklenir.
     */
    public abstract void onStop();

    /**
     * Script durdurulduğunda LiveJava tarafından arka planda (otomatik) çağrılan temizleme mekanizması.
     */
    public final void cleanupBase() {
        for (Command cmd : registeredCommands) {
            CommandAPI.unregister(cmd);
        }
        registeredCommands.clear();

        // Otomatik Listener Temizliği (Script eğer Listener implement etmişse hayalet eventleri siler)
        if (this instanceof org.bukkit.event.Listener) {
            org.bukkit.event.HandlerList.unregisterAll((org.bukkit.event.Listener) this);
        }
    }

    /**
     * Skript vari mükemmel kolaylıkta Komut oluşturma fonksiyonu.
     * Kullanım:
     * command("merhaba", "Harika bir komut")
     *      .execute((sender, args) -> sender.sendMessage("Selam!"))
     *      .register();
     */
    public LiveCommandBuilder command(String name, String description) {
        return new LiveCommandBuilder(name, description, this);
    }

    // Builder Sınıfı Mimari
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
                            sender.sendMessage("§cKomut çalışırken bir hata oluştu!");
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

            // Sisteme enjekte et ve oyunculara kırmızı çizgiyi yok edecek paketi gönder!
            CommandAPI.register(dynamicCommand);

            // Çöpe atıcı sisteme kodlanan komutu kaydet (reload atınca temizlesin diye)
            owner.registeredCommands.add(dynamicCommand);
        }
    }
}