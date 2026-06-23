package com.tosshie.rbd.event;

import com.tosshie.rbd.RandomBiomeDimensions;
import com.tosshie.rbd.util.DimensionHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * プレイヤーが攻撃したエンティティの体力・攻撃力・防御力をログ出力する。
 */
public class AttackLogHandler {

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player)) {
            return;
        }

        LivingEntity target = event.getEntity();

        if (!DimensionHelper.isRBDDimension(target.level().dimension())) {
            return;
        }

        double health = target.getHealth();
        double maxHealth = target.getMaxHealth();
        double attack = getAttributeValue(target, Attributes.ATTACK_DAMAGE);
        double armor = getAttributeValue(target, Attributes.ARMOR);

        RandomBiomeDimensions.LOGGER.info("[AttackLog] {} - HP: {}/{}, Attack: {}, Armor: {}",
                target.getName().getString(), health, maxHealth, attack, armor);
    }

    private double getAttributeValue(LivingEntity entity, net.minecraft.world.entity.ai.attributes.Attribute attribute) {
        var instance = entity.getAttribute(attribute);
        return instance != null ? instance.getValue() : 0.0;
    }
}
