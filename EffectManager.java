package com.zeed.ppzeed;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class EffectManager {

    private record BlockKey(UUID world, int x, int y, int z) {}

    private record Saved(int x, int y, int z, BlockData original) {}

    private static class Trap {
        World world;
        List<Saved> blocks = new ArrayList<>();
        BukkitTask task;
        File file;
    }

    private final PPZeed plugin;
    private final File elytraDir;
    private final File trapDir;
    private final Map<UUID, BukkitTask> elytraTasks = new HashMap<>();
    private final Map<UUID, Trap> traps = new HashMap<>();
    private final Set<BlockKey> trapBlocks = new HashSet<>();

    public EffectManager(PPZeed plugin) {
        this.plugin = plugin;
        this.elytraDir = new File(plugin.getDataFolder(), "elytras-guardadas");
        this.trapDir = new File(plugin.getDataFolder(), "teias-ativas");
        elytraDir.mkdirs();
        trapDir.mkdirs();
    }

    // =====================================================================
    // ANTI ELYTRA
    // =====================================================================

    public boolean isAntiElytra(Player p) {
        return elytraTasks.containsKey(p.getUniqueId());
    }

    public boolean applyAntiElytra(Player attacker, Player target) {
        UUID id = target.getUniqueId();
        if (elytraTasks.containsKey(id)) {
            plugin.send(attacker, "anti-elytra-ja-ativo", "jogador", target.getName());
            return false;
        }

        int sec = Math.max(1, plugin.getConfig().getInt("itens.anti-elytra.duracao-segundos", 15));

        // Tira a elytra do peito (se estiver usando) e guarda em disco, para nunca ser perdida
        PlayerInventory inv = target.getInventory();
        ItemStack chest = inv.getChestplate();
        if (chest != null && chest.getType() == Material.ELYTRA) {
            if (saveStored(id, chest.clone())) {
                inv.setChestplate(null);
            }
        }
        target.setGliding(false);

        elytraTasks.put(id, Bukkit.getScheduler().runTaskLater(plugin, () -> endAntiElytra(id), sec * 20L));

        plugin.send(attacker, "anti-elytra-aplicado", "jogador", target.getName(), "segundos", String.valueOf(sec));
        plugin.send(target, "anti-elytra-alvo", "segundos", String.valueOf(sec));
        return true;
    }

    private void endAntiElytra(UUID id) {
        BukkitTask t = elytraTasks.remove(id);
        if (t != null) t.cancel();

        Player p = Bukkit.getPlayer(id);
        // Offline ou morto: a elytra fica guardada e volta no join/respawn
        if (p == null || !p.isOnline() || p.isDead()) return;

        if (giveBack(p)) {
            plugin.send(p, "elytra-voltou");
        } else {
            plugin.send(p, "anti-elytra-acabou");
        }
    }

    private boolean saveStored(UUID id, ItemStack item) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("item", item);
        try {
            y.save(new File(elytraDir, id + ".yml"));
            return true;
        } catch (IOException ex) {
            plugin.getLogger().warning("Nao foi possivel guardar a elytra de " + id + ": " + ex.getMessage());
            return false;
        }
    }

    /** Devolve a elytra guardada (se houver). Retorna true se devolveu. */
    public boolean giveBack(Player p) {
        File f = new File(elytraDir, p.getUniqueId() + ".yml");
        if (!f.exists()) return false;

        ItemStack item = YamlConfiguration.loadConfiguration(f).getItemStack("item");
        if (item != null) {
            PlayerInventory inv = p.getInventory();
            ItemStack chest = inv.getChestplate();
            if (chest == null || chest.getType().isAir()) {
                inv.setChestplate(item);
            } else {
                Map<Integer, ItemStack> left = inv.addItem(item);
                for (ItemStack l : left.values()) {
                    p.getWorld().dropItem(p.getLocation(), l);
                }
            }
        }
        f.delete();
        return true;
    }

    public void onQuit(Player p) {
        BukkitTask t = elytraTasks.remove(p.getUniqueId());
        if (t != null) t.cancel();
        giveBack(p);
    }

    public void onDeath(PlayerDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        BukkitTask t = elytraTasks.remove(id);
        if (t != null) t.cancel();

        File f = new File(elytraDir, id + ".yml");
        if (!f.exists()) return;
        if (!e.getKeepInventory()) {
            ItemStack item = YamlConfiguration.loadConfiguration(f).getItemStack("item");
            if (item != null) e.getDrops().add(item);
            f.delete();
        }
        // Com keepInventory a elytra volta no respawn (PPListener)
    }

    // =====================================================================
    // TRAP DE TEIA
    // =====================================================================

    public boolean isTrapBlock(Block b) {
        if (trapBlocks.isEmpty()) return false;
        return trapBlocks.contains(new BlockKey(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ()));
    }

    private boolean canWeb(Block b) {
        Material t = b.getType();
        if (t == Material.NETHER_PORTAL || t == Material.END_PORTAL || t == Material.END_GATEWAY) return false;
        if (t.isAir()) return true;
        if (b.isLiquid() || t == Material.COBWEB) return false;
        // Blocos sem colisao (grama, flores...) sao trocados e depois restaurados; placas/banners nunca
        return b.isPassable() && !(b.getState() instanceof TileState);
    }

    public boolean applyTrap(Player attacker, Player target) {
        UUID id = target.getUniqueId();
        if (traps.containsKey(id)) {
            plugin.send(attacker, "teia-ja-ativa", "jogador", target.getName());
            return false;
        }

        FileConfiguration c = plugin.getConfig();
        int r = Math.max(1, Math.min(3, c.getInt("itens.trap-teia.raio", 1)));
        int sec = Math.max(1, c.getInt("itens.trap-teia.duracao-segundos", 15));

        World w = target.getWorld();
        Location loc = target.getLocation();
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        if (by < w.getMinHeight() || by >= w.getMaxHeight()) return false;

        BlockData web = Material.COBWEB.createBlockData();
        Trap trap = new Trap();
        trap.world = w;

        // Camada unica de teia no nivel dos pes do alvo
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int x = bx + dx;
                int z = bz + dz;
                if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                Block b = w.getBlockAt(x, by, z);
                if (!canWeb(b)) continue;
                trap.blocks.add(new Saved(x, by, z, b.getBlockData()));
                b.setBlockData(web, false);
                trapBlocks.add(new BlockKey(w.getUID(), x, by, z));
            }
        }

        if (trap.blocks.isEmpty()) {
            plugin.send(attacker, "teia-sem-espaco");
            return false;
        }

        trap.file = new File(trapDir, id + ".yml");
        saveTrap(trap);
        trap.task = Bukkit.getScheduler().runTaskLater(plugin, () -> endTrap(id, true), sec * 20L);
        traps.put(id, trap);

        plugin.send(attacker, "teia-aplicada", "jogador", target.getName(), "segundos", String.valueOf(sec));
        plugin.send(target, "teia-alvo", "segundos", String.valueOf(sec));
        return true;
    }

    private void endTrap(UUID id, boolean notify) {
        Trap trap = traps.remove(id);
        if (trap == null) return;
        if (trap.task != null) trap.task.cancel();

        for (Saved s : trap.blocks) {
            trapBlocks.remove(new BlockKey(trap.world.getUID(), s.x(), s.y(), s.z()));
            Block b = trap.world.getBlockAt(s.x(), s.y(), s.z());
            // So desfaz se ainda for a teia do trap (nunca mexe em outro bloco)
            if (b.getType() == Material.COBWEB) {
                b.setBlockData(s.original(), false);
            }
        }
        if (trap.file != null && trap.file.exists()) trap.file.delete();

        if (notify) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) plugin.send(p, "teia-sumiu");
        }
    }

    private void saveTrap(Trap trap) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("world", trap.world.getName());
        List<String> lines = new ArrayList<>();
        for (Saved s : trap.blocks) {
            lines.add(s.x() + "|" + s.y() + "|" + s.z() + "|" + s.original().getAsString());
        }
        y.set("blocos", lines);
        try {
            y.save(trap.file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Nao foi possivel salvar a teia em disco: " + ex.getMessage());
        }
    }

    /** Remove teias que ficaram no mapa se o servidor caiu com um trap ativo. */
    public void recoverLeftoverTraps() {
        File[] files = trapDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        int total = 0;
        for (File f : files) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            World w = Bukkit.getWorld(y.getString("world", ""));
            if (w == null) continue;
            for (String line : y.getStringList("blocos")) {
                String[] p = line.split("\\|", 4);
                if (p.length < 4) continue;
                try {
                    Block b = w.getBlockAt(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                    if (b.getType() == Material.COBWEB) {
                        b.setBlockData(Bukkit.createBlockData(p[3]), false);
                        total++;
                    }
                } catch (Exception ignored) {
                    // linha invalida
                }
            }
            f.delete();
        }
        if (total > 0) plugin.getLogger().info("Removidas " + total + " teias de traps antigos.");
    }

    // =====================================================================
    // CONFUSAO
    // =====================================================================

    public boolean applyConfusion(Player attacker, Player target) {
        PlayerInventory inv = target.getInventory();
        int held = inv.getHeldItemSlot();
        boolean hotbar = plugin.getConfig().getBoolean("itens.confusao.incluir-hotbar", true);

        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            if (i == held) continue;
            if (!hotbar && i < 9) continue;
            ItemStack s = inv.getItem(i);
            if (s != null && !s.getType().isAir()) candidates.add(i);
        }
        if (candidates.isEmpty()) {
            plugin.send(attacker, "confusao-sem-itens", "jogador", target.getName());
            return false;
        }

        int pick = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        ItemStack heldItem = inv.getItem(held);
        ItemStack other = inv.getItem(pick);
        inv.setItem(held, other);
        inv.setItem(pick, heldItem);

        plugin.send(attacker, "confusao-aplicada", "jogador", target.getName());
        plugin.send(target, "confusao-alvo");
        return true;
    }

    // =====================================================================

    /** Desligando: devolve elytras e remove todas as teias. */
    public void shutdown() {
        for (UUID id : new ArrayList<>(elytraTasks.keySet())) {
            BukkitTask t = elytraTasks.remove(id);
            if (t != null) t.cancel();
            Player p = Bukkit.getPlayer(id);
            if (p != null) giveBack(p);
        }
        for (UUID id : new ArrayList<>(traps.keySet())) {
            endTrap(id, false);
        }
    }
}
