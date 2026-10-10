package dev.cbscratch.ui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import dev.cbscratch.compile.Compiler;
import dev.cbscratch.model.Block;
import dev.cbscratch.model.BlockDef;
import dev.cbscratch.model.BlockRegistry;
import dev.cbscratch.model.Category;
import dev.cbscratch.model.Script;
import dev.cbscratch.model.Workspace;
import dev.cbscratch.CbScratchClient;
import dev.cbscratch.place.ChainPlacer;
import dev.cbscratch.place.Dir;
import dev.cbscratch.place.PlacementContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/**
 * Scratch-подобный редактор: палитра блоков слева, рабочая область по центру,
 * предпросмотр команд снизу. Блоки перетаскиваются мышью и защёлкиваются друг на друга.
 */
public final class ScratchScreen extends Screen {
	private static final int TOP_H = 24;
	private static final int TAB_H = 14;
	private static final int SNAP = 18;
	private static final int MAX_FIELD_PX = 150;

	// ---- состояние ----
	private final Workspace ws;
	private final PlacementContext placement;
	private final Map<Category, List<Block>> palette = new EnumMap<>(Category.class);
	private BlockLayout layout;

	private Category cat = Category.EVENTS;
	private Script active;
	private int paletteScroll;
	private int paletteContentH;
	private int previewScroll;
	private int panX = 24;
	private int panY = 24;
	private boolean panning;
	private int lastMx;
	private int lastMy;

	// перетаскивание
	private List<Block> drag;
	private int dragOffX;
	private int dragOffY;

	// редактирование поля
	private Block editBlock;
	private BlockDef.Field editField;
	private final StringBuilder editBuf = new StringBuilder();

	// выпадающий список
	private Block popBlock;
	private BlockDef.Field popField;
	private int popX;
	private int popY;
	private int popW;

	private String status = "";
	private long statusUntil;
	private long clearArmedUntil;

	// раскладка экрана (пересчитывается каждый кадр)
	private int paletteW;
	private int prevH;
	private int wsLeft;
	private int wsTop;
	private int wsBottom;
	private int palTop;

	private record Btn(int x, int y, int w, int h, Runnable action) {
		boolean contains(int px, int py) {
			return px >= x && px < x + w && py >= y && py < y + h;
		}
	}

	private final List<Btn> buttons = new ArrayList<>();
	private final List<int[]> tabRects = new ArrayList<>();

	public ScratchScreen(Workspace ws, PlacementContext placement) {
		super(Text.literal("Command Block Scratch"));
		this.ws = ws;
		this.placement = placement;
		for (Category c : Category.values()) {
			List<Block> list = new ArrayList<>();
			for (BlockDef d : BlockRegistry.inCategory(c)) {
				list.add(new Block(d));
			}
			palette.put(c, list);
		}
		if (!ws.scripts.isEmpty()) {
			active = ws.scripts.get(0);
		}
	}

