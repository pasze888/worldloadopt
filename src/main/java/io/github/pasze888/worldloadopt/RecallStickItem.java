package io.github.pasze888.worldloadopt;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 右键传送回出生点的示例物品:回到玩家的重生点(床/重生锚),未设置时回到世界出生点。
 *
 * 签名依据(api-sources/,1.21.1):
 * - ServerPlayer.getRespawnPosition() -> @Nullable BlockPos(重生点 API 在 ServerPlayer,不在 Player)
 * - ServerPlayer.getRespawnDimension() -> ResourceKey<Level>
 * - Level.getSharedSpawnPos() -> BlockPos(世界出生点兜底)
 * - ServerPlayer.serverLevel().getServer().getLevel(dim) -> @Nullable ServerLevel
 * - ServerPlayer.teleportTo(ServerLevel, double, double, double, float, float)
 * - ItemStack.hurtAndBreak(int, LivingEntity, EquipmentSlot)
 */
public class RecallStickItem extends Item {

    public RecallStickItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            ResourceKey<Level> respawnDimension = serverPlayer.getRespawnDimension();
            ServerLevel target = serverPlayer.serverLevel().getServer().getLevel(respawnDimension);
            if (target != null) {
                BlockPos pos = serverPlayer.getRespawnPosition();
                if (pos == null) {
                    pos = target.getSharedSpawnPos();
                }
                serverPlayer.teleportTo(target, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                        serverPlayer.getYRot(), serverPlayer.getXRot());
            }
            stack.hurtAndBreak(1, serverPlayer, LivingEntity.getSlotForHand(usedHand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
