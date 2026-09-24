package com.emerald.haven.quest.runs;

import com.emerald.haven.invasion.HavenMonsterKilledEvent;
import com.emerald.haven.quest.HavenQuest;
import com.emerald.haven.quest.QuestRun;
import com.emerald.jak.gun.GunForm;
import com.emerald.jak.gun.MorphGunData;
import com.emerald.jak.gun.MorphGunItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * LA CHASSE de Sig (§71) : vingt-cinq monstres abattus au Scatter Gun, la ville envahie. Un
 * monstre compte quand son tueur, de l'equipe, tient le Morph Gun en forme Scatter Gun --
 * l'arme a tir instantane : le monstre meurt pendant le tir, l'arme en main est la bonne.
 */
public class HuntRun extends QuestRun {

    public static final int GOAL = 25;
    private int kills;

    public HuntRun(HavenQuest quest, ServerLevel level) {
        super(quest, level);
    }

    @Override
    public void begin() {
    }

    @Override
    public void onKill(HavenMonsterKilledEvent event, ServerPlayer killer) {
        if (!member(killer.getUUID())) {
            return;
        }
        if (!(killer.getMainHandItem().getItem() instanceof MorphGunItem)
                || MorphGunData.of(killer.getMainHandItem()).form() != GunForm.RED_1) {
            killer.displayClientMessage(Component.translatable("game.emeraldweapons.haven.quete.chasse.arme")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        this.kills++;
        if (this.kills >= GOAL) {
            succeed();
        }
    }

    @Override
    public void tick(long now) {
    }

    @Override
    public Component objective() {
        return Component.translatable("game.emeraldweapons.haven.quete.chasse.objectif", this.kills, GOAL);
    }

    @Override
    public float progress() {
        return this.kills / (float) GOAL;
    }
}