	@Override
	protected void init() {
		layout = new BlockLayout(s -> textRenderer.getWidth(s), this::fieldText);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void removed() {
		commitEdit();
		if (drag != null) {
			// не теряем блоки, если экран закрылся посреди перетаскивания
			Script s = new Script(-panX + 8, -panY + 8);
			s.blocks.addAll(drag);
			ws.scripts.add(s);
			drag = null;
		}
		ws.save(CbScratchClient.workspaceFile());
	}

	// ======================================================================
	// Вспомогательное
	// ======================================================================

	private int mouseX() {
		return (int) (client.mouse.getX() * client.getWindow().getScaledWidth() / (double) client.getWindow().getWidth());
	}

	private int mouseY() {
		return (int) (client.mouse.getY() * client.getWindow().getScaledHeight() / (double) client.getWindow().getHeight());
	}

	private void say(String msg) {
		status = msg;
		statusUntil = System.currentTimeMillis() + 4000;
	}

	private String clipHead(String s, int max) {
		if (textRenderer.getWidth(s) <= max) {
			return s;
		}
		String t = s;
		while (!t.isEmpty() && textRenderer.getWidth(t + "…") > max) {
			t = t.substring(0, t.length() - 1);
		}
		return t + "…";
	}

	private String clipTail(String s, int max) {
		if (textRenderer.getWidth(s) <= max) {
			return s;
		}
		String t = s;
		while (!t.isEmpty() && textRenderer.getWidth("…" + t) > max) {
			t = t.substring(1);
		}
		return "…" + t;
	}

	/** Что показывать внутри поля блока. */
	private String fieldText(Block b, BlockDef.Field f) {
		if (b == editBlock && f == editField) {
			return clipTail(editBuf.toString(), MAX_FIELD_PX);
		}
		String v = b.value(f.key);
		if (f.kind == BlockDef.Kind.CHOICE) {
			return v + " ▾";
		}
		return clipHead(v, MAX_FIELD_PX);
	}

	private Script activeScript() {
		if (active != null && ws.scripts.contains(active)) {
			return active;
		}
		for (Script s : ws.scripts) {
			if (s.hasHat()) {
				return s;
			}
		}
		return ws.scripts.isEmpty() ? null : ws.scripts.get(0);
	}

	// ======================================================================
	// Отрисовка
	// ======================================================================

	@Override
	public void render(DrawContext ctx, int mx, int my, float delta) {
		paletteW = Math.max(190, Math.min(270, width * 34 / 100));
		prevH = Math.max(64, Math.min(110, height / 4));
		wsLeft = paletteW;
		wsTop = TOP_H;
		wsBottom = height - prevH;
		palTop = TOP_H + TAB_H * 2;

		if (panning) {
			panX += mx - lastMx;
			panY += my - lastMy;
		}
		lastMx = mx;
		lastMy = my;

		buttons.clear();
		tabRects.clear();

		ctx.fill(0, 0, width, height, 0xFF1B1B21);

		drawWorkspace(ctx);
		drawPalette(ctx, mx, my);
		drawTopBar(ctx, mx, my);
		drawTabs(ctx);
		drawPreview(ctx);

		if (drag != null) {
			layout.layoutStack(drag, mx - dragOffX, my - dragOffY);
			drawStack(ctx, drag);
		}
		drawPopup(ctx, mx, my);
	}

	private void drawWorkspace(DrawContext ctx) {
		ctx.fill(wsLeft, wsTop, width, wsBottom, 0xFF2B2B35);
		int grid = 24;
		for (int gx = wsLeft + Math.floorMod(panX, grid); gx < width; gx += grid) {
			ctx.fill(gx, wsTop, gx + 1, wsBottom, 0xFF31313D);
		}
		for (int gy = wsTop + Math.floorMod(panY, grid); gy < wsBottom; gy += grid) {
			ctx.fill(wsLeft, gy, width, gy + 1, 0xFF31313D);
		}

		ctx.enableScissor(wsLeft, wsTop, width, wsBottom);
		int ox = wsLeft + panX;
		int oy = wsTop + panY;
		for (Script s : ws.scripts) {
			layout.layoutStack(s.blocks, ox + s.x, oy + s.y);
			drawStack(ctx, s.blocks);
		}
		Script act = activeScript();
		if (act != null && ws.scripts.size() > 1 && !act.blocks.isEmpty()) {
			Block f = act.blocks.get(0);
			ctx.drawStrokedRectangle(f.x - 3, f.y - 3, f.w + 6, f.headerH + 6, 0xAAFFFFFF);
		}
		if (ws.scripts.isEmpty() && drag == null) {
			ctx.drawText(textRenderer, "Перетащите блоки слева сюда.", wsLeft + 16, wsTop + 20, 0xFFB0B0C0, false);
			ctx.drawText(textRenderer, "Начните с блока из вкладки «События».", wsLeft + 16, wsTop + 32, 0xFFB0B0C0,
					false);
			ctx.drawText(textRenderer, "ПКМ по блоку — удалить, перетащить в палитру — тоже удалить.", wsLeft + 16,
					wsTop + 44, 0xFF808090, false);
		}
		ctx.disableScissor();
	}

	private void drawPalette(DrawContext ctx, int mx, int my) {
		ctx.fill(0, palTop, paletteW, wsBottom, 0xFF232329);
		ctx.fill(paletteW - 1, palTop, paletteW, wsBottom, 0xFF111116);

		ctx.enableScissor(0, palTop, paletteW - 1, wsBottom);
		int y = palTop + 8 - paletteScroll;
		int start = y;
		for (Block b : palette.get(cat)) {
			layout.layout(b, 8, y);
			drawBlock(ctx, b);
			y += b.h + 8;
		}
		paletteContentH = y - start + 8;
		ctx.disableScissor();

		int maxScroll = Math.max(0, paletteContentH - (wsBottom - palTop));
		paletteScroll = Math.max(0, Math.min(paletteScroll, maxScroll));
	}

	private void drawTabs(DrawContext ctx) {
		Category[] cats = Category.values();
		int perRow = 4;
		int tw = paletteW / perRow;
		for (int i = 0; i < cats.length; i++) {
			int col = i % perRow;
			int row = i / perRow;
			int x = col * tw;
			int y = TOP_H + row * TAB_H;
			int w = (col == perRow - 1 || i == cats.length - 1) ? paletteW - x : tw;
			boolean sel = cats[i] == cat;
			ctx.fill(x, y, x + w, y + TAB_H, sel ? cats[i].color : cats[i].dark());
			if (sel) {
				ctx.fill(x, y + TAB_H - 2, x + w, y + TAB_H, 0xFFFFFFFF);
			}
			String label = cats[i].title;
			ctx.drawText(textRenderer, label, x + (w - textRenderer.getWidth(label)) / 2, y + 3, 0xFFFFFFFF, true);
			tabRects.add(new int[] { x, y, w, TAB_H, i });
		}
	}

	private void drawTopBar(DrawContext ctx, int mx, int my) {
		ctx.fill(0, 0, width, TOP_H, 0xFF14141A);
		int x = 6;
		Dir dir = Dir.parse(ws.dir);
		x = button(ctx, mx, my, x, "Направление: " + dir.title, () -> ws.dir = Dir.parse(ws.dir).next().name());
		x = button(ctx, mx, my, x, "Поставить скрипт", this::placeActive);
		x = button(ctx, mx, my, x, "Поставить все", this::placeAll);
		x = button(ctx, mx, my, x, "Копировать команды", this::copyCommands);
		boolean armed = System.currentTimeMillis() < clearArmedUntil;
		button(ctx, mx, my, x, armed ? "Точно очистить?" : "Очистить", this::clearAll);
		String close = "Закрыть";
		int cw = textRenderer.getWidth(close) + 12;
		int cx = width - cw - 6;
		drawButton(ctx, mx, my, cx, 4, cw, close);
		buttons.add(new Btn(cx, 4, cw, TOP_H - 8, this::close));
	}

	private int button(DrawContext ctx, int mx, int my, int x, String label, Runnable action) {
		int w = textRenderer.getWidth(label) + 12;
		drawButton(ctx, mx, my, x, 4, w, label);
		buttons.add(new Btn(x, 4, w, TOP_H - 8, action));
		return x + w + 4;
	}

	private void drawButton(DrawContext ctx, int mx, int my, int x, int y, int w, String label) {
		int h = TOP_H - 8;
		boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
		ctx.fill(x, y, x + w, y + h, hover ? 0xFF5A5A70 : 0xFF3A3A4A);
		ctx.fill(x, y, x + w, y + 1, 0xFF6A6A80);
		ctx.drawText(textRenderer, label, x + 6, y + (h - 9) / 2 + 1, 0xFFFFFFFF, false);
	}

	private void drawPreview(DrawContext ctx) {
		int top = wsBottom;
		ctx.fill(0, top, width, height, 0xFF14141A);
		ctx.fill(0, top, width, top + 1, 0xFF3A3A4A);

		boolean showStatus = System.currentTimeMillis() < statusUntil;
		String head = showStatus ? status : "Команды выбранного скрипта (колёсико — прокрутка)";
		ctx.drawText(textRenderer, head, 8, top + 4, showStatus ? 0xFF8CE08C : 0xFF9090A0, false);

		Script s = activeScript();
		List<String> lines = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		List<Integer> tags = new ArrayList<>();
		if (s == null) {
			lines.add("Скриптов пока нет");
			colors.add(0xFF808090);
			tags.add(0);
		} else {
			Compiler.Result r = Compiler.compile(s);
			for (String w : new LinkedHashSet<>(r.warnings())) {
				lines.add("! " + w);
				colors.add(0xFFFFD36B);
				tags.add(0);
			}
			int i = 1;
			for (Compiler.Step st : r.steps()) {
				lines.add(i++ + ". " + st.command());
				colors.add(0xFFE8E8F0);
				tags.add(switch (st.mode()) {
					case IMPULSE -> 0xFFE08A2E;
					case CHAIN -> 0xFF4FA64F;
					case REPEAT -> 0xFF9B59D0;
				});
			}
		}

		int areaTop = top + 16;
		int lineH = 11;
		int visible = Math.max(1, (height - areaTop - 2) / lineH);
		int maxScroll = Math.max(0, lines.size() - visible);
		previewScroll = Math.max(0, Math.min(previewScroll, maxScroll));

		ctx.enableScissor(0, areaTop, width, height);
		for (int i = 0; i < visible && i + previewScroll < lines.size(); i++) {
			int idx = i + previewScroll;
			int y = areaTop + i * lineH;
			int tag = tags.get(idx);
			if (tag != 0) {
				ctx.fill(8, y, 11, y + lineH - 2, tag);
			}
			ctx.drawText(textRenderer, clipHead(lines.get(idx), width - 30), 15, y, colors.get(idx), false);
		}
		ctx.disableScissor();
		if (!lines.isEmpty()) {
			String legend = "оранжевый — импульс · зелёный — цепочка · фиолетовый — повторяющийся";
			ctx.drawText(textRenderer, legend, width - textRenderer.getWidth(legend) - 8, top + 4, 0xFF606070, false);
		}
	}

	private void drawStack(DrawContext ctx, List<Block> list) {
		for (Block b : list) {
			drawBlock(ctx, b);
		}
	}

	private void drawBlock(DrawContext ctx, Block b) {
		int c = b.def.category.color;
		int dk = b.def.category.dark();
		int lt = b.def.category.light();
		int top = b.def.shape == BlockDef.Shape.HAT ? BlockLayout.HAT_EXTRA : 0;

		// шапка-«горка» у блоков события
		if (top > 0) {
			int capW = Math.min(b.headerW, 70);
			for (int i = 0; i < top; i++) {
				int inset = (top - 1 - i) * 2;
				ctx.fill(b.x + inset, b.y + i, b.x + capW - inset, b.y + i + 1, i == 0 ? lt : c);
			}
		}

		int hy = b.y + top;
		// заголовок
		ctx.fill(b.x, hy, b.x + b.headerW, hy + BlockLayout.ROW_H, dk);
		ctx.fill(b.x + 1, hy + 1, b.x + b.headerW - 1, hy + BlockLayout.ROW_H - 1, c);
		ctx.fill(b.x + 1, hy + 1, b.x + b.headerW - 1, hy + 2, lt);

		if (b.isC()) {
			int armBottom = b.bodyY + b.bodyH;
			ctx.fill(b.x, b.y + b.headerH, b.x + BlockLayout.C_INDENT, armBottom, dk);
			ctx.fill(b.x + 1, b.y + b.headerH, b.x + BlockLayout.C_INDENT - 1, armBottom, c);
			int footW = Math.max(48, Math.min(b.headerW, 72));
			ctx.fill(b.x, armBottom, b.x + footW, armBottom + BlockLayout.C_FOOT, dk);
			ctx.fill(b.x + 1, armBottom, b.x + footW - 1, armBottom + BlockLayout.C_FOOT - 1, c);
		}

		// подписи и поля
		int cx = b.x + BlockLayout.PAD;
		int ty = hy + (BlockLayout.ROW_H - 9) / 2 + 1;
		int boxIdx = 0;
		for (BlockDef.Part p : b.def.parts) {
			if (p.isField()) {
				Block.FieldBox box = b.boxes.get(boxIdx++);
				drawField(ctx, b, box);
				cx = box.x + box.w + BlockLayout.GAP;
			} else {
				ctx.drawText(textRenderer, p.text, cx, ty, 0xFFFFFFFF, true);
				cx += textRenderer.getWidth(p.text) + BlockLayout.GAP;
			}
		}

		if (b.isC()) {
			drawStack(ctx, b.body);
		}
	}

	private void drawField(DrawContext ctx, Block b, Block.FieldBox box) {
		boolean editing = b == editBlock && box.field == editField;
		int x = box.x;
		int y = box.y;
		int w = box.w;
		int h = box.h;
		if (editing) {
			ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF4C97FF);
		}
		// «скруглённая» белая плашка
		ctx.fill(x + 1, y, x + w - 1, y + h, 0xFFFFFFFF);
		ctx.fill(x, y + 1, x + w, y + h - 1, 0xFFFFFFFF);
		String shown = fieldText(b, box.field);
		int tx = x + BlockLayout.FIELD_PAD;
		int ty = y + (h - 9) / 2 + 1;
		ctx.drawText(textRenderer, shown, tx, ty, 0xFF3B3B45, false);
		if (editing && (System.currentTimeMillis() / 500) % 2 == 0) {
			int cxp = tx + textRenderer.getWidth(shown);
			ctx.fill(cxp, y + 2, cxp + 1, y + h - 2, 0xFF3B3B45);
		}
	}

