package ac.grim.grimac.utils.inventory;

import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.TypedBlockEntityData;
import com.github.retrooper.packetevents.protocol.component.builtin.TypedEntityData;
import com.github.retrooper.packetevents.protocol.component.builtin.item.BundleContents;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ChargedProjectiles;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemContainerContents;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.nbt.NBT;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTList;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Measures how deeply an item's data is nested: every NBT compound or list is one level, and so
 * is every item stored inside another item (shulker, bundle and crossbow contents). Walks the
 * already decoded item, so nothing is serialized again, and stops as soon as the limit is passed.
 */
public final class ItemDataDepth {
    /** Never recurse deeper than this, whatever the config says. */
    public static final int MAX_LIMIT = 1024;

    private ItemDataDepth() {
    }

    /** Returns the depth of the item's data, or a value above {@code limit} as soon as it passes it. */
    public static int of(@Nullable ItemStack item, int limit) {
        return item(item, 0, Math.min(limit, MAX_LIMIT));
    }

    private static int item(@Nullable ItemStack item, int level, int limit) {
        if (item == null || item.isEmpty()) return level;
        int max = level;

        NBTCompound nbt = item.getNBT(); // before 1.20.5
        if (nbt != null) max = Math.max(max, nbt(nbt, level + 1, limit));
        if (max > limit || !item.hasComponentPatches()) return max;

        max = Math.max(max, nbt(item.getComponentOr(ComponentTypes.CUSTOM_DATA, null), level + 1, limit));
        max = Math.max(max, nbt(item.getComponentOr(ComponentTypes.BLOCK_ENTITY_DATA, null), level + 1, limit));
        max = Math.max(max, nbt(item.getComponentOr(ComponentTypes.ENTITY_DATA, null), level + 1, limit));
        max = Math.max(max, nbt(item.getComponentOr(ComponentTypes.BUCKET_ENTITY_DATA, null), level + 1, limit));
        if (max > limit) return max;

        TypedBlockEntityData blockEntity = item.getComponentOr(ComponentTypes.TYPED_BLOCK_ENTITY_DATA, null);
        if (blockEntity != null) max = Math.max(max, nbt(blockEntity.getCompound(), level + 1, limit));
        TypedEntityData entity = item.getComponentOr(ComponentTypes.TYPED_ENTITY_DATA, null);
        if (entity != null) max = Math.max(max, nbt(entity.getCompound(), level + 1, limit));
        if (max > limit) return max;

        ItemContainerContents container = item.getComponentOr(ComponentTypes.CONTAINER, null);
        if (container != null) max = Math.max(max, items(container.getItems(), level + 1, limit));
        if (max > limit) return max;
        BundleContents bundle = item.getComponentOr(ComponentTypes.BUNDLE_CONTENTS, null);
        if (bundle != null) max = Math.max(max, items(bundle.getItems(), level + 1, limit));
        if (max > limit) return max;
        ChargedProjectiles projectiles = item.getComponentOr(ComponentTypes.CHARGED_PROJECTILES, null);
        if (projectiles != null) max = Math.max(max, items(projectiles.getItems(), level + 1, limit));
        return max;
    }

    private static int items(@Nullable List<ItemStack> items, int level, int limit) {
        int max = level;
        if (items == null || level > limit) return max;
        for (ItemStack item : items) {
            max = Math.max(max, item(item, level, limit));
            if (max > limit) break;
        }
        return max;
    }

    /** Deepest container level inside {@code tag}, where {@code tag} itself is at {@code level}. */
    private static int nbt(@Nullable NBT tag, int level, int limit) {
        if (tag instanceof NBTCompound compound) {
            int max = level;
            if (level > limit) return max;
            for (NBT child : compound.getTags().values()) {
                max = Math.max(max, nbt(child, level + 1, limit));
                if (max > limit) break;
            }
            return max;
        }
        if (tag instanceof NBTList<?> list) {
            int max = level;
            if (level > limit) return max;
            for (NBT child : list.getTags()) {
                max = Math.max(max, nbt(child, level + 1, limit));
                if (max > limit) break;
            }
            return max;
        }
        return level - 1; // primitives and missing tags add no level
    }
}
