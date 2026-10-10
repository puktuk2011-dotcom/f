package dev.cbscratch.place;

import java.util.ArrayDeque;
import java.util.List;

import dev.cbscratch.compile.Compiler;
import dev.cbscratch.model.BlockDef.Mode;
import net.minecraft.block.entity.CommandBlockBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.c2s.play.UpdateCommandBlockC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Расставляет цепочки командных блоков в мире и записывает в них команды.
 *
 * <p>Работает целиком на клиенте и сервера-мода не требует:
 * <ol>
 *   <li>фаза 1: командой {@code /setblock} ставится обычный командный блок с нужным направлением;</li>
 *   <li>фаза 2: пакетом «обновить командный блок» (как при вводе команды в GUI блока) задаётся команда,
 *       тип (импульс / цепочка / повторяющийся) и «всегда активен».</li>
 * </ol>
 * Для этого нужны права оператора (уровень 2+) и творческий режим. Действия выполняются по очереди,
 * чтобы не спамить сервер.
 */
public final class ChainPlacer {
	private record Task(Runnable action, int delayAfter) {
	}

	private static final ArrayDeque<Task> QUEUE = new ArrayDeque<>();
	private static int cooldown;

	private ChainPlacer() {
	}

	/** Вызывать каждый клиентский тик. */
	public static void tick(MinecraftClient mc) {
		if (mc.player == null || mc.getNetworkHandler() == null) {
			QUEUE.clear();
			cooldown = 0;
			return;
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		Task t = QUEUE.poll();
		if (t != null) {
			t.action().run();
			cooldown = t.delayAfter();
		}
	}

	public static boolean busy() {
		return !QUEUE.isEmpty();
	}

	/**
	 * @return {@code true}, если цепочки поставлены в очередь
	 */
	public static boolean place(MinecraftClient mc, List<Compiler.Result> scripts, PlacementContext pc, Dir dir) {
		if (mc.player == null || mc.getNetworkHandler() == null) {
			return false;
		}
		if (!mc.player.isCreativeLevelTwoOp()) {
			mc.player.sendMessage(Text.literal("§cНужны права оператора и творческий режим"), true);
			return false;
		}
		if (busy()) {
			mc.player.sendMessage(Text.literal("§eПодождите, предыдущая цепочка ещё ставится"), true);
			return false;
		}

		Direction flow = dir.direction != null ? dir.direction : pc.lookFacing();
		Direction right = flow.getAxis() == Direction.Axis.Y ? Direction.EAST : flow.rotateYClockwise();

		record Placed(BlockPos pos, Compiler.Step step) {
		}
		java.util.ArrayList<Placed> placed = new java.util.ArrayList<>();
		int lane = 0;
		for (Compiler.Result r : scripts) {
			if (!r.ok()) {
				continue;
			}
			BlockPos base = pc.origin().offset(right, lane * 2);
			for (int i = 0; i < r.steps().size(); i++) {
				placed.add(new Placed(base.offset(flow, i), r.steps().get(i)));
			}
			lane++;
		}
		if (placed.isEmpty()) {
			mc.player.sendMessage(Text.literal("§eНечего ставить: у скрипта нет блока события или команд"), true);
			return false;
		}

		// Фаза 1: сами блоки.
		for (Placed p : placed) {
			String cmd = "setblock " + p.pos().getX() + " " + p.pos().getY() + " " + p.pos().getZ()
					+ " minecraft:command_block[facing=" + flow.asString() + "]";
			QUEUE.add(new Task(() -> send(mc, cmd), 2));
		}
		// Пауза, чтобы сервер точно успел поставить все блоки.
		QUEUE.add(new Task(() -> {
		}, 6));
		// Фаза 2: команды и режимы.
		for (Placed p : placed) {
			Compiler.Step step = p.step();
			QUEUE.add(new Task(() -> {
				ClientPlayNetworkHandler h = mc.getNetworkHandler();
				if (h != null) {
					h.sendPacket(new UpdateCommandBlockC2SPacket(p.pos(), step.command(), typeOf(step.mode()), true,
							false, step.auto()));
				}
			}, 1));
		}
		QUEUE.add(new Task(() -> mc.player.sendMessage(
				Text.literal("§aГотово: поставлено командных блоков — " + placed.size()), true), 0));
		return true;
	}

	private static void send(MinecraftClient mc, String command) {
		ClientPlayNetworkHandler h = mc.getNetworkHandler();
		if (h != null) {
			h.sendChatCommand(command);
		}
	}

	private static CommandBlockBlockEntity.Type typeOf(Mode m) {
		return switch (m) {
			case IMPULSE -> CommandBlockBlockEntity.Type.REDSTONE;
			case CHAIN -> CommandBlockBlockEntity.Type.SEQUENCE;
			case REPEAT -> CommandBlockBlockEntity.Type.AUTO;
		};
	}
}
