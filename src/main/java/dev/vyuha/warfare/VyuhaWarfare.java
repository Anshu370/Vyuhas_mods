package dev.vyuha.warfare;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.List;

@Mod(VyuhaWarfare.MOD_ID)
public class VyuhaWarfare {
    public static final String MOD_ID = "vyuha_warfare";
    private static final Logger LOGGER = LogUtils.getLogger();

    public VyuhaWarfare() {
        LOGGER.info("Mahabharata Vyūha Warfare 0.3.0 loaded.");
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeEvents {
        @SubscribeEvent
        public static void onRegisterCommands(RegisterCommandsEvent event) {
            event.getDispatcher().register(
                Commands.literal("vyuha")
                    .then(Commands.literal("preview")
                        .then(Commands.literal("line")
                            .then(Commands.argument("count", IntegerArgumentType.integer(2, 20))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ServerLevel level = player.serverLevel();
                                    int count = IntegerArgumentType.getInteger(context, "count");
                                    Vec3 forward = horizontalForward(player);
                                    Vec3 right = new Vec3(forward.z, 0, -forward.x);
                                    Vec3 center = player.position().add(0, 1.0, 0);
                                    for (int i = 0; i < count; i++) {
                                        double offset = (i - (count - 1) / 2.0) * 1.5;
                                        Vec3 point = center.add(right.scale(offset));
                                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                            point.x, point.y, point.z, 1, 0.08, 0.08, 0.08, 0.0);
                                    }
                                    context.getSource().sendSuccess(
                                        () -> net.minecraft.network.chat.Component.literal(
                                            "Vyūha preview: displayed " + count + " line positions."),
                                        false);
                                    return count;
                                }))))
                    .then(Commands.literal("form")
                        .then(Commands.literal("line")
                            .then(Commands.argument("radius", IntegerArgumentType.integer(4, 32))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ServerLevel level = player.serverLevel();
                                    int radius = IntegerArgumentType.getInteger(context, "radius");
                                    AABB searchBox = player.getBoundingBox().inflate(radius);
                                    List<Mob> recruits = level.getEntitiesOfClass(Mob.class, searchBox,
                                        mob -> "recruits".equals(
                                            BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getNamespace())
                                            && mob.isAlive());
                                    recruits.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(player)));
                                    if (recruits.isEmpty()) {
                                        context.getSource().sendFailure(
                                            net.minecraft.network.chat.Component.literal(
                                                "No Villager Recruits mobs found within " + radius
                                                + " blocks. Check that Recruits is installed and recruits are nearby."));
                                        return 0;
                                    }
                                    Vec3 forward = horizontalForward(player);
                                    Vec3 right = new Vec3(forward.z, 0, -forward.x);
                                    Vec3 center = player.position();
                                    int moved = 0;
                                    for (int i = 0; i < recruits.size(); i++) {
                                        Mob recruit = recruits.get(i);
                                        double offset = (i - (recruits.size() - 1) / 2.0) * 1.5;
                                        Vec3 target = center.add(right.scale(offset)).add(forward.scale(4.0));
                                        boolean accepted = recruit.getNavigation().moveTo(
                                            target.x, target.y, target.z, 1.0);
                                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                            target.x, target.y + 0.6, target.z, 1,
                                            0.08, 0.08, 0.08, 0.0);
                                        if (accepted) moved++;
                                    }
                                    final int total = recruits.size();
                                    final int ordered = moved;
                                    context.getSource().sendSuccess(
                                        () -> net.minecraft.network.chat.Component.literal(
                                            "Mixed line order issued to " + ordered + " of " + total
                                            + " Recruits. They may need a clear path to reach their slots."),
                                        false);
                                    return moved;
                                }))))
            );
        }

        private static Vec3 horizontalForward(ServerPlayer player) {
            Vec3 look = player.getLookAngle();
            Vec3 horizontal = new Vec3(look.x, 0, look.z);
            if (horizontal.lengthSqr() < 0.0001) return new Vec3(0, 0, 1);
            return horizontal.normalize();
        }
    }
}
