package dev.cbscratch.model;

import static dev.cbscratch.model.BlockDef.cblock;
import static dev.cbscratch.model.BlockDef.hat;
import static dev.cbscratch.model.BlockDef.stack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.cbscratch.model.BlockDef.Mode;

/** Все доступные блоки. Чтобы добавить свой блок, достаточно дописать одну строку. */
public final class BlockRegistry {
	private static final Map<String, BlockDef> BY_ID = new LinkedHashMap<>();

	static {
		// ---- События (шляпки) ----
		add(hat("hat_redstone", "когда есть сигнал редстоуна", Mode.IMPULSE, false));
		add(hat("hat_once", "при запуске (один раз)", Mode.IMPULSE, true));
		add(hat("hat_forever", "всегда (каждый тик)", Mode.REPEAT, true));

		// ---- Логика (C-блоки) ----
		add(cblock("repeat", Category.CONTROL, "повторить {n#3} раз", ""));
		add(cblock("exec_as", Category.CONTROL, "от имени {sel|@a}", "execute as {sel}"));
		add(cblock("exec_at", Category.CONTROL, "на позиции {sel|@a}", "execute at {sel}"));
		add(cblock("if_score", Category.CONTROL, "если {var|points} у {sel|@p} в диапазоне {range|1..}",
				"execute if score {sel} {var} matches {range}"));
		add(cblock("unless_score", Category.CONTROL, "если НЕ {var|points} у {sel|@p} в диапазоне {range|1..}",
				"execute unless score {sel} {var} matches {range}"));
		add(cblock("if_entity", Category.CONTROL, "если существует {sel|@e[type=zombie]}", "execute if entity {sel}"));
		add(cblock("unless_entity", Category.CONTROL, "если НЕ существует {sel|@e[type=zombie]}",
				"execute unless entity {sel}"));
		add(cblock("if_block", Category.CONTROL, "если блок {x|~} {y|~-1} {z|~} это {block|stone}",
				"execute if block {x} {y} {z} {block}"));
		add(cblock("unless_block", Category.CONTROL, "если блок {x|~} {y|~-1} {z|~} НЕ {block|stone}",
				"execute unless block {x} {y} {z} {block}"));

		// ---- Чат и звук ----
		add(stack("say", Category.CHAT, "сказать {msg|Привет!}", "say {msg}"));
		add(stack("tellraw", Category.CHAT, "написать {sel|@a}: {msg|Привет!}", "tellraw {sel} {\"text\":\"{msg:j}\"}"));
		add(stack("title", Category.CHAT, "{kind=title,subtitle,actionbar} {sel|@a}: {msg|Привет!}",
				"title {sel} {kind} {\"text\":\"{msg:j}\"}"));
		add(stack("playsound", Category.CHAT, "звук {sound|entity.experience_orb.pickup} для {sel|@a}",
				"playsound minecraft:{sound} master {sel}"));
		add(stack("particle", Category.CHAT, "частицы {p|flame} {x|~} {y|~1} {z|~} кол-во {n#10}",
				"particle {p} {x} {y} {z} 0.3 0.3 0.3 0.05 {n}"));

		// ---- Игроки и сущности ----
		add(stack("give", Category.PLAYER, "выдать {sel|@p} {item|diamond} x{n#1}", "give {sel} {item} {n}"));
		add(stack("tp", Category.PLAYER, "телепорт {sel|@p} в {x|~} {y|~1} {z|~}", "tp {sel} {x} {y} {z}"));
		add(stack("effect", Category.PLAYER, "эффект {sel|@p} {eff|speed} {sec#10} сек, уровень {lvl#0}",
				"effect give {sel} {eff} {sec} {lvl}"));
		add(stack("effect_clear", Category.PLAYER, "снять эффекты {sel|@p}", "effect clear {sel}"));
		add(stack("kill", Category.PLAYER, "убить {sel|@e[type=zombie]}", "kill {sel}"));
		add(stack("summon", Category.PLAYER, "призвать {ent|zombie} в {x|~} {y|~} {z|~}", "summon {ent} {x} {y} {z}"));
		add(stack("gamemode", Category.PLAYER, "режим {mode=survival,creative,adventure,spectator} для {sel|@p}",
				"gamemode {mode} {sel}"));
		add(stack("xp", Category.PLAYER, "опыт +{n#10} для {sel|@p}", "xp add {sel} {n} points"));
		add(stack("clear_inv", Category.PLAYER, "очистить инвентарь {sel|@p}", "clear {sel}"));

		// ---- Мир ----
		add(stack("setblock", Category.WORLD, "поставить {block|stone} в {x|~} {y|~} {z|~}",
				"setblock {x} {y} {z} {block}"));
		add(stack("fill", Category.WORLD, "залить {block|stone} {x1|~} {y1|~} {z1|~} до {x2|~5} {y2|~5} {z2|~5}",
				"fill {x1} {y1} {z1} {x2} {y2} {z2} {block}"));
		add(stack("time", Category.WORLD, "время {t|day}", "time set {t}"));
		add(stack("weather", Category.WORLD, "погода {w=clear,rain,thunder}", "weather {w}"));
		add(stack("gamerule", Category.WORLD, "правило {rule|keepInventory} = {val=true,false}",
				"gamerule {rule} {val}"));
		add(stack("difficulty", Category.WORLD, "сложность {d=peaceful,easy,normal,hard}", "difficulty {d}"));
		add(stack("worldspawn", Category.WORLD, "точка спавна мира {x|~} {y|~} {z|~}", "setworldspawn {x} {y} {z}"));

		// ---- Переменные (scoreboard) ----
		add(stack("var_create", Category.VARIABLES, "создать переменную {var|points}",
				"scoreboard objectives add {var} dummy"));
		add(stack("var_set", Category.VARIABLES, "задать {var|points} у {sel|@a} = {n#0}",
				"scoreboard players set {sel} {var} {n}"));
		add(stack("var_add", Category.VARIABLES, "{var|points} у {sel|@a} увеличить на {n#1}",
				"scoreboard players add {sel} {var} {n}"));
		add(stack("var_remove", Category.VARIABLES, "{var|points} у {sel|@a} уменьшить на {n#1}",
				"scoreboard players remove {sel} {var} {n}"));
		add(stack("var_op", Category.VARIABLES, "{sel|@a} {a|points} {op=+=,-=,*=,/=,%=,=,<,>,><} {sel2|@a} {b|points}",
				"scoreboard players operation {sel} {a} {op} {sel2} {b}"));
		add(stack("var_show", Category.VARIABLES, "показать {var|points} в {slot=sidebar,list,belowName}",
				"scoreboard objectives setdisplay {slot} {var}"));

		// ---- Прочее ----
		add(stack("raw", Category.ADVANCED, "команда {cmd|say hi}", "{cmd}"));
	}

	private BlockRegistry() {
	}

	private static void add(BlockDef def) {
		if (BY_ID.put(def.id, def) != null) {
			throw new IllegalStateException("Дубликат id блока: " + def.id);
		}
	}

	public static BlockDef get(String id) {
		return BY_ID.get(id);
	}

	public static List<BlockDef> all() {
		return new ArrayList<>(BY_ID.values());
	}

	public static List<BlockDef> inCategory(Category c) {
		List<BlockDef> out = new ArrayList<>();
		for (BlockDef d : BY_ID.values()) {
			if (d.category == c) {
				out.add(d);
			}
		}
		return out;
	}
}
