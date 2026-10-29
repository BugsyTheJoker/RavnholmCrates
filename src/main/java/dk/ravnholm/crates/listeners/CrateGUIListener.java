
package dk.ravnholm.crates.listeners;

import dk.ravnholm.crates.RavnholmCrates;
import dk.ravnholm.crates.gui.CrateOpeningGUI;
import dk.ravnholm.crates.gui.CratePreviewGUI;

import org.bukkit.Bukkit;
import org.bukkit.block.Chest;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;

import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class CrateGUIListener implements Listener {

    // =====================================================
    // HENT PLUGINET
    // =====================================================

    private RavnholmCrates getPlugin() {

        return RavnholmCrates.getPlugin(
                RavnholmCrates.class
        );
    }

    // =====================================================
    // KONTROLLÉR OM INVENTORY TILHØR EN FYSISK CRATE
    // =====================================================

    private boolean isCrateInventory(Inventory inventory) {

        if (inventory == null) {
            return false;
        }

        // Dobbelte kister har to separate halvdele.
        // Begge skal kontrolleres.

        if (inventory instanceof DoubleChestInventory doubleChest) {

            return isCrateInventory(doubleChest.getLeftSide())
                    || isCrateInventory(doubleChest.getRightSide());
        }

        InventoryHolder holder = inventory.getHolder();

        // Kontrollér om inventory tilhører en fysisk kiste.

        if (holder instanceof Chest chest) {

            return getPlugin()
                    .getCrateManager()
                    .isRegisteredCrateLocation(
                            chest.getLocation()
                    );
        }

        return false;
    }

    // =====================================================
    // NYT I TRIN 90:
    // KONTROLLÉR ALLE BESKYTTEDE INVENTORIES
    // =====================================================

    private boolean isProtectedInventory(Inventory inventory) {

        if (inventory == null) {
            return false;
        }

        InventoryHolder holder = inventory.getHolder();

        // Vores preview-menu er beskyttet.
        if (holder instanceof CratePreviewGUI) {
            return true;
        }

        // Vores animationsmenu er beskyttet.
        if (holder instanceof CrateOpeningGUI) {
            return true;
        }

        // Fysiske crate-kister er også beskyttet.
        return isCrateInventory(inventory);
    }

    // =====================================================
    // FORHINDR AUTOMATISKE INVENTORY-OVERFØRSLER
    // =====================================================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryMove(InventoryMoveItemEvent event) {

        // =================================================
        // BESKYT KILDEN
        // =================================================

        // Forhindr automatiske systemer i at
        // trække genstande UD af et beskyttet inventory.

        if (isProtectedInventory(event.getSource())) {

            event.setCancelled(true);
            return;
        }

        // =================================================
        // BESKYT DESTINATIONEN
        // =================================================

        // Forhindr automatiske systemer i at
        // flytte genstande IND i et beskyttet inventory.

        if (isProtectedInventory(event.getDestination())) {

            event.setCancelled(true);
            return;
        }

        // =================================================
        // BESKYT INITIATOREN
        // =================================================

        // Ekstra kontrol, hvis et beskyttet inventory
        // selv forsøger at starte en overførsel.

        if (isProtectedInventory(event.getInitiator())) {

            event.setCancelled(true);
        }
    }

    // =====================================================
    // NYT: BESKYT MOD INVENTORY-PICKUP
    // =====================================================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryPickup(InventoryPickupItemEvent event) {

        // Bloker hvis et beskyttet inventory
        // forsøger at samle en genstand op.

        if (isProtectedInventory(event.getInventory())) {

            event.setCancelled(true);
        }
    }

    // =====================================================
    // FORHINDR NORMAL ÅBNING AF CRATE-INVENTORY
    // =====================================================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {

        // Gælder kun de fysiske crate-kister.
        // Vores egne GUI'er kan stadig åbnes.

        if (isCrateInventory(event.getInventory())) {

            event.setCancelled(true);
        }
    }

    // =====================================================
    // BESKYT INVENTORY MOD KLIK
    // =====================================================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {

        Inventory topInventory =
                event.getView().getTopInventory();

        InventoryHolder holder =
                topInventory.getHolder();

        // =================================================
        // CRATE PREVIEW
        // =================================================

        if (holder instanceof CratePreviewGUI) {

            // Bloker alle klik i både den øverste
            // GUI og spillerens nederste inventory.
            //
            // Det beskytter bl.a. mod:
            // - Almindelige klik
            // - Shift-klik
            // - Nummertaster / hotbar-swap
            // - Offhand-swap
            // - Double-click collect
            // - Drop-klik
            // - Creative inventory-handlinger

            event.setCancelled(true);

            // Slot 49 = Luk menu.
            if (event.getRawSlot() == 49) {

                // Luk menuen på næste tick.
                // Inventory skal ikke lukkes direkte
                // inde i InventoryClickEvent.

                Bukkit.getScheduler().runTask(
                        getPlugin(),
                        () -> event.getWhoClicked().closeInventory()
                );
            }

            return;
        }

        // =================================================
        // RUNERNES DOM - ÅBNINGSANIMATION
        // =================================================

        if (holder instanceof CrateOpeningGUI) {

            // Spilleren må ikke tage belønningen
            // direkte fra animationsmenuen.
            //
            // Vi blokerer alle klik, uanset slot
            // og kliktype.

            event.setCancelled(true);

            return;
        }

        // =================================================
        // FYSISK CRATE-INVENTORY
        // =================================================

        if (isCrateInventory(topInventory)) {

            // Ekstra beskyttelse, hvis et andet plugin
            // har åbnet kistens inventory.

            event.setCancelled(true);
        }
    }

    // =====================================================
    // BESKYT MOD DRAG-AND-DROP
    // =====================================================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {

        Inventory topInventory =
                event.getView().getTopInventory();

        // =================================================
        // BESKYT ALLE CRATE-INVENTORIES
        // =================================================

        // Vi afviser hele drag-handlingen.
        //
        // Dermed kan spilleren ikke trække
        // genstande ind i animationsmenuen
        // eller forsøge at hente belønninger ud.

        if (isProtectedInventory(topInventory)) {

            event.setCancelled(true);
        }
    }
}
