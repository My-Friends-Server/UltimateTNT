package fr.mrmicky.ultimatetnt.utils;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import fr.mrmicky.ultimatetnt.UltimateTNT;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Set;

/**
 * Утилитарный класс для интеграции с WorldGuard
 * Обеспечивает проверку разрешений на размещение TNT и взрывы в защищенных регионах
 */
public class WorldGuardHelper {
    
    private static boolean worldGuardEnabled = false;
    private static WorldGuardPlugin worldGuardPlugin = null;
    private static UltimateTNT plugin = null;
    
    /**
     * Инициализация интеграции с WorldGuard
     */
    public static void initialize(UltimateTNT ultimateTNTPlugin) {
        plugin = ultimateTNTPlugin;
        Plugin wgPlugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (wgPlugin != null && wgPlugin.isEnabled()) {
            worldGuardPlugin = (WorldGuardPlugin) wgPlugin;
            worldGuardEnabled = true;
            plugin.getLogger().info("WorldGuard integration enabled");
        } else {
            worldGuardEnabled = false;
            plugin.getLogger().info("WorldGuard not found, integration disabled");
        }
    }
    
    /**
     * Проверяет, включена ли интеграция с WorldGuard
     * @return true если WorldGuard доступен
     */
    public static boolean isEnabled() {
        return worldGuardEnabled;
    }
    
    /**
     * Проверяет, разрешены ли взрывы в указанной локации
     * @param location локация для проверки
     * @param player игрок, который является источником взрыва (может быть null)
     * @return true, если взрывы разрешены
     */
    public static boolean canExplode(Location location, Player player) {
        if (!worldGuardEnabled) {
            return true;
        }
        
        try {
            RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                    .get(BukkitAdapter.adapt(location.getWorld()));
            
            if (regionManager == null) {
                return true;
            }
            
            ApplicableRegionSet regions = regionManager.getApplicableRegions(
                    BukkitAdapter.asBlockVector(location));
            
            // Если нет регионов в этой локации, разрешаем взрывы
            if (regions.size() == 0) {
                return true;
            }
            
            // Проверяем флаг TNT, если включено в конфигурации
            if (plugin.getConfig().getBoolean("WorldGuard.RespectTNTFlag", true)) {
                Boolean tntFlag = regions.testState(player != null ? worldGuardPlugin.wrapPlayer(player) : null, Flags.TNT);
                if (tntFlag != null && tntFlag == Boolean.FALSE) {
                    // Если флаг явно запрещает взрывы, проверяем является ли игрок владельцем/участником
                    if (player != null && plugin.getConfig().getBoolean("WorldGuard.AllowForMembers", true)) {
                        if (isPlayerMemberOfAnyRegion(regions, player)) {
                            return true; // Владелец/участник может взрывать даже при запрещающем флаге
                        } else {
                            return false;
                        }
                    } else {
                        return false;
                    }
                }
            }
            
            // Проверяем флаг OTHER_EXPLOSION, если включено в конфигурации
            if (plugin.getConfig().getBoolean("WorldGuard.RespectExplosionFlag", true)) {
                Boolean explosionFlag = regions.testState(player != null ? worldGuardPlugin.wrapPlayer(player) : null, Flags.OTHER_EXPLOSION);
                if (explosionFlag != null && explosionFlag == Boolean.FALSE) {
                    // Если флаг явно запрещает взрывы, проверяем является ли игрок владельцем/участником
                    if (player != null && plugin.getConfig().getBoolean("WorldGuard.AllowForMembers", true)) {
                        if (isPlayerMemberOfAnyRegion(regions, player)) {
                            return true; // Владелец/участник может взрывать даже при запрещающем флаге
                        } else {
                            return false;
                        }
                    } else {
                        return false;
                    }
                }
            }
            
            return true;
        } catch (Exception e) {
            // В случае ошибки разрешаем действие
            return true;
        }
    }
    
