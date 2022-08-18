package net.jacg.simple_trade;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SimpleTrade implements ModInitializer {
	public static Map<UUID, Set<UUID>> WAIT_LIST = new HashMap<>();

	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register(TradeCommand::register);
	}
}
