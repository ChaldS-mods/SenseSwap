package com.chalds.senseswap.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;

public class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("senseswap.json");
    private static ModConfig INSTANCE;

    // === Visual settings ===
    public boolean hudEnabled = true;
    public float hudScale = 1.0f;
    public HudPosition hudPosition = HudPosition.TOP_LEFT;
    public int blindEffectStrength = 5;
    public float fogIntensity = 0.95f;

    // === Game settings ===
    public boolean autoAssignRoles = true;
    public boolean showRolePopup = true;
    public int rolePopupDuration = 5;

    // === Phase cycle settings ===
    public boolean phaseCycleEnabled = true;
    public int gamePhaseDurationMinutes = 20;
    public int restPhaseDurationMinutes = 2;

    public enum HudPosition {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    public static ModConfig get() {
        if (INSTANCE == null) INSTANCE = load();
        return INSTANCE;
    }

    public static ModConfig load() {
        if (CONFIG_PATH.toFile().exists()) {
            try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
                ModConfig cfg = GSON.fromJson(reader, ModConfig.class);
                if (cfg != null) { INSTANCE = cfg; return cfg; }
            } catch (IOException e) {
                System.err.println("[SenseSwap] Failed to load config: " + e.getMessage());
            }
        }
        INSTANCE = new ModConfig();
        INSTANCE.save();
        return INSTANCE;
    }

    public void save() {
        try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            System.err.println("[SenseSwap] Failed to save config: " + e.getMessage());
        }
    }

    public void resetToDefault() {
        hudEnabled = true;
        hudScale = 1.0f;
        hudPosition = HudPosition.TOP_LEFT;
        blindEffectStrength = 5;
        fogIntensity = 0.95f;
        autoAssignRoles = true;
        showRolePopup = true;
        rolePopupDuration = 5;
        phaseCycleEnabled = true;
        gamePhaseDurationMinutes = 20;
        restPhaseDurationMinutes = 2;
        save();
    }
}
