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
    private static final int VAJRA_NOMAD_SLOTS = 24;
    private static final int VAJRA_HORSEMAN_SLOTS = 40;
    private static final int VAJRA_SHIELDMAN_SLOTS = 80;
    private static final int VAJRA_RECRUIT_SLOTS = 64;
    private static final int VAJRA_BOWMAN_SLOTS = 40;
    private static final int VAJRA_CROSSBOWMAN_SLOTS = 32;
    private static final int VAJRA_TOTAL_SLOTS = VAJRA_NOMAD_SLOTS + VAJRA_HORSEMAN_SLOTS
        + VAJRA_SHIELDMAN_SLOTS + VAJRA_RECRUIT_SLOTS + VAJRA_BOWMAN_SLOTS + VAJRA_CROSSBOWMAN_SLOTS;
    private static final double VAJRA_SLOT_SPACING = 1.4;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double SLOT_SPACING = 1.75;
    private static final Map<UUID, FormationState> ACTIVE_FORMATIONS = new HashMap<>();

    public VyuhaWarfare() {
        LOGGER.info("Mahabharata Vyūha Warfare 1.0.0 edge-balanced Vajra blueprint loaded.");
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
                                    IntegerArgumentType.getInteger(context, "radius"), "line", false, context.getSource()))))
                        .then(Commands.literal("vajra")
                            .then(Commands.literal("full")
                                .then(Commands.argument("radius", IntegerArgumentType.integer(4, 32))
                                    .executes(context -> applyFormation(context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "radius"), "vajra", true, context.getSource()))))
                            .then(Commands.literal("partial")
                                .then(Commands.argument("radius", IntegerArgumentType.integer(4, 32))
                                    .executes(context -> applyFormation(context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "radius"), "vajra", false, context.getSource()))))
                            .then(Commands.argument("radius", IntegerArgumentType.integer(4, 32))
                                .executes(context -> applyFormation(context.getSource().getPlayerOrException(),
                                    IntegerArgumentType.getInteger(context, "radius"), "vajra", false, context.getSource())))))
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

        private static int applyFormation(ServerPlayer player, int radius, String formation, boolean fullVajra,
                                          CommandSourceStack source) throws CommandSyntaxException {
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
            int assigned = 0, unsuitable = 0, overflowCount = 0;
            List<UUID> assignedIds = new ArrayList<>();
            if (formation.equals("vajra")) {
                Map<VajraRole, List<Mob>> groups = new java.util.EnumMap<>(VajraRole.class);
                for (VajraRole role : VajraRole.values()) groups.put(role, new ArrayList<>());
                for (Mob recruit : recruits) groups.get(classifyVajraRole(recruit)).add(recruit);
                for (List<Mob> group : groups.values()) {
                    group.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(player)));
                }
                if (fullVajra) {
                    String missing = describeMissingRoster(groups);
                    if (!missing.isEmpty()) {
                        source.sendFailure(Component.literal("Vajravyūha full deployment needs the complete 280-slot roster. Missing: " + missing
                            + ". Use /vyuha form vajra partial " + radius + " for a partial deployment."));
                        return 0;
                    }
                }
                Map<VajraRole, List<Slot>> roleSlots = makeVajraRoleSlots(anchor, forward, right);
                if (fullVajra) {
                    int unsafe = 0;
                    for (List<Slot> roleSlotList : roleSlots.values()) {
                        for (Slot slot : roleSlotList) {
                            if (findSurface(level, slot.x(), slot.z()) == null) unsafe++;
                        }
                    }
                    if (unsafe > 0) {
                        source.sendFailure(Component.literal("Vajravyūha full deployment cancelled: " + unsafe
                            + " of " + VAJRA_TOTAL_SLOTS + " required formation slots have unsafe or obstructed ground. Clear a flat 64×64-block area and try again."));
                        return 0;
                    }
                }
                int formationIndex = 0;
                for (VajraRole role : VajraRole.values()) {
                    List<Mob> group = groups.get(role);
                    List<Slot> slotsForRole = roleSlots.get(role);
                    int regular = Math.min(group.size(), slotsForRole.size());
                    for (int i = 0; i < regular; i++) {
                        Mob recruit = group.get(i);
                        Slot slot = slotsForRole.get(i);
                        if (assignRecruit(level, player, recruit, slot, formationIndex++, assignedIds)) assigned++;
                        else unsuitable++;
                    }
                    if (group.size() > regular) {
                        List<Slot> reserveSlots = makeReserveSlots(group.size() - regular, anchor, forward, right, overflowCount);
                        for (int i = regular; i < group.size(); i++) {
                            Mob recruit = group.get(i);
                            Slot slot = reserveSlots.get(i - regular);
                            if (assignRecruit(level, player, recruit, slot, formationIndex++, assignedIds)) {
                                assigned++;
                                overflowCount++;
                            } else unsuitable++;
                        }
                    }
                }
            } else {
                List<Slot> slots = makeLineSlots(recruits.size(), anchor, right);
                for (int i = 0; i < recruits.size(); i++) {
                    if (assignRecruit(level, player, recruits.get(i), slots.get(i), i, assignedIds)) assigned++;
                    else unsuitable++;
                }
            }
            ACTIVE_FORMATIONS.put(player.getUUID(), new FormationState(level.dimension().location().toString(), assignedIds));
            final int count = assigned, skipped = unsuitable, ignored = ineligible, reserves = overflowCount;
            String formationName = formation.equals("vajra") ? "category-aware Vajravyūha diamond" : "battle line";
            source.sendSuccess(() -> Component.literal("Recruits-native " + formationName + ": assigned " + count
                + " of " + recruits.size() + " commandable recruits."
                + (formation.equals("vajra") ? " Footprint: 64×64 blocks; template capacity: " + VAJRA_TOTAL_SLOTS
                    + " (Nomad 24, Horseman 40, Shieldman 80, Recruit 64, Bowman 40, Crossbowman 32). Mode: "
                    + (fullVajra ? "FULL" : "PARTIAL") + ". Unfilled category slots remain empty; " + reserves
                    + " surplus recruits placed in the rear square reserve." : "")
                + (ignored > 0 ? " Ignored " + ignored + " unowned/non-listening Recruits." : "")
                + (skipped > 0 ? " Skipped " + skipped + " slots without safe ground or usable native controls." : "")), false);
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

        private enum VajraRole { NOMAD, HORSEMAN, SHIELDMAN, RECRUIT, BOWMAN, CROSSBOWMAN }

        private static String describeMissingRoster(Map<VajraRole, List<Mob>> groups) {
            Map<VajraRole, Integer> required = Map.of(
                VajraRole.NOMAD, VAJRA_NOMAD_SLOTS,
                VajraRole.HORSEMAN, VAJRA_HORSEMAN_SLOTS,
                VajraRole.SHIELDMAN, VAJRA_SHIELDMAN_SLOTS,
                VajraRole.RECRUIT, VAJRA_RECRUIT_SLOTS,
                VajraRole.BOWMAN, VAJRA_BOWMAN_SLOTS,
                VajraRole.CROSSBOWMAN, VAJRA_CROSSBOWMAN_SLOTS);
            List<String> missing = new ArrayList<>();
            for (VajraRole role : VajraRole.values()) {
                int have = groups.get(role).size();
                int need = required.get(role);
                if (have < need) missing.add(roleLabel(role) + " " + have + "/" + need + " (need " + (need - have) + " more)");
            }
            return String.join(", ", missing);
        }

        private static String roleLabel(VajraRole role) {
            return switch (role) {
                case NOMAD -> "Nomads";
                case HORSEMAN -> "Horsemen";
                case SHIELDMAN -> "Shieldmen";
                case RECRUIT -> "Recruits";
                case BOWMAN -> "Bowmen";
                case CROSSBOWMAN -> "Crossbowmen";
            };
        }

        private static Map<VajraRole, List<Slot>> makeVajraRoleSlots(Vec3 anchor, Vec3 forward, Vec3 right) {
            Map<VajraRole, List<Slot>> result = new java.util.EnumMap<>(VajraRole.class);
            for (VajraRole role : VajraRole.values()) result.put(role, new ArrayList<>());

            // Fixed Vajra 2.0 blueprint: one dedicated diamond perimeter per role.
            // The entire template fits inside the 64x64 footprint (outer radius 22 * 1.4 = 30.8 blocks).
            // Outer-to-inner infantry rings remain separate; cavalry occupy a wider pair of wings.
            addSelectedDiamondRing(result.get(VajraRole.SHIELDMAN), anchor, forward, right, 20,
                VAJRA_SHIELDMAN_SLOTS, false);
            addSelectedDiamondRing(result.get(VajraRole.HORSEMAN), anchor, forward, right, 22,
                VAJRA_HORSEMAN_SLOTS, true);
            addSelectedDiamondRing(result.get(VajraRole.RECRUIT), anchor, forward, right, 16,
                VAJRA_RECRUIT_SLOTS, false);
            addSelectedDiamondRing(result.get(VajraRole.BOWMAN), anchor, forward, right, 10,
                VAJRA_BOWMAN_SLOTS, false);
            addSelectedDiamondRing(result.get(VajraRole.CROSSBOWMAN), anchor, forward, right, 8,
                VAJRA_CROSSBOWMAN_SLOTS, false);
            addSelectedDiamondRing(result.get(VajraRole.NOMAD), anchor, forward, right, 6,
                VAJRA_NOMAD_SLOTS, false);

            return result;
        }

        private static void addSelectedDiamondRing(List<Slot> out, Vec3 anchor, Vec3 forward, Vec3 right,
                                                   int radius, int capacity, boolean wingsOnly) {
            if (wingsOnly) {
                // Horsemen are split evenly over the four diamond slopes: 10 per slope,
                // with endpoints excluded so the sharp front/rear points remain clear.
                // This is intentionally independent of the available troop count.
                int perSlope = capacity / 4;
                double[][] vertices = new double[][] {
                    {-radius, 0}, {0, radius}, {radius, 0}, {0, -radius}, {-radius, 0}
                };
                for (int edge = 0; edge < 4; edge++) {
                    double rowA = vertices[edge][0];
                    double colA = vertices[edge][1];
                    double rowB = vertices[edge + 1][0];
                    double colB = vertices[edge + 1][1];
                    for (int i = 0; i < perSlope; i++) {
                        double t = (i + 1.0) / (perSlope + 1.0);
                        double row = rowA + (rowB - rowA) * t;
                        double col = colA + (colB - colA) * t;
                        addGridPoint(out, anchor, forward, right, row, col);
                    }
                }
                return;
            }

            // Exact diamond perimeter: four edges, each contributes 'radius' points.
            // Every corner is included once, with no duplicate or cross-like coordinates.
            List<int[]> candidates = new ArrayList<>(radius * 4);
            for (int step = 0; step < radius; step++) {
                candidates.add(new int[]{-radius + step, step});       // front to right
                candidates.add(new int[]{step, radius - step});        // right to rear
                candidates.add(new int[]{radius - step, -step});       // rear to left
                candidates.add(new int[]{-step, -radius + step});      // left to front
            }
            int take = Math.min(capacity, candidates.size());
            for (int i = 0; i < take; i++) {
                int index = (int) Math.floor(i * candidates.size() / (double) take);
                int[] point = candidates.get(index);
                addGridPoint(out, anchor, forward, right, point[0], point[1]);
            }
        }

        private static void addGridPoint(List<Slot> out, Vec3 anchor, Vec3 forward, Vec3 right, double row, double col) {
            double depth = row * VAJRA_SLOT_SPACING;
            double lateral = col * VAJRA_SLOT_SPACING;
            out.add(new Slot(new UUID(0L, 0L), anchor.x - forward.x * depth + right.x * lateral,
                anchor.y, anchor.z - forward.z * depth + right.z * lateral));
        }

        private static void trim(List<Slot> slots, int capacity) {
            if (slots.size() > capacity) slots.subList(capacity, slots.size()).clear();
        }

        private static List<Slot> makeReserveSlots(int count, Vec3 anchor, Vec3 forward, Vec3 right, int offset) {
            List<Slot> slots = new ArrayList<>();
            int width = 8;
            Vec3 reserveAnchor = anchor.add(forward.scale(-38.0));
            for (int i = 0; i < count; i++) {
                int reserveIndex = offset + i;
                int row = reserveIndex / width;
                int col = reserveIndex % width;
                double lateral = (col - (width - 1) / 2.0) * VAJRA_SLOT_SPACING;
                double depth = row * VAJRA_SLOT_SPACING;
                slots.add(new Slot(new UUID(0L, 0L), reserveAnchor.x - forward.x * depth + right.x * lateral,
                    reserveAnchor.y, reserveAnchor.z - forward.z * depth + right.z * lateral));
            }
            return slots;
        }

        private static boolean assignRecruit(ServerLevel level, ServerPlayer player, Mob recruit, Slot slot,
                                             int formationIndex, List<UUID> assignedIds) {
            BlockPos surface = findSurface(level, slot.x(), slot.z());
            if (surface == null) return false;
            Vec3 target = new Vec3(surface.getX() + 0.5, surface.getY(), surface.getZ() + 0.5);
            try {
                invokeBoolean(recruit, "setShouldRest", false);
                invokeRequired(recruit, "setHoldPos", new Class<?>[]{Vec3.class}, target);
                invokeRequired(recruit, "setFollowState", new Class<?>[]{int.class}, 3);
                setPublicFieldIfPresent(recruit, "formationPos", formationIndex);
                setPublicFieldIfPresent(recruit, "isInFormation", true);
                setPublicFieldIfPresent(recruit, "holdFormation", true);
                setPublicFieldIfPresent(recruit, "ownerRot", player.getYRot());
                recruit.getNavigation().stop();
                assignedIds.add(recruit.getUUID());
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.x, target.y + 0.7, target.z,
                    1, 0.05, 0.05, 0.05, 0.0);
                return true;
            } catch (ReflectiveOperationException ex) {
                LOGGER.warn("Could not apply Recruits-native formation to {}: {}", recruit.getType(), ex.toString());
                return false;
            }
        }

        private static VajraRole classifyVajraRole(Mob mob) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath().toLowerCase(java.util.Locale.ROOT);
            String equipment = (mob.getMainHandItem().getItem().toString() + " " + mob.getOffhandItem().getItem().toString()).toLowerCase(java.util.Locale.ROOT);
            if (id.contains("nomad")) return VajraRole.NOMAD;
            if (id.contains("horseman") || id.contains("cavalry") || id.contains("mounted")) return VajraRole.HORSEMAN;
            if (id.contains("crossbow") || equipment.contains("crossbow")) return VajraRole.CROSSBOWMAN;
            if (id.contains("bowman") || id.contains("archer") || equipment.contains("bow")) return VajraRole.BOWMAN;
            if (id.contains("shieldman") || id.contains("shield") || mob.getOffhandItem().getItem() instanceof net.minecraft.world.item.ShieldItem) return VajraRole.SHIELDMAN;
            return VajraRole.RECRUIT;
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
