package com.zeed.ppzeed;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class PPZeed extends JavaPlugin {

    private EffectManager effects;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ItemManager items = new ItemManager(this);
        effects = new EffectManager(this);
        effects.recoverLeftoverTraps();

        getServer().getPluginManager().registerEvents(new PPListener(this, items, effects), this);

        PPCommand cmd = new PPCommand(this, items);
        PluginCommand pc = getCommand("ppzeed");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }
        getLogger().info("PPZeed ativado! Por Fantazeed.");
    }

    @Override
    public void onDisable() {
        if (effects != null) effects.shutdown();
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    /** Envia mensagem do config. Pares: chave, valor, chave, valor... */
    public void send(CommandSender to, String key, String... kv) {
        String raw = getConfig().getString("mensagens." + key);
        if (raw == null || raw.isEmpty()) return;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            raw = raw.replace("{" + kv[i] + "}", kv[i + 1]);
        }
        String prefix = getConfig().getString("mensagens.prefixo", "");
        to.sendMessage(color(prefix + raw));
    }
}
