package com.tosshie.rbd.dimension;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Lifecycle;
import com.tosshie.rbd.RandomBiomeDimensions;
import com.tosshie.rbd.config.RBDConfig;
import com.tosshie.rbd.util.DimensionHelper;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * ディメンションの動的生成・登録を行うマネージャ。
 * レジストリへの登録とServerLevelの即時インスタンス化を行い、再起動不要で利用可能にする。
 */
public class DimensionManager {

    /**
     * ディメンションを生成し、即座にServerLevelとして読み込む。
     *
     * @param server MinecraftServer
     * @return 作成されたディメンションのキーリスト
     */
    public static List<ResourceKey<Level>> generateDimensions(MinecraftServer server) {
        int count = RBDConfig.PORTAL_BLOCKS.get().size();
        List<String> poolPatterns = new ArrayList<>(RBDConfig.BIOME_POOL.get());

        RandomBiomeDimensions.LOGGER.info("バイオームプール設定: {}エントリ", poolPatterns.size());

        Registry<Biome> biomeRegistry = server.registryAccess().registryOrThrow(Registries.BIOME);
        List<String> pool = resolveBiomePool(poolPatterns, biomeRegistry);

        RandomBiomeDimensions.LOGGER.info("レジストリ照合後: {}個のバイオームが有効", pool.size());

        if (pool.isEmpty()) {
            RandomBiomeDimensions.LOGGER.warn("バイオームプールに有効なバイオームがありません。設定のバイオームIDがレジストリに存在するか確認してください");
            if (!poolPatterns.isEmpty()) {
                RandomBiomeDimensions.LOGGER.warn("最初の設定値: {}, レジストリに存在: {}",
                        poolPatterns.get(0),
                        biomeRegistry.containsKey(new ResourceLocation(poolPatterns.get(0))));
            }
            return Collections.emptyList();
        }

        Collections.shuffle(pool);
        int actualCount = Math.min(count, pool.size());

        List<ResourceKey<Level>> created = new ArrayList<>();
        for (int i = 0; i < actualCount; i++) {
            ResourceKey<Level> dimKey = DimensionHelper.dimensionKey(i);
            String biomeName = pool.get(i);
            ServerLevel level = getOrCreateLevel(server, dimKey, biomeName);
            if (level != null) {
                created.add(dimKey);
            }
        }

        return created;
    }

    /**
     * ディメンションが既に存在すればそのまま返し、なければ動的に作成して返す。
     */
    @SuppressWarnings("deprecation")
    private static ServerLevel getOrCreateLevel(MinecraftServer server, ResourceKey<Level> levelKey, String biomeName) {
        Map<ResourceKey<Level>, ServerLevel> map = server.forgeGetWorldMap();
        ServerLevel existing = map.get(levelKey);
        if (existing != null) {
            return existing;
        }
        return createAndRegisterLevel(server, map, levelKey, biomeName);
    }

    /**
     * ServerLevelを新規に作成し、サーバーに登録する。
     */
    @SuppressWarnings("deprecation")
    private static ServerLevel createAndRegisterLevel(MinecraftServer server,
                                                      Map<ResourceKey<Level>, ServerLevel> map,
                                                      ResourceKey<Level> levelKey,
                                                      String biomeName) {
        try {
            Registry<Biome> biomeRegistry = server.registryAccess().registryOrThrow(Registries.BIOME);
            Registry<DimensionType> dimTypeRegistry = server.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE);
            Registry<NoiseGeneratorSettings> noiseSettingsRegistry = server.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS);

            ResourceKey<Biome> biomeKey = ResourceKey.create(Registries.BIOME, new ResourceLocation(biomeName));
            BiomeSource biomeSource = new FixedBiomeSource(biomeRegistry.getHolderOrThrow(biomeKey));

