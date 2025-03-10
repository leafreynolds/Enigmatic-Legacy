package com.aizistral.etherium.items;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.aizistral.enigmaticlegacy.api.materials.EnigmaticMaterials;
import com.aizistral.enigmaticlegacy.helpers.AOEMiningHelper;
import com.aizistral.enigmaticlegacy.helpers.ItemLoreHelper;
import com.aizistral.etherium.core.EtheriumUtil;
import com.aizistral.etherium.core.IEtheriumConfig;
import com.aizistral.etherium.core.IEtheriumTool;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

public class EtheriumScythe extends SwordItem implements IEtheriumTool {
	protected static final Map<Block, BlockState> HOE_LOOKUP = Maps.newHashMap(ImmutableMap.of(Blocks.GRASS_BLOCK,
			Blocks.FARMLAND.defaultBlockState(), Blocks.DIRT_PATH, Blocks.FARMLAND.defaultBlockState(), Blocks.DIRT,
			Blocks.FARMLAND.defaultBlockState(), Blocks.COARSE_DIRT, Blocks.DIRT.defaultBlockState()));

	public EtheriumScythe() {
		super(EnigmaticMaterials.ETHERIUM, 3, -2.0F, EtheriumUtil.defaultProperties(EtheriumScythe.class).fireResistant());
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void appendHoverText(ItemStack stack, @Nullable Level worldIn, List<Component> list, TooltipFlag flagIn) {
		if (this.getConfig().getScytheMiningVolume() == -1)
			return;

		if (Screen.hasShiftDown()) {
			ItemLoreHelper.addLocalizedString(list, "tooltip.enigmaticlegacy.etheriumScythe1", ChatFormatting.GOLD, this.getConfig().getScytheMiningVolume() + this.getConfig().getAOEBoost(Minecraft.getInstance().player));
			ItemLoreHelper.addLocalizedString(list, "tooltip.enigmaticlegacy.etheriumScythe2", ChatFormatting.GOLD, this.getConfig().getScytheMiningVolume());
			ItemLoreHelper.addLocalizedString(list, "tooltip.enigmaticlegacy.void");
			if (!this.getConfig().disableAOEShiftInhibition()) {
				ItemLoreHelper.addLocalizedString(list, "tooltip.enigmaticlegacy.etheriumScythe3");
			}
			ItemLoreHelper.addLocalizedString(list, "tooltip.enigmaticlegacy.etheriumScythe4");
		} else {
			ItemLoreHelper.addLocalizedString(list, "tooltip.enigmaticlegacy.holdShift");
		}

		if (!this.areaEffectsAllowed(stack)) {
			ItemLoreHelper.addLocalizedString(list, "tooltip.enigmaticlegacy.void");
			ItemLoreHelper.addLocalizedString(list, "tooltip.enigmaticlegacy.aoeDisabled");
		}
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		player.startUsingItem(hand);

		if (player.isCrouching()) {
			this.toggleAreaEffects(player, stack);

			return new InteractionResultHolder<>(InteractionResult.SUCCESS, stack);
		} else
			return super.use(world, player, hand);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getPlayer().isCrouching())
			return this.use(context.getLevel(), context.getPlayer(), context.getHand()).getResult();

		InteractionResult type = Items.DIAMOND_HOE.useOn(context);

		if (!this.areaEffectsEnabled(context.getPlayer(), context.getItemInHand()))
			return type;

		int supRad = (this.getConfig().getScytheMiningVolume() - 1) / 2;

		if (type == InteractionResult.CONSUME) {
			for (int x = -supRad; x <= supRad; x++) {
				for (int z = -supRad; z <= supRad; z++) {
					if (x == 0 & z == 0) {
						continue;
					}

					Items.DIAMOND_HOE.useOn(new UseOnContext(context.getPlayer(), context.getHand(), new BlockHitResult(context.getClickLocation().add(x, 0, z), Direction.UP, context.getClickedPos().offset(x, 0, z), context.isInside())));
				}
			}
		}

		return type;
	}

	@Override
	public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
		return toolAction == ToolActions.HOE_TILL || toolAction == ToolActions.HOE_DIG
				|| super.canPerformAction(stack, toolAction);
	}

	@Override
	public boolean mineBlock(ItemStack stack, Level world, BlockState state, BlockPos pos, LivingEntity entityLiving) {
		if (entityLiving instanceof Player && this.areaEffectsEnabled((Player) entityLiving, stack) && /*this.effectiveMaterials.contains(state.getMaterial()) &&*/ !world.isClientSide && this.getConfig().getScytheMiningVolume() != -1) {
			Direction face = Direction.UP;
			int volume = this.getConfig().getScytheMiningVolume() + (this.getConfig().getAOEBoost((Player) entityLiving));

			AOEMiningHelper.harvestCube(world, (Player) entityLiving, face, pos.offset(0, (volume - 1) / 2, 0), (s) -> false, volume, volume, false, pos, stack, (objPos, objState) -> {
				stack.hurtAndBreak(1, entityLiving, p -> p.broadcastBreakEvent(Mob.getEquipmentSlotForItem(stack)));
			});
		}

		return super.mineBlock(stack, world, state, pos, entityLiving);
	}

	@Override
	public float getDestroySpeed(ItemStack stack, BlockState state) {
		return !this.isCorrectToolForDrops(stack, state) ? super.getDestroySpeed(stack, state) : this.getTier().getSpeed();
	}

}