	private void drawPopup(DrawContext ctx, int mx, int my) {
		if (popBlock == null) {
			return;
		}
		List<String> opts = popField.options;
		int rowH = 13;
		int w = 0;
		for (String o : opts) {
			w = Math.max(w, textRenderer.getWidth(o));
		}
		popW = w + 12;
		int h = opts.size() * rowH + 2;
		int x = popX;
		int y = popY;
		if (y + h > height) {
			y = Math.max(0, height - h);
		}
		if (x + popW > width) {
			x = Math.max(0, width - popW);
		}
		popX = x;
		popY = y;
		ctx.fill(x - 1, y - 1, x + popW + 1, y + h + 1, 0xFF000000);
		ctx.fill(x, y, x + popW, y + h, 0xFF2E2E3A);
		for (int i = 0; i < opts.size(); i++) {
			int ry = y + 1 + i * rowH;
			boolean hover = mx >= x && mx < x + popW && my >= ry && my < ry + rowH;
			if (hover) {
				ctx.fill(x, ry, x + popW, ry + rowH, 0xFF4C97FF);
			}
			ctx.drawText(textRenderer, opts.get(i), x + 6, ry + 3, 0xFFFFFFFF, false);
		}
	}

	// ======================================================================
	// Поиск блоков под курсором
	// ======================================================================