            ChunkGenerator chunkGenerator = new NoiseBasedChunkGenerator(
                    biomeSource,
                    noiseSettingsRegistry.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD)
            );

            ResourceKey<DimensionType> dimTypeKey = ResourceKey.create(Registries.DIMENSION_TYPE,
                    new ResourceLocation("minecraft", "overworld"));

            LevelStem stem = new LevelStem(
                    dimTypeRegistry.getHolderOrThrow(dimTypeKey),
                    chunkGenerator
            );

            // レジストリに登録（既に存在する場合もunfreezeして再登録）
            ResourceKey<LevelStem> stemKey = ResourceKey.create(Registries.LEVEL_STEM, levelKey.location());
            Registry<LevelStem> stemRegistry = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
            if (stemRegistry instanceof MappedRegistry<LevelStem> mappedRegistry) {
                mappedRegistry.unfreeze();
                if (!mappedRegistry.containsKey(stemKey)) {
                    mappedRegistry.register(stemKey, stem, Lifecycle.stable());
                }
                mappedRegistry.freeze();
            }

            // ServerLevelをインスタンス化
            ServerLevel overworld = server.overworld();
            WorldData worldData = server.getWorldData();
            DerivedLevelData derivedLevelData = new DerivedLevelData(worldData, worldData.overworldData());

            Executor executor = getExecutorFromServer(server);
            LevelStorageSource.LevelStorageAccess storageSource = getStorageSourceFromServer(server);
            ChunkProgressListenerFactory listenerFactory = getProgressListenerFactoryFromServer(server);
            ChunkProgressListener chunkProgressListener = listenerFactory.create(11);

            WorldOptions worldOptions = worldData.worldGenOptions();
            long seed = worldOptions.seed();

            ServerLevel newLevel = new ServerLevel(
                    server,
                    executor,
                    storageSource,
                    derivedLevelData,
                    levelKey,
                    stem,
                    chunkProgressListener,
                    worldData.isDebugWorld(),
                    BiomeManager.obfuscateSeed(seed),
                    ImmutableList.of(),
                    false,
                    null
            );

            // ワールドボーダーリスナー登録
            overworld.getWorldBorder().addListener(new net.minecraft.world.level.border.BorderChangeListener.DelegateBorderChangeListener(newLevel.getWorldBorder()));

            // サーバーに登録
            map.put(levelKey, newLevel);
            server.markWorldsDirty();

            // ワールドロードイベント発火
            MinecraftForge.EVENT_BUS.post(new LevelEvent.Load(newLevel));

            RandomBiomeDimensions.LOGGER.info("ディメンションを作成・読み込み: {} (バイオーム: {})", levelKey.location(), biomeName);
            return newLevel;

        } catch (Exception e) {
            RandomBiomeDimensions.LOGGER.error("ディメンションの作成に失敗: {} (バイオーム: {})", levelKey.location(), biomeName, e);
            return null;
        }
    }

    /**
     * バニラバイオームのスポーンディメンションを生成する。
     * オーバーワールドと同じMultiNoiseBiomeSourceとノイズ設定を使用する。
     *
     * @param server MinecraftServer
     * @return 生成されたServerLevel
     */
    @SuppressWarnings("deprecation")
    public static ServerLevel getOrCreateSpawnDimension(MinecraftServer server) {
        ResourceKey<Level> spawnKey = DimensionHelper.spawnDimensionKey();
        Map<ResourceKey<Level>, ServerLevel> map = server.forgeGetWorldMap();
        ServerLevel existing = map.get(spawnKey);
        if (existing != null) {
            return existing;
        }
        return createSpawnLevel(server, map, spawnKey);
    }

    /**
     * バニラのオーバーワールドプリセットを使ったスポーンディメンションを作成する。
     */
    @SuppressWarnings("deprecation")
    private static ServerLevel createSpawnLevel(MinecraftServer server,
                                                Map<ResourceKey<Level>, ServerLevel> map,
                                                ResourceKey<Level> levelKey) {
        try {
            Registry<DimensionType> dimTypeRegistry = server.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE);
            Registry<NoiseGeneratorSettings> noiseSettingsRegistry = server.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS);

            // バニラのオーバーワールドプリセットのMultiNoiseBiomeSourceを使用
            Registry<Biome> biomeRegistry = server.registryAccess().registryOrThrow(Registries.BIOME);
            MultiNoiseBiomeSourceParameterList paramList = new MultiNoiseBiomeSourceParameterList(
                    MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD, biomeRegistry.asLookup());
            BiomeSource biomeSource = MultiNoiseBiomeSource.createFromList(paramList.parameters());

            ChunkGenerator chunkGenerator = new NoiseBasedChunkGenerator(
                    biomeSource,
                    noiseSettingsRegistry.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD)
            );

            ResourceKey<DimensionType> dimTypeKey = ResourceKey.create(Registries.DIMENSION_TYPE,
                    new ResourceLocation("minecraft", "overworld"));

            LevelStem stem = new LevelStem(
                    dimTypeRegistry.getHolderOrThrow(dimTypeKey),
                    chunkGenerator
            );

            ResourceKey<LevelStem> stemKey = ResourceKey.create(Registries.LEVEL_STEM, levelKey.location());
            Registry<LevelStem> stemRegistry = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
            if (stemRegistry instanceof MappedRegistry<LevelStem> mappedRegistry) {
                mappedRegistry.unfreeze();
                mappedRegistry.register(stemKey, stem, Lifecycle.stable());
                mappedRegistry.freeze();
            }

            WorldData worldData = server.getWorldData();
            DerivedLevelData derivedLevelData = new DerivedLevelData(worldData, worldData.overworldData());
            Executor executor = getExecutorFromServer(server);
            LevelStorageSource.LevelStorageAccess storageSource = getStorageSourceFromServer(server);
            ChunkProgressListenerFactory listenerFactory = getProgressListenerFactoryFromServer(server);
            ChunkProgressListener chunkProgressListener = listenerFactory.create(11);
            WorldOptions worldOptions = worldData.worldGenOptions();
            long seed = worldOptions.seed();

            ServerLevel newLevel = new ServerLevel(
                    server, executor, storageSource, derivedLevelData, levelKey, stem,
                    chunkProgressListener, worldData.isDebugWorld(),
                    BiomeManager.obfuscateSeed(seed), ImmutableList.of(), false, null
            );

            ServerLevel overworld = server.overworld();
            overworld.getWorldBorder().addListener(
                    new net.minecraft.world.level.border.BorderChangeListener.DelegateBorderChangeListener(newLevel.getWorldBorder()));
            map.put(levelKey, newLevel);
            server.markWorldsDirty();
            MinecraftForge.EVENT_BUS.post(new LevelEvent.Load(newLevel));

            RandomBiomeDimensions.LOGGER.info("スポーンディメンションを作成: {}", levelKey.location());
            return newLevel;
        } catch (Exception e) {
            RandomBiomeDimensions.LOGGER.error("スポーンディメンションの作成に失敗", e);
            return null;
        }
    }

    /**
     * ワールドボーダーを設定する。
     */
    public static void setupWorldBorder(ServerLevel level) {
        int size = RBDConfig.WORLD_SIZE.get();
        WorldBorder border = level.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(size);
    }

    /**
     * 既存のRBDディメンションをレジストリからマークする（完全な削除には再起動が必要）。
     */
    @SuppressWarnings("deprecation")
    public static void removeDimensions(MinecraftServer server) {
        Map<ResourceKey<Level>, ServerLevel> map = server.forgeGetWorldMap();
        List<ResourceKey<Level>> toRemove = new ArrayList<>();
        for (ResourceKey<Level> key : map.keySet()) {
            if (DimensionHelper.isRBDDimension(key)) {
                toRemove.add(key);
            }
        }
        for (ResourceKey<Level> key : toRemove) {
            ServerLevel level = map.remove(key);
            if (level != null) {
                level.save(null, false, level.noSave());
                // ディメンションのワールドデータを削除
                deleteWorldData(level);
            }
        }
        if (!toRemove.isEmpty()) {
            server.markWorldsDirty();
            RandomBiomeDimensions.LOGGER.info("{}個のRBDディメンションを削除しました", toRemove.size());
        }
    }

    /**
     * ディメンションのワールドデータを削除してチャンクデータをリセットする。
     */
    private static void deleteWorldData(ServerLevel level) {
        try {
            java.nio.file.Path worldDir = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
            // ディメンションのフォルダはdimensions/<namespace>/<path>
            ResourceKey<Level> dimKey = level.dimension();
            java.nio.file.Path dimPath = worldDir.resolve("dimensions")
                    .resolve(dimKey.location().getNamespace())
                    .resolve(dimKey.location().getPath());
            if (java.nio.file.Files.exists(dimPath)) {
                deleteRecursive(dimPath.toFile());
                RandomBiomeDimensions.LOGGER.info("ディメンションデータを削除: {}", dimPath);
            }
        } catch (Exception e) {
            RandomBiomeDimensions.LOGGER.error("ディメンションデータの削除に失敗", e);
        }
    }

    private static void deleteRecursive(java.io.File file) {
        if (file.isDirectory()) {
            java.io.File[] children = file.listFiles();
            if (children != null) {
                for (java.io.File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    /**
     * バイオームプールパターンを実際に存在するバイオームIDに解決する。
     * ワイルドカード（"namespace:*"）に対応。
     */
    private static List<String> resolveBiomePool(List<String> patterns, Registry<Biome> biomeRegistry) {
        List<String> resolved = new ArrayList<>();
        for (String pattern : patterns) {
            if (pattern.endsWith(":*")) {
                String namespace = pattern.substring(0, pattern.length() - 2);
                biomeRegistry.keySet().stream()
                        .filter(loc -> loc.getNamespace().equals(namespace))
                        .map(ResourceLocation::toString)
                        .forEach(resolved::add);
            } else {
                if (biomeRegistry.containsKey(new ResourceLocation(pattern))) {
                    resolved.add(pattern);
                }
            }
        }
        return resolved.stream().distinct().collect(Collectors.toList());
    }

    // --- リフレクションヘルパー（型ベースで検索） ---

    private static Executor getExecutorFromServer(MinecraftServer server) {
        try {
            for (Field field : MinecraftServer.class.getDeclaredFields()) {
                if (field.getType() == Executor.class) {
                    field.setAccessible(true);
                    return (Executor) field.get(server);
                }
            }
            throw new NoSuchFieldException("Executor field not found in MinecraftServer");
        } catch (Exception e) {
            throw new RuntimeException("MinecraftServer.executorフィールドへのアクセスに失敗", e);
        }
    }

    private static LevelStorageSource.LevelStorageAccess getStorageSourceFromServer(MinecraftServer server) {
        try {
            for (Field field : MinecraftServer.class.getDeclaredFields()) {
                if (field.getType() == LevelStorageSource.LevelStorageAccess.class) {
                    field.setAccessible(true);
                    return (LevelStorageSource.LevelStorageAccess) field.get(server);
                }
            }
            throw new NoSuchFieldException("LevelStorageAccess field not found in MinecraftServer");
        } catch (Exception e) {
            throw new RuntimeException("MinecraftServer.storageSourceフィールドへのアクセスに失敗", e);
        }
    }

    private static ChunkProgressListenerFactory getProgressListenerFactoryFromServer(MinecraftServer server) {
        try {
            for (Field field : MinecraftServer.class.getDeclaredFields()) {
                if (field.getType() == ChunkProgressListenerFactory.class) {
                    field.setAccessible(true);
                    return (ChunkProgressListenerFactory) field.get(server);
                }
            }
            throw new NoSuchFieldException("ChunkProgressListenerFactory field not found in MinecraftServer");
        } catch (Exception e) {
            throw new RuntimeException("MinecraftServer.progressListenerFactoryフィールドへのアクセスに失敗", e);
        }
    }

}
