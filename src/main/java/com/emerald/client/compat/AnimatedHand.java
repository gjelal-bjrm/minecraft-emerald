package com.emerald.client.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * La main ANIMEE : ou playerAnimator a mis l'objet tenu.
 *
 * Better Combat pose les armes par playerAnimator -- a deux mains, en garde,
 * en travers du corps. Le calque vanilla de l'objet tenu suit parce que
 * playerAnimator s'y greffe (HeldItemMixin) et applique, juste avant de
 * dessiner, la transformation de l'os « rightItem » / « leftItem » de
 * l'animation en cours : echelle, position, rotations Z, Y, X. Notre calque de
 * halo, lui, rejouait les transformations VANILLA et s'arretait la : la lame
 * etait en travers de la poitrine et sa lueur restait a la hanche.
 *
 * On lit donc le meme os, par reflexion : playerAnimator n'est pas une
 * dependance du mod et la classe ne doit pas manquer quand il est absent.
 * Sans lui, ou sans animation active, on ne touche a rien.
 *
 * Les valeurs suivent HeldItemMixin.changeItemLocation : la position est en
 * seiziemes de bloc, les rotations en radians, dans cet ordre.
 */
public final class AnimatedHand {

    private static final MethodHandle GET_ANIMATION;
    private static final MethodHandle IS_ACTIVE;
    private static final MethodHandle GET_3D;
    private static final MethodHandle VEC_NEW;
    private static final MethodHandle VEC_X;
    private static final MethodHandle VEC_Y;
    private static final MethodHandle VEC_Z;
    private static final Object T_POSITION;
    private static final Object T_ROTATION;
    private static final Object T_SCALE;

    static {
        MethodHandle getAnimation = null;
        MethodHandle isActive = null;
        MethodHandle get3d = null;
        MethodHandle vecNew = null;
        MethodHandle vx = null;
        MethodHandle vy = null;
        MethodHandle vz = null;
        Object pos = null;
        Object rot = null;
        Object scale = null;
        try {
            MethodHandles.Lookup l = MethodHandles.publicLookup();
            Class<?> animated = Class.forName("dev.kosmx.playerAnim.impl.IAnimatedPlayer");
            Class<?> applier = Class.forName("dev.kosmx.playerAnim.impl.animation.AnimationApplier");
            Class<?> processor = Class.forName("dev.kosmx.playerAnim.core.impl.AnimationProcessor");
            Class<?> vec = Class.forName("dev.kosmx.playerAnim.core.util.Vec3f");
            Class<?> type = Class.forName("dev.kosmx.playerAnim.api.TransformType");
            getAnimation = l.findVirtual(animated, "playerAnimator_getAnimation",
                    MethodType.methodType(applier));
            isActive = l.findVirtual(applier, "isActive", MethodType.methodType(boolean.class));
            get3d = l.findVirtual(processor, "get3DTransform",
                    MethodType.methodType(vec, String.class, type, vec));
            vecNew = l.findConstructor(vec, MethodType.methodType(void.class,
                    float.class, float.class, float.class));
            vx = l.findVirtual(vec, "getX", MethodType.methodType(Number.class));
            vy = l.findVirtual(vec, "getY", MethodType.methodType(Number.class));
            vz = l.findVirtual(vec, "getZ", MethodType.methodType(Number.class));
            for (Object constant : type.getEnumConstants()) {
                switch (constant.toString()) {
                    case "POSITION" -> pos = constant;
                    case "ROTATION" -> rot = constant;
                    case "SCALE" -> scale = constant;
                    default -> { }
                }
            }
        } catch (Throwable absent) {
            getAnimation = null;                  // playerAnimator n'est pas la : on s'efface
        }
        GET_ANIMATION = getAnimation;
        IS_ACTIVE = isActive;
        GET_3D = get3d;
        VEC_NEW = vecNew;
        VEC_X = vx;
        VEC_Y = vy;
        VEC_Z = vz;
        T_POSITION = pos;
        T_ROTATION = rot;
        T_SCALE = scale;
    }

    private AnimatedHand() {
    }

    /** Vrai si playerAnimator est charge et sait animer un porteur. */
    public static boolean available() {
        return GET_ANIMATION != null && T_POSITION != null && T_ROTATION != null && T_SCALE != null;
    }

    /**
     * Applique a la pose la transformation de l'os de l'objet tenu, si une
     * animation est active. A appeler la ou le calque vanilla dessine l'objet :
     * apres translateToHand et les retournements du bras.
     */
    public static void apply(LivingEntity holder, HumanoidArm arm, PoseStack pose) {
        if (!available()) {
            return;
        }
        try {
            Object animation = GET_ANIMATION.invoke(holder);   // ClassCastException si non anime
            if (animation == null || !(boolean) IS_ACTIVE.invoke(animation)) {
                return;
            }
            String bone = arm == HumanoidArm.LEFT ? "leftItem" : "rightItem";
            Object one = VEC_NEW.invoke(1.0F, 1.0F, 1.0F);
            Object zero = VEC_NEW.invoke(0.0F, 0.0F, 0.0F);
            Object scale = GET_3D.invoke(animation, bone, T_SCALE, one);
            Object position = GET_3D.invoke(animation, bone, T_POSITION, zero);
            Object rotation = GET_3D.invoke(animation, bone, T_ROTATION, zero);
            pose.scale(x(scale), y(scale), z(scale));
            pose.translate(x(position) * 0.0625F, y(position) * 0.0625F, z(position) * 0.0625F);
            pose.mulPose(Axis.ZP.rotation(z(rotation)));
            pose.mulPose(Axis.YP.rotation(y(rotation)));
            pose.mulPose(Axis.XP.rotation(x(rotation)));
        } catch (Throwable ignored) {
            // un porteur que playerAnimator n'anime pas (monstre) : pose vanilla
        }
    }

    private static float x(Object vec) throws Throwable {
        return ((Number) VEC_X.invoke(vec)).floatValue();
    }

    private static float y(Object vec) throws Throwable {
        return ((Number) VEC_Y.invoke(vec)).floatValue();
    }

    private static float z(Object vec) throws Throwable {
        return ((Number) VEC_Z.invoke(vec)).floatValue();
    }
}