	private record FieldHit(Block block, Block.FieldBox box) {
	}

	private record Loc(List<Block> list, int index, Script script) {
	}

	private FieldHit hitField(List<Block> list, int mx, int my) {
		for (Block b : list) {
			for (Block.FieldBox fb : b.boxes) {
				if (fb.contains(mx, my)) {
					return new FieldHit(b, fb);
				}
			}
			if (b.isC()) {
				FieldHit h = hitField(b.body, mx, my);
				if (h != null) {
					return h;
				}
			}
		}
		return null;
	}

	private FieldHit hitFieldAny(int mx, int my) {
		for (Script s : ws.scripts) {
			FieldHit h = hitField(s.blocks, mx, my);
			if (h != null) {
				return h;
			}
		}
		return null;
	}

	private Block hitBlock(List<Block> list, int mx, int my) {
		for (Block b : list) {
			if (!b.contains(mx, my)) {
				continue;
			}
			if (b.isC()) {
				Block inner = hitBlock(b.body, mx, my);
				if (inner != null) {
					return inner;
				}
				boolean inHole = mx >= b.bodyX && my >= b.bodyY && my < b.bodyY + b.bodyH;
				if (inHole) {
					continue;
				}
			}
			return b;
		}
		return null;
	}

	private Loc locate(Block target) {
		for (Script s : ws.scripts) {
			Loc l = locateIn(s.blocks, target, s);
			if (l != null) {
				return l;
			}
		}
		return null;
	}

