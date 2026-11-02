package com.zeed.ppzeed;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class PPCommand implements CommandExecutor, TabCompleter {

    /** Marca o inventario do menu para o listener reconhecer. */
    public static class MenuHolder implements InventoryHolder {
        private Inventory inventory;

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final PPZeed plugin;
    private final ItemManager items;

    public PPCommand(PPZeed plugin, ItemManager items) {
        this.plugin = plugin;
        this.items = items;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("ppzeed.admin")) {
                plugin.send(sender, "sem-permissao");
                return true;
            }
            plugin.reloadConfig();
            plugin.send(sender, "recarregado");
            return true;
        }

        if (!(sender instanceof Player p)) {
            plugin.send(sender, "apenas-jogadores");
            return true;
        }
        if (!p.hasPermission("ppzeed.menu")) {
            plugin.send(p, "sem-permissao");
            return true;
        }
        openMenu(p);
        return true;
    }

    private void openMenu(Player p) {
        MenuHolder holder = new MenuHolder();
        String title = PPZeed.color(plugin.getConfig().getString("menu.titulo", "&8Menu PPZeed"));
        Inventory inv = Bukkit.createInventory(holder, 27, title);
        holder.setInventory(inv);

        inv.setItem(11, display(ItemType.ANTI_ELYTRA));
        inv.setItem(13, display(ItemType.TRAP_TEIA));
        inv.setItem(15, display(ItemType.CONFUSAO));
        p.openInventory(inv);
    }

    /** Item do menu: o mesmo item, com uma linha extra "Clique para pegar". */
    private ItemStack display(ItemType t) {
        ItemStack it = items.create(t);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            String extra = plugin.getConfig().getString("mensagens.menu-clique", "");
            if (!extra.isEmpty()) {
                lore.add("");
                lore.add(PPZeed.color(extra));
            }
            meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("ppzeed.admin") && "reload".startsWith(args[0].toLowerCase())) {
            out.add("reload");
        }
        return out;
    }
}
