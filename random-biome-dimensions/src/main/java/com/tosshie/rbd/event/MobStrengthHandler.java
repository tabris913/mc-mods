package com.tosshie.rbd.event;

import com.tosshie.rbd.config.RBDConfig;
import com.tosshie.rbd.util.DimensionHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * RBDディメンションのモブを距離に応じて強化し、名前とレベルを頭上に表示する。
 * 経験値ドロップ量も強化段階に応じて増加する。
 */
public class MobStrengthHandler {

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }

        if (!DimensionHelper.isRBDDimension(event.getLevel().dimension())) {
            if (DimensionHelper.isSpawnDimension(event.getLevel().dimension())) {
                String mobName = mob.getType().getDescription().getString();
                mob.setCustomName(Component.literal(mobName));
                mob.setCustomNameVisible(true);
            }
            return;
        }

        double x = mob.getX();
        double z = mob.getZ();
        double distance = Math.max(Math.abs(x), Math.abs(z));

        int interval = RBDConfig.DIFFICULTY_INTERVAL.get();
        int steps = (int) (distance / interval);

        String mobName = mob.getType().getDescription().getString();
        if (steps <= 0) {
            mob.setCustomName(Component.literal(mobName + " Lv.1"));
        } else {
            double healthMult = 1.0 + (RBDConfig.HEALTH_MULTIPLIER.get() - 1.0) * steps;
            double attackMult = 1.0 + (RBDConfig.ATTACK_MULTIPLIER.get() - 1.0) * steps;
            double defenseMult = 1.0 + (RBDConfig.DEFENSE_MULTIPLIER.get() - 1.0) * steps;

            applyMultiplier(mob, Attributes.MAX_HEALTH, healthMult);
            mob.setHealth(mob.getMaxHealth());
            applyMultiplier(mob, Attributes.ATTACK_DAMAGE, attackMult);
            applyMultiplier(mob, Attributes.ARMOR, defenseMult);

            mob.setCustomName(Component.literal(mobName + " Lv." + (steps + 1)));
        }
        mob.setCustomNameVisible(true);
    }

    @SubscribeEvent
    public void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        if (!DimensionHelper.isRBDDimension(event.getEntity().level().dimension())) {
            return;
        }

        double x = event.getEntity().getX();
        double z = event.getEntity().getZ();
        double distance = Math.max(Math.abs(x), Math.abs(z));

        int interval = RBDConfig.DIFFICULTY_INTERVAL.get();
        int steps = (int) (distance / interval);
        if (steps <= 0) {
            return;
        }

        double xpMult = 1.0 + (RBDConfig.XP_MULTIPLIER.get() - 1.0) * steps;
        event.setDroppedExperience((int) (event.getDroppedExperience() * xpMult));
    }

    private void applyMultiplier(Mob mob, net.minecraft.world.entity.ai.attributes.Attribute attribute, double multiplier) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            double base = instance.getBaseValue();
            instance.setBaseValue(base * multiplier);
        }
    }
}
