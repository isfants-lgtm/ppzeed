package com.zeed.ppzeed;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class PPListener implements Listener {

    private final PPZeed plugin;
    private final ItemManager items;
    private final EffectManager effects;

    public PPListener(PPZeed plugin, ItemManager items, EffectManager effects) {
        this.plugin = plugin;
        this.items = items;
        this.effects = effects;
    }

    // ------------------------------------------------ uso dos itens (bater no jogador)

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (!(e.getEntity() instanceof Player target)) return;
        if (attacker.getUniqueId().equals(target.getUniqueId())) return;

        ItemType type = items.getType(attacker.getInventory().getItemInMainHand());
        if (type == null) return;

        boolean ok = switch (type) {
            case ANTI_ELYTRA -> effects.applyAntiElytra(attacker, target);
            case TRAP_TEIA -> effects.applyTrap(attacker, target);
            case CONFUSAO -> effects.applyConfusion(attacker, target);
        };

        if (ok) consume(attacker, type);
        if (plugin.getConfig().getBoolean("cancelar-dano-dos-itens", false)) {
            e.setCancelled(true);
        }
    }

    private void consume(Player user, ItemType type) {
        if (!plugin.getConfig().getBoolean("itens." + type.cfg + ".consumir", false)) return;
        ItemStack cur = user.getInventory().getItemInMainHand();
        if (cur.getAmount() <= 1) {
            user.getInventory().setItemInMainHand(null);
        } else {
            ItemStack rest = cur.clone();
            rest.setAmount(cur.getAmount() - 1);
            user.getInventory().setItemInMainHand(rest);
        }
    }

    // Os itens so servem para bater: nao deixa usar com botao direito (ex: olho do ender)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        if (items.getType(e.getItem()) != null) {
            e.setUseItemInHand(Event.Result.DENY);
        }
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (items.getType(it) != null) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    // ------------------------------------------------ menu

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof PPCommand.MenuHolder)) return;
        e.setCancelled(true);

        if (e.getClickedInventory() == null
                || !(e.getClickedInventory().getHolder() instanceof PPCommand.MenuHolder)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        ItemType type = items.getType(e.getCurrentItem());
        if (type == null) return;

        ItemStack give = items.create(type);
        Map<Integer, ItemStack> left = p.getInventory().addItem(give);
        for (ItemStack l : left.values()) {
            p.getWorld().dropItem(p.getLocation(), l);
        }
        String name = give.getItemMeta() != null ? give.getItemMeta().getDisplayName() : type.cfg;
        plugin.send(p, "item-recebido", "item", name);
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof PPCommand.MenuHolder) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------ anti elytra

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.isGliding() && effects.isAntiElytra(p)) {
            e.setCancelled(true);
            plugin.send(p, "voar-bloqueado");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        effects.onQuit(e.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        effects.onDeath(e);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && effects.giveBack(p)) plugin.send(p, "elytra-voltou");
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // Se o servidor caiu com a elytra guardada, devolve ao entrar
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && effects.giveBack(p)) plugin.send(p, "elytra-voltou");
        });
    }

    // ------------------------------------------------ protecao da teia do trap

    private boolean protect(Block b) {
        return plugin.getConfig().getBoolean("itens.trap-teia.proteger-teia", true) && effects.isTrapBlock(b);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (protect(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(this::protect);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(this::protect);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) {
            if (protect(b)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) {
            if (protect(b)) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
