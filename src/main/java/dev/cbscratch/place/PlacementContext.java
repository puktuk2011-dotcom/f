package dev.cbscratch.place;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Где ставить цепочку: запоминается в момент открытия редактора (пока ещё виден прицел). */
public record PlacementContext(BlockPos origin, Direction lookFacing) {

	public static PlacementContext capture(MinecraftClient mc) {
		Direction look = mc.player.getHorizontalFacing();
		HitResult hit = mc.crosshairTarget;
		BlockPos origin;
		if (hit instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
			origin = bhr.getBlockPos().offset(bhr.getSide());
		} else {
			origin = mc.player.getBlockPos().offset(look, 2);
		}
		return new PlacementContext(origin, look);
	}
}
