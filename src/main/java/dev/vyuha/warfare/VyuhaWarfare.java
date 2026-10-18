package dev.vyuha.warfare;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
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
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
        LOGGER.info("Mahabharata Vyūha Warfare 0.6.0 Vajravyuha formation integration loaded.");
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
                                .executes(context -> applyFormation(context.getSource().getPlayerOrException(),
                                    IntegerArgumentType.getInteger(context, "radius"), "line", context.getSource()))))
                        .then(Commands.literal("vajra")
                            .then(Commands.argument("radius", IntegerArgumentType.integer(4, 32))
                                .executes(context -> applyFormation(context.getSource().getPlayerOrException(),
                                    IntegerArgumentType.getInteger(context, "radius"), "vajra", context.getSource())))))
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

        private static int applyFormation(ServerPlayer player, int radius, String formation, CommandSourceStack source)
            throws CommandSyntaxException {
            ServerLevel level = player.serverLevel();
            if (!ModList.get().isLoaded("recruits")) {
                source.sendFailure(Component.literal("Villager Recruits is not loaded. Install Recruits 1.20.1 Forge to use native formation integration."));
                return 0;
            }
            AABB searchBox = player.getBoundingBox().inflate(radius);
            List<Mob> nearby = level.getEntitiesOfClass(Mob.class, searchBox,
                mob -> mob.isAlive() && isRecruitsMob(mob));
            nearby.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(player)));
            List<Mob> recruits = new ArrayList<>();
            int ineligible = 0;
            for (Mob mob : nearby) {
                if (Boolean.TRUE.equals(canPlayerCommand(mob, player))) recruits.add(mob);
                else ineligible++;
            }
            if (recruits.isEmpty()) {
                source.sendFailure(Component.literal("No commandable Recruits found within " + radius + " blocks. Recruits must be alive, owned by you, and listening for commands."));
                return 0;
            }

            Vec3 forward = horizontalForward(player);
            Vec3 right = new Vec3(forward.z, 0, -forward.x);
            Vec3 anchor = player.position().add(forward.scale(5.0));
            List<Slot> slots = formation.equals("vajra")
                ? makeVajraSlots(recruits.size(), anchor, forward, right)
                : makeLineSlots(recruits.size(), anchor, right);

            if (formation.equals("vajra")) {
                // Arrange role groups for the Vajra's visual layers. Registry IDs vary by Recruits version,
                // so classification uses conservative name fragments and falls back to ordinary infantry.
                recruits.sort(Comparator.comparingInt(ForgeEvents::vajraRoleOrder)
                    .thenComparingDouble(mob -> mob.distanceToSqr(player)));
            }

            int assigned = 0, unsuitable = 0;
            List<UUID> assignedIds = new ArrayList<>();
            int n = Math.min(recruits.size(), slots.size());
            for (int i = 0; i < n; i++) {
                Mob recruit = recruits.get(i);
                Slot slot = slots.get(i);
                BlockPos surface = findSurface(level, slot.x(), slot.z());
                if (surface == null) { unsuitable++; continue; }
                Vec3 target = new Vec3(surface.getX() + 0.5, surface.getY(), surface.getZ() + 0.5);
                try {
                    invokeBoolean(recruit, "setShouldRest", false);
                    invokeRequired(recruit, "setHoldPos", new Class<?>[]{Vec3.class}, target);
                    invokeRequired(recruit, "setFollowState", new Class<?>[]{int.class}, 3);
                    setPublicFieldIfPresent(recruit, "formationPos", assigned);
                    setPublicFieldIfPresent(recruit, "isInFormation", true);
                    setPublicFieldIfPresent(recruit, "holdFormation", true);
                    setPublicFieldIfPresent(recruit, "ownerRot", player.getYRot());
                    recruit.getNavigation().stop();
                    assignedIds.add(recruit.getUUID());
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.x, target.y + 0.7, target.z,
                        1, 0.05, 0.05, 0.05, 0.0);
                    assigned++;
                } catch (ReflectiveOperationException ex) {
                    LOGGER.warn("Could not apply Recruits-native {} formation to {}: {}", formation,
                        recruit.getType().toString(), ex.toString());
                }
            }
            ACTIVE_FORMATIONS.put(player.getUUID(), new FormationState(level.dimension().location().toString(), assignedIds));
            final int count = assigned, skipped = unsuitable, ignored = ineligible;
            String formationName = formation.equals("vajra") ? "Vajravyūha diamond" : "battle line";
            source.sendSuccess(() -> Component.literal("Recruits-native " + formationName + " assigned to " + count + "/" + recruits.size()
                + " of your Recruits. Their built-in hold-position goal manages movement and settling."
                + (formation.equals("vajra") ? " Ranged units are ordered toward the inner/rear ranks; mobile units toward outer ranks where their registry names are recognizable." : "")
                + (ignored > 0 ? " Ignored " + ignored + " unowned/non-listening Recruits." : "")
                + (skipped > 0 ? " Skipped " + skipped + " slots without safe ground." : "")), false);
            return assigned;
        }

        private static List<Slot> makeLineSlots(int count, Vec3 anchor, Vec3 right) {
            List<Slot> slots = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                double offset = (i - (count - 1) / 2.0) * SLOT_SPACING;
                slots.add(new Slot(new UUID(0L, 0L), anchor.x + right.x * offset, anchor.y, anchor.z + right.z * offset));
            }
            return slots;
        }

        private static List<Slot> makeVajraSlots(int count, Vec3 anchor, Vec3 forward, Vec3 right) {
            // Diamond lattice: narrow pointed front and rear, widest ranks through the middle.
            List<Slot> slots = new ArrayList<>();
            int halfDepth = Math.max(2, (int)Math.ceil(Math.sqrt(count)));
            List<double[]> points = new ArrayList<>();
            for (int row = -halfDepth; row <= halfDepth; row++) {
                int width = halfDepth - Math.abs(row);
                for (int col = -width; col <= width; col++) {
                    points.add(new double[]{row, col});
                }
            }
            // Front-to-rear rank order; within each rank, alternate left/right to keep the shape balanced.
            points.sort((a, b) -> {
                int rowCompare = Double.compare(a[0], b[0]);
                if (rowCompare != 0) return rowCompare;
                int absCompare = Double.compare(Math.abs(a[1]), Math.abs(b[1]));
                if (absCompare != 0) return absCompare;
                return Double.compare(a[1], b[1]);
            });
            for (int i = 0; i < Math.min(count, points.size()); i++) {
                double row = points.get(i)[0], col = points.get(i)[1];
                double depth = row * SLOT_SPACING * 1.25;
                double lateral = col * SLOT_SPACING;
                double x = anchor.x - forward.x * depth + right.x * lateral;
                double z = anchor.z - forward.z * depth + right.z * lateral;
                slots.add(new Slot(new UUID(0L, 0L), x, anchor.y, z));
            }
            return slots;
        }

        private static int vajraRoleOrder(Mob mob) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath().toLowerCase(java.util.Locale.ROOT);
            if (id.contains("horseman") || id.contains("cavalry") || id.contains("nomad")) return 0;
            if (id.contains("crossbow") || id.contains("bowman") || id.contains("archer")) return 1;
            if (id.contains("shieldman") || id.contains("shield")) return 2;
            return 3;
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