    /**
     * Проверяет, может ли игрок размещать TNT в указанной локации
     * @param location локация для проверки
     * @param player игрок
     * @return true если размещение разрешено
     */
    public static boolean canPlaceTNT(Location location, Player player) {
        if (!worldGuardEnabled || player == null) {
            return true;
        }
        
        try {
            RegionManager regionManager = WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(location.getWorld()));
            
            if (regionManager == null) {
                return true;
            }
            
            ApplicableRegionSet regions = regionManager.getApplicableRegions(
                    BukkitAdapter.asBlockVector(location)
            );
            
            // Проверяем флаг TNT, если включено в конфигурации
            if (plugin.getConfig().getBoolean("WorldGuard.RespectTNTFlag", true)) {
                // Если игрок является участником региона и это разрешено в конфигурации
                if (plugin.getConfig().getBoolean("WorldGuard.AllowForMembers", true)) {
                    if (isPlayerMemberOfAnyRegion(regions, player)) {
                        return true;
                    }
                }
                
                Boolean tntFlag = regions.testState(worldGuardPlugin.wrapPlayer(player), Flags.TNT);
                if (tntFlag != null) {
                    return tntFlag;
                }
            }
            
            // Проверяем права на строительство
            return regions.testState(worldGuardPlugin.wrapPlayer(player), Flags.BUILD);
            
        } catch (Exception e) {
            return true; // Безопасный fallback
        }
    }
    
    /**
     * Проверяет, является ли игрок участником или владельцем любого из регионов
     * @param regions набор регионов
     * @param player игрок
     * @return true если игрок является участником или владельцем
     */
    private static boolean isPlayerMemberOfAnyRegion(ApplicableRegionSet regions, Player player) {
        try {
            for (ProtectedRegion region : regions) {
                // Проверяем, является ли игрок владельцем или участником региона
                if (region.isOwner(worldGuardPlugin.wrapPlayer(player)) || 
                    region.isMember(worldGuardPlugin.wrapPlayer(player))) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Игнорируем ошибки
        }
        return false;
    }
    
    /**
     * Проверяет, может ли конкретный блок быть разрушен взрывом
     * @param blockLocation локация блока
     * @param explosionLocation локация взрыва
     * @param player игрок, который является источником взрыва (может быть null)
     * @return true, если блок может быть разрушен
     */
    public static boolean canExplodeBlock(Location blockLocation, Location explosionLocation, Player player) {
        if (!worldGuardEnabled) {
            return true;
        }
        
        try {
            RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                    .get(BukkitAdapter.adapt(blockLocation.getWorld()));
            
            if (regionManager == null) {
                return true;
            }
            
            ApplicableRegionSet blockRegions = regionManager.getApplicableRegions(
                    BukkitAdapter.asBlockVector(blockLocation));
            ApplicableRegionSet explosionRegions = regionManager.getApplicableRegions(
                    BukkitAdapter.asBlockVector(explosionLocation));
            
            // Если блок находится в регионе, а взрыв произошел вне этого региона
            if (blockRegions.size() > 0) {
                // Проверяем, находится ли взрыв в том же регионе что и блок
                boolean sameRegion = false;
                for (ProtectedRegion blockRegion : blockRegions) {
                    for (ProtectedRegion explosionRegion : explosionRegions) {
                        if (blockRegion.getId().equals(explosionRegion.getId())) {
                            sameRegion = true;
                            break;
                        }
                    }
                    if (sameRegion) break;
                }
                
                // Если взрыв произошел вне региона, где находится блок
                if (!sameRegion) {
                    // Проверяем, является ли игрок владельцем/участником региона блока
                    if (player != null && plugin.getConfig().getBoolean("WorldGuard.AllowForMembers", true)) {
                        if (isPlayerMemberOfAnyRegion(blockRegions, player)) {
                            return true; // Владелец может разрушать свои блоки
                        }
                    }
                    return false; // Блок защищен от внешних взрывов
                }
            }
            
            return true;
        } catch (Exception e) {
            return true;
        }
    }
    
    /**
     * Получает информацию о регионах в указанной локации
     * @param location локация для проверки
     * @return строка с информацией о регионах или null
     */
    public static String getRegionInfo(Location location) {
        if (!worldGuardEnabled) {
            return null;
        }
        
        try {
            RegionManager regionManager = WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(location.getWorld()));
            
            if (regionManager == null) {
                return null;
            }
            
            ApplicableRegionSet regions = regionManager.getApplicableRegions(
                    BukkitAdapter.asBlockVector(location)
            );
            
            if (regions.size() == 0) {
                return "No regions";
            }
            
            StringBuilder info = new StringBuilder("Regions: ");
            for (ProtectedRegion region : regions) {
                info.append(region.getId()).append(", ");
            }
            
            return info.substring(0, info.length() - 2); // Убираем последнюю запятую
            
        } catch (Exception e) {
            return "Error getting region info";
        }
    }
}