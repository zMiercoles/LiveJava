package livejava.api;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.SimpleCommandMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class CommandAPI {

    /**
     * Spigot'un gizli CommandMap nesnesine sızarak, plugin.yml gerektirmeden dinamik komut ekler.
     */
    public static void register(Command command) {
        try {
            // Sunucunun ana CommandMap nesnesini reflection ile al
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            SimpleCommandMap commandMap = (SimpleCommandMap) commandMapField.get(Bukkit.getServer());

            // Komutu sisteme kaydet (Wrapper falan değil, direkt saf obje olarak ki çalışsın)
            commandMap.register("livejava", command);

            // Sunucunun Komut Ağacını (Brigadier) güncelle ki Kırmızılıklar gitsin ve Tab-Complete çalışsın
            syncCommands();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Script durdurulduğunda komutu sunucudan sileriz ki hayalet komutlar kalmasın.
     */
    @SuppressWarnings("unchecked")
    public static void unregister(Command command) {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            SimpleCommandMap commandMap = (SimpleCommandMap) commandMapField.get(Bukkit.getServer());

            // knownCommands map'ine sızıntı
            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            // Komutu ve varsa alias'ları sil
            knownCommands.remove(command.getName().toLowerCase());
            knownCommands.remove("livejava:" + command.getName().toLowerCase());
            for (String alias : command.getAliases()) {
                knownCommands.remove(alias.toLowerCase());
                knownCommands.remove("livejava:" + alias.toLowerCase());
            }

            // Komut silindi, bunu oyunculara bildir (Komut artık oyunda yok mesajı için)
            syncCommands();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Minecraft 1.13+ Sürümlerinde komut listesi oyuna oyuncu ilk girerken gönderilir.
     * Sonradan bir komut eklendiğinde veya silindiğinde bu ağacı MİNECRAFT'a bildirmek zorundayız.
     * Yoksa çalışsa bile kırmızı çizgi (bilinmeyen komut) hatası verir ve TAB tamamlamaz.
     */
    private static void syncCommands() {
        // 1. ADIM: Sunucu Çekirdeği (CommandDispatcher & Brigadier) Senkronizasyonu (Sadece 1.13+)
        try {
            Method syncMethod = Bukkit.getServer().getClass().getDeclaredMethod("syncCommands");
            syncMethod.setAccessible(true);
            syncMethod.invoke(Bukkit.getServer());
        } catch (Exception ignored) {
            // Method yoksa büyük ihtimal 1.12 veya altı bir sürümdür.
            // 1.12 ve altında Brigadier (Komut Ağacı) mantığı olmadığı için sorun yaratmaz atla.
        }

        // 2. ADIM: İstemci (Client) Oyuncu Paket Senkronizasyonu
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            try {
                // 1.13 ve üstü her sürümde oyuncudaki yansımayı zorla yeniletir
                p.updateCommands();
            } catch (NoSuchMethodError ignored) {
                // 1.12 ve altı sürümlerde 'updateCommands' fonksiyonu API'de yer almaz.
                // Catch ile yakalayıp görmezden geliyoruz (Eski sürümlerde buna gerek yok zaten)
            }
        }
    }
}