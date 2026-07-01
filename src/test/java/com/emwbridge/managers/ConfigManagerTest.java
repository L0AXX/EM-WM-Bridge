package com.emwbridge.managers;

import com.emwbridge.EMWMBridge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigManagerTest {

    @Mock(lenient = true)
    private EMWMBridge plugin;

    private ConfigManager configManager;

    @BeforeEach
    void setUp() {
        when(plugin.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
        configManager = new ConfigManager(plugin);
    }

    @Nested
    @DisplayName("配置版本")
    class VersionTests {

        @Test
        @DisplayName("getConfigVersion 应返回当前版本")
        void getConfigVersionShouldReturnCurrentVersion() {
            assertEquals("1.2.0", configManager.getConfigVersion());
        }
    }

    @Nested
    @DisplayName("配置验证")
    class ValidationTests {

        @Test
        @DisplayName("loadAndMigrate 新版本配置应正常加载")
        void loadAndMigrateNewVersionShouldLoadNormally() {
            var config = mock(org.bukkit.configuration.file.FileConfiguration.class);
            when(plugin.getConfig()).thenReturn(config);
            when(config.getString("config-version", "0.0.0")).thenReturn("1.2.0");

            configManager.loadAndMigrate();
        }

        @Test
        @DisplayName("loadAndMigrate 旧版本配置应触发迁移")
        void loadAndMigrateOldVersionShouldTriggerMigration() {
            var config = mock(org.bukkit.configuration.file.FileConfiguration.class);
            when(plugin.getConfig()).thenReturn(config);
            when(config.getString("config-version", "0.0.0")).thenReturn("1.0.0");
            when(config.contains(anyString())).thenReturn(true);

            File dataFolder = new File("build/test-data");
            dataFolder.mkdirs();
            when(plugin.getDataFolder()).thenReturn(dataFolder);

            InputStream stream = getClass().getClassLoader().getResourceAsStream("config.yml");
            when(plugin.getResource("config.yml")).thenReturn(stream);

            configManager.loadAndMigrate();
        }

        @Test
        @DisplayName("loadAndMigrate 默认配置应正常加载")
        void loadAndMigrateDefaultVersionShouldLoad() {
            var config = mock(org.bukkit.configuration.file.FileConfiguration.class);
            when(plugin.getConfig()).thenReturn(config);
            when(config.getString("config-version", "0.0.0")).thenReturn("0.0.0");
            when(config.contains(anyString())).thenReturn(false);

            File dataFolder = new File("build/test-data");
            dataFolder.mkdirs();
            when(plugin.getDataFolder()).thenReturn(dataFolder);

            InputStream stream = getClass().getClassLoader().getResourceAsStream("config.yml");
            when(plugin.getResource("config.yml")).thenReturn(stream);

            configManager.loadAndMigrate();
        }

        @Test
        @DisplayName("loadAndMigrate 资源文件不存在应处理")
        void loadAndMigrateMissingResourceShouldHandle() {
            var config = mock(org.bukkit.configuration.file.FileConfiguration.class);
            when(plugin.getConfig()).thenReturn(config);
            when(config.getString("config-version", "0.0.0")).thenReturn("0.0.0");

            File dataFolder = new File("build/test-data");
            dataFolder.mkdirs();
            when(plugin.getDataFolder()).thenReturn(dataFolder);
            when(plugin.getResource("config.yml")).thenReturn(null);

            assertDoesNotThrow(() -> configManager.loadAndMigrate());
        }
    }
}
