
package dk.ravnholm.crates.gui;

import dk.ravnholm.crates.RavnholmCrates;
import dk.ravnholm.crates.crates.Crate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CrateOpeningGUI implements InventoryHolder {

    private static final LegacyComponentSerializer COLORS =
            LegacyComponentSerializer.legacySection();

    private final Inventory inventory;

    // =====================================================
    // GUI-PLACERINGER
    // =====================================================

    public static final int REWARD_SLOT = 13;

    private static final int[] RUNE_SLOTS = {
            3, 4, 5,
            12, 14,
            21, 22, 23
    };

    // 18 frames med 4 ticks mellem hver.
    // Animationen afsluttes før reveal ved tick 72.

    private static final int TOTAL_FRAMES = 18;
    private static final long FRAME_INTERVAL = 4L;

    // =====================================================
    // ANIMATIONSSTATUS
    // =====================================================

    private boolean revealed = false;
    private boolean animationStarted = false;
    private boolean animationStopped = false;

    // NYT: Gem opgaven, så den kan stoppes direkte.

    private BukkitTask animationTask;

    // =====================================================
    // KONSTRUKTØR
    // =====================================================

    public CrateOpeningGUI(Crate crate) {

        inventory = Bukkit.createInventory(
                this,
                27,
                color("§8Runernes Dom §7- " + crate.getName())
        );

        createBackground();
        createDormantRunes();
        createWaitingReward();
    }

    // =====================================================
    // BAGGRUND
    // =====================================================

    private void createBackground() {

        ItemStack background = createItem(
                Material.BLACK_STAINED_GLASS_PANE,
                " "
        );

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, background);
        }

        // Lysende hjørner.

        ItemStack corner = createItem(
                Material.CYAN_STAINED_GLASS_PANE,
                "§b✦ §7Ravnholms Runer §b✦"
        );

        inventory.setItem(0, corner);
        inventory.setItem(8, corner);
        inventory.setItem(18, corner);
        inventory.setItem(26, corner);
    }

    // =====================================================
    // SLUKKEDE RUNER
    // =====================================================

    private void createDormantRunes() {

        for (int i = 0; i < RUNE_SLOTS.length; i++) {
            setRuneActive(i, false);
        }
    }

    // =====================================================
    // MIDTERSTE GENSTAND
    // =====================================================

    private void createWaitingReward() {

        inventory.setItem(
                REWARD_SLOT,
                createItem(
                        Material.NETHER_STAR,
                        "§6§l✦ RUNERNES DOM ✦",
                        "§7Runerne er endnu ikke vækket.",
                        "",
                        "§8Afventer skæbnens afgørelse..."
                )
        );
    }

    // =====================================================
    // TÆND ELLER SLUK EN RUNE
    // =====================================================

    public void setRuneActive(int runeIndex, boolean active) {

        if (revealed || animationStopped) {
            return;
        }

        if (runeIndex < 0 || runeIndex >= RUNE_SLOTS.length) {
            return;
        }

        Material material = active
                ? Material.CYAN_STAINED_GLASS_PANE
                : Material.GRAY_STAINED_GLASS_PANE;

        String name = active
                ? "§b§l✦ Rune " + (runeIndex + 1) + " §aAktiveret"
                : "§8Rune " + (runeIndex + 1) + " §7Slukket";

        inventory.setItem(
                RUNE_SLOTS[runeIndex],
                createItem(material, name)
        );
    }

    // =====================================================
    // KONTROLLÉR OM SPILLEREN SER DENNE GUI
    // =====================================================

    private boolean isOpenFor(Player player) {

        return player != null
                && player.isOnline()
                && player.getOpenInventory().getTopInventory()
                == inventory;
    }

    // =====================================================
    // START ANIMATION
    // =====================================================

    public void startRuneAnimation(
            RavnholmCrates plugin,
            Player player
    ) {

        // Animationen må kun startes én gang.
        // En stoppet animation må ikke genstartes.

        if (animationStarted
                || animationStopped
                || revealed) {

            return;
        }

        // Start kun, hvis den rigtige GUI er åben.

        if (!isOpenFor(player)) {
            return;
        }

        animationStarted = true;

        try {

            animationTask = Bukkit.getScheduler()
                    .runTaskTimer(
                            plugin,
                            new Runnable() {

                                private int frame = 0;

                                @Override
                                public void run() {

                                    // =====================================
                                    // KONTROLLÉR ANIMATIONSSTATUS
                                    // =====================================

                                    if (animationStopped || revealed) {

                                        stopAnimation();
                                        return;
                                    }

                                    // =====================================
                                    // KONTROLLÉR SPILLER OG MENU
                                    // =====================================

                                    if (!isOpenFor(player)) {

                                        stopAnimation();
                                        return;
                                    }

                                    // =====================================
                                    // KONTROLLÉR ANTAL FRAMES
                                    // =====================================

                                    if (frame >= TOTAL_FRAMES) {

                                        stopAnimation();
                                        return;
                                    }

                                    // =====================================
                                    // AFSPIL NÆSTE FRAME
                                    // =====================================

                                    playAnimationFrame(player, frame);

                                    frame++;

                                    // =====================================
                                    // STOP EFTER SIDSTE FRAME
                                    // =====================================

                                    if (frame >= TOTAL_FRAMES) {

                                        stopAnimation();
                                    }
                                }
                            },
                            0L,
                            FRAME_INTERVAL
                    );

        } catch (RuntimeException exception) {

            // Sørg for, at GUI'en ikke efterlades
            // i en uafklaret animationsstatus.

            stopAnimation();

            throw exception;
        }
    }

    // =====================================================
    // NYT: STOP ANIMATION SIKKERT
    // =====================================================

    public void stopAnimation() {

        // Markér animationen som permanent afsluttet.

        animationStopped = true;

        // Stop den planlagte opgave, hvis den findes.

        if (animationTask != null) {

            if (!animationTask.isCancelled()) {

                animationTask.cancel();
            }

            animationTask = null;
        }
    }

    // =====================================================
    // NYT: KONTROLLÉR OM ANIMATION ER STOPPET
    // =====================================================

    public boolean isAnimationStopped() {

        return animationStopped;
    }

    // =====================================================
    // ANIMATIONS-FRAMES
    // =====================================================

    private void playAnimationFrame(
            Player player,
            int frame
    ) {

        // =================================================
        // FASE 1: RUNERNE VÆKKES
        // Frames 0-7
        // =================================================

        if (frame < 8) {

            setRuneActive(frame, true);

            inventory.setItem(
                    REWARD_SLOT,
                    createItem(
                            Material.AMETHYST_SHARD,
                            "§b§lRunerne vækkes...",
                            "§7De gamle runer begynder",
                            "§7at gløde med magisk energi.",
                            "",
                            "§bAktiverede runer: §f"
                                    + (frame + 1) + "§7/§f8"
                    )
            );

            player.playSound(
                    player.getLocation(),
                    "minecraft:block.amethyst_block.chime",
                    0.8f,
                    0.65f + frame * 0.10f
            );

            if (frame == 0 || frame == 4) {

                player.spawnParticle(
                        Particle.END_ROD,
                        player.getLocation().add(0, 1, 0),
                        8,
                        0.3,
                        0.4,
                        0.3,
                        0.02
                );
            }

            return;
        }

        // =================================================
        // FASE 2: RUNERNES DOM
        // Frames 8-13
        // =================================================

        if (frame < 14) {

            int pulse = frame - 8;

            // Lyset vandrer rundt mellem runerne.

            int activeRune = pulse % RUNE_SLOTS.length;

            for (int i = 0; i < RUNE_SLOTS.length; i++) {

                boolean active =
                        i == activeRune
                                || i == (activeRune + 1)
                                % RUNE_SLOTS.length;

                setRuneActive(i, active);
            }

            inventory.setItem(
                    REWARD_SLOT,
                    createItem(
                            Material.NETHER_STAR,
                            "§d§l✦ RUNERNES DOM ✦",
                            "§7Runerne søger din skæbne...",
                            "",
                            "§dHvilken skat bliver din?"
                    )
            );

            player.playSound(
                    player.getLocation(),
                    "minecraft:block.note_block.pling",
                    0.7f,
                    0.9f + pulse * 0.12f
            );

            if (pulse % 2 == 0) {

                player.spawnParticle(
                        Particle.END_ROD,
                        player.getLocation().add(0, 1.2, 0),
                        6,
                        0.35,
                        0.35,
                        0.35,
                        0.02
                );
            }

            return;
        }

        // =================================================
        // FASE 3: SKÆBNEN AFGØRES
        // Frames 14-17
        // =================================================

        int finalFrame = frame - 14;

        Material runeMaterial =
                finalFrame % 2 == 0
                        ? Material.YELLOW_STAINED_GLASS_PANE
                        : Material.CYAN_STAINED_GLASS_PANE;

        for (int i = 0; i < RUNE_SLOTS.length; i++) {

            inventory.setItem(
                    RUNE_SLOTS[i],
                    createItem(
                            runeMaterial,
                            "§6§l✦ Skæbnens Rune ✦"
                    )
            );
        }

        String[] messages = {
                "§e§lRunerne samler deres kraft...",
                "§6§lSkæbnen nærmer sig...",
                "§e§lRunernes dom er næsten klar...",
                "§6§l✦ RUNERNE HAR VALGT ✦"
        };

        inventory.setItem(
                REWARD_SLOT,
                createItem(
                        Material.NETHER_STAR,
                        messages[finalFrame],
                        "§7Din belønning afsløres om lidt..."
                )
        );

        player.playSound(
                player.getLocation(),
                "minecraft:block.beacon.activate",
                0.5f,
                0.7f + finalFrame * 0.15f
        );

        if (finalFrame == 3) {

            player.spawnParticle(
                    Particle.END_ROD,
                    player.getLocation().add(0, 1, 0),
                    20,
                    0.5,
                    0.6,
                    0.5,
                    0.05
            );
        }
    }

    // =====================================================
    // AFSLØR BELØNNING
    // =====================================================

    public void revealReward(
            Player player,
            ItemStack reward
    ) {

        if (revealed
                || reward == null
                || reward.getType().isAir()) {

            return;
        }

        // NYT: Vis ikke belønningen i en GUI,
        // som spilleren allerede har lukket.

        if (!isOpenFor(player)) {

            stopAnimation();
            return;
        }

        revealed = true;

        // NYT: Stop animationen med det samme.
        // Det forhindrer, at efterfølgende frames
        // overskriver den viste belønning.

        stopAnimation();

        // =================================================
        // ALLE RUNER BLIVER GYLDNE
        // =================================================

        for (int i = 0; i < RUNE_SLOTS.length; i++) {

            inventory.setItem(
                    RUNE_SLOTS[i],
                    createItem(
                            Material.YELLOW_STAINED_GLASS_PANE,
                            "§6§l✦ Rune " + (i + 1) + " Fuldendt ✦",
                            "§eRunernes dom er afgjort!"
                    )
            );
        }

        // =================================================
        // VIS BELØNNINGEN
        // =================================================

        setDisplayedReward(reward);

        // =================================================
        // AFSLØRINGSLYDE
        // =================================================

        player.playSound(
                player.getLocation(),
                "minecraft:entity.player.levelup",
                1.0f,
                0.9f
        );

        player.playSound(
                player.getLocation(),
                "minecraft:block.beacon.activate",
                0.9f,
                1.2f
        );

        player.playSound(
                player.getLocation(),
                "minecraft:block.amethyst_block.chime",
                1.0f,
                1.5f
        );

        // =================================================
        // MAGISKE PARTIKLER
        // =================================================

        player.spawnParticle(
                Particle.END_ROD,
                player.getLocation().add(0, 1, 0),
                45,
                0.6,
                0.8,
                0.6,
                0.08
        );

        player.spawnParticle(
                Particle.TOTEM_OF_UNDYING,
                player.getLocation().add(0, 1, 0),
                35,
                0.5,
                0.7,
                0.5,
                0.10
        );
    }

    // =====================================================
    // OPDATÉR BELØNNING I MIDTEN
    // =====================================================

    public void setDisplayedReward(ItemStack item) {

        if (item == null || item.getType().isAir()) {
            return;
        }

        // Vis en kopi, så den rigtige belønning
        // i spillerens inventory ikke ændres.

        inventory.setItem(
                REWARD_SLOT,
                item.clone()
        );
    }

    // =====================================================
    // OPRET GUI-GENSTAND
    // =====================================================

    private ItemStack createItem(
            Material material,
            String name,
            String... loreLines
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
                text == null ? "" : text.replace('&', '§')
        );
    }

    // =====================================================
    // ÅBN GUI
    // =====================================================

    public void open(Player player) {

        if (player == null || !player.isOnline()) {
            return;
        }

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
