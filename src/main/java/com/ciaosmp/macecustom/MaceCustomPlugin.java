package com.ciaosmp.macecustom;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EquipmentSlot;
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

    @Override
    public void onEnable() {
        this.maceKey = new NamespacedKey(this, "mace_type");

        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("givemace") != null) {
            getCommand("givemace").setExecutor(this);
        }
    }

    public ItemStack createMace(String type) {
        ItemStack mace = new ItemStack(Material.MACE);
        ItemMeta meta = mace.getItemMeta();
        if (meta == null) return mace;

        meta.setUnbreakable(true);

        if (type.equalsIgnoreCase("lave")) {
            meta.displayName(mm.deserialize("<bold><gradient:#FF0000:#FF7700>Masse de Lave</gradient></bold>"));
            meta.getPersistentDataContainer().set(maceKey, PersistentDataType.STRING, "lave");
            meta.addEnchant(Enchantment.DENSITY, 6, true);
            addHealthModifier(meta);
        } else if (type.equalsIgnoreCase("glace")) {
            meta.displayName(mm.deserialize("<bold><gradient:#00FFFF:#0088FF>Masse de Glace</gradient></bold>"));
            meta.getPersistentDataContainer().set(maceKey, PersistentDataType.STRING, "glace");
            meta.addEnchant(Enchantment.DENSITY, 6, true);
            addHealthModifier(meta);
        } else if (type.equalsIgnoreCase("god")) {
            meta.displayName(mm.deserialize("<bold><gradient:#FFFF00:#FFFFFF>Masse Divine</gradient></bold>"));
            meta.getPersistentDataContainer().set(maceKey, PersistentDataType.STRING, "god");
            meta.addEnchant(Enchantment.DENSITY, 7, true);
        }

        mace.setItemMeta(meta);
        return mace;
    }

    private void addHealthModifier(ItemMeta meta) {
        AttributeModifier modifier = new AttributeModifier(
                UUID.fromString("d8f31b2e-0000-4000-8000-000000000001"),
                "mace_health",
                6.0, // +6 HP = +3 cœurs
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlot.HAND
        );
        meta.addAttributeModifier(Attribute.GENERIC_MAX_HEALTH, modifier);
    }

    @EventHandler
    public void onEntityHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon.getType() != Material.MACE || !weapon.hasItemMeta()) return;

        ItemMeta meta = weapon.getItemMeta();
        String type = meta.getPersistentDataContainer().get(maceKey, PersistentDataType.STRING);
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
                if (ThreadLocalRandom.current().nextInt(100) < 30) {
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
        if (weapon.getType() != Material.MACE || !weapon.hasItemMeta()) return;

        ItemMeta meta = weapon.getItemMeta();
        String type = meta.getPersistentDataContainer().get(maceKey, PersistentDataType.STRING);

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
