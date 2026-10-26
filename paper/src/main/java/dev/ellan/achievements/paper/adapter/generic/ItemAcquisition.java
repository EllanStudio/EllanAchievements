package dev.ellan.achievements.paper.adapter.generic;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.function.Consumer;

/**
 * "A player just got this item": picking it up, or taking it out of any non-player inventory
 * (containers, crafting and furnace result slots — CraftItemEvent is an InventoryClickEvent).
 * Shared by the generic and brewing adapters.
 */
public final class ItemAcquisition {
    /** {@code replace} writes a modified stack back where the item currently is (no-op where impossible). */
    @FunctionalInterface
    public interface Handler {
        void acquired(Player player, ItemStack stack, Consumer<ItemStack> replace);
    }

    private static final Set<InventoryAction> TAKING = Set.of(InventoryAction.PICKUP_ALL, InventoryAction.PICKUP_HALF,
        InventoryAction.PICKUP_ONE, InventoryAction.PICKUP_SOME, InventoryAction.MOVE_TO_OTHER_INVENTORY,
        InventoryAction.HOTBAR_SWAP, InventoryAction.SWAP_WITH_CURSOR);

    private ItemAcquisition() {
    }

    public static void listen(Plugin plugin, Handler handler) {
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onPickup(EntityPickupItemEvent event) {
                if (event.getEntity() instanceof Player player && present(event.getItem().getItemStack())) {
                    handler.acquired(player, event.getItem().getItemStack(), event.getItem()::setItemStack);
                }
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onClick(InventoryClickEvent event) {
                Inventory clicked = event.getClickedInventory();
                if (!(event.getWhoClicked() instanceof Player player) || clicked == null
                    || clicked.getType() == InventoryType.PLAYER || !TAKING.contains(event.getAction())
                    || !present(event.getCurrentItem())) {
                    return;
                }
                handler.acquired(player, event.getCurrentItem(), event::setCurrentItem);
            }
        }, plugin);
    }

    private static boolean present(ItemStack stack) {
        return stack != null && !stack.getType().isAir() && stack.getAmount() > 0;
    }
}
