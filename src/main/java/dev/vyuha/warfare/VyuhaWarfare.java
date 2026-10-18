package dev.vyuha.warfare;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
    private static final double SLOT_SPACING = 1.75;
    private static final Map<UUID, FormationState> ACTIVE_FORMATIONS = new HashMap<>();

    public VyuhaWarfare() {
        LOGGER.info("Mahabharata Vyūha Warfare 0.5.0 Recruits-native formation integration loaded.");
    }

    private record Slot(UUID recruitId, double x, double y, double z) {}
    private static final class FormationState {
        final String dimension;
        final List<UUID> recruitIds;
        FormationState(String dimension, List<UUID> recruitIds) {
            this.dimension = dimension;
            this.recruitIds = recruitIds;
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
                            .then(Commands.argument("count", IntegerArgumentType.integer(2, 40))
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
                                    context.getSource().sendSuccess(() -> Component.literal(
                                        "Vyūha preview: displayed " + count + " battle-line slots."), false);
                                    return count;
                                }))))
                    .then(Commands.literal("form")
                        .then(Commands.literal("line")
                            .then(Commands.argument("radius", IntegerArgumentType.integer(4, 32))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ServerLevel level = player.serverLevel();
                                    int radius = IntegerArgumentType.getInteger(context, "radius");
                                    if (!ModList.get().isLoaded("recruits")) {
                                        context.getSource().sendFailure(Component.literal(
                                            "Villager Recruits is not loaded. Install Recruits 1.20.1 Forge to use native formation integration."));
                                        return 0;
                                    }
                                    AABB searchBox = player.getBoundingBox().inflate(radius);
                                    List<Mob> nearby = level.getEntitiesOfClass(Mob.class, searchBox,
                                        mob -> mob.isAlive() && isRecruitsMob(mob));
                                    nearby.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(player)));

                                    List<Mob> recruits = new ArrayList<>();
                                    int ineligible = 0;
                                    for (Mob mob : nearby) {
                                        Boolean controlled = canPlayerCommand(mob, player);
                                        if (Boolean.TRUE.equals(controlled)) recruits.add(mob);
                                        else ineligible++;
                                    }
                                    if (recruits.isEmpty()) {
                                        context.getSource().sendFailure(Component.literal(
                                            "No commandable Recruits found within " + radius + " blocks. Recruits must be alive, owned by you, and listening for commands."));
                                        return 0;
                                    }

                                    Vec3 forward = horizontalForward(player);
                                    Vec3 right = new Vec3(forward.z, 0, -forward.x);
                                    Vec3 anchor = player.position().add(forward.scale(5.0));
                                    List<Slot> slots = new ArrayList<>();
                                    int unsuitable = 0;
                                    for (int i = 0; i < recruits.size(); i++) {
                                        Mob recruit = recruits.get(i);
                                        double offset = (i - (recruits.size() - 1) / 2.0) * SLOT_SPACING;
                                        double rawX = anchor.x + right.x * offset;
                                        double rawZ = anchor.z + right.z * offset;
                                        BlockPos surface = findSurface(level, rawX, rawZ);
                                        if (surface == null) { unsuitable++; continue; }
                                        slots.add(new Slot(recruit.getUUID(), surface.getX() + 0.5,
                                            surface.getY(), surface.getZ() + 0.5));
                                    }
                                    if (slots.isEmpty()) {
                                        context.getSource().sendFailure(Component.literal(
                                            "No safe ground slots found. Try a flatter, more open area."));
                                        return 0;
                                    }

                                    int assigned = 0;
                                    List<UUID> assignedIds = new ArrayList<>();
                                    for (Slot slot : slots) {
                                        Entity entity = level.getEntity(slot.recruitId());
                                        if (!(entity instanceof Mob recruit) || !recruit.isAlive()) continue;
                                        Vec3 target = new Vec3(slot.x(), slot.y(), slot.z());
                                        try {
                                            // Use Recruits' own hold-position goal/state instead of competing with it
                                            // through vanilla navigation. All integration points are reflective so
                                            // this addon does not bundle or compile against Recruits implementation code.
                                            if (invokeBoolean(recruit, "setShouldRest", false) == false) {
                                                // Missing optional method is tolerated; setFollowState below is required.
                                            }
                                            invokeRequired(recruit, "setHoldPos", new Class<?>[]{Vec3.class}, target);
                                            invokeRequired(recruit, "setFollowState", new Class<?>[]{int.class}, 3);
                                            setPublicFieldIfPresent(recruit, "formationPos", assigned);
                                            setPublicFieldIfPresent(recruit, "isInFormation", true);
                                            setPublicFieldIfPresent(recruit, "holdFormation", true);
                                            setPublicFieldIfPresent(recruit, "ownerRot", player.getYRot());
                                            recruit.getNavigation().stop();
                                            assignedIds.add(recruit.getUUID());
                                            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                                target.x, target.y + 0.7, target.z, 1, 0.05, 0.05, 0.05, 0.0);
                                            assigned++;
                                        } catch (ReflectiveOperationException ex) {
                                            LOGGER.warn("Could not apply Recruits-native formation to {}: {}",
                                                recruit.getType().toString(), ex.toString());
                                        }
                                    }
                                    ACTIVE_FORMATIONS.put(player.getUUID(), new FormationState(
                                        level.dimension().location().toString(), assignedIds));
                                    final int ineligibleCount = ineligible;
                                    final int unsuitableCount = unsuitable;
                                    final int resultCount = assigned;
                                    context.getSource().sendSuccess(() -> Component.literal(
                                        "Recruits-native battle line assigned to " + resultCount + "/" + recruits.size() +
                                        " of your Recruits. Their built-in hold-position goal will manage movement and settling." +
                                        (ineligibleCount > 0 ? " Ignored " + ineligibleCount + " unowned/non-listening Recruits." : "") +
                                        (unsuitableCount > 0 ? " Skipped " + unsuitableCount + " slots without safe ground." : "")), false);
                                    return assigned;
                                }))))
                    .then(Commands.literal("release").executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        FormationState state = ACTIVE_FORMATIONS.remove(player.getUUID());
                        if (state == null || !state.dimension.equals(player.serverLevel().dimension().location().toString())) {
                            context.getSource().sendSuccess(() -> Component.literal(
                                "No tracked Vyūha line found for this player in this dimension."), false);
                            return 0;
                        }
                        int released = 0;
                        for (UUID id : state.recruitIds) {
                            Entity entity = player.serverLevel().getEntity(id);
                            if (!(entity instanceof Mob recruit) || !isRecruitsMob(recruit)) continue;
                            try {
                                // Return each soldier to Recruits' native stationary Hold Position state at its current position.
                                invokeRequired(recruit, "setFollowState", new Class<?>[]{int.class}, 2);
                                setPublicFieldIfPresent(recruit, "isInFormation", false);
                                setPublicFieldIfPresent(recruit, "holdFormation", false);
                                released++;
                            } catch (ReflectiveOperationException ex) {
                                LOGGER.warn("Could not release Recruits formation for {}: {}", id, ex.toString());
                            }
                        }
                        final int releasedCount = released;
                        context.getSource().sendSuccess(() -> Component.literal(
                            "Vyūha line released. " + releasedCount + " soldiers switched to Recruits' native Hold Position state."), false);
                        return released;
                    }))
            );
        }

        private static Boolean canPlayerCommand(Mob mob, ServerPlayer player) {
            try {
                Method eligible = findMethod(mob.getClass(), "isEffectedByCommand", UUID.class);
                if (eligible != null) {
                    Object value = eligible.invoke(mob, player.getUUID());
                    return value instanceof Boolean b ? b : Boolean.FALSE;
                }
                // Conservative fallback: never command recruits whose owner cannot be verified.
                Method owner = findMethod(mob.getClass(), "getOwnerUUID");
                Method owned = findMethod(mob.getClass(), "isOwned");
                if (owner == null || owned == null) return false;
                Object ownerId = owner.invoke(mob);
                Object isOwned = owned.invoke(mob);
                Method listen = findMethod(mob.getClass(), "getListen");
                boolean listening = listen == null || Boolean.TRUE.equals(listen.invoke(mob));
                return Boolean.TRUE.equals(isOwned) && player.getUUID().equals(ownerId) && listening;
            } catch (ReflectiveOperationException ex) {
                LOGGER.debug("Could not verify Recruits ownership for {}: {}", mob.getType(), ex.toString());
                return false;
            }
        }

        private static boolean invokeBoolean(Mob entity, String name, boolean argument) {
            try {
                Method method = findMethod(entity.getClass(), name, boolean.class);
                if (method == null) return false;
                method.invoke(entity, argument);
                return true;
            } catch (ReflectiveOperationException ex) {
                LOGGER.debug("Optional Recruits method {} unavailable: {}", name, ex.toString());
                return false;
            }
        }

        private static void invokeRequired(Mob entity, String name, Class<?>[] parameterTypes, Object... args)
            throws ReflectiveOperationException {
            Method method = findMethod(entity.getClass(), name, parameterTypes);
            if (method == null) throw new NoSuchMethodException(name);
            method.invoke(entity, args);
        }

        private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
            Class<?> current = type;
            while (current != null) {
                try {
                    Method method = current.getMethod(name, parameterTypes);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) { current = current.getSuperclass(); }
            }
            return null;
        }

        private static void setPublicFieldIfPresent(Object target, String fieldName, Object value) {
            try {
                Field field = target.getClass().getField(fieldName);
                field.set(target, value);
            } catch (ReflectiveOperationException | IllegalArgumentException ex) {
                LOGGER.debug("Optional Recruits field {} unavailable on {}: {}", fieldName,
                    target.getClass().getName(), ex.toString());
            }
        }

        private static BlockPos findSurface(ServerLevel level, double x, double z) {
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
