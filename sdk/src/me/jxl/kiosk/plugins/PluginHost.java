// SPDX-License-Identifier: Apache-2.0
package me.jxl.kiosk.plugins;

import java.util.Map;

/** Versioned host API. Access is scoped to the plugin and revoked when it stops. */
public interface PluginHost {
    interface CommandCallback { void onResult(boolean ok, Object data, String error); }
    default void executeCommand(String command, Map<String, Object> arguments, CommandCallback callback) { throw new UnsupportedOperationException("SDK 1 required"); }
    default void subscribe(String event) { throw new UnsupportedOperationException("SDK 1 required"); }
    default void unsubscribe(String event) { throw new UnsupportedOperationException("SDK 1 required"); }
    default Map<String, Object> shizukuState() { throw new UnsupportedOperationException("Shizuku is unavailable"); }
    default void executeShizuku(String[] command, int timeoutMs, CommandCallback callback) { throw new UnsupportedOperationException("Shizuku is unavailable"); }
    default void publishScreensaver(String key, String title, String html) { throw new UnsupportedOperationException("Screensavers are unavailable"); }
    default void publishScreensaverAsset(String key, String title, String entry, Map<String, Object> data) { throw new UnsupportedOperationException("Screensaver assets are unavailable"); }
    default void removeScreensaver(String key) { throw new UnsupportedOperationException("Screensavers are unavailable"); }
    default void publishSeries(String key, Map<String, Object> chart) { throw new UnsupportedOperationException("Charts are unavailable"); }
    default void removeSeries(String key) { throw new UnsupportedOperationException("Charts are unavailable"); }
    default void publishStatusTile(String key, String title, String level, String text) { throw new UnsupportedOperationException("Status tiles are unavailable"); }
    default void removeStatusTile(String key) { throw new UnsupportedOperationException("Status tiles are unavailable"); }
    void showWindow(String title, String message, String buttonLabel);
    void hideWindow();
    void log(String message);
    default String nativeLibraryPath(String name) { throw new UnsupportedOperationException("SDK 1 required"); }
    default String packagePath() { throw new UnsupportedOperationException("SDK 1 required"); }
    default void status(String message, boolean error) { throw new UnsupportedOperationException("SDK 1 required"); }
    default void saveSettings(Map<String, Object> values) { throw new UnsupportedOperationException("SDK 1 required"); }
    default void publishLight(String key, String name, String[] effects, Map<String, Object> state) { throw new UnsupportedOperationException("SDK 1 required"); }
    default void removeLight(String key) { throw new UnsupportedOperationException("SDK 1 required"); }
    default void publishSensor(String key, String name, Map<String, Object> metadata, Double state) { throw new UnsupportedOperationException("SDK 1 required"); }
    default void removeSensor(String key) { throw new UnsupportedOperationException("SDK 1 required"); }
    default void publishTextSensor(String key, String name, String state) { throw new UnsupportedOperationException("SDK 1 required"); }
    default void removeTextSensor(String key) { throw new UnsupportedOperationException("SDK 1 required"); }
    default void publishBinarySensor(String key, String name, String deviceClass, Boolean state) { throw new UnsupportedOperationException("SDK 1 required"); }
    default void removeBinarySensor(String key) { throw new UnsupportedOperationException("SDK 1 required"); }
    default void publishSwitch(String key, String name, boolean state) { throw new UnsupportedOperationException("SDK 1 required"); }
    default void removeSwitch(String key) { throw new UnsupportedOperationException("SDK 1 required"); }
    default void publishSelect(String key, String name, String[] options, String state) { throw new UnsupportedOperationException("SDK 1 required"); }
    default void removeSelect(String key) { throw new UnsupportedOperationException("SDK 1 required"); }
}
