package com.tosshie.rbd.event;

import com.tosshie.rbd.config.RBDConfig;
import com.tosshie.rbd.util.DimensionHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Strengthens mobs in RBD dimensions based on distance from origin.
 * Every difficultyInterval blocks, health/attack/defense multiply.
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
            return;
        }

        double x = mob.getX();
        double z = mob.getZ();
        double distance = Math.max(Math.abs(x), Math.abs(z));

        int interval = RBDConfig.DIFFICULTY_INTERVAL.get();
        int steps = (int) (distance / interval);

        if (steps <= 0) {
            return;
        }

        double healthMult = Math.pow(RBDConfig.HEALTH_MULTIPLIER.get(), steps);
        double attackMult = Math.pow(RBDConfig.ATTACK_MULTIPLIER.get(), steps);
        double defenseMult = Math.pow(RBDConfig.DEFENSE_MULTIPLIER.get(), steps);

        applyMultiplier(mob, Attributes.MAX_HEALTH, healthMult);
        mob.setHealth(mob.getMaxHealth());
        applyMultiplier(mob, Attributes.ATTACK_DAMAGE, attackMult);
        applyMultiplier(mob, Attributes.ARMOR, defenseMult);
    }

    private void applyMultiplier(Mob mob, net.minecraft.world.entity.ai.attributes.Attribute attribute, double multiplier) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            double base = instance.getBaseValue();
            instance.setBaseValue(base * multiplier);
        }
    }
}
