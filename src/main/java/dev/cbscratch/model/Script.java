package dev.cbscratch.model;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Один скрипт: стопка блоков на рабочей области. Координаты — в системе рабочей области. */
public final class Script {
	public int x;
	public int y;
	public final List<Block> blocks = new ArrayList<>();

	public Script(int x, int y) {
		this.x = x;
		this.y = y;
	}

	public boolean hasHat() {
		return !blocks.isEmpty() && blocks.get(0).def.shape == BlockDef.Shape.HAT;
	}

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("x", x);
		o.addProperty("y", y);
		JsonArray arr = new JsonArray();
		for (Block b : blocks) {
			arr.add(b.toJson());
		}
		o.add("blocks", arr);
		return o;
	}

	public static Script fromJson(JsonObject o) {
		Script s = new Script(o.get("x").getAsInt(), o.get("y").getAsInt());
		for (JsonElement el : o.getAsJsonArray("blocks")) {
			Block b = Block.fromJson(el.getAsJsonObject());
			if (b == null) {
				continue;
			}
			// Шляпка допустима только первой.
			if (b.def.shape == BlockDef.Shape.HAT && !s.blocks.isEmpty()) {
				continue;
			}
			s.blocks.add(b);
		}
		return s;
	}
}
