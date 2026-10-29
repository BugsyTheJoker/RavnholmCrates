
package dk.ravnholm.crates.commands;

import dk.ravnholm.crates.RavnholmCrates;
import dk.ravnholm.crates.crates.Crate;
import dk.ravnholm.crates.rewards.Reward;
import dk.ravnholm.crates.statistics.CrateStatisticsManager;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.type.Chest.Type;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CratesCommand implements TabExecutor {

    // =====================================================
    // FELTER OG INDSTILLINGER
    // =====================================================

    private final RavnholmCrates plugin;

    private static final String PREFIX =
            "§8[§6Ravnholm§8] §7";

    private static final BlockFace[] HORIZONTAL_FACES = {
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST
    };

    // =====================================================
    // KONSTRUKTØR
    // =====================================================

    public CratesCommand(RavnholmCrates plugin) {
        this.plugin = plugin;
    }

    // =====================================================
    // KOMMANDOER
    // =====================================================

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {

        if (!sender.hasPermission("ravnholmcrates.admin")) {

            sender.sendMessage(
                    PREFIX + "§cDu har ikke adgang til denne kommando."
            );

            return true;
        }

        // =================================================
        // LIST
        // =================================================

        if (args.length == 1
                && args[0].equalsIgnoreCase("list")) {

            showCrateList(sender);
            return true;
        }

        // =================================================
        // STATS
        // =================================================

        if (args.length >= 1
                && args[0].equalsIgnoreCase("stats")) {

            if (args.length == 1) {

                showCrateStatistics(sender);
                return true;
            }

            if (args.length == 2) {

                showPlayerStatistics(sender, args[1]);
                return true;
            }

            sender.sendMessage(
                    PREFIX + "§cBrug: §e/ravnholmcrates stats [spiller]"
            );

            return true;
        }

        // =================================================
        // INFO
        // =================================================

        if (args.length >= 1
                && args[0].equalsIgnoreCase("info")) {

            if (args.length != 2) {

                sender.sendMessage(
                        PREFIX + "§cBrug: §e/ravnholmcrates info <crate>"
                );

                return true;
            }

            String crateId =
                    args[1].toLowerCase(Locale.ROOT);

            Crate crate = plugin.getCrateManager()
                    .getCrate(crateId);

            if (crate == null) {

                sender.sendMessage(
                        PREFIX + "§cDenne crate findes ikke."
                );

                sender.sendMessage(
                        "§7Brug §e/ravnholmcrates list "
                                + "§7for at se alle crate-typer."
                );

                return true;
            }

            showCrateInfo(sender, crate);
            return true;
        }

        // =================================================
        // RELOAD
        // =================================================

        if (args.length == 1
                && args[0].equalsIgnoreCase("reload")) {

            plugin.reloadConfig();

            boolean success =
                    plugin.reloadCrateTypes();

            if (success) {

                // Hologrammerne synkroniseres allerede
                // af reloadCrateTypes() i hovedklassen.

                sender.sendMessage(
                        PREFIX + "§aRavnholmCrates er blevet genindlæst!"
                );

                sender.sendMessage(
                        "§7Crate-typer og belønninger er opdateret."
                );

            } else {

                sender.sendMessage(
                        PREFIX + "§cKunne ikke genindlæse crate-typerne!"
                );

                sender.sendMessage(
                        "§7De tidligere indlæste crates "
                                + "og belønninger er bevaret."
                );

                sender.sendMessage(
                        "§eKontrollér crate-types.yml "
                                + "og serverens konsol."
                );
            }

            return true;
        }

        // =================================================
        // GIVE
        // =================================================

        if ((args.length == 3 || args.length == 4)
                && args[0].equalsIgnoreCase("give")) {

            Player target =
                    Bukkit.getPlayerExact(args[1]);

            if (target == null) {

                sender.sendMessage(
                        "§cSpilleren er ikke online."
                );

                return true;
            }

            String crateId =
                    args[2].toLowerCase(Locale.ROOT);

            Crate crate = plugin.getCrateManager()
                    .getCrate(crateId);

            if (crate == null) {

                sender.sendMessage(
                        "§cDenne crate findes ikke."
                );

                return true;
            }

            int amount = 1;

            if (args.length == 4) {

                try {

                    amount = Integer.parseInt(args[3]);

                } catch (NumberFormatException exception) {

                    sender.sendMessage(
                            "§cAntallet skal være et tal."
                    );

                    return true;
                }
            }

            if (amount < 1 || amount > 64) {

                sender.sendMessage(
                        "§cAntallet skal være mellem 1 og 64."
                );

                return true;
            }

            ItemStack key = plugin.getKeyManager()
                    .createKey(crateId, amount);

            Map<Integer, ItemStack> leftovers =
                    target.getInventory().addItem(key);

            for (ItemStack leftover : leftovers.values()) {

                target.getWorld().dropItemNaturally(
                        target.getLocation(),
                        leftover
                );
            }

            sender.sendMessage(
                    PREFIX + "§aDu gav §e"
                            + amount + "x "
                            + formatColors(crate.getName())
                            + " Nøgle §atil §e"
                            + target.getName()
            );

            target.sendMessage(
                    PREFIX + "§aDu har modtaget §e"
                            + amount + "x "
                            + formatColors(crate.getName())
                            + " Nøgle!"
            );

            if (!leftovers.isEmpty()) {

                target.sendMessage(
                        "§eDit inventory var fuldt. "
                                + "De resterende nøgler ligger på jorden."
                );
            }

            return true;
        }

        // =================================================
        // REMOVE
        // =================================================

        if (args.length == 1
                && args[0].equalsIgnoreCase("remove")) {

            if (!(sender instanceof Player player)) {

                sender.sendMessage(
                        "§cDenne kommando kan kun bruges i spillet."
                );

                return true;
            }

            Block block =
                    player.getTargetBlockExact(5);

            if (block == null) {

                player.sendMessage(
                        "§cDu skal kigge på en crate inden for 5 blokke."
                );

                return true;
            }

            boolean removed = plugin.getCrateManager()
                    .removeCrateLocation(
                            block.getLocation()
                    );

            if (!removed) {

                player.sendMessage(
                        "§cDenne blok er ikke registreret som en crate."
                );

                return true;
            }

            // =================================================
            // GEM ÆNDRINGEN
            // =================================================

            plugin.getCrateManager()
                    .saveLocations(plugin);

            // =================================================
            // FANCYHOLOGRAMS - FJERN HOLOGRAMMET
            // =================================================

            // Synkronisering fjerner hologrammer,
            // som ikke længere har en crate-placering.
            //
            // Metoden fungerer også, hvis
            // FancyHolograms ikke er installeret.

            plugin.refreshCrateHolograms();

            player.sendMessage(
                    PREFIX + "§aCraten er nu fjernet fra systemet."
            );

            return true;
        }

        // =================================================
        // SET
        // =================================================

        if (args.length == 2
                && args[0].equalsIgnoreCase("set")) {

            if (!(sender instanceof Player player)) {

                sender.sendMessage(
                        "§cDenne kommando kan kun bruges i spillet."
                );

                return true;
            }

            String crateId =
                    args[1].toLowerCase(Locale.ROOT);

            Crate crate = plugin.getCrateManager()
                    .getCrate(crateId);

            if (crate == null) {

                player.sendMessage(
                        "§cDenne crate findes ikke."
                );

                return true;
            }

            Block block =
                    player.getTargetBlockExact(5);

            if (block == null
                    || !(block.getState() instanceof Chest)) {

                player.sendMessage(
                        "§cDu skal kigge på en kiste inden for 5 blokke."
                );

                return true;
            }

            // =================================================
            // KONTROLLÉR KISTENS TYPE
            // =================================================

            if (!(block.getBlockData()
                    instanceof org.bukkit.block.data.type.Chest chestData)) {

                player.sendMessage(
                        "§cKunne ikke kontrollere kistens type."
                );

                return true;
            }

            if (chestData.getType() != Type.SINGLE) {

                player.sendMessage(
                        PREFIX + "§cDu kan ikke registrere "
                                + "en dobbeltkiste som crate!"
                );

                player.sendMessage(
                        "§7Brug en almindelig enkeltkiste i stedet."
                );

                return true;
            }

            // =================================================
            // KONTROLLÉR NABOKISTER
            // =================================================

            for (BlockFace face : HORIZONTAL_FACES) {

                Block neighbour =
                        block.getRelative(face);

                Material neighbourType =
                        neighbour.getType();

                if (neighbourType == Material.CHEST
                        || neighbourType == Material.TRAPPED_CHEST) {

                    player.sendMessage(
                            PREFIX + "§cDer står allerede "
                                    + "en kiste direkte ved siden af!"
                    );

                    player.sendMessage(
                            "§7Flyt kisterne fra hinanden, "
                                    + "før du registrerer craten."
                    );

                    return true;
                }
            }

            // =================================================
            // REGISTRER FYSISK CRATE
            // =================================================

            boolean success = plugin.getCrateManager()
                    .registerCrateLocation(
                            block.getLocation(),
                            crateId
                    );

            if (success) {

                // =================================================
                // GEM CRATEN
                // =================================================

                plugin.getCrateManager()
                        .saveLocations(plugin);

                // =================================================
                // FANCYHOLOGRAMS - OPRET HOLOGRAM
                // =================================================

                // Synkroniser straks, så spilleren
                // ikke skal vente 30 sekunder.
                //
                // Hvis kisten allerede var registreret
                // som en anden crate-type, opdateres
                // hologrammets tekst.

                plugin.refreshCrateHolograms();

                player.sendMessage(
                        PREFIX + "§aKisten er nu "
                                + "registreret som §e"
                                + formatColors(crate.getName())
                                + "§a."
                );

            } else {

                player.sendMessage(
                        "§cKunne ikke registrere craten."
                );
            }

            return true;
        }

        // =================================================
        // HJÆLPEMENU
        // =================================================

        showHelp(sender);
        return true;
    }

    // =====================================================
    // VIS SPILLERSTATISTIK
    // =====================================================

    private void showPlayerStatistics(
            CommandSender sender,
            String playerName
    ) {

        CrateStatisticsManager statistics =
                plugin.getStatisticsManager();

        if (statistics == null) {

            sender.sendMessage(
                    PREFIX + "§cStatistiksystemet er ikke tilgængeligt."
            );

            return;
        }

        // =================================================
        // FIND SPILLEREN
        // =================================================

        OfflinePlayer target =
                Bukkit.getPlayerExact(playerName);

        if (target == null) {

            target = Bukkit.getOfflinePlayerIfCached(
                    playerName
            );
        }

        if (target == null) {

            for (OfflinePlayer offlinePlayer
                    : Bukkit.getOfflinePlayers()) {

                String knownName =
                        offlinePlayer.getName();

                if (knownName != null
                        && knownName.equalsIgnoreCase(playerName)) {

                    target = offlinePlayer;
                    break;
                }
            }
        }

        if (target == null) {

            sender.sendMessage(
                    PREFIX + "§cKunne ikke finde spilleren §e"
                            + playerName + "§c."
            );

            sender.sendMessage(
                    "§7Spilleren skal tidligere have været "
                            + "registreret på serveren."
            );

            return;
        }

        // =================================================
        // HENT STATISTIK
        // =================================================

        long openings =
                statistics.getPlayerOpenings(
                        target.getUniqueId()
                );

        String displayName = target.getName();

        if (displayName == null) {
            displayName = playerName;
        }

        // =================================================
        // VIS RESULTAT
        // =================================================

        sender.sendMessage(
                "§8§m--------------------------------"
        );

        sender.sendMessage(
                "§6§l✦ SPILLERSTATISTIK ✦"
        );

        sender.sendMessage(
                "§8§m--------------------------------"
        );

        sender.sendMessage(
                "§7Spiller: §e" + displayName
        );

        sender.sendMessage(
                "§7Status: "
                        + (target.isOnline()
                        ? "§aOnline"
                        : "§cOffline")
        );

        sender.sendMessage(
                "§7Samlede crate-åbninger: §6§l"
                        + openings
        );

        sender.sendMessage(
                "§8§m--------------------------------"
        );
    }

    // =====================================================
    // VIS SERVERENS CRATE-STATISTIK
    // =====================================================

    private void showCrateStatistics(
            CommandSender sender
    ) {

        CrateStatisticsManager statistics =
                plugin.getStatisticsManager();

        if (statistics == null) {

            sender.sendMessage(
                    PREFIX + "§cStatistiksystemet er ikke tilgængeligt."
            );

            return;
        }

        // =================================================
        // SORTER CRATES EFTER ANTAL ÅBNINGER
        // =================================================

        List<Crate> crates = new ArrayList<>(
                plugin.getCrateManager().getCrates()
        );

        crates.sort(
                Comparator
                        .comparingLong(
                                (Crate crate) ->
                                        statistics.getCrateOpenings(
                                                crate.getId()
                                        )
                        )
                        .reversed()
                        .thenComparing(
                                Crate::getId,
                                String.CASE_INSENSITIVE_ORDER
                        )
        );

        // =================================================
        // OVERSKRIFT
        // =================================================

        sender.sendMessage(
                "§8§m--------------------------------"
        );

        sender.sendMessage(
                "§6§l✦ RAVNHOLM STATISTIK ✦"
        );

        sender.sendMessage(
                "§8§m--------------------------------"
        );

        sender.sendMessage(
                "§7Samlede crate-åbninger: §6§l"
                        + statistics.getTotalOpenings()
        );

        sender.sendMessage(
                "§7Aktive crate-typer: §e"
                        + crates.size()
        );

        sender.sendMessage(
                "§8§m--------------------------------"
        );

        sender.sendMessage(
                "§6§lÅBNINGER PR. CRATE"
        );

        // =================================================
        // ÅBNINGER FOR HVER CRATE
        // =================================================

        if (crates.isEmpty()) {

            sender.sendMessage(
                    "§cIngen crate-typer er registreret."
            );

        } else {

            for (Crate crate : crates) {

                long openings =
                        statistics.getCrateOpenings(
                                crate.getId()
                        );

                sender.sendMessage(
                        "§6✦ §f"
                                + formatColors(crate.getName())
                );

                sender.sendMessage(
                        "   §7Åbninger: §a" + openings
                );
            }
        }

        // =================================================
        // TIDLIGERE CRATE-TYPER
        // =================================================

        Map<String, Long> allCrateStatistics =
                statistics.getCrateStatistics();

        boolean hasHistoricalCrates = false;

        for (Map.Entry<String, Long> entry
                : allCrateStatistics.entrySet()) {

            if (plugin.getCrateManager()
                    .getCrate(entry.getKey()) != null) {

                continue;
            }

            if (!hasHistoricalCrates) {

                sender.sendMessage(
                        "§8§m--------------------------------"
                );

                sender.sendMessage(
                        "§7§lTIDLIGERE CRATE-TYPER"
                );

                hasHistoricalCrates = true;
            }

            sender.sendMessage(
                    "§8✦ §7" + entry.getKey()
                            + " §8» §f"
                            + entry.getValue()
                            + " åbninger"
            );
        }

        // =================================================
        // PERSONLIG STATISTIK
        // =================================================

        if (sender instanceof Player player) {

            sender.sendMessage(
                    "§8§m--------------------------------"
            );

            sender.sendMessage(
                    "§6§lDIN STATISTIK"
            );

            long personalOpenings =
                    statistics.getPlayerOpenings(
                            player.getUniqueId()
                    );

            sender.sendMessage(
                    "§7Dine crate-åbninger: §a§l"
                            + personalOpenings
            );
        }

        // =================================================
        // AFSLUTNING
        // =================================================

        sender.sendMessage(
                "§8§m--------------------------------"
        );

        sender.sendMessage(
                "§7Statistik gemmes automatisk "
                        + "hvert §e5. minut§7."
        );

        sender.sendMessage(
                "§8§m--------------------------------"
        );
    }

    // =====================================================
    // VIS ALLE CRATE-TYPER
    // =====================================================

    private void showCrateList(
            CommandSender sender
    ) {

        List<Crate> crates = new ArrayList<>(
                plugin.getCrateManager().getCrates()
        );

        crates.sort(
                Comparator.comparing(
                        Crate::getId,
                        String.CASE_INSENSITIVE_ORDER
                )
        );

        sender.sendMessage(
                "§8§m--------------------------------"
        );

        sender.sendMessage(
                "§6§l✦ RAVNHOLM CRATES ✦"
        );

        sender.sendMessage(
                "§7Registrerede crate-typer: §e"
                        + crates.size()
        );

        sender.sendMessage(
                "§8§m--------------------------------"
        );

        if (crates.isEmpty()) {

            sender.sendMessage(
                    "§cIngen crate-typer er registreret."
            );

            sender.sendMessage(
                    "§7Kontrollér din crate-types.yml."
            );

            sender.sendMessage(
                    "§8§m--------------------------------"
            );

            return;
        }

        int totalRewards = 0;

        // =================================================
        // VIS HVER CRATE
        // =================================================

        for (Crate crate : crates) {

            String crateId = crate.getId();

            String crateName =
                    formatColors(crate.getName());

            int rewardCount =
                    plugin.getRewardManager()
                            .getRewards(crateId)
                            .size();

            totalRewards += rewardCount;

            sender.sendMessage(
                    "§6✦ §e" + crateId
                            + " §8» §f" + crateName
            );

            sender.sendMessage(
                    "   §7Belønninger: §a"
                            + rewardCount
            );
        }

        sender.sendMessage(
                "§8§m--------------------------------"
        );

        sender.sendMessage(
                "§7Crate-typer: §e"
                        + crates.size()
                        + " §8| §7Belønninger: §a"
                        + totalRewards
        );

        sender.sendMessage(
                "§8§m--------------------------------"
        );
    }

    // =====================================================
    // DETALJER OM EN BESTEMT CRATE
    // =====================================================

    private void showCrateInfo(
            CommandSender sender,
            Crate crate
    ) {

        List<Reward> rewards =
                plugin.getRewardManager()
                        .getRewards(crate.getId());

        // Brug den præcise vægt fra Reward.java,
        // så decimalvægte kan vises korrekt.

        long totalWeightUnits = 0;

        for (Reward reward : rewards) {

            totalWeightUnits += reward.getWeightUnits();
        }

        double totalWeight =
                totalWeightUnits / 1_000_000.0;

        sender.sendMessage(
                "§8§m--------------------------------"
        );

        sender.sendMessage(
                "§6§l✦ CRATE INFORMATION ✦"
        );

        sender.sendMessage(
                "§7Navn: §f"
                        + formatColors(crate.getName())
        );

        sender.sendMessage(
                "§7ID: §e" + crate.getId()
        );

        sender.sendMessage(
                "§7Antal belønninger: §a"
                        + rewards.size()
        );

        sender.sendMessage(
                "§7Samlet vægt: §e" + totalWeight
        );

        sender.sendMessage(
                "§8§m--------------------------------"
        );

        if (rewards.isEmpty()) {

            sender.sendMessage(
                    "§cDenne crate har ingen belønninger."
            );

            sender.sendMessage(
                    "§8§m--------------------------------"
            );

            return;
        }

        sender.sendMessage(
                "§6§lMULIGE BELØNNINGER"
        );

        int index = 1;

        // =================================================
        // VIS BELØNNINGER OG VINDERCHANCER
        // =================================================

        for (Reward reward : rewards) {

            double chance = totalWeightUnits > 0
                    ? reward.getWeightUnits()
                    * 100.0 / totalWeightUnits
                    : 0.0;

            String chanceText = String.format(
                    Locale.US,
                    "%.2f",
                    chance
            );

            ItemStack rewardItem =
                    reward.getItem();

            sender.sendMessage(
                    "§e" + index + ". §f"
                            + formatColors(reward.getName())
            );

            sender.sendMessage(
                    "   §7Antal: §e"
                            + rewardItem.getAmount()
                            + " §8| §7Chance: §6"
                            + chanceText + "%"
            );

            sender.sendMessage(
                    "   §7Vægt: §f"
                            + reward.getExactWeight()
            );

            index++;
        }

        sender.sendMessage(
                "§8§m--------------------------------"
        );

        sender.sendMessage(
                "§7Vinderchancerne beregnes ud fra "
                        + "§fbelønningernes vægt§7."
        );

        sender.sendMessage(
                "§8§m--------------------------------"
        );
    }

    // =====================================================
    // HJÆLPEMENU
    // =====================================================

    private void showHelp(
            CommandSender sender
    ) {

        sender.sendMessage(
                "§8§m--------------------------------"
        );

        sender.sendMessage(
                "§6§lRAVNHOLM CRATES §8- §7Administration"
        );

        sender.sendMessage(
                "§8§m--------------------------------"
        );

        sender.sendMessage(
                "§e/ravnholmcrates list"
        );

        sender.sendMessage(
                "§7Vis alle registrerede crate-typer."
        );

        sender.sendMessage(
                "§e/ravnholmcrates info <crate>"
        );

        sender.sendMessage(
                "§7Vis belønninger og vinderchancer."
        );

        sender.sendMessage(
                "§e/ravnholmcrates stats [spiller]"
        );

        sender.sendMessage(
                "§7Vis serverens eller en spillers crate-statistik."
        );

        if (sender instanceof Player) {

            sender.sendMessage(
                    "§e/ravnholmcrates set <crate>"
            );

            sender.sendMessage(
                    "§7Registrér kisten, du kigger på."
            );

            sender.sendMessage(
                    "§e/ravnholmcrates remove"
            );

            sender.sendMessage(
                    "§7Fjern registreringen fra en kiste."
            );
        }

        sender.sendMessage(
                "§e/ravnholmcrates give <spiller> <crate> [antal]"
        );

        sender.sendMessage(
                "§7Giv en spiller crate-nøgler."
        );

        sender.sendMessage(
                "§e/ravnholmcrates reload"
        );

        sender.sendMessage(
                "§7Genindlæs crate-konfigurationen."
        );

        sender.sendMessage(
                "§8§m--------------------------------"
        );
    }

    // =====================================================
    // FARVEKODER
    // =====================================================

    private String formatColors(
            String text
    ) {

        if (text == null) {
            return "";
        }

        return text.replace('&', '§');
    }

    // =====================================================
    // TAB-COMPLETION
    // =====================================================

    @Override
    public @NotNull List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {

        if (!sender.hasPermission("ravnholmcrates.admin")) {
            return List.of();
        }

        List<String> suggestions =
                new ArrayList<>();

        // =================================================
        // UNDERKOMMANDOER
        // =================================================

        if (args.length == 1) {

            suggestions.add("give");
            suggestions.add("info");
            suggestions.add("list");
            suggestions.add("reload");
            suggestions.add("stats");

            if (sender instanceof Player) {

                suggestions.add("set");
                suggestions.add("remove");
            }

            return filterSuggestions(
                    suggestions,
                    args[0]
            );
        }

        String subCommand =
                args[0].toLowerCase(Locale.ROOT);

        // =================================================
        // STATS <SPILLER>
        // =================================================

        if (subCommand.equals("stats")
                && args.length == 2) {

            for (Player player : Bukkit.getOnlinePlayers()) {

                suggestions.add(
                        player.getName()
                );
            }

            return filterSuggestions(
                    suggestions,
                    args[1]
            );
        }

        // =================================================
        // INFO <CRATE>
        // =================================================

        if (subCommand.equals("info")
                && args.length == 2) {

            for (Crate crate : plugin.getCrateManager()
                    .getCrates()) {

                suggestions.add(
                        crate.getId()
                );
            }

            return filterSuggestions(
                    suggestions,
                    args[1]
            );
        }

        // =================================================
        // SET <CRATE>
        // =================================================

        if (subCommand.equals("set")
                && args.length == 2
                && sender instanceof Player) {

            for (Crate crate : plugin.getCrateManager()
                    .getCrates()) {

                suggestions.add(
                        crate.getId()
                );
            }

            return filterSuggestions(
                    suggestions,
                    args[1]
            );
        }

        // =================================================
        // GIVE <SPILLER>
        // =================================================

        if (subCommand.equals("give")
                && args.length == 2) {

            for (Player player : Bukkit.getOnlinePlayers()) {

                suggestions.add(
                        player.getName()
                );
            }

            return filterSuggestions(
                    suggestions,
                    args[1]
            );
        }

        // =================================================
        // GIVE <SPILLER> <CRATE>
        // =================================================

        if (subCommand.equals("give")
                && args.length == 3) {

            for (Crate crate : plugin.getCrateManager()
                    .getCrates()) {

                suggestions.add(
                        crate.getId()
                );
            }

            return filterSuggestions(
                    suggestions,
                    args[2]
            );
        }

        // =================================================
        // GIVE <SPILLER> <CRATE> <ANTAL>
        // =================================================

        if (subCommand.equals("give")
                && args.length == 4) {

            suggestions.addAll(
                    Arrays.asList(
                            "1",
                            "5",
                            "10",
                            "16",
                            "32",
                            "64"
                    )
            );

            return filterSuggestions(
                    suggestions,
                    args[3]
            );
        }

        return List.of();
    }

    // =====================================================
    // FILTRER OG SORTÉR TAB-FORSLAG
    // =====================================================

    private List<String> filterSuggestions(
            List<String> suggestions,
            String input
    ) {

        String search =
                input.toLowerCase(Locale.ROOT);

        List<String> filtered =
                new ArrayList<>();

        for (String suggestion : suggestions) {

            if (suggestion.toLowerCase(Locale.ROOT)
                    .startsWith(search)) {

                filtered.add(suggestion);
            }
        }

        filtered.sort(
                String.CASE_INSENSITIVE_ORDER
        );

        return filtered;
    }
}
