package com.zeed.ppzeed;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class ItemManager {

    private final PPZeed plugin;
    private final NamespacedKey key;

    public ItemManager(PPZeed plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "item_ppzeed");
    }

    public ItemStack create(ItemType t) {
        FileConfiguration c = plugin.getConfig();
        String path = "itens." + t.cfg + ".";

        Material m = Material.matchMaterial(c.getString(path + "material", t.defMaterial));
        if (m == null || m.isAir() || !m.isItem()) {
            plugin.getLogger().warning("Material invalido em " + path + "material. Usando " + t.defMaterial);
            m = Material.matchMaterial(t.defMaterial);
        }

        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(PPZeed.color(c.getString(path + "nome", "&f" + t.cfg)));
            List<String> lore = new ArrayList<>();
            for (String l : c.getStringList(path + "lore")) {
                lore.add(PPZeed.color(l));
            }
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, t.id);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemType getType(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String id = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return id == null ? null : ItemType.fromId(id);
    }
}
