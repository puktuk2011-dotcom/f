package dev.cbscratch.place;

import net.minecraft.util.math.Direction;

/** Направление, в котором расставляется цепочка командных блоков. */
public enum Dir {
	AUTO("по взгляду", null),
	NORTH("север", Direction.NORTH),
	SOUTH("юг", Direction.SOUTH),
	EAST("восток", Direction.EAST),
	WEST("запад", Direction.WEST),
	UP("вверх", Direction.UP),
	DOWN("вниз", Direction.DOWN);

	public final String title;
	public final Direction direction;

	Dir(String title, Direction direction) {
		this.title = title;
		this.direction = direction;
	}

	public Dir next() {
		Dir[] all = values();
		return all[(ordinal() + 1) % all.length];
	}

	public static Dir parse(String name) {
		try {
			return valueOf(name);
		} catch (IllegalArgumentException e) {
			return AUTO;
		}
	}
}
