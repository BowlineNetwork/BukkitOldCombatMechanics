package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

public class ModuleOldArmourDurability extends Module {

    private final Map<UUID, List<ItemStack>> explosionDamaged = new HashMap<>();

    public ModuleOldArmourDurability(OCMMain plugin) {
        super(plugin, "old-armour-durability");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemDamage(PlayerItemDamageEvent e){
        Player player = e.getPlayer();
        if(!isEnabled(player.getWorld())) return;
        final ItemStack item = e.getItem();
        final Material itemType = item.getType();

        // Check if it's a piece of armour they're currently wearing
        if(Arrays.stream(player.getInventory().getArmorContents())
                .noneMatch(armourPiece -> armourPiece != null && armourPiece.getType() == itemType)) return;

        final UUID uuid = player.getUniqueId();
        if(explosionDamaged.containsKey(uuid)){
            final List<ItemStack> armour = explosionDamaged.get(uuid);
            // ItemStack.equals() checks material, durability and quantity to make sure nothing changed in the meantime
            // We're checking all the pieces this way just in case they're wearing two helmets or something strange
            final List<ItemStack> matchedPieces = armour.stream().filter(piece -> piece.equals(item)).collect(Collectors.toList());
            armour.removeAll(matchedPieces);
            debug("Item matched explosion, ignoring...", player);
            if(!matchedPieces.isEmpty()) return;
        }

        final int reduction = module().getInt("reduction");
        debug("Item damaged: " + itemType + " Damage: " + e.getDamage() + " Changed to: " + reduction, player);
        e.setDamage(reduction);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerExplosionDamage(EntityDamageEvent e){
        if(e.isCancelled()) return;
        if(e.getEntityType() != EntityType.PLAYER) return;
        final EntityDamageEvent.DamageCause cause = e.getCause();
        if(cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION &&
                cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) return;

        final Player player = (Player) e.getEntity();
        final UUID uuid = player.getUniqueId();
        final List<ItemStack> armour = Arrays.stream(player.getInventory().getArmorContents()).filter(Objects::nonNull).collect(Collectors.toList());
        explosionDamaged.put(uuid, armour);

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                explosionDamaged.remove(uuid);
                debug("Removed from explosion set!", player);
            }
        };

        // This delay seems enough for the durability events to fire
        runnable.runTaskLaterAsynchronously(plugin, 1);
        debug("Detected explosion!", player);
    }
}