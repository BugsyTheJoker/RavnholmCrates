
package dk.ravnholm.crates.keys;

import dk.ravnholm.crates.RavnholmCrates;
import dk.ravnholm.crates.crates.Crate;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class KeyManager {

    private final RavnholmCrates plugin;
    private final NamespacedKey keyIdentifier;

    // =====================================================
    // KONSTRUKTØR
    // =====================================================

    public KeyManager(RavnholmCrates plugin) {

        this.plugin = plugin;

        // VIGTIGT:
        // Behold den eksisterende PDC-identifikator.
        //
        // Ellers vil allerede udstedte crate-nøgler
        // ikke længere blive genkendt.

        this.keyIdentifier = new NamespacedKey(
                plugin,
                "crate_key"
        );
    }

    // =====================================================
    // OPRET CRATE-NØGLE
    // =====================================================

    public ItemStack createKey(String crateId, int amount) {

        // =================================================
        // KONTROLLÉR INPUT
        // =================================================

        if (crateId == null
                || crateId.isBlank()
                || amount < 1
                || amount > 64) {

            throw new IllegalArgumentException(
                    "Ugyldig crate eller antal."
            );
        }

        // =================================================
        // FIND CRATE-TYPEN
        // =================================================

        String normalizedId = crateId
                .trim()
                .toLowerCase(Locale.ROOT);

        Crate crate = plugin.getCrateManager()
                .getCrate(normalizedId);

        if (crate == null) {

            throw new IllegalArgumentException(
                    "Crate-typen findes ikke: " + crateId
            );
        }

        // Brug altid ID'et fra den registrerede crate.
        // Dermed oprettes nøgler konsekvent.

        String canonicalId = crate.getId();

        // =================================================
        // INDLÆS NØGLEKONFIGURATION
        // =================================================

        File file = new File(
                plugin.getDataFolder(),
                "crate-types.yml"
        );

        YamlConfiguration config =
                YamlConfiguration.loadConfiguration(file);

        ConfigurationSection keySection =
                config.getConfigurationSection(
                        "crates." + canonicalId + ".key"
                );

        // =================================================
        // STANDARDVÆRDIER
        // =================================================

        String materialName = "TRIPWIRE_HOOK";

        String keyName = "&6{crate} &eNøgle";

        List<String> keyLore = new ArrayList<>();

        keyLore.add("&8&m------------------------");
        keyLore.add("&7En nøgle fra Ravnholms rige.");
        keyLore.add("");
        keyLore.add("&7Åbner: &e{crate}");
        keyLore.add("&7Bruges ved den tilhørende crate.");
        keyLore.add("");
        keyLore.add("&6✦ &eRunernes Dom &6✦");
        keyLore.add("&8&m------------------------");

        // =================================================
        // OVERSKRIV MED YAML-VÆRDIER
        // =================================================

        if (keySection != null) {

            materialName = keySection.getString(
                    "material",
                    materialName
            );

            keyName = keySection.getString(
                    "name",
                    keyName
            );

            if (keySection.isList("lore")) {

                keyLore = keySection.getStringList("lore");
            }
        }

        // =================================================
        // KONTROLLÉR MATERIALER
        // =================================================

        Material material = null;

        if (materialName != null
                && !materialName.isBlank()) {

            material = Material.matchMaterial(
                    materialName.toUpperCase(Locale.ROOT)
            );
        }

        if (material == null
                || material.isAir()
                || !material.isItem()
                || material.getMaxStackSize() < amount) {

            plugin.getLogger().warning(
                    "Ugyldigt nøglemateriale eller stackstørrelse "
                            + "for crate '" + canonicalId
                            + "'. Bruger TRIPWIRE_HOOK."
            );

            material = Material.TRIPWIRE_HOOK;
        }

        // =================================================
        // OPRET GENSTANDEN
        // =================================================

        ItemStack item = new ItemStack(
                material,
                amount
        );

        ItemMeta meta = item.getItemMeta();

        if (meta == null) {

            throw new IllegalStateException(
                    "Kunne ikke oprette metadata til crate-nøglen."
            );
        }

        // =================================================
        // NAVN OG FARVER
        // =================================================

        meta.setDisplayName(
                formatText(keyName, crate)
        );

        // =================================================
        // BESKRIVELSE (LORE)
        // =================================================

        List<String> formattedLore = new ArrayList<>();

        for (String line : keyLore) {

            formattedLore.add(
                    formatText(line, crate)
            );
        }

        meta.setLore(formattedLore);

        // =================================================
        // GEM DET ÆGTE CRATE-ID I PDC
        // =================================================

        // Det er denne værdi, vi bruger til at
        // identificere nøglen.
        //
        // Navn, farver, lore og materiale alene
        // gør ALDRIG en genstand til en gyldig nøgle.

        PersistentDataContainer data =
                meta.getPersistentDataContainer();

        data.set(
                keyIdentifier,
                PersistentDataType.STRING,
                canonicalId
        );

        item.setItemMeta(meta);

        return item;
    }

    // =====================================================
    // KONTROLLÉR OM EN NØGLE ER GYLDIG
    // =====================================================

    public boolean isValidKey(
            ItemStack item,
            String crateId
    ) {

        // =================================================
        // KONTROLLÉR GENSTANDEN
        // =================================================

        if (item == null
                || item.getType().isAir()
                || item.getAmount() < 1
                || !item.hasItemMeta()) {

            return false;
        }

        // =================================================
        // KONTROLLÉR CRATE-ID
        // =================================================

        if (crateId == null || crateId.isBlank()) {
            return false;
        }

        String requestedId = crateId
                .trim()
                .toLowerCase(Locale.ROOT);

        // =================================================
        // KONTROLLÉR AT CRATEN FINDES
        // =================================================

        Crate crate = plugin.getCrateManager()
                .getCrate(requestedId);

        if (crate == null) {

            // Nøgler til slettede eller ikke-eksisterende
            // crates skal ikke kunne bruges.

            return false;
        }

        // =================================================
        // HENT PDC-DATA
        // =================================================

        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return false;
        }

        PersistentDataContainer data =
                meta.getPersistentDataContainer();

        // =================================================
        // KONTROLLÉR DATATYPEN
        // =================================================

        // En almindelig genstand uden den korrekte
        // PDC-identifikator bliver afvist.
        //
        // Det samme gælder, hvis identifikatoren
        // findes med en forkert datatype.

        if (!data.has(
                keyIdentifier,
                PersistentDataType.STRING
        )) {

            return false;
        }

        // =================================================
        // HENT NØGLENS GEMTE CRATE-ID
        // =================================================

        String storedCrateId = data.get(
                keyIdentifier,
                PersistentDataType.STRING
        );

        if (storedCrateId == null
                || storedCrateId.isBlank()) {

            return false;
        }

        // =================================================
        // SAMMENLIGN CRATE-ID'ER
        // =================================================

        String normalizedStoredId = storedCrateId
                .trim()
                .toLowerCase(Locale.ROOT);

        String canonicalId = crate.getId()
                .toLowerCase(Locale.ROOT);

        // Kun nøgler, der indeholder det korrekte
        // crate-ID, kan åbne den pågældende crate.

        return canonicalId.equals(normalizedStoredId);
    }

    // =====================================================
    // FORMATER FARVER OG PLACEHOLDERS
    // =====================================================

    private String formatText(
            String text,
            Crate crate
    ) {

        if (text == null) {
            return "";
        }

        String formatted = text
                .replace("{crate}", crate.getName())
                .replace("{crate_id}", crate.getId());

        return formatted.replace('&', '§');
    }
}