	private Loc locateIn(List<Block> list, Block target, Script s) {
		for (int i = 0; i < list.size(); i++) {
			Block b = list.get(i);
			if (b == target) {
				return new Loc(list, i, s);
			}
			if (b.isC()) {
				Loc l = locateIn(b.body, target, s);
				if (l != null) {
					return l;
				}
			}
		}
		return null;
	}

	// ======================================================================
	// Мышь
	// ======================================================================

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		int mx = mouseX();
		int my = mouseY();
		int btn = click.button();

		// выпадающий список перехватывает всё
		if (popBlock != null) {
			handlePopupClick(mx, my);
			return true;
		}

		boolean inWs = mx >= wsLeft && my >= wsTop && my < wsBottom;

		// клик вне редактируемого поля завершает редактирование
		if (editBlock != null) {
			FieldHit under = inWs ? hitFieldAny(mx, my) : null;
			if (under != null && under.block() == editBlock && under.box().field == editField) {
				return true;
			}
			commitEdit();
		}

		if (btn != 0 && btn != 1) {
			return super.mouseClicked(click, doubled);
		}

		if (btn == 0) {
			for (Btn b : buttons) {
				if (b.contains(mx, my)) {
					b.action().run();
					return true;
				}
			}
			for (int[] t : tabRects) {
				if (mx >= t[0] && mx < t[0] + t[2] && my >= t[1] && my < t[1] + t[3]) {
					cat = Category.values()[t[4]];
					paletteScroll = 0;
					return true;
				}
			}
		}

