package com.lagclear;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.lagclear.config.LagClearConfig;

import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;

public class LagClearMod implements ModInitializer {
	public static final String MOD_ID = "lagclearmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	
	private static int tickCounter = 0;
	private static long lastWarningTick1Min = 0;  // 1 dakika uyarısı
	private static long lastWarningTick30Sec = 0; // 30 saniye uyarısı
	private static boolean actionbarShown = false; // ActionBar gösterildi mi

	@Override
	public void onInitialize() {
		// Config dosyasını yükle
		LagClearConfig.loadConfig();
		
		if (!LagClearConfig.isEnabled()) {
			LOGGER.info("[Lag Clear Mod] Pasif durumdadır. Aktif etmek için: /lagclear config enable");
			return;
		}
		
		LOGGER.info("[Lag Clear Mod] Başlatılıyor...");
		
		// Server tick event'ini dinle
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!LagClearConfig.isEnabled()) {
				return;
			}
			
			tickCounter++;
			int interval = LagClearConfig.getCleanupIntervalTicks();
			int remainingTicks = interval - tickCounter;
			
			// 1 dakika kaldığında uyar (1200 ticks = 60 saniye)
			if (remainingTicks == 1200 && lastWarningTick1Min != tickCounter) {
				broadcastMessage(server, "§e[Lag Clear] Düşen eşyalar 1 DAKIKA içinde temizlenecek!");
				lastWarningTick1Min = tickCounter;
			}
			
			// 30 saniye kaldığında uyar (600 ticks = 30 saniye)
			if (remainingTicks == 600 && lastWarningTick30Sec != tickCounter) {
				broadcastMessage(server, "§6[Lag Clear] Düşen eşyalar 30 SANİYE içinde temizlenecek!");
				lastWarningTick30Sec = tickCounter;
			}
			
			// Son 10 saniye de ActionBar'da geri sayım (200 ticks = 10 saniye)
			if (remainingTicks > 0 && remainingTicks <= 200) {
				int secondsRemaining = (remainingTicks + 19) / 20; // Yukarı yuvarla
				for (var player : server.getPlayerList().getPlayers()) {
					player.displayClientMessage(
						net.minecraft.network.chat.Component.literal("§c⚠ Temizleme: " + secondsRemaining + " saniye"),
						true // ActionBar
					);
				}
			}
			
			// Temizleme zamanı
			if (tickCounter >= interval) {
				int cleared = clearDroppedItems(server);
				broadcastMessage(server, "§a[Lag Clear] Düşen eşyalar temizlendi! (" + cleared + " adet)");
				LOGGER.info("[Lag Clear Mod] Otomatik temizleme: {} eşya kaldırıldı, sonraki temizleme {} dakika sonra", 
					cleared, LagClearConfig.getCleanupIntervalMinutes());
				tickCounter = 0;
				lastWarningTick1Min = 0;
				lastWarningTick30Sec = 0;
				actionbarShown = false;
			}
		});
		
		// /lagclear komutu ekle
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(literal("lagclear")
				.then(literal("config")
					.then(literal("enable")
						.executes(context -> {
							LagClearConfig.setEnabled(true);
							context.getSource().sendSuccess(
								() -> net.minecraft.network.chat.Component.literal("§a[Lag Clear] Mod aktifleştirildi!"),
								true
							);
							return 1;
						})
					)
					.then(literal("disable")
						.executes(context -> {
							LagClearConfig.setEnabled(false);
							context.getSource().sendSuccess(
								() -> net.minecraft.network.chat.Component.literal("§a[Lag Clear] Mod pasifleştirildi!"),
								true
							);
							return 1;
						})
					)
					.then(literal("interval")
						.then(argument("minutes", IntegerArgumentType.integer(1, 1440))
							.executes(context -> {
								int minutes = IntegerArgumentType.getInteger(context, "minutes");
								LagClearConfig.setCleanupIntervalMinutes(minutes);
								context.getSource().sendSuccess(
									() -> net.minecraft.network.chat.Component.literal(
										"§a[Lag Clear] Temizleme aralığı " + minutes + " dakika olarak ayarlandı!"
									),
									true
								);
								return 1;
							})
						)
					)
					.then(literal("status")
						.executes(context -> {
							String status = LagClearConfig.isEnabled() ? "§aAktif" : "§cPasif";
							String interval = String.valueOf(LagClearConfig.getCleanupIntervalMinutes());
							context.getSource().sendSuccess(
								() -> net.minecraft.network.chat.Component.literal(
									"§e[Lag Clear] Durum: " + status + " §e| Aralık: " + interval + " dakika"
								),
								true
							);
							return 1;
						})
					)
				)
				.executes(context -> {
					if (!LagClearConfig.isEnabled()) {
						context.getSource().sendFailure(
							net.minecraft.network.chat.Component.literal("§c[Lag Clear] Mod pasif durumdadır!")
						);
						return 0;
					}
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
		
		LOGGER.info("[Lag Clear Mod] Başarıyla yüklendi! Temizleme aralığı: {} dakika", LagClearConfig.getCleanupIntervalMinutes());
		LOGGER.info("[Lag Clear Mod] Komutlar: /lagclear (manuel temizle) | /lagclear config (ayarlar)");
	}
	
	private static void broadcastMessage(MinecraftServer server, String message) {
		for (var player : server.getPlayerList().getPlayers()) {
			player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
		}
		LOGGER.info(message);
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
