package dev.cbscratch.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Все скрипты пользователя + выбранное направление цепочки. Хранится в config/cbscratch/workspace.json. */
public final class Workspace {
	public final List<Script> scripts = new ArrayList<>();
	/** Имя элемента {@code dev.cbscratch.place.Dir}. */
	public String dir = "AUTO";

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("dir", dir);
		JsonArray arr = new JsonArray();
		for (Script s : scripts) {
			if (!s.blocks.isEmpty()) {
				arr.add(s.toJson());
			}
		}
		o.add("scripts", arr);
		return o;
	}

	public static Workspace fromJson(JsonObject o) {
		Workspace w = new Workspace();
		if (o.has("dir")) {
			w.dir = o.get("dir").getAsString();
		}
		if (o.has("scripts")) {
			for (JsonElement el : o.getAsJsonArray("scripts")) {
				Script s = Script.fromJson(el.getAsJsonObject());
				if (!s.blocks.isEmpty()) {
					w.scripts.add(s);
				}
			}
		}
		return w;
	}

	public void save(Path file) {
		try {
			Files.createDirectories(file.getParent());
			String json = new GsonBuilder().setPrettyPrinting().create().toJson(toJson());
			Files.writeString(file, json, StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.err.println("[cbscratch] Не удалось сохранить рабочую область: " + e);
		}
	}

	public static Workspace load(Path file) {
		try {
			if (Files.exists(file)) {
				String json = Files.readString(file, StandardCharsets.UTF_8);
				return fromJson(JsonParser.parseString(json).getAsJsonObject());
			}
		} catch (Exception e) {
			System.err.println("[cbscratch] Не удалось прочитать рабочую область, начинаем с чистой: " + e);
		}
		return new Workspace();
	}
}
