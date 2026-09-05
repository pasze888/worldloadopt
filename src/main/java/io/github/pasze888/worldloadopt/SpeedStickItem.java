package io.github.pasze888.worldloadopt;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 示例自定义物品:右键给自己施加 30 秒速度 II,消耗 1 点耐久。
 *
 * 签名依据(api-sources/,1.21.1):
 * - Item.use(Level, Player, InteractionHand) -> InteractionResultHolder<ItemStack>
 * - LivingEntity.addEffect(MobEffectInstance) -> boolean
 * - MobEffects.MOVEMENT_SPEED -> Holder<MobEffect>(1.21 起由 SPEED 改名)
 * - MobEffectInstance(Holder<MobEffect>, int duration, int amplifier)
 * - LivingEntity.getSlotForHand(InteractionHand) -> EquipmentSlot
 * - ItemStack.hurtAndBreak(int, LivingEntity, EquipmentSlot)
 */
public class SpeedStickItem extends Item {

    public SpeedStickItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!level.isClientSide) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 1));
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(usedHand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
