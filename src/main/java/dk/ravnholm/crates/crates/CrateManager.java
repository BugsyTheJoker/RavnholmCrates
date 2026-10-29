
package dk.ravnholm.crates.crates;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CrateManager {

    // =====================================================
    // ALLE INDLÆSTE CRATE-TYPER
    // =====================================================

    private final Map<String, Crate> crates =
            new HashMap<>();

    // =====================================================
    // FYSISKE CRATE-PLACERINGER
    // =====================================================

    private final Map<Location, String> crateLocations =
            new HashMap<>();

    // =====================================================
    // PLACERINGER I VERDENER, DER IKKE ER INDLÆST
    // =====================================================

    private final Map<StoredLocation, String> pendingLocations =
            new HashMap<>();

    // =====================================================
    // STATUS FOR INDLÆSNING
    // =====================================================

    private boolean locationsLoadSuccessful = true;

    // =====================================================
    // INTERN REPRÆSENTATION AF EN PLACERING
    // =====================================================

    private record StoredLocation(
            String worldName,
            int x,
            int y,
            int z
    ) {
    }

    // =====================================================
    // NORMALISER CRATE-ID
    // =====================================================

    private String normalizeCrateId(String crateId) {

        if (crateId == null || crateId.isBlank()) {
            return null;
        }

        return crateId.trim().toLowerCase(Locale.ROOT);
    }

    // =====================================================
    // NORMALISER BLOKPLACERING
    // =====================================================

    private Location normalizeLocation(Location location) {

        if (location == null || location.getWorld() == null) {
            return null;
        }

        // Brug blokkoordinater direkte.
        // Vi behøver ikke indlæse chunken.

        return new Location(
                location.getWorld(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    // =====================================================
    // OPRET GEMBAR PLACERING
    // =====================================================

    private StoredLocation toStoredLocation(Location location) {

        if (location == null || location.getWorld() == null) {
            return null;
        }

        return new StoredLocation(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    // =====================================================
    // REGISTRER CRATE-TYPE
    // =====================================================

    public void registerCrate(Crate crate) {

        if (crate == null) {

            throw new IllegalArgumentException(
                    "Crate må ikke være null."
            );
        }

        String normalizedId = normalizeCrateId(
                crate.getId()
        );

        if (normalizedId == null) {

            throw new IllegalArgumentException(
                    "Crate skal have et gyldigt ID."
            );
        }

        crates.put(normalizedId, crate);
    }

    // =====================================================
    // FIND CRATE-TYPE
    // =====================================================

    public Crate getCrate(String id) {

        String normalizedId = normalizeCrateId(id);

        if (normalizedId == null) {
            return null;
        }

        return crates.get(normalizedId);
    }

    // =====================================================
    // HENT ALLE CRATE-TYPER
    // =====================================================

    public Collection<Crate> getCrates() {

        return crates.values();
    }

    // =====================================================
    // AKTIVÉR EN AFVENTENDE PLACERING
    // =====================================================

    private void resolvePendingLocation(Location location) {

        Location blockLocation = normalizeLocation(location);

        if (blockLocation == null) {
            return;
        }

        StoredLocation storedLocation =
                toStoredLocation(blockLocation);

        if (storedLocation == null) {
            return;
        }

        String crateId = pendingLocations.remove(
                storedLocation
        );

        if (crateId != null) {

            crateLocations.put(
                    blockLocation,
                    crateId
            );
        }
    }

    // =====================================================
    // REGISTRER FYSISK CRATE
    // =====================================================

    public boolean registerCrateLocation(
            Location location,
            String crateId
    ) {

        Location blockLocation = normalizeLocation(location);

        Crate crate = getCrate(crateId);

        if (blockLocation == null || crate == null) {
            return false;
        }

        // Fjern en eventuel afventende registrering
        // på samme placering.

        StoredLocation storedLocation =
                toStoredLocation(blockLocation);

        if (storedLocation != null) {

            pendingLocations.remove(storedLocation);
        }

        crateLocations.put(
                blockLocation,
                crate.getId()
        );

        return true;
    }

    // =====================================================
    // FJERN FYSISK CRATE
    // =====================================================

    public boolean removeCrateLocation(Location location) {

        Location blockLocation = normalizeLocation(location);

        if (blockLocation == null) {
            return false;
        }

        boolean removedActive =
                crateLocations.remove(blockLocation) != null;

        StoredLocation storedLocation =
                toStoredLocation(blockLocation);

        boolean removedPending = false;

        if (storedLocation != null) {

            removedPending =
                    pendingLocations.remove(storedLocation) != null;
        }

        return removedActive || removedPending;
    }

    // =====================================================
    // FIND CRATE PÅ EN BESTEMT PLACERING
    // =====================================================

    public Crate getCrateAt(Location location) {

        Location blockLocation = normalizeLocation(location);

        if (blockLocation == null) {
            return null;
        }

        // Hvis verden først er blevet indlæst senere,
        // kan placeringen nu aktiveres.

        resolvePendingLocation(blockLocation);

        String crateId = crateLocations.get(
                blockLocation
        );

        if (crateId == null) {
            return null;
        }

        // Hvis crate-typen mangler, returneres null.
        // Placeringen bliver stadig bevaret.

        return getCrate(crateId);
    }

    // =====================================================
    // KONTROLLÉR OM EN PLACERING ER REGISTRERET
    // =====================================================

    public boolean isRegisteredCrateLocation(
            Location location
    ) {

        Location blockLocation = normalizeLocation(location);

        if (blockLocation == null) {
            return false;
        }

        resolvePendingLocation(blockLocation);

        return crateLocations.containsKey(blockLocation);
    }

    // =====================================================
    // HENT ALLE AKTIVE CRATE-PLACERINGER
    // FANCYHOLOGRAMS INTEGRATION
    // =====================================================

    public Map<Location, String> getCrateLocations() {

        // Aktivér placeringer fra verdener,
        // som er blevet indlæst efter serverstart.

        for (StoredLocation storedLocation
                : new HashSet<>(pendingLocations.keySet())) {

            World world = Bukkit.getWorld(
                    storedLocation.worldName()
            );

            if (world == null) {
                continue;
            }

            resolvePendingLocation(new Location(
                    world,
                    storedLocation.x(),
                    storedLocation.y(),
                    storedLocation.z()
            ));
        }

        // Opret en beskyttet kopi.
        // Andre systemer må ikke kunne ændre
        // vores interne Location-objekter.

        Map<Location, String> snapshot = new HashMap<>();

        for (Map.Entry<Location, String> entry
                : crateLocations.entrySet()) {

            snapshot.put(
                    entry.getKey().clone(),
                    entry.getValue()
            );
        }

        return Map.copyOf(snapshot);
    }

    // =====================================================
    // STATUS FOR INDLÆSNING
    // =====================================================

    public boolean wasLocationLoadSuccessful() {

        return locationsLoadSuccessful;
    }

    // =====================================================
    // GEM FYSISKE CRATES
    // =====================================================

    public boolean saveLocations(JavaPlugin plugin) {

        if (plugin == null) {
            return false;
        }

        // =================================================
        // UNDGÅ AT OVERSKRIVE DATA EFTER INDLÆSNINGSFEJL
        // =================================================

        if (!locationsLoadSuccessful) {

            plugin.getLogger().severe(
                    "crates.yml gemmes IKKE, fordi "
                            + "den seneste indlæsning fejlede."
            );

            return false;
        }

        File file = new File(
                plugin.getDataFolder(),
                "crates.yml"
        );

        // =================================================
        // SAMMENSÆT ALLE PLACERINGER
        // =================================================

        // Start med placeringer fra verdener,
        // der endnu ikke er indlæst.

        Map<StoredLocation, String> allLocations =
                new LinkedHashMap<>(pendingLocations);

        // Tilføj aktive placeringer.

        for (Map.Entry<Location, String> entry
                : crateLocations.entrySet()) {

            StoredLocation storedLocation =
                    toStoredLocation(entry.getKey());

            if (storedLocation != null) {

                allLocations.put(
                        storedLocation,
                        entry.getValue()
                );
            }
        }

        // =================================================
        // OPRET YAML-KONFIGURATION
        // =================================================

        YamlConfiguration config =
                new YamlConfiguration();

        // Opret sektionen, selv hvis der ikke er
        // nogen placerede crates.

        config.createSection("locations");

        int index = 0;

        for (Map.Entry<StoredLocation, String> entry
                : allLocations.entrySet()) {

            StoredLocation location = entry.getKey();
            String crateId = entry.getValue();

            String path = "locations." + index;

            config.set(
                    path + ".world",
                    location.worldName()
            );

            config.set(
                    path + ".x",
                    location.x()
            );

            config.set(
                    path + ".y",
                    location.y()
            );

            config.set(
                    path + ".z",
                    location.z()
            );

            // Bevar også ID'er til crate-typer,
            // der ikke længere er konfigureret.

            config.set(
                    path + ".crate",
                    crateId
            );

            index++;
        }

        // =================================================
        // GEM FØRST TIL EN MIDLERTIDIG FIL
        // =================================================

        Path temporaryFile = null;

        try {

            Path dataFolder =
                    plugin.getDataFolder().toPath();

            Files.createDirectories(dataFolder);

            temporaryFile = Files.createTempFile(
                    dataFolder,
                    "crates-",
                    ".tmp"
            );

            config.save(temporaryFile.toFile());

            // =================================================
            // ERSTAT DEN GAMLE FIL
            // =================================================

            try {

                Files.move(
                        temporaryFile,
                        file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );

            } catch (AtomicMoveNotSupportedException exception) {

                // Nogle filsystemer understøtter
                // ikke atomisk flytning.

                Files.move(
                        temporaryFile,
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            return true;

        } catch (IOException | SecurityException exception) {

            plugin.getLogger().severe(
                    "Kunne ikke gemme crates.yml: "
                            + exception.getMessage()
            );

            return false;

        } finally {

            // Fjern eventuel efterladt midlertidig fil.

            if (temporaryFile != null) {

                try {

                    Files.deleteIfExists(temporaryFile);

                } catch (IOException exception) {

                    plugin.getLogger().warning(
                            "Kunne ikke slette midlertidig "
                                    + "crate-fil: "
                                    + exception.getMessage()
                    );
                }
            }
        }
    }

    // =====================================================
    // INDLÆS FYSISKE CRATES
    // =====================================================

    public void loadLocations(JavaPlugin plugin) {

        if (plugin == null) {
            return;
        }

        File file = new File(
                plugin.getDataFolder(),
                "crates.yml"
        );

        // =================================================
        // HÅNDTER MANGLENDE FIL
        // =================================================

        if (!file.exists()) {

            // Ved første opstart er det normalt,
            // at filen endnu ikke findes.
            //
            // Vi sletter ikke placeringer, som
            // allerede ligger i hukommelsen.

            locationsLoadSuccessful = true;

            plugin.getLogger().info(
                    "crates.yml findes endnu ikke. "
                            + "Ingen gemte placeringer indlæst."
            );

            return;
        }

        // =================================================
        // INDLÆS YAML MED FEJLHÅNDTERING
        // =================================================

        YamlConfiguration config =
                new YamlConfiguration();

        try {

            config.load(file);

        } catch (IOException
                 | InvalidConfigurationException exception) {

            locationsLoadSuccessful = false;

            plugin.getLogger().severe(
                    "Kunne ikke indlæse crates.yml: "
                            + exception.getMessage()
            );

            plugin.getLogger().severe(
                    "Eksisterende placeringer bevares "
                            + "i hukommelsen, og automatisk "
                            + "overskrivning blokeres."
            );

            return;
        }

        // =================================================
        // FIND PLACERINGSSEKTION
        // =================================================

        ConfigurationSection section =
                config.getConfigurationSection("locations");

        if (section == null) {

            locationsLoadSuccessful = false;

            plugin.getLogger().severe(
                    "crates.yml mangler en gyldig "
                            + "'locations'-sektion."
            );

            return;
        }

        // =================================================
        // OPRET MIDLERTIDIGE MAPS
        // =================================================

        // Vi ændrer ikke de aktive placeringer,
        // før hele filen er valideret.

        Map<Location, String> loadedLocations =
                new HashMap<>();

        Map<StoredLocation, String> loadedPending =
                new HashMap<>();

        Set<StoredLocation> seenLocations =
                new HashSet<>();

        int missingTypes = 0;
        int missingWorlds = 0;

        // =================================================
        // GENNEMGÅ GEMTE PLACERINGER
        // =================================================

        for (String key : section.getKeys(false)) {

            String path = "locations." + key;

            ConfigurationSection locationSection =
                    config.getConfigurationSection(path);

            if (locationSection == null) {

                failLocationLoad(
                        plugin,
                        "Ugyldig placering ved " + path
                );

                return;
            }

            // =================================================
            // KONTROLLÉR VERDEN OG CRATE-ID
            // =================================================

            String worldName =
                    locationSection.getString("world");

            String crateId =
                    locationSection.getString("crate");

            if (worldName == null
                    || worldName.isBlank()
                    || crateId == null
                    || crateId.isBlank()) {

                failLocationLoad(
                        plugin,
                        "Manglende verden eller crate-ID ved "
                                + path
                );

                return;
            }

            // =================================================
            // KONTROLLÉR KOORDINATER
            // =================================================

            if (!locationSection.isInt("x")
                    || !locationSection.isInt("y")
                    || !locationSection.isInt("z")) {

                failLocationLoad(
                        plugin,
                        "Ugyldige koordinater ved " + path
                );

                return;
            }

            int x = locationSection.getInt("x");
            int y = locationSection.getInt("y");
            int z = locationSection.getInt("z");

            StoredLocation storedLocation =
                    new StoredLocation(
                            worldName,
                            x,
                            y,
                            z
                    );

            // =================================================
            // KONTROLLÉR DUBLERET PLACERING
            // =================================================

            if (!seenLocations.add(storedLocation)) {

                failLocationLoad(
                        plugin,
                        "Dubleret crate-placering ved "
                                + path
                );

                return;
            }

            // =================================================
            // FIND VERDEN
            // =================================================

            World world = Bukkit.getWorld(worldName);

            if (world == null) {

                // VIGTIGT:
                //
                // Slet ikke placeringen, bare fordi
                // verden endnu ikke er indlæst.

                loadedPending.put(
                        storedLocation,
                        crateId
                );

                missingWorlds++;

                plugin.getLogger().warning(
                        "Verden '" + worldName
                                + "' er ikke indlæst. "
                                + "Crate-placeringen ved "
                                + x + ", " + y + ", " + z
                                + " bevares."
                );

            } else {

                Location location = new Location(
                        world,
                        x,
                        y,
                        z
                );

                loadedLocations.put(
                        location,
                        crateId
                );
            }

            // =================================================
            // KONTROLLÉR CRATE-TYPEN
            // =================================================

            if (getCrate(crateId) == null) {

                missingTypes++;

                plugin.getLogger().warning(
                        "Crate-typen '" + crateId
                                + "' findes ikke længere, "
                                + "men placeringen bevares."
                );
            }
        }

        // =====================================================
        // AKTIVÉR KUN VED FULDSTÆNDIG GYLDIG FIL
        // =====================================================

        crateLocations.clear();
        crateLocations.putAll(loadedLocations);

        pendingLocations.clear();
        pendingLocations.putAll(loadedPending);

        locationsLoadSuccessful = true;

        // =====================================================
        // RESULTAT
        // =====================================================

        plugin.getLogger().info(
                "Indlæste "
                        + crateLocations.size()
                        + " aktive crate-placeringer."
        );

        if (missingWorlds > 0) {

            plugin.getLogger().warning(
                    missingWorlds
                            + " crate-placeringer afventer "
                            + "indlæsning af deres verden."
            );
        }

        if (missingTypes > 0) {

            plugin.getLogger().warning(
                    missingTypes
                            + " crate-placeringer afventer "
                            + "en gyldig crate-type."
            );
        }
    }

    // =====================================================
    // HÅNDTER FEJL VED INDLÆSNING
    // =====================================================

    private void failLocationLoad(
            JavaPlugin plugin,
            String reason
    ) {

        locationsLoadSuccessful = false;

        plugin.getLogger().severe(
                "Fejl i crates.yml: " + reason
        );

        plugin.getLogger().severe(
                "Indlæsningen blev afbrudt. "
                        + "Eksisterende placeringer er ikke ændret."
        );
    }
}
