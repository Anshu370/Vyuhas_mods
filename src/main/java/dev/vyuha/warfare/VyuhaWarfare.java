package dev.vyuha.warfare;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod(VyuhaWarfare.MOD_ID)
public class VyuhaWarfare {
    public static final String MOD_ID = "vyuha_warfare";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, FormationState> ACTIVE_FORMATIONS = new HashMap<>();
    private static final double SLOT_SPACING = 1.65;
    private static final double ARRIVAL_DISTANCE_SQR = 2.25;
    private static final int REORDER_INTERVAL_TICKS = 20;

    public VyuhaWarfare() {
        LOGGER.info("Mahabharata Vyūha Warfare 0.4.0 Formation Controller loaded.");
    }

    private record Slot(UUID recruitId, double x, double y, double z) {}
    private static final class FormationState {
        final UUID ownerId;
        String dimension;
        final List<Slot> slots;
        long lastOrderTick;
        FormationState(UUID ownerId, String dimension, List<Slot> slots, long lastOrderTick) {
            this.ownerId = ownerId;
            this.dimension = dimension;
            this.slots = slots;
            this.lastOrderTick = lastOrderTick;
        }
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
                                        double offset = (i - (count - 1) / 2.0) * SLOT_SPACING;
                                        Vec3 point = center.add(right.scale(offset));
                                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                            point.x, point.y, point.z, 1, 0.08, 0.08, 0.08, 0.0);
                                    }
                                    context.getSource().sendSuccess(
                                        () -> Component.literal("Vyūha preview: displayed " + count + " line positions."), false);
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
                                        mob -> isRecruitsMob(mob) && mob.isAlive());
                                    recruits.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(player)));
                                    if (recruits.isEmpty()) {
                                        context.getSource().sendFailure(Component.literal(
                                            "No Villager Recruits mobs found within " + radius + " blocks. Check that Recruits is installed and recruits are nearby."));
                                        return 0;
                                    }
                                    Vec3 forward = horizontalForward(player);
                                    Vec3 right = new Vec3(forward.z, 0, -forward.x);
                                    Vec3 anchor = player.position().add(forward.scale(5.0));
                                    List<Slot> slots = new ArrayList<>();
                                    int skipped = 0;
                                    for (int i = 0; i < recruits.size(); i++) {
                                        Mob recruit = recruits.get(i);
                                        double offset = (i - (recruits.size() - 1) / 2.0) * SLOT_SPACING;
                                        double rawX = anchor.x + right.x * offset;
                                        double rawZ = anchor.z + right.z * offset;
                                        BlockPos surface = findSurface(level, rawX, rawZ, recruit);
                                        if (surface == null) {
                                            skipped++;
                                            continue;
                                        }
                                        slots.add(new Slot(recruit.getUUID(), surface.getX() + 0.5,
                                            surface.getY(), surface.getZ() + 0.5));
                                    }
                                    if (slots.isEmpty()) {
                                        context.getSource().sendFailure(Component.literal(
                                            "Could not find walkable ground for formation slots. Try flatter terrain."));
                                        return 0;
                                    }
                                    ACTIVE_FORMATIONS.put(player.getUUID(), new FormationState(
                                        player.getUUID(), level.dimension().location().toString(), slots, level.getGameTime()));
                                    FormationState state = ACTIVE_FORMATIONS.get(player.getUUID());
                                    issueOrders(level, state, true);
                                    final int assigned = slots.size();
                                    final int total = recruits.size();
                                    final int noGround = skipped;
                                    context.getSource().sendSuccess(() -> Component.literal(
                                        "Battle line controller active: " + assigned + "/" + total +
                                        " Recruits assigned unique ground-level slots. They will be guided back into position; use /vyuha release to cancel." +
                                        (noGround > 0 ? " Skipped " + noGround + " slots with unsuitable ground." : "")), false);
                                    return assigned;
                                }))))
                    .then(Commands.literal("release").executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        FormationState removed = ACTIVE_FORMATIONS.remove(player.getUUID());
                        if (removed == null) {
                            context.getSource().sendSuccess(() -> Component.literal("You have no active Vyūha formation."), false);
                        } else {
                            context.getSource().sendSuccess(() -> Component.literal(
                                "Formation controller released. Soldiers are free to follow their normal Recruits AI."), false);
                        }
                        return removed == null ? 0 : 1;
                    }))
            );
        }

        @SubscribeEvent
        public static void onLevelTick(TickEvent.LevelTickEvent event) {
            if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
            long now = level.getGameTime();
            Iterator<Map.Entry<UUID, FormationState>> iterator = ACTIVE_FORMATIONS.entrySet().iterator();
            while (iterator.hasNext()) {
                FormationState state = iterator.next().getValue();
                if (!state.dimension.equals(level.dimension().location().toString())) continue;
                if (now - state.lastOrderTick < REORDER_INTERVAL_TICKS) continue;
                issueOrders(level, state, false);
                state.lastOrderTick = now;
                if (state.slots.isEmpty()) iterator.remove();
            }
        }

        private static void issueOrders(ServerLevel level, FormationState state, boolean initial) {
            Iterator<Slot> iterator = state.slots.iterator();
            while (iterator.hasNext()) {
                Slot slot = iterator.next();
                Entity entity = level.getEntity(slot.recruitId());
                if (!(entity instanceof Mob mob) || !mob.isAlive() || !isRecruitsMob(mob)) {
                    iterator.remove();
                    continue;
                }
                Vec3 target = new Vec3(slot.x(), slot.y(), slot.z());
                if (mob.position().distanceToSqr(target) <= ARRIVAL_DISTANCE_SQR) {
                    if (initial) level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        target.x, target.y + 0.7, target.z, 1, 0.08, 0.08, 0.08, 0.0);
                    continue;
                }
                // Do not reset an active path every tick; re-path only at the controller interval.
                if (mob.getNavigation().isDone() || initial) {
                    mob.getNavigation().moveTo(target.x, target.y, target.z, 1.0);
                }
            }
        }

        private static BlockPos findSurface(ServerLevel level, double x, double z, Mob mob) {
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            BlockPos feet = new BlockPos(bx, surfaceY, bz);
            if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) return null;
            if (!level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) return null;
            if (!level.getBlockState(feet.below()).blocksMotion()) return null;
            if (surfaceY < level.getMinBuildHeight() || surfaceY >= level.getMaxBuildHeight() - 2) return null;
            return feet;
        }

        private static boolean isRecruitsMob(Mob mob) {
            return "recruits".equals(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getNamespace());
        }

        private static Vec3 horizontalForward(ServerPlayer player) {
            Vec3 look = player.getLookAngle();
            Vec3 horizontal = new Vec3(look.x, 0, look.z);
            if (horizontal.lengthSqr() < 0.0001) return new Vec3(0, 0, 1);
            return horizontal.normalize();
        }
    }
}
