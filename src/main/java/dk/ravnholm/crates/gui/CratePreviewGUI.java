
package dk.ravnholm.crates.gui;

import dk.ravnholm.crates.crates.Crate;
import dk.ravnholm.crates.rewards.Reward;
import dk.ravnholm.crates.rewards.RewardManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CratePreviewGUI implements InventoryHolder {

    private static final int INVENTORY_SIZE = 54;
    private static final int MAX_REWARDS = 45;
    private static final int CLOSE_SLOT = 49;

    private static final LegacyComponentSerializer COLORS =
            LegacyComponentSerializer.legacySection();

    private final Inventory inventory;

    // =====================================================
    // OPRET PREVIEW-MENU
    // =====================================================

    public CratePreviewGUI(
            Crate crate,
            RewardManager rewardManager
    ) {

        inventory = Bukkit.createInventory(
                this,
                INVENTORY_SIZE,
                color("§8Runernes Skatte §7- " + crate.getName())
        );

        // Opret mørk baggrund
        ItemStack background = createItem(
                Material.BLACK_STAINED_GLASS_PANE,
                " ",
                List.of()
        );

        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            inventory.setItem(slot, background);
        }

        // Indlæs belønninger
        List<Reward> rewards =
                rewardManager.getRewards(crate.getId());

        long totalWeight = 0;

        for (Reward reward : rewards) {
            totalWeight += reward.getWeight();
        }

        // =================================================
        // VIS BELØNNINGER
        // =================================================

        int displayedCount = Math.min(
                rewards.size(),
                MAX_REWARDS
        );

        int numberOfRows = (displayedCount + 8) / 9;

        // Centrér belønningerne lodret i menuen
        int startingRow = Math.max(
                0,
                (5 - numberOfRows) / 2
        );

        for (int index = 0; index < displayedCount; index++) {

            Reward reward = rewards.get(index);

            int row = index / 9;
            int positionInRow = index % 9;

            int itemsInRow = Math.min(
                    9,
                    displayedCount - row * 9
            );

            // Centrér hver række vandret
            int startingColumn = (9 - itemsInRow) / 2;

            int slot = (startingRow + row) * 9
                    + startingColumn
                    + positionInRow;

            ItemStack displayItem = reward.getItem();
            ItemMeta meta = displayItem.getItemMeta();

            if (meta == null) {
                continue;
            }

            // Brug belønningens konfigurerede navn
            meta.displayName(
                    color(reward.getName())
            );

            // Behold eventuel eksisterende lore
            List<Component> lore = new ArrayList<>();

            if (meta.lore() != null) {
                lore.addAll(meta.lore());
            }

            double chance = totalWeight > 0
                    ? reward.getWeight() * 100.0 / totalWeight
                    : 0.0;

            String chanceText = String.format(
                    Locale.US,
                    "%.2f",
                    chance
            );

            lore.add(color("§8§m------------------------"));
            lore.add(color("§7Belønning: §f" + reward.getName()));
            lore.add(color("§7Antal: §e" + displayItem.getAmount()));
            lore.add(color("§7Vinderchance: §6§l" + chanceText + "%"));
            lore.add(color("§8§m------------------------"));
            lore.add(color("§8Mulig belønning fra Runernes Dom"));

            meta.lore(lore);
            displayItem.setItemMeta(meta);

            inventory.setItem(slot, displayItem);
        }

        // =================================================
        // INGEN BELØNNINGER
        // =================================================

        if (rewards.isEmpty()) {

            inventory.setItem(
                    22,
                    createItem(
                            Material.BARRIER,
                            "§cIngen belønninger",
                            List.of(
                                    "§7Denne crate har endnu",
                                    "§7ingen registrerede belønninger."
                            )
                    )
            );
        }

        // =================================================
        // NEDERSTE MENU-RÆKKE
        // =================================================

        inventory.setItem(
                45,
                createItem(
                        Material.CYAN_STAINED_GLASS_PANE,
                        "§b✦",
                        List.of()
                )
        );

        inventory.setItem(
                46,
                createItem(
                        Material.BOOK,
                        "§b§lOm denne crate",
                        List.of(
                                "§7Navn: §f" + crate.getName(),
                                "§7Antal belønninger: §e" + rewards.size(),
                                "",
                                "§7Venstreklik på craten",
                                "§7for at se belønninger.",
                                "",
                                "§7Højreklik med en gyldig nøgle",
                                "§7for at starte Runernes Dom."
                        )
                )
        );

        inventory.setItem(
                48,
                createItem(
                        Material.COMPASS,
                        "§6§lVinderchancer",
                        List.of(
                                "§7Hver belønning har sin",
                                "§7egen chance for at blive valgt.",
                                "",
                                "§7Chancen vises på",
                                "§7hver enkelt belønning.",
                                "",
                                "§eAlle gyldige belønninger",
                                "§eindgår i lodtrækningen."
                        )
                )
        );

        // Slot 49 bruges allerede af CrateGUIListener
        inventory.setItem(
                CLOSE_SLOT,
                createItem(
                        Material.BARRIER,
                        "§c§lLuk menuen",
                        List.of(
                                "§7Klik for at lukke."
                        )
                )
        );

        inventory.setItem(
                50,
                createItem(
                        Material.NETHER_STAR,
                        "§6§l✦ RUNERNES DOM ✦",
                        List.of(
                                "§7Ravnholms gamle runer",
                                "§7vogter over disse skatte.",
                                "",
                                "§eHvilken belønning",
                                "§evil runerne vælge til dig?"
                        )
                )
        );

        if (rewards.size() > MAX_REWARDS) {

            inventory.setItem(
                    52,
                    createItem(
                            Material.PAPER,
                            "§e§lFlere belønninger",
                            List.of(
                                    "§7Viser: §f" + MAX_REWARDS
                                            + " §7af §f" + rewards.size(),
                                    "",
                                    "§7De øvrige belønninger",
                                    "§7indgår stadig i lodtrækningen.",
                                    "",
                                    "§eFlere previewsider kommer senere."
                            )
                    )
            );

        } else {

            inventory.setItem(
                    52,
                    createItem(
                            Material.AMETHYST_SHARD,
                            "§d§lRunernes Magi",
                            List.of(
                                    "§7Hver åbning er unik.",
                                    "§7Lad runerne afgøre din skæbne."
                            )
                    )
            );
        }

        inventory.setItem(
                53,
                createItem(
                        Material.CYAN_STAINED_GLASS_PANE,
                        "§b✦",
                        List.of()
                )
        );
    }

    // =====================================================
    // OPRET GUI-GENSTAND
    // =====================================================

    private ItemStack createItem(
            Material material,
            String name,
            List<String> loreLines
    ) {

        ItemStack item = new ItemStack(material);

        ItemMeta meta = item.getItemMeta();

        if (meta != null) {

            meta.displayName(color(name));

            List<Component> lore = new ArrayList<>();

            for (String line : loreLines) {
                lore.add(color(line));
            }

            meta.lore(lore);

            item.setItemMeta(meta);
        }

        return item;
    }

    // =====================================================
    // FARVEKODER
    // =====================================================

    private Component color(String text) {

        return COLORS.deserialize(
                text == null ? "" : text
        );
    }

    // =====================================================
    // ÅBN MENUEN
    // =====================================================

    public void open(Player player) {

        player.openInventory(inventory);
    }

    // =====================================================
    // INVENTORY HOLDER
    // =====================================================

    @Override
    public @NotNull Inventory getInventory() {

        return inventory;
    }
}
