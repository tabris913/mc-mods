package com.tosshie.rbd.advancement;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tosshie.rbd.RandomBiomeDimensions;
import com.tosshie.rbd.config.RBDConfig;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * プレイヤーごとのバイオーム発見状況を管理し、MOD単位の進捗を判定する。
 * データはワールド保存フォルダにJSONで永続化する。
 */
public class BiomeAdvancementManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_FILE = "rbd_biome_discoveries.json";
    private static final Type DATA_TYPE = new TypeToken<Map<String, Set<String>>>() {}.getType();

    /** プレイヤーUUID -> 発見済みバイオームID のセット */
    private final Map<String, Set<String>> discoveries = new HashMap<>();

    /** MOD namespace -> そのMODのバイオームプール内バイオーム集合 */
    private Map<String, Set<String>> modBiomes = new HashMap<>();

    /** MOD namespace -> 進捗タイトル */
    private Map<String, String> advancementTitles = new HashMap<>();

    /** プレイヤーUUID -> 達成済みMOD namespace */
    private final Map<String, Set<String>> completedAdvancements = new HashMap<>();

    private MinecraftServer server;

    /**
     * サーバー起動時に初期化する。
     *
     * @param server MinecraftServer
     */
    public void init(MinecraftServer server) {
        this.server = server;
        loadData();
        rebuildModBiomes();
    }

    /**
     * 設定とレジストリからMODごとのバイオーム集合を構築する。
     */
    private void rebuildModBiomes() {
        modBiomes.clear();
        advancementTitles.clear();

        // 設定からadvancementModsを読み取る
        List<? extends String> advMods = RBDConfig.ADVANCEMENT_MODS.get();
        for (String entry : advMods) {
            int colonIndex = entry.indexOf(':');
            if (colonIndex > 0) {
                String namespace = entry.substring(0, colonIndex);
                String title = entry.substring(colonIndex + 1);
                advancementTitles.put(namespace, title);
            }
        }

        // biomePoolからMODごとのバイオーム集合を構築
        Registry<Biome> biomeRegistry = server.registryAccess().registryOrThrow(Registries.BIOME);
        List<? extends String> pool = RBDConfig.BIOME_POOL.get();
        Set<String> excludes = new HashSet<>(RBDConfig.BIOME_EXCLUDE.get());

        for (String pattern : pool) {
            if (pattern.endsWith(":*")) {
                String namespace = pattern.substring(0, pattern.length() - 2);
                if (!advancementTitles.containsKey(namespace)) continue;
                Set<String> biomes = biomeRegistry.keySet().stream()
                        .filter(loc -> loc.getNamespace().equals(namespace))
                        .map(ResourceLocation::toString)
                        .filter(id -> !excludes.contains(id))
                        .collect(Collectors.toSet());
                modBiomes.put(namespace, biomes);
            } else {
                int colonIndex = pattern.indexOf(':');
                if (colonIndex <= 0) continue;
                String namespace = pattern.substring(0, colonIndex);
                if (!advancementTitles.containsKey(namespace)) continue;
                if (!excludes.contains(pattern) && biomeRegistry.containsKey(new ResourceLocation(pattern))) {
                    modBiomes.computeIfAbsent(namespace, k -> new HashSet<>()).add(pattern);
                }
            }
        }

        RandomBiomeDimensions.LOGGER.info("進捗対象MOD: {}", advancementTitles.keySet());
        for (Map.Entry<String, Set<String>> entry : modBiomes.entrySet()) {
            RandomBiomeDimensions.LOGGER.info("  {}: {}バイオーム", entry.getKey(), entry.getValue().size());
        }
    }

    /**
     * プレイヤーがバイオームを発見したときに呼ばれる。
     *
     * @param player プレイヤー
     * @param biomeId バイオームのリソースロケーション文字列
     * @return 新規発見の場合true
     */
    public boolean onBiomeDiscovered(ServerPlayer player, String biomeId) {
        String uuid = player.getStringUUID();
        Set<String> playerDiscoveries = discoveries.computeIfAbsent(uuid, k -> new HashSet<>());

        if (!playerDiscoveries.add(biomeId)) {
            return false;
        }

        // 進捗チェック
        int colonIndex = biomeId.indexOf(':');
        if (colonIndex > 0) {
            String namespace = biomeId.substring(0, colonIndex);
            checkAdvancement(player, namespace);
        }

        saveData();
        return true;
    }

    /**
     * 指定MODの進捗条件を満たしたか確認し、達成時にメッセージを送信する。
     */
    private void checkAdvancement(ServerPlayer player, String namespace) {
        if (!advancementTitles.containsKey(namespace)) return;

        String uuid = player.getStringUUID();
        Set<String> completed = completedAdvancements.computeIfAbsent(uuid, k -> new HashSet<>());
        if (completed.contains(namespace)) return;

        Set<String> required = modBiomes.get(namespace);
        if (required == null || required.isEmpty()) return;

        Set<String> playerDiscoveries = discoveries.getOrDefault(uuid, Collections.emptySet());
        if (playerDiscoveries.containsAll(required)) {
            completed.add(namespace);
            String title = advancementTitles.get(namespace);
            player.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal(player.getName().getString() + " が挑戦 ")
                            .append(Component.literal("[" + title + "]").withStyle(ChatFormatting.DARK_PURPLE))
                            .append(Component.literal(" を達成した！")),
                    false
            );
            RandomBiomeDimensions.LOGGER.info("{} が進捗 [{}] を達成", player.getName().getString(), title);
            saveData();
        }
    }

    /**
     * 指定プレイヤーの指定MODの発見進捗を取得する。
     *
     * @param playerUUID プレイヤーUUID
     * @param namespace MODネームスペース
     * @return [発見数, 必要数]
     */
    public int[] getProgress(String playerUUID, String namespace) {
        Set<String> required = modBiomes.getOrDefault(namespace, Collections.emptySet());
        Set<String> playerDiscoveries = discoveries.getOrDefault(playerUUID, Collections.emptySet());
        int found = (int) playerDiscoveries.stream()
                .filter(b -> b.startsWith(namespace + ":"))
                .filter(required::contains)
                .count();
        return new int[]{found, required.size()};
    }

    /**
     * 永続化データをロードする。
     */
    private void loadData() {
        Path dataPath = getDataPath();
        if (Files.exists(dataPath)) {
            try {
                String json = Files.readString(dataPath);
                Map<String, Set<String>> loaded = GSON.fromJson(json, DATA_TYPE);
                if (loaded != null) {
                    discoveries.clear();
                    discoveries.putAll(loaded);
                }
            } catch (IOException e) {
                RandomBiomeDimensions.LOGGER.error("バイオーム発見データの読み込みに失敗", e);
            }
        }

        // 達成済み進捗を復元
        completedAdvancements.clear();
        for (Map.Entry<String, Set<String>> entry : discoveries.entrySet()) {
            String uuid = entry.getKey();
            Set<String> playerBiomes = entry.getValue();
            for (Map.Entry<String, Set<String>> modEntry : modBiomes.entrySet()) {
                if (playerBiomes.containsAll(modEntry.getValue()) && !modEntry.getValue().isEmpty()) {
                    completedAdvancements.computeIfAbsent(uuid, k -> new HashSet<>()).add(modEntry.getKey());
                }
            }
        }
    }

    /**
     * 永続化データを保存する。
     */
    private void saveData() {
        Path dataPath = getDataPath();
        try {
            Files.createDirectories(dataPath.getParent());
            Files.writeString(dataPath, GSON.toJson(discoveries, DATA_TYPE));
        } catch (IOException e) {
            RandomBiomeDimensions.LOGGER.error("バイオーム発見データの保存に失敗", e);
        }
    }

    private Path getDataPath() {
        return server.getWorldPath(LevelResource.ROOT).resolve(DATA_FILE);
    }
}
