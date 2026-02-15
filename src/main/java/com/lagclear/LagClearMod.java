package com.lagclear;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.commands.Commands.literal;

public class LagClearMod implements ModInitializer {
	public static final String MOD_ID = "lagclearmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	
	// 5 dakika = 5 * 60 * 20 ticks = 6000 ticks
	private static final int CLEAR_INTERVAL = 6000;
	private static int tickCounter = 0;

	@Override
	public void onInitialize() {
		LOGGER.info("[Lag Clear Mod] Başlatılıyor...");
		
		// Server tick event'ini dinle
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickCounter++;
			
			// Her 5 dakikada bir temizle
			if (tickCounter >= CLEAR_INTERVAL) {
				clearDroppedItems(server);
				tickCounter = 0;
			}
		});
		
		// /lagclear komutu ekle
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(literal("lagclear")
				.executes(context -> {
					MinecraftServer server = context.getSource().getServer();
					int cleared = clearDroppedItems(server);
					context.getSource().sendSuccess(
						() -> net.minecraft.network.chat.Component.literal(
							"§a[Lag Clear] " + cleared + " adet düşen eşya temizlendi!"
						),
						true
					);
					return 1;
				})
			);
		});
		
		LOGGER.info("[Lag Clear Mod] Başarıyla yüklendi! Düşen eşyalar her 5 dakikada temizlenecek. Komut: /lagclear");
	}
	
	private static int clearDroppedItems(MinecraftServer server) {
		int totalCleared = 0;
		
		// Tüm dünyaları kontrol et
		for (ServerLevel level : server.getAllLevels()) {
			// Çok geniş bir AABB oluştur (dünya sınırları gibi)
			AABB searchBox = new AABB(-30000000, -64, -30000000, 30000000, 320, 30000000);
			
			// Tüm ItemEntity'leri bul ve sil
			for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, searchBox)) {
				itemEntity.discard();
				totalCleared++;
			}
		}
		
		if (totalCleared > 0) {
			LOGGER.info("[Lag Clear Mod] {} adet düşen eşya temizlendi!", totalCleared);
		}
		
		return totalCleared;
	}
}
