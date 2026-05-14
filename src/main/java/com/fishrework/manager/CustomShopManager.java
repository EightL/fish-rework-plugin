package com.fishrework.manager;

import com.fishrework.FishRework;
import com.fishrework.economy.EconomyResult;
import com.fishrework.model.CustomShop;
import com.fishrework.model.CustomShopListing;
import com.fishrework.util.FormatUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CustomShopManager {

    public static final int LISTING_SLOT_COUNT = 28;

    private final FishRework plugin;
    private final NamespacedKey shopIdKey;
    private final Map<String, String> activeShopInstances = new HashMap<>();
    private final Map<UUID, PendingPrice> pendingPrices = new HashMap<>();
    private final Map<String, Object> listingLocks = new java.util.concurrent.ConcurrentHashMap<>();

    public CustomShopManager(FishRework plugin) {
        this.plugin = plugin;
        this.shopIdKey = new NamespacedKey(plugin, "custom_shop_id");
    }

    public NamespacedKey getShopIdKey() {
        return shopIdKey;
    }

    public boolean isCustomShopInteraction(org.bukkit.entity.Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(shopIdKey, PersistentDataType.STRING);
    }

    public String getShopId(org.bukkit.entity.Entity entity) {
        return entity == null ? null : entity.getPersistentDataContainer().get(shopIdKey, PersistentDataType.STRING);
    }

    public CustomShop createShop(Player owner, Location blockLocation) {
        if (owner == null || blockLocation == null || blockLocation.getWorld() == null) return null;
        CustomShop existingAtLocation = plugin.getDatabaseManager().loadCustomShopAt(
                blockLocation.getWorld().getName(),
                blockLocation.getBlockX(),
                blockLocation.getBlockY(),
                blockLocation.getBlockZ());
        if (existingAtLocation != null) {
            activeShopInstances.put(existingAtLocation.id(), existingAtLocation.modelInstanceId());
            return null;
        }

        String shopId = UUID.randomUUID().toString();
        String instanceId = spawnShopModel(shopId, blockLocation, owner.getLocation().getYaw());
        if (instanceId == null) return null;

        CustomShop shop = new CustomShop(
                shopId,
                owner.getUniqueId(),
                owner.getName(),
                blockLocation.getWorld().getName(),
                blockLocation.getBlockX(),
                blockLocation.getBlockY(),
                blockLocation.getBlockZ(),
                owner.getLocation().getYaw(),
                instanceId);
        plugin.getDatabaseManager().saveCustomShop(shop);
        activeShopInstances.put(shopId, instanceId);
        return shop;
    }

    public void restoreShops() {
        for (CustomShop shop : plugin.getDatabaseManager().loadCustomShops()) {
            World world = Bukkit.getWorld(shop.worldName());
            if (world == null) continue;
            Location location = new Location(world, shop.x(), shop.y(), shop.z());
            location.getChunk().load();

            List<String> existingInstances = plugin.getFishStallManager()
                    .findStandaloneInstancesForShop(world, shopIdKey, shop.id());
            if (!existingInstances.isEmpty()) {
                String canonicalInstanceId = chooseCanonicalInstanceId(existingInstances, shop.modelInstanceId());
                for (String instanceId : existingInstances) {
                    if (!instanceId.equals(canonicalInstanceId)) {
                        plugin.getFishStallManager().destroyInstanceByMetadata(instanceId);
                    }
                }
                activeShopInstances.put(shop.id(), canonicalInstanceId);
                plugin.getDatabaseManager().updateCustomShopInstance(shop.id(), canonicalInstanceId);
                continue;
            }

            if (shop.modelInstanceId() != null && !shop.modelInstanceId().isBlank()) {
                plugin.getFishStallManager().destroyInstanceByMetadata(shop.modelInstanceId());
            }

            String spawnedInstanceId = spawnShopModel(shop.id(), location, shop.yaw());
            if (spawnedInstanceId != null) {
                activeShopInstances.put(shop.id(), spawnedInstanceId);
                plugin.getDatabaseManager().updateCustomShopInstance(shop.id(), spawnedInstanceId);
            }
        }
    }

    private String spawnShopModel(String shopId, Location location, float yaw) {
        return plugin.getFishStallManager().spawnModel(
                "fish_stall",
                location.clone(),
                Map.of(shopIdKey, shopId));
    }

    public CustomShop getShop(String shopId) {
        return plugin.getDatabaseManager().loadCustomShop(shopId);
    }

    public Map<Integer, CustomShopListing> getListingsBySlot(String shopId) {
        Map<Integer, CustomShopListing> listings = new LinkedHashMap<>();
        for (CustomShopListing listing : plugin.getDatabaseManager().loadCustomShopListings(shopId)) {
            if (listing.slotIndex() >= 0 && listing.slotIndex() < LISTING_SLOT_COUNT) {
                listings.put(listing.slotIndex(), listing);
            }
        }
        return listings;
    }

    public void saveListing(String shopId, int slotIndex, ItemStack item, long price) {
        synchronized (lockForListing(shopId, slotIndex)) {
            plugin.getDatabaseManager().saveCustomShopListing(shopId, slotIndex, item, price);
        }
    }

    public EconomyResult purchase(Player buyer, CustomShop shop, CustomShopListing listing) {
        if (buyer == null || shop == null || listing == null || listing.price() <= 0) {
            return EconomyResult.failure("Shop listing is not available.");
        }
        if (buyer.getUniqueId().equals(shop.ownerUuid())) {
            return EconomyResult.failure("You cannot buy from your own shop.");
        }

        Object lock = lockForListing(shop.id(), listing.slotIndex());
        synchronized (lock) {
            CustomShopListing liveListing = plugin.getDatabaseManager().loadCustomShopListing(shop.id(), listing.slotIndex());
            if (liveListing == null || liveListing.price() <= 0) {
                return EconomyResult.failure("Shop listing is not available.");
            }

            EconomyResult withdrawal = plugin.getEconomyManager().withdraw(buyer, liveListing.price());
            if (!withdrawal.success()) {
                return withdrawal;
            }

            boolean consumed = plugin.getDatabaseManager().deleteCustomShopListing(shop.id(), listing.slotIndex());
            if (!consumed) {
                plugin.getEconomyManager().deposit(buyer, liveListing.price());
                return EconomyResult.failure("Shop listing is no longer available.");
            }

            EconomyResult sellerDeposit = plugin.getEconomyManager().deposit(shop.ownerUuid(), shop.ownerName(), liveListing.price());
            if (!sellerDeposit.success()) {
                plugin.getDatabaseManager().saveCustomShopListing(shop.id(), listing.slotIndex(), liveListing.item(), liveListing.price());
                plugin.getEconomyManager().deposit(buyer, liveListing.price());
                return EconomyResult.failure("Seller payout failed: " + sellerDeposit.message());
            }

            ItemStack reward = liveListing.item().clone();
            Map<Integer, ItemStack> leftover = buyer.getInventory().addItem(reward);
            for (ItemStack drop : leftover.values()) {
                buyer.getWorld().dropItemNaturally(buyer.getLocation(), drop);
            }
            return EconomyResult.success(liveListing.price(), plugin.getEconomyManager().getBalance(buyer));
        }
    }

    public ItemStack reclaimListing(Player owner, CustomShop shop, int slotIndex) {
        if (owner == null || shop == null || !owner.getUniqueId().equals(shop.ownerUuid())) {
            return null;
        }
        synchronized (lockForListing(shop.id(), slotIndex)) {
            CustomShopListing listing = plugin.getDatabaseManager().loadCustomShopListing(shop.id(), slotIndex);
            if (listing == null) {
                return null;
            }
            if (!plugin.getDatabaseManager().deleteCustomShopListing(shop.id(), slotIndex)) {
                return null;
            }
            return listing.item().clone();
        }
    }

    public boolean deleteShop(CustomShop shop) {
        if (shop == null) return false;
        String instanceId = activeShopInstances.remove(shop.id());
        if (instanceId == null || instanceId.isBlank()) {
            instanceId = shop.modelInstanceId();
        }
        if (instanceId != null && !instanceId.isBlank()) {
            plugin.getFishStallManager().destroyInstance(instanceId);
            plugin.getFishStallManager().destroyInstanceByMetadata(instanceId);
        }
        plugin.getDatabaseManager().deleteCustomShop(shop.id());
        return true;
    }

    public void startPriceInput(Player player, String shopId, int slotIndex, ItemStack item) {
        pendingPrices.put(player.getUniqueId(), new PendingPrice(shopId, slotIndex, item.clone()));
        player.sendMessage(Component.text("Set your listing price with ")
                .color(NamedTextColor.GOLD)
                .append(Component.text("/fs fish_stall setprice <price>")
                        .color(NamedTextColor.AQUA)
                        .decorate(TextDecoration.BOLD))
                .append(Component.text(".")
                        .color(NamedTextColor.GOLD)));
    }

    public PendingPrice consumePendingPrice(UUID uuid) {
        return pendingPrices.remove(uuid);
    }

    public String formatPrice(long price) {
        return FormatUtil.format("%.0f", (double) price) + " " + plugin.getEconomyManager().getCurrencyName();
    }

    private Object lockForListing(String shopId, int slotIndex) {
        return listingLocks.computeIfAbsent(shopId + ":" + slotIndex, ignored -> new Object());
    }

    private String chooseCanonicalInstanceId(List<String> instanceIds, String preferred) {
        if (instanceIds == null || instanceIds.isEmpty()) {
            return null;
        }
        if (preferred != null && !preferred.isBlank()) {
            for (String instanceId : instanceIds) {
                if (preferred.equals(instanceId)) {
                    return instanceId;
                }
            }
        }
        return instanceIds.get(0);
    }

    public record PendingPrice(String shopId, int slotIndex, ItemStack item) {
    }
}
