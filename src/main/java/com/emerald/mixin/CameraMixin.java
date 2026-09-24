package com.emerald.mixin;

import com.emerald.jak.vehicle.JakVehicleClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LA VUE DU CONDUCTEUR (cahier §98), le second greffon du mod, cote client : NeoForge laisse
 * tourner la camera (ViewportEvent.ComputeCameraAngles), pas la deplacer. En premiere personne,
 * a bord d'une voiture de Haven, JakVehicleClient.cockpit la leve au-dessus de ce qui bouche la
 * vue devant le conducteur.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    public abstract Vec3 getPosition();

    @Inject(method = "setup", at = @At("TAIL"))
    private void emeraldweapons$cockpit(BlockGetter level, Entity entity, boolean detached, boolean mirrored,
                                        float partialTick, CallbackInfo callback) {
        if (!detached) {
            Vec3 lifted = JakVehicleClient.cockpit(entity, this.getPosition());
            if (lifted != null) {
                this.setPosition(lifted);
            }
        }
    }
}
