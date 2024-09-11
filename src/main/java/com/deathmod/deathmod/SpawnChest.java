package com.deathmod.deathmod;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SpawnChest {
    private static final Logger LOGGER = LoggerFactory.getLogger("deathmod");
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("deathmod");
    private static final Path GRAVE_DATA_FILE = CONFIG_DIR.resolve("grave_data.json");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");
    public static int graveTimer = 120;  // Default value
    public static boolean isDestructible = false;  // Default value
    private static final Gson gson = new Gson();
    static final Map<BlockPos, Long> graveChests = new HashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static void init() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (IOException e) {
            LOGGER.warn("Warning: Failed to create directories: {}", CONFIG_DIR.toAbsolutePath());
        }
        loadConfig();
        loadGraveData();
        LOGGER.info("LOADED CONFIG & GRAVE DATA");
    }

    public static void loadConfig() {
        if (Files.exists(CONFIG_FILE)) {
            try (var reader = Files.newBufferedReader(CONFIG_FILE)) {
                Map<String, Object> config = gson.fromJson(reader, new TypeToken<Map<String, Object>>() {}.getType());
                if (config != null) {
                    graveTimer = ((Number) config.getOrDefault("graveTimer", 120)).intValue();
                    isDestructible = (Boolean) config.getOrDefault("is_destructible", false);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load config file: {}", CONFIG_FILE, e);
            }
        } else {
            saveConfig();
        }
    }

    public static void saveConfig() {
        Map<String, Object> config = Map.of("graveTimer", graveTimer, "is_destructible", isDestructible);
        try (var writer = Files.newBufferedWriter(CONFIG_FILE)) {
            gson.toJson(config, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save config file: {}", CONFIG_FILE, e);
        }
    }

    public static void spawnAtDeathLocation(ServerPlayerEntity player) {
        World world = player.getWorld();
        BlockPos chestPos = player.getBlockPos();

        if (world.getBlockState(chestPos).getBlock() == Blocks.WATER) {
            world.setBlockState(chestPos, Blocks.BARREL.getDefaultState());
            if (world.getBlockEntity(chestPos) instanceof BarrelBlockEntity barrel) {
                transferInventory(player, barrel);
                scheduleDespawn(world, chestPos, graveTimer);
            }
        }
        else if (!world.getBlockState(chestPos).isAir()) {
            chestPos = chestPos.up();
        }
        if (world.getBlockState(chestPos).isAir() && !(world.getBlockState(chestPos).getBlock() == Blocks.LAVA)) {
            world.setBlockState(chestPos, Blocks.BARREL.getDefaultState());
            if (world.getBlockEntity(chestPos) instanceof BarrelBlockEntity barrel) {
                transferInventory(player, barrel);
                scheduleDespawn(world, chestPos, graveTimer);
            }
        }
    }

    private static void scheduleDespawn(World world, BlockPos pos, int delaySeconds) {
        long despawnTimestamp = Instant.now().getEpochSecond() + delaySeconds;
        graveChests.put(pos, despawnTimestamp);

        saveGraveData(); // Persist the data

        scheduler.schedule(() -> {
            // Ensure this code runs on the main thread, as Minecraft's world operations should be done on the main thread
            Objects.requireNonNull(world.getServer()).execute(() -> {
                if (!world.isClient() && world.getBlockState(pos).isOf(Blocks.BARREL)) {
                    world.removeBlock(pos, false);
                    graveChests.remove(pos);
                    saveGraveData();
                }
            });
        }, delaySeconds, TimeUnit.SECONDS);
    }

    public static void loadGraveData() {
        if (Files.exists(GRAVE_DATA_FILE)) {
            try (var reader = Files.newBufferedReader(GRAVE_DATA_FILE)) {
                Type type = new TypeToken<Map<String, Long>>() {}.getType();
                Map<String, Long> loadedGraves = gson.fromJson(reader, type);

                if (loadedGraves != null) {
                    graveChests.clear();
                    for (Map.Entry<String, Long> entry : loadedGraves.entrySet()) {
                        String[] parts = entry.getKey().split(",");
                        if (parts.length == 3) {
                            try {
                                int x = Integer.parseInt(parts[0]);
                                int y = Integer.parseInt(parts[1]);
                                int z = Integer.parseInt(parts[2]);
                                graveChests.put(new BlockPos(x, y, z), entry.getValue());
                            } catch (NumberFormatException e) {
                                LOGGER.warn("Invalid BlockPos format: {}", entry.getKey(), e);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load grave data file: {}", GRAVE_DATA_FILE, e);
            }
        }
    }

    private static void saveGraveData() {
        try (var writer = Files.newBufferedWriter(GRAVE_DATA_FILE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            Map<String, Long> stringKeyedGraves = graveChests.entrySet().stream()
                    .collect(Collectors.toMap(
                            e -> e.getKey().getX() + "," + e.getKey().getY() + "," + e.getKey().getZ(),
                            Map.Entry::getValue
                    ));
            gson.toJson(stringKeyedGraves, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save grave data file: {}", GRAVE_DATA_FILE, e);
        }
    }

    private static void transferInventory(PlayerEntity player, BarrelBlockEntity barrel) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack itemStack = player.getInventory().getStack(i);
            if (!itemStack.isEmpty()) {
                ItemStack copyStack = itemStack.copy();
                boolean added = false;
                for (int j = 0; j < barrel.size(); j++) {
                    if (barrel.getStack(j).isEmpty()) {
                        barrel.setStack(j, copyStack);
                        added = true;
                        break;
                    }
                }
                if (!added) {
                    player.dropItem(copyStack, false);
                }
                player.getInventory().setStack(i, ItemStack.EMPTY);
            }
        }
    }

    public static void setGraveTimer(int seconds) {
        graveTimer = seconds;
        saveConfig();
    }

    public static void resumeChestTimers(World world) {
        long currentTime = Instant.now().getEpochSecond();
        graveChests.entrySet().removeIf(entry -> {
            long timeLeft = entry.getValue() - currentTime;
            if (timeLeft > 0) {
                scheduleDespawn(world, entry.getKey(), (int) timeLeft);
                return false;
            } else {
                if (world.getBlockState(entry.getKey()).isOf(Blocks.BARREL)) {
                    if (isDestructible) {
                        world.removeBlock(entry.getKey(), false);
                    }
                }
                return true;
            }
        });
        saveGraveData();
    }
}
