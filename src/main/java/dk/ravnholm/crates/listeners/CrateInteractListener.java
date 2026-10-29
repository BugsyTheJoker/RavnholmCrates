
package dk.ravnholm.crates.listeners;

import dk.ravnholm.crates.RavnholmCrates;
import dk.ravnholm.crates.crates.Crate;
import dk.ravnholm.crates.gui.CrateOpeningGUI;
import dk.ravnholm.crates.gui.CratePreviewGUI;
import dk.ravnholm.crates.rewards.Reward;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CrateInteractListener implements Listener {

    private static final String PREFIX = "§8[§6Ravnholm§8] §7";

    private static final BlockFace[] HORIZONTAL_FACES = {
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST
    };

    private final RavnholmCrates plugin;

    private final Map<UUID, OpeningSession> activeOpenings =
            new HashMap<>();

    private final Set<UUID> processingOpenings =
            new HashSet<>();

    public CrateInteractListener(RavnholmCrates plugin) {
        this.plugin = plugin;
    }

    private static class OpeningSession {

        private final CrateOpeningGUI gui;
        private final String rewardName;
        private final boolean droppedItems;

        private BukkitTask particleTask;
        private BukkitTask revealTask;

        private OpeningSession(
                CrateOpeningGUI gui,
                String rewardName,
                boolean droppedItems
        ) {
            this.gui = gui;
            this.rewardName = rewardName;
            this.droppedItems = droppedItems;
        }
    }

    private record DeliveryResult(
            boolean success,
            boolean droppedItems
    ) {
    }

    private boolean isProtectedCrate(Block block) {
        return plugin.getCrateManager()
                .isRegisteredCrateLocation(block.getLocation());
    }

    @EventHandler(
            priority = EventPriority.HIGHEST,
            ignoreCancelled = true
    )
    public void onChestPlace(BlockPlaceEvent event) {

        Block placedBlock = event.getBlockPlaced();
        Material material = placedBlock.getType();

        if (material != Material.CHEST
                && material != Material.TRAPPED_CHEST) {
            return;
        }

        for (BlockFace face : HORIZONTAL_FACES) {

            Block neighbour = placedBlock.getRelative(face);

            if (!isProtectedCrate(neighbour)) {
                continue;
            }

            event.setCancelled(true);

            event.getPlayer().sendMessage(
                    PREFIX + "§cDu kan ikke placere "
                            + "en kiste direkte ved siden af en crate!"
            );

            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCrateBreak(BlockBreakEvent event) {

        if (!isProtectedCrate(event.getBlock())) {
            return;
        }

        event.setCancelled(true);

        event.getPlayer().sendMessage(
                PREFIX + "§cDenne crate er beskyttet "
                        + "og kan ikke ødelægges!"
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isProtectedCrate);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isProtectedCrate);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonExtend(BlockPistonExtendEvent event) {

        BlockFace direction = event.getDirection();

        for (Block block : event.getBlocks()) {

            if (isProtectedCrate(block)
                    || isProtectedCrate(block.getRelative(direction))) {

                event.setCancelled(true);
                return;
            }
        }

        if (isProtectedCrate(
                event.getBlock().getRelative(direction))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonRetract(BlockPistonRetractEvent event) {

        BlockFace direction =
                event.getDirection().getOppositeFace();

        for (Block block : event.getBlocks()) {

            if (isProtectedCrate(block)
                    || isProtectedCrate(block.getRelative(direction))) {

                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onCrateInteract(PlayerInteractEvent event) {

        Action action = event.getAction();

        if (action != Action.LEFT_CLICK_BLOCK
                && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();

        if (clickedBlock == null
                || !isProtectedCrate(clickedBlock)) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        Crate crate = plugin.getCrateManager()
                .getCrateAt(clickedBlock.getLocation());

        if (crate == null) {
            player.sendMessage(
                    PREFIX + "§cDenne crate er midlertidigt utilgængelig."
            );
            return;
        }

        if (activeOpenings.containsKey(playerId)
                || processingOpenings.contains(playerId)) {

            player.sendMessage(
                    PREFIX + "§cDu er allerede i gang "
                            + "med at åbne en crate!"
            );
            return;
        }

        if (action == Action.LEFT_CLICK_BLOCK) {

            CratePreviewGUI preview = new CratePreviewGUI(
                    crate,
                    plugin.getRewardManager()
            );

            preview.open(player);
            return;
        }

        if (!processingOpenings.add(playerId)) {
            return;
        }

        try {
            processCrateOpening(
                    player,
                    playerId,
                    crate,
                    clickedBlock
            );
        } finally {
            processingOpenings.remove(playerId);
        }
    }

    private void processCrateOpening(
            Player player,
            UUID playerId,
            Crate crate,
            Block clickedBlock
    ) {

        ItemStack heldItem = player.getInventory()
                .getItemInMainHand();

        if (!plugin.getKeyManager().isValidKey(
                heldItem,
                crate.getId())) {

            player.sendMessage(
                    PREFIX + "§cDu mangler en nøgle til §e"
                            + crate.getName() + "§c."
            );
            return;
        }

        List<Reward> possibleRewards =
                plugin.getRewardManager()
                        .getRewards(crate.getId());

        if (possibleRewards.isEmpty()) {
            player.sendMessage(
                    PREFIX + "§cDenne crate har ingen belønninger."
            );
            return;
        }

        // Kontrollér alle præmier før lodtrækningen.
        for (Reward possibleReward : possibleRewards) {

            if (possibleReward.isMoneyReward()) {

                if (getEconomyProvider() == null) {

                    player.sendMessage(
                            PREFIX + "§cCraten kan ikke åbnes lige nu."
                    );

                    player.sendMessage(
                            "§7Serverens økonomisystem er ikke tilgængeligt."
                    );

                    player.sendMessage(
                            "§aDin nøgle er ikke brugt."
                    );
                    return;
                }

                continue;
            }

            ItemStack possibleItem;

            try {
                possibleItem = getDeliveryItem(possibleReward);

            } catch (RuntimeException exception) {

                plugin.getLogger().severe(
                        "Ugyldig belønning i crate "
                                + crate.getId() + ": "
                                + exception.getMessage()
                );

                player.sendMessage(
                        PREFIX + "§cEn belønning er ikke "
                                + "konfigureret korrekt."
                );
                return;
            }

            if (!hasSpaceForReward(player, possibleItem)) {

                player.sendMessage(
                        PREFIX + "§cDu har ikke plads til alle "
                                + "mulige belønninger fra denne crate!"
                );

                player.sendMessage(
                        "§7Ryd plads i dit inventory og prøv igen."
                );

                player.sendMessage(
                        "§aDin nøgle er ikke brugt."
                );
                return;
            }
        }

        Reward reward = plugin.getRewardManager()
                .rollReward(crate.getId());

        if (reward == null) {
            player.sendMessage(
                    PREFIX + "§cIngen belønning kunne udtrækkes."
            );
            return;
        }

        ItemStack displayItem = reward.getItem();
        ItemStack deliveryItem = null;

        if (!reward.isMoneyReward()) {

            try {
                deliveryItem = getDeliveryItem(reward);

            } catch (RuntimeException exception) {

                plugin.getLogger().severe(
                        "Kunne ikke klargøre belønning "
                                + reward.getId() + ": "
                                + exception.getMessage()
                );

                player.sendMessage(
                        PREFIX + "§cBelønningen kunne ikke klargøres."
                );
                return;
            }

            if (!hasSpaceForReward(player, deliveryItem)) {

                player.sendMessage(
                        PREFIX + "§cDer er ikke plads til belønningen."
                );
                return;
            }
        }

        CrateOpeningGUI openingGUI;

        try {
            openingGUI = new CrateOpeningGUI(crate);

        } catch (RuntimeException exception) {

            plugin.getLogger().severe(
                    "Kunne ikke oprette crate-GUI: "
                            + exception.getMessage()
            );

            player.sendMessage(
                    PREFIX + "§cCraten kunne ikke åbnes lige nu."
            );

            player.sendMessage(
                    "§aDin nøgle er ikke brugt."
            );
            return;
        }

        // Præmien udleveres før animationen.
        DeliveryResult delivery = deliverReward(
                player,
                crate,
                reward,
                deliveryItem
        );

        if (!delivery.success()) {

            player.sendMessage(
                    PREFIX + "§cBelønningen kunne ikke udbetales."
            );

            player.sendMessage(
                    "§aDin crate-nøgle er ikke brugt."
            );
            return;
        }

        plugin.getStatisticsManager().recordOpening(
                playerId,
                crate.getId()
        );

        Location crateLocation = clickedBlock
                .getLocation()
                .add(0.5, 1.0, 0.5);

        OpeningSession session = new OpeningSession(
                openingGUI,
                reward.getName(),
                delivery.droppedItems()
        );

        activeOpenings.put(playerId, session);

        try {
            openingGUI.open(player);

            openingGUI.startRuneAnimation(
                    plugin,
                    player
            );

            session.particleTask = startCrateParticles(
                    player,
                    playerId,
                    session,
                    crateLocation
            );

            session.revealTask = Bukkit.getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> revealOpening(
                                    player,
                                    playerId,
                                    session,
                                    displayItem
                            ),
                            72L
                    );

        } catch (RuntimeException exception) {

            plugin.getLogger().severe(
                    "Kunne ikke starte crate-animation for "
                            + player.getName() + ": "
                            + exception.getMessage()
            );

            finishOpening(
                    player,
                    playerId,
                    session,
                    true
            );

            player.sendMessage(
                    PREFIX + "§eAnimationen kunne ikke startes, "
                            + "men belønningen er allerede uddelt."
            );
        }
    }

    private ItemStack getDeliveryItem(Reward reward) {

        if (reward.isKeyReward()) {
            return plugin.getKeyManager().createKey(
                    reward.getKeyCrateId(),
                    reward.getKeyAmount()
            );
        }

        if (reward.isItemReward()) {
            return reward.getItem();
        }

        throw new IllegalArgumentException(
                "Belønningen er ikke en item- eller nøglepræmie."
        );
    }

    // =====================================================
    // SIKKER UDLEVERING
    // =====================================================

    private DeliveryResult deliverReward(
            Player player,
            Crate crate,
            Reward reward,
            ItemStack deliveryItem
    ) {

        if (!plugin.getKeyManager().isValidKey(
                player.getInventory().getItemInMainHand(),
                crate.getId()
        )) {

            plugin.getLogger().warning(
                    "Spiller " + player.getName()
                            + " manglede gyldig nøgle ved udlevering."
            );

            return new DeliveryResult(false, false);
        }

        // MONEY: Vault + EssentialsX.
        if (reward.isMoneyReward()) {

            if (!depositMoney(
                    player,
                    reward.getMoneyAmount()
            )) {
                return new DeliveryResult(false, false);
            }

            try {
                consumeOneKey(player);

            } catch (RuntimeException exception) {

                plugin.getLogger().severe(
                        "KRITISK: Pengepræmie blev udbetalt til "
                                + player.getName()
                                + ", men nøglen kunne ikke forbruges: "
                                + exception.getMessage()
                );
            }

            return new DeliveryResult(true, false);
        }

        // ITEM / KEY: Gem inventory før ændringer.
        if (deliveryItem == null
                || deliveryItem.getType().isAir()
                || deliveryItem.getAmount() <= 0) {

            return new DeliveryResult(false, false);
        }

        ItemStack[] before = cloneStorageContents(
                player.getInventory().getStorageContents()
        );

        try {
            consumeOneKey(player);

            Map<Integer, ItemStack> leftovers =
                    player.getInventory().addItem(
                            deliveryItem.clone()
                    );

            if (!leftovers.isEmpty()) {

                plugin.getLogger().warning(
                        "Inventory blev fuldt under udlevering af "
                                + reward.getId()
                                + " til " + player.getName()
                                + ". Ruller åbningen tilbage."
                );

                restoreStorageContents(player, before);
                return new DeliveryResult(false, false);
            }

            return new DeliveryResult(true, false);

        } catch (RuntimeException exception) {

            plugin.getLogger().severe(
                    "Fejl under udlevering af "
                            + reward.getId()
                            + " til " + player.getName()
                            + ": " + exception.getMessage()
            );

            // Genskab hele inventory, ikke kun nøglen.
            try {
                restoreStorageContents(player, before);

            } catch (RuntimeException restoreException) {

                plugin.getLogger().severe(
                        "KRITISK: Inventory kunne ikke genskabes for "
                                + player.getName()
                                + ". Kontrollér manuelt: "
                                + restoreException.getMessage()
                );
            }

            return new DeliveryResult(false, false);
        }
    }

    private ItemStack[] cloneStorageContents(ItemStack[] contents) {

        ItemStack[] copy = new ItemStack[contents.length];

        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null
                    ? null
                    : contents[i].clone();
        }

        return copy;
    }

    private void restoreStorageContents(
            Player player,
            ItemStack[] snapshot
    ) {

        player.getInventory().setStorageContents(
                cloneStorageContents(snapshot)
        );

        player.updateInventory();
    }

    private void consumeOneKey(Player player) {

        ItemStack heldItem = player.getInventory()
                .getItemInMainHand();

        if (heldItem.getAmount() <= 1) {

            player.getInventory()
                    .setItemInMainHand(null);

        } else {

            heldItem.setAmount(
                    heldItem.getAmount() - 1
            );
        }
    }

    // =====================================================
    // VAULT ECONOMY
    // =====================================================

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object getEconomyProvider() {

        Plugin vault = Bukkit.getPluginManager()
                .getPlugin("Vault");

        if (vault == null || !vault.isEnabled()) {
            return null;
        }

        try {
            Class<?> economyClass = Class.forName(
                    "net.milkbowl.vault.economy.Economy",
                    true,
                    vault.getClass().getClassLoader()
            );

            RegisteredServiceProvider<?> registration =
                    Bukkit.getServicesManager()
                            .getRegistration((Class) economyClass);

            if (registration == null) {
                return null;
            }

            return registration.getProvider();

        } catch (ReflectiveOperationException
                 | LinkageError exception) {

            plugin.getLogger().warning(
                    "Vault Economy API er ikke tilgængelig: "
                            + exception.getMessage()
            );

            return null;
        }
    }

    private boolean depositMoney(
            Player player,
            double amount
    ) {

        if (!Double.isFinite(amount) || amount <= 0) {
            return false;
        }

        Plugin vault = Bukkit.getPluginManager()
                .getPlugin("Vault");

        Object economy = getEconomyProvider();

        if (vault == null || economy == null) {
            return false;
        }

        try {
            Class<?> economyClass = Class.forName(
                    "net.milkbowl.vault.economy.Economy",
                    true,
                    vault.getClass().getClassLoader()
            );

            Method depositMethod = economyClass.getMethod(
                    "depositPlayer",
                    OfflinePlayer.class,
                    double.class
            );

            Object response = depositMethod.invoke(
                    economy,
                    player,
                    amount
            );

            if (response == null) {
                return false;
            }

            Method successMethod = response.getClass()
                    .getMethod("transactionSuccess");

            Object result = successMethod.invoke(response);

            return Boolean.TRUE.equals(result);

        } catch (ReflectiveOperationException
                 | LinkageError exception) {

            plugin.getLogger().severe(
                    "Fejl ved Vault-udbetaling til "
                            + player.getName() + ": "
                            + exception.getMessage()
            );

            return false;
        }
    }

    // =====================================================
    // INVENTORY PLADSKONTROL
    // =====================================================

    private boolean hasSpaceForReward(
            Player player,
            ItemStack rewardItem
    ) {

        if (rewardItem == null
                || rewardItem.getType().isAir()
                || rewardItem.getAmount() <= 0) {

            return false;
        }

        ItemStack[] simulatedContents = cloneStorageContents(
                player.getInventory().getStorageContents()
        );

        int heldSlot = player.getInventory()
                .getHeldItemSlot();

        if (heldSlot < 0
                || heldSlot >= simulatedContents.length) {

            return false;
        }

        ItemStack simulatedKey = simulatedContents[heldSlot];

        if (simulatedKey == null
                || simulatedKey.getAmount() <= 0) {

            return false;
        }

        if (simulatedKey.getAmount() == 1) {

            simulatedContents[heldSlot] = null;

        } else {

            simulatedKey.setAmount(
                    simulatedKey.getAmount() - 1
            );
        }

        Inventory simulatedInventory =
                Bukkit.createInventory(null, 36);

        simulatedInventory.setStorageContents(
                simulatedContents
        );

        Map<Integer, ItemStack> leftovers =
                simulatedInventory.addItem(
                        rewardItem.clone()
                );

        return leftovers.isEmpty();
    }

    // =====================================================
    // PARTIKLER OG ANIMATION
    // =====================================================

    private BukkitTask startCrateParticles(
            Player player,
            UUID playerId,
            OpeningSession session,
            Location crateLocation
    ) {

        return new BukkitRunnable() {

            private int step = 0;

            @Override
            public void run() {

                if (activeOpenings.get(playerId) != session) {
                    cancel();
                    return;
                }

                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                if (player.getOpenInventory()
                        .getTopInventory()
                        != session.gui.getInventory()) {

                    cancel();
                    return;
                }

                if (step >= 8) {
                    cancel();
                    return;
                }

                if (crateLocation.getWorld() == null) {
                    cancel();
                    return;
                }

                crateLocation.getWorld().spawnParticle(
                        Particle.ENCHANT,
                        crateLocation,
                        25,
                        0.6,
                        0.5,
                        0.6,
                        0.15
                );

                crateLocation.getWorld().spawnParticle(
                        Particle.END_ROD,
                        crateLocation,
                        8,
                        0.4,
                        0.4,
                        0.4,
                        0.03
                );

                step++;
            }

        }.runTaskTimer(
                plugin,
                0L,
                8L
        );
    }

    private void revealOpening(
            Player player,
            UUID playerId,
            OpeningSession session,
            ItemStack displayItem
    ) {

        if (activeOpenings.get(playerId) != session) {
            return;
        }

        if (!player.isOnline()) {

            finishOpening(
                    player,
                    playerId,
                    session,
                    false
            );
            return;
        }

        if (player.getOpenInventory()
                .getTopInventory()
                != session.gui.getInventory()) {

            finishOpening(
                    player,
                    playerId,
                    session,
                    true
            );
            return;
        }

        try {
            session.gui.revealReward(
                    player,
                    displayItem.clone()
            );

        } finally {

            finishOpening(
                    player,
                    playerId,
                    session,
                    true
            );
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        UUID playerId = player.getUniqueId();

        OpeningSession session = activeOpenings.get(playerId);

        if (session == null) {
            return;
        }

        if (event.getInventory()
                != session.gui.getInventory()) {
            return;
        }

        finishOpening(
                player,
                playerId,
                session,
                true
        );
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        OpeningSession session = activeOpenings.get(playerId);

        if (session == null) {
            return;
        }

        finishOpening(
                player,
                playerId,
                session,
                false
        );
    }

    private void finishOpening(
            Player player,
            UUID playerId,
            OpeningSession session,
            boolean notifyPlayer
    ) {

        boolean removed = activeOpenings.remove(
                playerId,
                session
        );

        if (!removed) {
            return;
        }

        if (session.particleTask != null) {
            session.particleTask.cancel();
            session.particleTask = null;
        }

        if (session.revealTask != null) {
            session.revealTask.cancel();
            session.revealTask = null;
        }

        session.gui.stopAnimation();

        if (notifyPlayer
                && player != null
                && player.isOnline()) {

            player.sendMessage(
                    PREFIX + "§6§lRUNERNES DOM!"
            );

            player.sendMessage(
                    PREFIX + "§aDu vandt: §e"
                            + session.rewardName
            );

            if (session.droppedItems) {

                player.sendMessage(
                        "§eDit inventory var fuldt. "
                                + "Resten af belønningen "
                                + "blev lagt på jorden."
                );

                player.sendMessage(
                        "§7De tabte genstande er markeret "
                                + "med dig som ejer."
                );
            }
        }
    }

    // =====================================================
    // SHUTDOWN
    // =====================================================

    public void shutdown() {

        Map<UUID, OpeningSession> sessions =
                new HashMap<>(activeOpenings);

        for (Map.Entry<UUID, OpeningSession> entry
                : sessions.entrySet()) {

            UUID playerId = entry.getKey();
            OpeningSession session = entry.getValue();

            Player player = Bukkit.getPlayer(playerId);

            finishOpening(
                    player,
                    playerId,
                    session,
                    false
            );

            if (player != null
                    && player.isOnline()
                    && player.getOpenInventory()
                    .getTopInventory()
                    == session.gui.getInventory()) {

                player.closeInventory();
            }
        }

        activeOpenings.clear();
        processingOpenings.clear();
    }
}
