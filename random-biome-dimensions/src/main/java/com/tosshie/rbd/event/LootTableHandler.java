package com.tosshie.rbd.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tosshie.rbd.RandomBiomeDimensions;
import com.tosshie.rbd.util.DimensionHelper;
import com.tosshie.rbd.util.LootTableFileManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * RBDディメンション内のモブドロップを、configディレクトリのルートテーブルJSONに基づいて置き換える。
 */
public class LootTableHandler {

    private JsonElement cachedLootTable;
    private boolean loaded;

    /**
     * ルートテーブルJSONを読み込む。初回呼び出し時にキャッシュする。
     */
    private JsonElement getLootTable() {
        if (!loaded) {
            cachedLootTable = LootTableFileManager.loadLootTableJson();
            loaded = true;
        }
        return cachedLootTable;
    }

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (mob.level().isClientSide()) {
            return;
        }
        if (!DimensionHelper.isRBDDimension(mob.level().dimension())) {
            return;
        }

        JsonElement lootJson = getLootTable();
        if (lootJson == null || !lootJson.isJsonObject()) {
            return;
        }

        event.getDrops().clear();

        List<ItemStack> drops = generateDrops(lootJson.getAsJsonObject(), mob.level().getRandom());
        for (ItemStack stack : drops) {
            event.getDrops().add(new ItemEntity(mob.level(), mob.getX(), mob.getY(), mob.getZ(), stack));
        }
    }

    /**
     * JSONのルートテーブル定義からドロップアイテムを生成する。
     *
     * @param lootTable ルートテーブルJSON
     * @param random    乱数ソース
     * @return 生成されたアイテムスタックのリスト
     */
    private List<ItemStack> generateDrops(JsonObject lootTable, RandomSource random) {
        List<ItemStack> result = new ArrayList<>();
        JsonArray pools = lootTable.getAsJsonArray("pools");
        if (pools == null) {
            return result;
        }

        for (JsonElement poolElement : pools) {
            JsonObject pool = poolElement.getAsJsonObject();
            int rolls = pool.has("rolls") ? pool.get("rolls").getAsInt() : 1;
            int bonusRolls = pool.has("bonus_rolls") ? pool.get("bonus_rolls").getAsInt() : 0;
            int totalRolls = rolls + (bonusRolls > 0 ? random.nextInt(bonusRolls + 1) : 0);

            JsonArray entries = pool.getAsJsonArray("entries");
            if (entries == null || entries.isEmpty()) {
                continue;
            }

            for (int i = 0; i < totalRolls; i++) {
                ItemStack stack = rollWeightedEntry(entries, random);
                if (stack != null && !stack.isEmpty()) {
                    result.add(stack);
                }
            }
        }
        return result;
    }

    /**
     * 重み付きエントリからアイテムを1つ選択する。
     */
    private ItemStack rollWeightedEntry(JsonArray entries, RandomSource random) {
        int totalWeight = 0;
        for (JsonElement e : entries) {
            totalWeight += e.getAsJsonObject().has("weight") ? e.getAsJsonObject().get("weight").getAsInt() : 1;
        }
        if (totalWeight <= 0) {
            return null;
        }

        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (JsonElement e : entries) {
            JsonObject entry = e.getAsJsonObject();
            int weight = entry.has("weight") ? entry.get("weight").getAsInt() : 1;
            cumulative += weight;
            if (roll < cumulative) {
                return createItemFromEntry(entry, random);
            }
        }
        return null;
    }

    /**
     * エントリJSONからItemStackを生成する。set_count関数に対応。
     */
    private ItemStack createItemFromEntry(JsonObject entry, RandomSource random) {
        String itemName = entry.has("name") ? entry.get("name").getAsString() : null;
        if (itemName == null) {
            return null;
        }

        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName));
        if (item == null) {
            RandomBiomeDimensions.LOGGER.warn("不明なアイテム: {}", itemName);
            return null;
        }

        int count = 1;
        if (entry.has("functions")) {
            for (JsonElement func : entry.getAsJsonArray("functions")) {
                JsonObject funcObj = func.getAsJsonObject();
                String function = funcObj.has("function") ? funcObj.get("function").getAsString() : "";
                if ("minecraft:set_count".equals(function) && funcObj.has("count")) {
                    count = resolveCount(funcObj.getAsJsonObject("count"), random);
                }
            }
        }

        return new ItemStack(item, count);
    }

    /**
     * count定義から実際の個数を算出する。uniform分布に対応。
     */
    private int resolveCount(JsonObject countObj, RandomSource random) {
        if (countObj.has("type") && "minecraft:uniform".equals(countObj.get("type").getAsString())) {
            int min = countObj.get("min").getAsInt();
            int max = countObj.get("max").getAsInt();
            return min + (max > min ? random.nextInt(max - min + 1) : 0);
        }
        // 固定値の場合
        if (countObj.has("value")) {
            return countObj.get("value").getAsInt();
        }
        return 1;
    }
}
