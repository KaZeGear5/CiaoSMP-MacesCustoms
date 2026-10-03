package com.ciaosmp.macecustom;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent; // <-- Correction de l'import ici
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class MaceCustomPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private NamespacedKey maceKey;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private static final UUID INV_HEALTH_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-1234-56789abcdef0");

    @Override
    public void onEnable() {
        this.maceKey = new NamespacedKey(this, "mace_type");

        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("givemace") != null) {
            getCommand("givemace").setExecutor(this);
        }

        // Tâche répétée toutes les 0.5s pour vérifier la présence des masses dans l'inventaire
        Bukkit.getScheduler().runTaskTimer(this, this::checkInventoriesForHealthBoost, 20L, 10L);
    }

    public ItemStack createMace(String type) {
        ItemStack mace = new ItemStack(Material.MACE);
        ItemMeta meta = mace.getItemMeta();
        if (meta == null) return mace;

        meta.setUnbreakable(true);

        meta.addEnchant(Enchantment.DENSITY, 3, true);
        meta.addEnchant(Enchantment.BREACH, 2, true);

        if (type.equalsIgnoreCase("lave")) {
            meta.displayName(mm.deserialize("<bold><gradient:#FF0000:#FF7700>Masse de Lave</gradient></bold>"));
            meta.getPersistentDataContainer().set(maceKey, PersistentDataType.STRING, "lave");
            meta.setCustomModelData(1001);
        } else if (type.equalsIgnoreCase("glace")) {
            meta.displayName(mm.deserialize("<bold><gradient:#00FFFF:#0088FF>Masse de Glace</gradient></bold>"));
            meta.getPersistentDataContainer().set(maceKey, PersistentDataType.STRING, "glace");
            meta.setCustomModelData(1002);
        } else if (type.equalsIgnoreCase("god")) {
            meta.displayName(mm.deserialize("<bold><gradient:#FFFF00:#FFFFFF>Masse Divine</gradient></bold>"));
            meta.getPersistentDataContainer().set(maceKey, PersistentDataType.STRING, "god");
            meta.setCustomModelData(1003);
        }

        mace.setItemMeta(meta);
        return mace;
    }

    private boolean isCustomMace(ItemStack item) {
        if (item == null || item.getType() != Material.MACE || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(maceKey, PersistentDataType.STRING);
    }

    private String getMaceType(ItemStack item) {
        if (!isCustomMace(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(maceKey, PersistentDataType.STRING);
    }

    private void checkInventoriesForHealthBoost() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean hasMace = false;

            for (ItemStack item : p.getInventory().getContents()) {
                String maceType = getMaceType(item);
                if ("lave".equals(maceType) || "glace".equals(maceType)) {
                    hasMace = true;
                    break;
                }
            }

            AttributeInstance attr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (attr == null) continue;

            AttributeModifier existingMod = null;
            for (AttributeModifier mod : attr.getModifiers()) {
                if (mod.getUniqueId().equals(INV_HEALTH_UUID)) {
                    existingMod = mod;
                    break;
                }
            }

            if (hasMace) {
                if (existingMod == null) {
                    AttributeModifier newMod = new AttributeModifier(
                            INV_HEALTH_UUID,
                            "mace_inv_health",
                            6.0, // +6 HP = +3 cœurs
                            AttributeModifier.Operation.ADD_NUMBER
                    );
                    attr.addModifier(newMod);
                }
            } else {
                if (existingMod != null) {
                    attr.removeModifier(existingMod);
                    if (p.getHealth() > attr.getValue()) {
                        p.setHealth(attr.getValue());
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        String type = getMaceType(weapon);
        if (type == null) return;

        Entity target = event.getEntity();

        switch (type) {
            case "lave" -> {
                target.setFireTicks(100);
                target.getLocation().getBlock().setType(Material.LAVA);
            }
            case "glace" -> {
                target.setFreezeTicks(300);
                target.getLocation().getBlock().setType(Material.POWDER_SNOW);
            }
            case "god" -> {
                if (target instanceof LivingEntity living) {
                    living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 255, false, false));
                    living.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 20, 200, false, false));
                }
                if (ThreadLocalRandom.current().nextInt(100) < 10) {
                    target.getWorld().strikeLightning(target.getLocation());
                }
            }
        }
    }

    @EventHandler
    public void onPlayerKill(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player killer = victim.getKiller();
        if (killer == null) return;

        ItemStack weapon = killer.getInventory().getItemInMainHand();
        String type = getMaceType(weapon);

        if ("god".equals(type)) {
            Date expires = Date.from(Instant.now().plus(15, ChronoUnit.MINUTES));
            Bukkit.getBanList(BanList.Type.NAME).addBan(
                    victim.getName(),
                    "Tu as été éliminé par la Masse Divine ! (Ban 15 min)",
                    expires,
                    "GodMace"
            );
            victim.kick(mm.deserialize("<red>Tu as été tué par la Masse Divine ! Banni 15 minutes.</red>"));
        }
    }

    @EventHandler
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        if (isCustomMace(inv.getItem(0)) || isCustomMace(inv.getItem(1))) {
            event.setResult(null);
        }
    }

    @EventHandler
    public void onEnchantPrepare(PrepareItemEnchantEvent event) {
        if (isCustomMace(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSmithingPrepare(PrepareSmithingEvent event) {
        if (isCustomMace(event.getInventory().getItem(0)) || isCustomMace(event.getInventory().getItem(1))) {
            event.setResult(null);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;

        InventoryType type = event.getInventory().getType();
        if (type == InventoryType.GRINDSTONE) {
            GrindstoneInventory grindstone = (GrindstoneInventory) event.getInventory();
            if (isCustomMace(grindstone.getItem(0)) || isCustomMace(grindstone.getItem(1))) {
                if (event.getSlot() == 2) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /givemace <lave|glace|god> [joueur]");
            return true;
        }

        Player target = (args.length >= 2) ? Bukkit.getPlayer(args[1]) : (sender instanceof Player p ? p : null);

        if (target == null) {
            sender.sendMessage("§cJoueur introuvable.");
            return true;
        }

        String type = args[0].toLowerCase();
        if (!type.equals("lave") && !type.equals("glace") && !type.equals("god")) {
            sender.sendMessage("§cType invalide ! Choisis : lave, glace ou god.");
            return true;
        }

        target.getInventory().addItem(createMace(type));
        sender.sendMessage("§aMasse " + type + " donnée à " + target.getName());
        return true;
    }
}
