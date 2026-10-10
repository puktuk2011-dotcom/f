package dev.cbscratch;

import java.nio.file.Path;

import org.lwjgl.glfw.GLFW;

import dev.cbscratch.model.Workspace;
import dev.cbscratch.place.ChainPlacer;
import dev.cbscratch.place.PlacementContext;
import dev.cbscratch.ui.ScratchScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;

public final class CbScratchClient implements ClientModInitializer {
	public static final String MOD_ID = "cbscratch";

	private static Workspace workspace;
	private static KeyBinding openKey;

	public static Path workspaceFile() {
		return FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("workspace.json");
	}

	public static Workspace workspace() {
		if (workspace == null) {
			workspace = Workspace.load(workspaceFile());
		}
		return workspace;
	}

	@Override
	public void onInitializeClient() {
		KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
		openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.cbscratch.open", GLFW.GLFW_KEY_K, category));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			ChainPlacer.tick(mc);
			while (openKey.wasPressed()) {
				if (mc.player != null && mc.currentScreen == null) {
					mc.setScreen(new ScratchScreen(workspace(), PlacementContext.capture(mc)));
				}
			}
		});
	}
}
