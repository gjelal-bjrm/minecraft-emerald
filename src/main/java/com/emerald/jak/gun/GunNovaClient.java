package com.emerald.jak.gun;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * La Super Nova vue du client : L'ECLAIR BLANC et LA SECOUSSE (gun-part.gc:3858,
 * 3915-3917 : deux secondes de blanc, la camera qui tremble).
 *
 * Leur force depend de la distance au point de detonation : pleine a moins de 24
 * blocs, nulle a {@value GunSpec#NOVA_FLASH_RANGE}. L'eclair part plein et s'eteint en
 * {@value GunSpec#NOVA_FLASH_TICKS} tiques, en carre : il aveugle une demi-seconde,
 * pas deux. La secousse dure autant, de trois degres au plus. A plusieurs, chacun a
 * la sienne ; rien n'est envoye a ceux qui sont loin.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class GunNovaClient {

    private static int ticksLeft;
    private static float strength;

    private GunNovaClient() {
    }

    public static void accept(GunNovaPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        double distance = mc.player.position().distanceTo(new Vec3(payload.x(), payload.y(), payload.z()));
        float s = (float) Math.max(0.0, Math.min(1.0, (GunSpec.NOVA_FLASH_RANGE - distance) / (GunSpec.NOVA_FLASH_RANGE - 24.0)));
        if (s > strength * ticksLeft / (float) GunSpec.NOVA_FLASH_TICKS) {
            strength = s;
            ticksLeft = GunSpec.NOVA_FLASH_TICKS;
        }
    }

    /** La part de l'effet encore la, de 1 a 0. */
    private static float left(float partial) {
        return ticksLeft <= 0 ? 0.0F : Math.max(0.0F, (ticksLeft - partial) / GunSpec.NOVA_FLASH_TICKS);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (ticksLeft > 0 && !Minecraft.getInstance().isPaused()) {
            ticksLeft--;
        }
    }

    /** Le calque de l'eclair, pose au-dessus de tout le HUD. */
    static void render(GuiGraphics graphics, DeltaTracker delta) {
        float left = left(delta.getGameTimeDeltaPartialTick(false));
        if (left <= 0.0F) {
            return;
        }
        int alpha = (int) (255.0F * strength * left * left);
        if (alpha > 2) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (alpha << 24) | 0xF4ECFF);
        }
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        float left = left((float) event.getPartialTick());
        if (left <= 0.0F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double t = (mc.level == null ? 0L : mc.level.getGameTime()) + event.getPartialTick();
        float shake = 3.0F * strength * left;
        event.setPitch(event.getPitch() + shake * (float) Math.sin(t * 2.7));
        event.setYaw(event.getYaw() + shake * (float) Math.sin(t * 3.4 + 1.3));
        event.setRoll(event.getRoll() + shake * 0.6F * (float) Math.sin(t * 2.1 + 0.4));
    }
}
