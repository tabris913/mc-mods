package com.tosshie.rbd.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.tosshie.rbd.RandomBiomeDimensions;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * configディレクトリ内のルートテーブルJSONファイルを管理する。
 * 初回起動時にデフォルトのルートテーブルを自動生成し、
 * 以降はユーザーが編集したファイルを読み込む。
 */
public final class LootTableFileManager {

    private static final String CONFIG_SUBDIR = "randombimedimensions";
    private static final String LOOT_TABLE_FILE = "rbd_mob_drops.json";
    private static final String DEFAULT_RESOURCE = "/data/randombimedimensions/default_loot_table.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private LootTableFileManager() {
    }

    /**
     * ルートテーブルファイルのパスを返す。
     * ファイルが存在しない場合はデフォルトを生成する。
     *
     * @return ルートテーブルJSONファイルのパス
     */
    public static Path getOrCreateLootTableFile() {
        Path configDir = FMLPaths.CONFIGDIR.get().resolve(CONFIG_SUBDIR);
        Path lootFile = configDir.resolve(LOOT_TABLE_FILE);

        if (!Files.exists(lootFile)) {
            try {
                Files.createDirectories(configDir);
                try (InputStream in = LootTableFileManager.class.getResourceAsStream(DEFAULT_RESOURCE)) {
                    if (in != null) {
                        Files.copy(in, lootFile);
                    } else {
                        RandomBiomeDimensions.LOGGER.error("デフォルトルートテーブルリソースが見つかりません: {}", DEFAULT_RESOURCE);
                    }
                }
                RandomBiomeDimensions.LOGGER.info("デフォルトルートテーブルを生成しました: {}", lootFile);
            } catch (IOException e) {
                RandomBiomeDimensions.LOGGER.error("ルートテーブルファイルの生成に失敗しました", e);
            }
        }

        return lootFile;
    }

    /**
     * ルートテーブルファイルを読み込みJsonElementとして返す。
     *
     * @return パースされたJSON、読み込み失敗時はnull
     */
    public static JsonElement loadLootTableJson() {
        Path lootFile = getOrCreateLootTableFile();
        try (var reader = Files.newBufferedReader(lootFile, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (IOException e) {
            RandomBiomeDimensions.LOGGER.error("ルートテーブルファイルの読み込みに失敗しました: {}", lootFile, e);
            return null;
        }
    }
}
