package dev.cbscratch.ui;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;

import dev.cbscratch.model.Block;
import dev.cbscratch.model.BlockDef;

/** Расчёт размеров и позиций блоков (без зависимости от Minecraft, чтобы можно было тестировать). */
public final class BlockLayout {
	public static final int PAD = 6;
	public static final int GAP = 4;
	public static final int ROW_H = 20;
	public static final int FIELD_PAD = 3;
	public static final int HAT_EXTRA = 10;
	public static final int C_INDENT = 12;
	public static final int C_FOOT = 8;
	public static final int EMPTY_BODY = 10;
	public static final int MIN_FIELD_W = 14;

	private final ToIntFunction<String> measure;
	private final BiFunction<Block, BlockDef.Field, String> display;

	/**
	 * @param measure ширина строки в пикселях
	 * @param display что показывать внутри поля (значение, буфер редактирования, "значение ▾" и т.д.)
	 */
	public BlockLayout(ToIntFunction<String> measure, BiFunction<Block, BlockDef.Field, String> display) {
		this.measure = measure;
		this.display = display;
	}

	/** Раскладывает стопку блоков сверху вниз, возвращает её общую высоту. */
	public int layoutStack(List<Block> list, int x, int y) {
		int cy = y;
		for (Block b : list) {
			layout(b, x, cy);
			cy += b.h;
		}
		return cy - y;
	}

	public int stackWidth(List<Block> list) {
		int w = 0;
		for (Block b : list) {
			w = Math.max(w, b.w);
		}
		return w;
	}

	public void layout(Block b, int x, int y) {
		b.x = x;
		b.y = y;
		int top = b.def.shape == BlockDef.Shape.HAT ? HAT_EXTRA : 0;
		b.boxes.clear();

		int cx = x + PAD;
		for (BlockDef.Part part : b.def.parts) {
			if (part.isField()) {
				String shown = display.apply(b, part.field);
				int fw = Math.max(MIN_FIELD_W, measure.applyAsInt(shown) + FIELD_PAD * 2);
				Block.FieldBox box = new Block.FieldBox(part.field);
				box.x = cx;
				box.y = y + top + 3;
				box.w = fw;
				box.h = ROW_H - 6;
				b.boxes.add(box);
				cx += fw + GAP;
			} else {
				cx += measure.applyAsInt(part.text) + GAP;
			}
		}
		int headerW = Math.max(40, cx - x + PAD - GAP);
		b.headerW = headerW;
		b.headerH = top + ROW_H;
		int cy = y + b.headerH;
		int width = headerW;

		if (b.isC()) {
			b.bodyX = x + C_INDENT;
			b.bodyY = cy;
			int bodyH = layoutStack(b.body, b.bodyX, b.bodyY);
			if (b.body.isEmpty()) {
				bodyH = EMPTY_BODY;
			} else {
				width = Math.max(width, C_INDENT + stackWidth(b.body));
			}
			b.bodyH = bodyH;
			cy += bodyH + C_FOOT;
		}
		b.w = width;
		b.h = cy - y;
	}
}
