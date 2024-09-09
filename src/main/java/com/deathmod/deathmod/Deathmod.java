package com.deathmod.deathmod;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

public class Deathmod implements ModInitializer {

    @Override
    public void onInitialize() {
        // Load saved chests on startup
        SpawnChest.init();

        ServerWorldEvents.LOAD.register((server, world) -> SpawnChest.resumeChestTimers(world));

        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, damageAmount) -> {
            if (entity instanceof ServerPlayerEntity player && !player.getWorld().isClient()) {
                SpawnChest.spawnAtDeathLocation(player);
                DeathLocation.sendDeathLocation(player);
            }
            return true;
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!world.isClient() && SpawnChest.graveChests.containsKey(pos) && !SpawnChest.isDestructible) {
                return ActionResult.FAIL;  // Make the chest indestructible if the flag is false
            }
            return ActionResult.PASS;
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, environment, dedicated) -> {
            dispatcher.register(CommandManager.literal("gravetimer")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.argument("seconds", IntegerArgumentType.integer(1))
                            .executes(context -> {
                                int seconds = IntegerArgumentType.getInteger(context, "seconds");
                                SpawnChest.setGraveTimer(seconds);
                                context.getSource().sendFeedback(
                                        () -> Text.literal("Grave timer set to " + seconds + " seconds."),
                                        true
                                );
                                return 1; // Command successful
                            })
                    )
            );

            dispatcher.register(CommandManager.literal("gravedestroy")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.argument("enabled", StringArgumentType.word())
                            .executes(context -> {
                                String enabled = StringArgumentType.getString(context, "enabled");
                                boolean isEnabled = "on".equalsIgnoreCase(enabled);
                                SpawnChest.isDestructible = isEnabled;
                                SpawnChest.saveConfig();
                                context.getSource().sendFeedback(
                                        () -> Text.literal("Grave destructibility set to " + (isEnabled ? "enabled" : "disabled") + "."),
                                        false
                                );
                                return 1; // Command successful
                            })
                    )
            );
        });
    }
}
