package com.deathmod.deathmod;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

public class DeathLocation {
    public static void sendDeathLocation(ServerPlayerEntity player) {
        Vec3d position = player.getPos();
        MutableText message = Text.literal("You died at ").formatted(Formatting.WHITE);
        // X, Y, Z labels with colors and numbers in yellow
        MutableText xPart = Text.literal("X: ").formatted(Formatting.RED)
                .append(Text.literal(Integer.toString((int) position.x)).formatted(Formatting.YELLOW));
        MutableText yPart = Text.literal(" Y: ").formatted(Formatting.GREEN)
                .append(Text.literal(Integer.toString((int) position.y)).formatted(Formatting.YELLOW));
        MutableText zPart = Text.literal(" Z: ").formatted(Formatting.BLUE)
                .append(Text.literal(Integer.toString((int) position.z)).formatted(Formatting.YELLOW));
        // Append colored parts to the main message
        message.append(xPart).append(yPart).append(zPart);
        // Send the message to the player
        player.sendMessage(message, false);
    }
}
