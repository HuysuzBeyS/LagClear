package com.lagclear.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LagClearConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("lagclearmod");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String CONFIG_DIR = "config";
	private static final String CONFIG_FILE = "lagclearmod.json";
	
	public static class ConfigData {
		public boolean enabled = true;
		public int cleanupIntervalMinutes = 5;
	}
	
	private static ConfigData config;
	
	public static void loadConfig() {
		try {
			// Config dizini oluştur
			Path configPath = Paths.get(CONFIG_DIR);
			if (!Files.exists(configPath)) {
				Files.createDirectories(configPath);
			}
			
			File configFile = new File(CONFIG_DIR, CONFIG_FILE);
			
			// Config dosyası varsa oku, yoksa varsayılan oluştur
			if (configFile.exists()) {
				try (FileReader reader = new FileReader(configFile)) {
					config = GSON.fromJson(reader, ConfigData.class);
					LOGGER.info("[Lag Clear Mod] Config yüklendi: enabled={}, interval={}dk", 
						config.enabled, config.cleanupIntervalMinutes);
				}
			} else {
				config = new ConfigData();
				saveConfig();
				LOGGER.info("[Lag Clear Mod] Yeni config dosyası oluşturuldu: {}", configFile.getName());
			}
		} catch (IOException e) {
			LOGGER.error("[Lag Clear Mod] Config yüklenirken hata:", e);
			config = new ConfigData(); // Varsayılan config kullan
		}
	}
	
	public static void saveConfig() {
		try {
			Path configPath = Paths.get(CONFIG_DIR);
			if (!Files.exists(configPath)) {
				Files.createDirectories(configPath);
			}
			
			File configFile = new File(CONFIG_DIR, CONFIG_FILE);
			try (FileWriter writer = new FileWriter(configFile)) {
				GSON.toJson(config, writer);
				LOGGER.info("[Lag Clear Mod] Config kaydedildi");
			}
		} catch (IOException e) {
			LOGGER.error("[Lag Clear Mod] Config kaydedilirken hata:", e);
		}
	}
	
	public static boolean isEnabled() {
		return config.enabled;
	}
	
	public static int getCleanupIntervalMinutes() {
		return config.cleanupIntervalMinutes;
	}
	
	public static void setEnabled(boolean enabled) {
		config.enabled = enabled;
		saveConfig();
	}
	
	public static void setCleanupIntervalMinutes(int minutes) {
		if (minutes < 1) {
			LOGGER.warn("[Lag Clear Mod] Minimum temizleme aralığı 1 dakika olması gerekir");
			config.cleanupIntervalMinutes = 1;
		} else {
			config.cleanupIntervalMinutes = minutes;
		}
		saveConfig();
	}
	
	public static int getCleanupIntervalTicks() {
		// Dakika from ticks'e dönüştür (1 dakika = 20 ticks * 60 saniye)
		return config.cleanupIntervalMinutes * 60 * 20;
	}
}
