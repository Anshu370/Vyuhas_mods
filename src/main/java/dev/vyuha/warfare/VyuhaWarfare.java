package dev.vyuha.warfare;

import com.mojang.logging.LogUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import org.slf4j.Logger;

@Mod(VyuhaWarfare.MOD_ID)
public class VyuhaWarfare {
    public static final String MOD_ID = "vyuha_warfare";
    private static final Logger LOGGER = LogUtils.getLogger();

    public VyuhaWarfare() {
        LOGGER.info("Mahabharata Vyūha Warfare prototype loaded.");
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

                                    Vec3 look = player.getLookAngle();
                                    Vec3 horizontal = new Vec3(look.x, 0, look.z);
                                    if (horizontal.lengthSqr() < 0.0001) {
                                        horizontal = new Vec3(0, 0, 1);
                                    } else {
                                        horizontal = horizontal.normalize();
                                    }
                                    Vec3 right = new Vec3(horizontal.z, 0, -horizontal.x);
                                    Vec3 center = player.position().add(0, 1.0, 0);

                                    for (int i = 0; i < count; i++) {
                                        double offset = (i - (count - 1) / 2.0) * 1.5;
                                        Vec3 point = center.add(right.scale(offset));
                                        level.sendParticles(
                                            ParticleTypes.HAPPY_VILLAGER,
                                            point.x, point.y, point.z,
                                            1, 0.08, 0.08, 0.08, 0.0
                                        );
                                    }

                                    context.getSource().sendSuccess(
                                        () -> net.minecraft.network.chat.Component.literal(
                                            "Vyūha preview: displayed " + count + " line positions."
                                        ),
                                        false
                                    );
                                    return count;
                                })
                            )
                        )
                    )
            );
        }
    }
}
