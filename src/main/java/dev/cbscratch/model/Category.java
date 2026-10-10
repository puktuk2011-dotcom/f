package dev.cbscratch.model;

/** Категория блоков (вкладка палитры) и её цвет. */
public enum Category {
	EVENTS("События", 0xFFFFBF00),
	CONTROL("Логика", 0xFFFFAB19),
	CHAT("Чат", 0xFF9966FF),
	PLAYER("Игроки", 0xFF4C97FF),
	WORLD("Мир", 0xFF59C059),
	VARIABLES("Перем.", 0xFFFF8C1A),
	ADVANCED("Прочее", 0xFFFF6680);

	public final String title;
	public final int color;

	Category(String title, int color) {
		this.title = title;
		this.color = color;
	}

	/** Затемнённый вариант цвета для обводки. */
	public int dark() {
		return shade(color, 0.72f);
	}

	/** Осветлённый вариант цвета для блика. */
	public int light() {
		return shade(color, 1.18f);
	}

	private static int shade(int argb, float k) {
		int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * k));
		int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * k));
		int b = Math.min(255, Math.round((argb & 0xFF) * k));
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}
}
