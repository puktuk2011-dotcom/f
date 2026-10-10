package dev.cbscratch.compile;

import java.util.ArrayList;
import java.util.List;

import dev.cbscratch.model.Block;
import dev.cbscratch.model.BlockDef;
import dev.cbscratch.model.BlockDef.Mode;
import dev.cbscratch.model.Script;

/**
 * Превращает скрипт в плоский список команд для цепочки командных блоков.
 *
 * <p>Правила:
 * <ul>
 *   <li>первая команда получает режим шляпки, остальные — «цепочка, всегда активен»;</li>
 *   <li>C-блоки «если / от имени / на позиции» добавляют префикс {@code execute ... run} к каждой вложенной команде;</li>
 *   <li>«повторить N раз» разворачивается в N копий вложенных команд.</li>
 * </ul>
 */
public final class Compiler {
	public static final int MAX_REPEAT = 64;
	public static final int MAX_COMMANDS = 400;

	public record Step(String command, Mode mode, boolean auto) {
	}

	public record Result(List<Step> steps, List<String> warnings) {
		public boolean ok() {
			return !steps.isEmpty();
		}
	}

	private Compiler() {
	}

	public static Result compile(Script script) {
		List<String> warnings = new ArrayList<>();
		List<Step> steps = new ArrayList<>();

		if (script.blocks.isEmpty()) {
			warnings.add("Скрипт пуст");
			return new Result(steps, warnings);
		}
		Block first = script.blocks.get(0);
		if (first.def.shape != BlockDef.Shape.HAT) {
			warnings.add("Нет блока из «События» сверху — скрипт не знает, как запускаться");
			return new Result(steps, warnings);
		}

		List<String> commands = new ArrayList<>();
		for (int i = 1; i < script.blocks.size(); i++) {
			emit(script.blocks.get(i), "", commands, warnings);
			if (commands.size() >= MAX_COMMANDS) {
				warnings.add("Слишком много команд, оставлено первых " + MAX_COMMANDS);
				break;
			}
		}
		if (commands.size() > MAX_COMMANDS) {
			commands = new ArrayList<>(commands.subList(0, MAX_COMMANDS));
		}
		if (commands.isEmpty()) {
			warnings.add("Под блоком события нет команд");
		}

		for (int i = 0; i < commands.size(); i++) {
			if (i == 0) {
				steps.add(new Step(commands.get(i), first.def.hatMode, first.def.hatAuto));
			} else {
				steps.add(new Step(commands.get(i), Mode.CHAIN, true));
			}
		}
		return new Result(steps, warnings);
	}

	private static void emit(Block b, String prefix, List<String> out, List<String> warnings) {
		if (out.size() >= MAX_COMMANDS) {
			return;
		}
		switch (b.def.shape) {
			case STACK -> {
				String cmd = fill(b, warnings);
				if (cmd.isEmpty()) {
					warnings.add("Пустая команда в блоке «" + b.def.id + "»");
					return;
				}
				out.add(prefix + cmd);
			}
			case C -> {
				if (b.def.id.equals("repeat")) {
					int n = parseCount(b.value("n"), warnings);
					for (int i = 0; i < n; i++) {
						for (Block child : b.body) {
							emit(child, prefix, out, warnings);
						}
					}
				} else {
					String head = fill(b, warnings);
					String next = prefix + head + " run ";
					for (Block child : b.body) {
						emit(child, next, out, warnings);
					}
				}
			}
			case HAT -> warnings.add("Блок события внутри скрипта проигнорирован");
		}
	}

	private static int parseCount(String raw, List<String> warnings) {
		try {
			int n = Integer.parseInt(raw.trim());
			if (n < 1) {
				warnings.add("«повторить» меньше 1 — пропущено");
				return 0;
			}
			if (n > MAX_REPEAT) {
				warnings.add("«повторить» ограничено " + MAX_REPEAT + " раз");
				return MAX_REPEAT;
			}
			return n;
		} catch (NumberFormatException e) {
			warnings.add("«повторить»: «" + raw + "» — не число");
			return 0;
		}
	}

	/** Подставляет значения полей в шаблон команды. */
	static String fill(Block b, List<String> warnings) {
		String s = b.def.template;
		for (BlockDef.Field f : b.def.fields) {
			String v = b.value(f.key).trim();
			if (v.isEmpty()) {
				warnings.add("Пустое поле «" + f.key + "» в блоке «" + b.def.id + "»");
			}
			s = s.replace("{" + f.key + ":j}", jsonEscape(v));
			s = s.replace("{" + f.key + "}", v);
		}
		s = s.trim();
		if (s.startsWith("/")) {
			s = s.substring(1).trim();
		}
		return s;
	}

	static String jsonEscape(String v) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < v.length(); i++) {
			char c = v.charAt(i);
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\t' -> sb.append("\\t");
				default -> sb.append(c);
			}
		}
		return sb.toString();
	}
}