		// палитра
		if (mx < paletteW && my >= palTop && my < wsBottom) {
			if (btn == 0) {
				Block hit = hitBlock(palette.get(cat), mx, my);
				if (hit != null) {
					drag = new ArrayList<>();
					drag.add(hit.copy());
					dragOffX = mx - hit.x;
					dragOffY = my - hit.y;
				}
			}
			return true;
		}

		// рабочая область
		if (inWs) {
			if (btn == 0) {
				FieldHit fh = hitFieldAny(mx, my);
				if (fh != null) {
					Loc loc = locate(fh.block());
					if (loc != null) {
						active = loc.script();
					}
					openField(fh);
					return true;
				}
				Block hit = null;
				for (Script s : ws.scripts) {
					hit = hitBlock(s.blocks, mx, my);
					if (hit != null) {
						break;
					}
				}
				if (hit != null) {
					startDrag(hit, mx, my);
				} else {
					panning = true;
				}
				return true;
			}
			// ПКМ — удалить блок
			Block hit = null;
			for (Script s : ws.scripts) {
				hit = hitBlock(s.blocks, mx, my);
				if (hit != null) {
					break;
				}
			}
			if (hit != null) {
				Loc loc = locate(hit);
				if (loc != null) {
					loc.list().remove(loc.index());
					if (loc.list() == loc.script().blocks && loc.list().isEmpty()) {
						ws.scripts.remove(loc.script());
					}
				}
			}
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	private void startDrag(Block hit, int mx, int my) {
		Loc loc = locate(hit);
		if (loc == null) {
			return;
		}
		active = loc.script();
		List<Block> tail = new ArrayList<>(loc.list().subList(loc.index(), loc.list().size()));
		loc.list().subList(loc.index(), loc.list().size()).clear();
		if (loc.list() == loc.script().blocks && loc.list().isEmpty()) {
			ws.scripts.remove(loc.script());
		}
		drag = tail;
		dragOffX = mx - hit.x;
		dragOffY = my - hit.y;
	}

	private void openField(FieldHit fh) {
		BlockDef.Field f = fh.box().field;
		if (f.kind == BlockDef.Kind.CHOICE) {
			popBlock = fh.block();
			popField = f;
			popX = fh.box().x;
			popY = fh.box().y + fh.box().h + 1;
		} else {
			editBlock = fh.block();
			editField = f;
			editBuf.setLength(0);
			editBuf.append(fh.block().value(f.key));
		}
	}

	private void handlePopupClick(int mx, int my) {
		List<String> opts = popField.options;
		int rowH = 13;
		if (mx >= popX && mx < popX + popW && my >= popY + 1 && my < popY + 1 + opts.size() * rowH) {
			int idx = (my - popY - 1) / rowH;
			if (idx >= 0 && idx < opts.size()) {
				popBlock.values.put(popField.key, opts.get(idx));
			}
		}
		popBlock = null;
		popField = null;
	}

	private void commitEdit() {
		if (editBlock != null) {
			editBlock.values.put(editField.key, editBuf.toString());
			editBlock = null;
			editField = null;
		}
	}

	@Override
	public boolean mouseReleased(Click click) {
		panning = false;
		if (drag != null && click.button() == 0) {
			drop(mouseX(), mouseY());
			return true;
		}
		return super.mouseReleased(click);
	}

	private static final class Snap {
		double dist;
		List<Block> list;
		int index;
		Script script;
	}

	private void consider(Snap snap, int dx, int dy, int ax, int ay, List<Block> list, int index, Script s) {
		double d = Math.hypot(dx - ax, dy - ay);
		if (d < snap.dist) {
			snap.dist = d;
			snap.list = list;
			snap.index = index;
			snap.script = s;
		}
	}

	private void scanList(List<Block> list, Script s, int dx, int dy, Snap snap) {
		for (int i = 0; i < list.size(); i++) {
			Block b = list.get(i);
			consider(snap, dx, dy, b.x, b.y + b.h, list, i + 1, s);
			if (b.isC()) {
				consider(snap, dx, dy, b.bodyX, b.bodyY, b.body, 0, s);
				scanList(b.body, s, dx, dy, snap);
			}
		}
	}

	private void drop(int mx, int my) {
		List<Block> stack = drag;
		drag = null;
		boolean inWs = mx >= wsLeft && my >= wsTop && my < wsBottom;
		if (!inWs) {
			return; // отпустили над палитрой или панелью — блоки удаляются
		}
		int dx = mx - dragOffX;
		int dy = my - dragOffY;
		int totalH = layout.layoutStack(stack, dx, dy);
		boolean hatDrag = stack.get(0).def.shape == BlockDef.Shape.HAT;

		Snap snap = new Snap();
		snap.dist = SNAP;
		for (Script s : ws.scripts) {
			if (s.blocks.isEmpty()) {
				continue;
			}
			Block first = s.blocks.get(0);
			if (first.def.shape != BlockDef.Shape.HAT) {
				// пристыковать стопку сверху к скрипту без шляпки
				consider(snap, dx, dy + totalH, first.x, first.y, s.blocks, 0, s);
			}
			if (!hatDrag) {
				scanList(s.blocks, s, dx, dy, snap);
			}
		}

		if (snap.list != null) {
			snap.list.addAll(snap.index, stack);
			active = snap.script;
		} else {
			Script ns = new Script(dx - (wsLeft + panX), dy - (wsTop + panY));
			ns.blocks.addAll(stack);
			ws.scripts.add(ns);
			active = ns;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int mx = (int) mouseX;
		int my = (int) mouseY;
		if (my >= wsBottom) {
			previewScroll -= (int) Math.signum(verticalAmount) * 2;
			return true;
		}
		if (mx < paletteW && my >= palTop) {
			paletteScroll -= (int) (verticalAmount * 22);
			return true;
		}
		if (mx >= wsLeft && my >= wsTop) {
			panY += (int) (verticalAmount * 24);
			panX += (int) (horizontalAmount * 24);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	// ======================================================================
	// Клавиатура
	// ======================================================================

	@Override
	public boolean keyPressed(KeyInput input) {
		if (popBlock != null) {
			if (input.isEscape()) {
				popBlock = null;
				popField = null;
			}
			return true;
		}
		if (editBlock != null) {
			if (input.isEnter() || input.isTab()) {
				commitEdit();
			} else if (input.isEscape()) {
				editBlock = null;
				editField = null;
			} else if (input.getKeycode() == GLFW.GLFW_KEY_BACKSPACE) {
				if (input.hasCtrl()) {
					editBuf.setLength(0);
				} else if (editBuf.length() > 0) {
					editBuf.setLength(editBuf.length() - 1);
				}
			} else if (input.isPaste()) {
				String clip = client.keyboard.getClipboard();
				if (clip != null) {
					for (char ch : clip.replace('\n', ' ').replace('\r', ' ').toCharArray()) {
						appendChar(ch);
					}
				}
			}
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(CharInput input) {
		if (editBlock != null && input.isValidChar()) {
			for (char ch : input.asString().toCharArray()) {
				appendChar(ch);
			}
			return true;
		}
		return super.charTyped(input);
	}

	private void appendChar(char ch) {
		if (editBuf.length() >= 256) {
			return;
		}
		if (editField.kind == BlockDef.Kind.NUMBER && "0123456789-.".indexOf(ch) < 0) {
			return;
		}
		editBuf.append(ch);
	}

	// ======================================================================
	// Кнопки
	// ======================================================================

	private void placeActive() {
		Script s = activeScript();
		if (s == null) {
			say("Сначала соберите скрипт");
			return;
		}
		Compiler.Result r = Compiler.compile(s);
		if (!r.ok()) {
			say("Скрипт нельзя поставить: " + (r.warnings().isEmpty() ? "нет команд" : r.warnings().get(0)));
			return;
		}
		if (ChainPlacer.place(client, List.of(r), placement, Dir.parse(ws.dir))) {
			close();
		}
	}

	private void placeAll() {
		List<Compiler.Result> results = new ArrayList<>();
		for (Script s : ws.scripts) {
			Compiler.Result r = Compiler.compile(s);
			if (r.ok()) {
				results.add(r);
			}
		}
		if (results.isEmpty()) {
			say("Нет ни одного готового скрипта (нужен блок из «События» и команды)");
			return;
		}
		if (ChainPlacer.place(client, results, placement, Dir.parse(ws.dir))) {
			close();
		}
	}

	private void copyCommands() {
		Script s = activeScript();
		if (s == null) {
			say("Нечего копировать");
			return;
		}
		Compiler.Result r = Compiler.compile(s);
		if (!r.ok()) {
			say("Нет команд для копирования");
			return;
		}
		StringBuilder sb = new StringBuilder();
		for (Compiler.Step st : r.steps()) {
			sb.append(st.command()).append('\n');
		}
		client.keyboard.setClipboard(sb.toString().trim());
		say("Скопировано команд: " + r.steps().size());
	}

	private void clearAll() {
		long now = System.currentTimeMillis();
		if (now < clearArmedUntil) {
			ws.scripts.clear();
			active = null;
			clearArmedUntil = 0;
			say("Рабочая область очищена");
		} else {
			clearArmedUntil = now + 3000;
		}
	}
}
