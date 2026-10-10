package dev.cbscratch.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.cbscratch.model.BlockDef.Field;

/** Экземпляр блока в скрипте: значения полей и (для C-блоков) вложенные блоки. */
public final class Block {
	public final BlockDef def;
	public final Map<String, String> values = new LinkedHashMap<>();
	public final List<Block> body = new ArrayList<>();

	// ---- раскладка (заполняется при отрисовке, не сохраняется) ----
	public int x, y, w, h;
	public int headerH, headerW;
	public int bodyX, bodyY, bodyH;
	public final List<FieldBox> boxes = new ArrayList<>();

	public static final class FieldBox {
		public final Field field;
		public int x, y, w, h;

		public FieldBox(Field field) {
			this.field = field;
		}

		public boolean contains(int px, int py) {
			return px >= x && px < x + w && py >= y && py < y + h;
		}
	}

	public Block(BlockDef def) {
		this.def = def;
		for (Field f : def.fields) {
			values.put(f.key, f.defaultValue);
		}
	}

	public String value(String key) {
		String v = values.get(key);
		return v == null ? "" : v;
	}

	public boolean isC() {
		return def.shape == BlockDef.Shape.C;
	}

	public boolean contains(int px, int py) {
		return px >= x && px < x + w && py >= y && py < y + h;
	}

	/** Глубокая копия (без раскладки). */
	public Block copy() {
		Block b = new Block(def);
		b.values.putAll(values);
		for (Block child : body) {
			b.body.add(child.copy());
		}
		return b;
	}

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("id", def.id);
		JsonObject v = new JsonObject();
		for (Map.Entry<String, String> e : values.entrySet()) {
			v.addProperty(e.getKey(), e.getValue());
		}
		o.add("v", v);
		if (!body.isEmpty()) {
			JsonArray arr = new JsonArray();
			for (Block b : body) {
				arr.add(b.toJson());
			}
			o.add("body", arr);
		}
		return o;
	}

	/** @return блок или {@code null}, если такого типа больше нет. */
	public static Block fromJson(JsonObject o) {
		BlockDef def = BlockRegistry.get(o.get("id").getAsString());
		if (def == null) {
			return null;
		}
		Block b = new Block(def);
		if (o.has("v")) {
			for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("v").entrySet()) {
				if (def.field(e.getKey()) != null) {
					b.values.put(e.getKey(), e.getValue().getAsString());
				}
			}
		}
		if (o.has("body") && b.isC()) {
			for (JsonElement el : o.getAsJsonArray("body")) {
				Block child = fromJson(el.getAsJsonObject());
				if (child != null && child.def.shape != BlockDef.Shape.HAT) {
					b.body.add(child);
				}
			}
		}
		return b;
	}
}
