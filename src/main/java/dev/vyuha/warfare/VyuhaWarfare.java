package dev.vyuha.warfare;

import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(VyuhaWarfare.MOD_ID)
public class VyuhaWarfare {
    public static final String MOD_ID = "vyuha_warfare";
    private static final Logger LOGGER = LogUtils.getLogger();

    public VyuhaWarfare() {
        LOGGER.info("Mahabharata Vyūha Warfare prototype loaded. Formation systems are under development.");
    }
}
