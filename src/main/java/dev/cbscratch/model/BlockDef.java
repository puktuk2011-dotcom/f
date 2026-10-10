package dev.cbscratch.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Описание типа блока: как он выглядит и в какую команду превращается.
 *
 * <p>Метка задаётся мини-языком: обычный текст остаётся подписью, а поля пишутся в фигурных скобках:
 * <ul>
 *   <li>{@code {key|значение}} — текстовое поле;</li>
 *   <li>{@code {key#5}} — числовое поле;</li>
 *   <li>{@code {key=a,b,c}} — выпадающий список (первый вариант по умолчанию).</li>
 * </ul>
 * Шаблон команды использует {@code {key}} для подстановки, а {@code {key:j}} — подстановка с экранированием для JSON.
 */
public final class BlockDef {
	public enum Shape { HAT, STACK, C }

	public enum Mode { IMPULSE, CHAIN, REPEAT }

	public enum Kind { TEXT, NUMBER, CHOICE }

	public static final class Field {
		public final String key;
		public final String defaultValue;
		public final Kind kind;
		public final List<String> options;

		Field(String key, String defaultValue, Kind kind, List<String> options) {
			this.key = key;
			this.defaultValue = defaultValue;
			this.kind = kind;
			this.options = options;
		}
	}

	/** Элемент метки: либо подпись, либо поле. */
	public static final class Part {
		public final String text;
		public final Field field;

		Part(String text, Field field) {
			this.text = text;
			this.field = field;
		}

		public boolean isField() {
			return field != null;
		}
	}

	public final String id;
	public final Category category;
	public final Shape shape;
	public final String template;
	public final List<Part> parts;
	public final List<Field> fields;

	/** Только для шляпок: режим первого командного блока. */
	public final Mode hatMode;
	public final boolean hatAuto;

	private BlockDef(String id, Category category, Shape shape, String label, String template, Mode hatMode, boolean hatAuto) {
		this.id = id;
		this.category = category;
		this.shape = shape;
		this.template = template;
		this.hatMode = hatMode;
		this.hatAuto = hatAuto;
		List<Part> ps = new ArrayList<>();
		List<Field> fs = new ArrayList<>();
		parse(label, ps, fs);
		this.parts = List.copyOf(ps);
		this.fields = List.copyOf(fs);
	}

	public static BlockDef hat(String id, String label, Mode mode, boolean auto) {
		return new BlockDef(id, Category.EVENTS, Shape.HAT, label, "", mode, auto);
	}

	public static BlockDef stack(String id, Category c, String label, String template) {
		return new BlockDef(id, c, Shape.STACK, label, template, null, false);
	}

	public static BlockDef cblock(String id, Category c, String label, String template) {
		return new BlockDef(id, c, Shape.C, label, template, null, false);
	}

	public Field field(String key) {
		for (Field f : fields) {
			if (f.key.equals(key)) {
				return f;
			}
		}
		return null;
	}

	private static void parse(String label, List<Part> parts, List<Field> fields) {
		StringBuilder lit = new StringBuilder();
		int i = 0;
		while (i < label.length()) {
			char ch = label.charAt(i);
			if (ch == '{') {
				int end = label.indexOf('}', i);
				if (end < 0) {
					throw new IllegalArgumentException("Незакрытая скобка в метке: " + label);
				}
				if (!lit.toString().isBlank()) {
					parts.add(new Part(lit.toString().trim(), null));
				}
				lit.setLength(0);
				Field f = parseField(label.substring(i + 1, end));
				fields.add(f);
				parts.add(new Part(null, f));
				i = end + 1;
			} else {
				lit.append(ch);
				i++;
			}
		}
		if (!lit.toString().isBlank()) {
			parts.add(new Part(lit.toString().trim(), null));
		}
	}

	private static Field parseField(String spec) {
		int cut = -1;
		char sep = 0;
		for (int i = 0; i < spec.length(); i++) {
			char c = spec.charAt(i);
			if (c == '|' || c == '#' || c == '=') {
				cut = i;
				sep = c;
				break;
			}
		}
		if (cut < 0) {
			return new Field(spec, "", Kind.TEXT, List.of());
		}
		String key = spec.substring(0, cut);
		String rest = spec.substring(cut + 1);
		return switch (sep) {
			case '#' -> new Field(key, rest, Kind.NUMBER, List.of());
			case '=' -> {
				List<String> opts = List.of(rest.split(","));
				yield new Field(key, opts.get(0), Kind.CHOICE, opts);
			}
			default -> new Field(key, rest, Kind.TEXT, List.of());
		};
	}
}
