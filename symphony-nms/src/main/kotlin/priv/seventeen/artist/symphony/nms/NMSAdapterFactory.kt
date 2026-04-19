package priv.seventeen.artist.symphony.nms

import priv.seventeen.artist.blink.BlinkLog

object NMSAdapterFactory {
    private lateinit var adapter: NMSAdapter

    fun initialize() {
        adapter = if (isAsteroidAvailable()) {
            try {
                AsteroidNMSAdapter().also {
                    BlinkLog.info("NMS 适配器已加载: ${it.version} (Asteroid)")
                }
            } catch (e: Exception) {
                BlinkLog.warn("Asteroid 初始化失败，回退到 Bukkit Fallback: ${e.message}")
                BukkitFallbackAdapter().also {
                    BlinkLog.info("NMS 适配器已加载: ${it.version} (Bukkit Fallback)")
                }
            }
        } else {
            BukkitFallbackAdapter().also {
                BlinkLog.info("NMS 适配器已加载: ${it.version} (Bukkit Fallback)")
            }
        }
    }

    fun get(): NMSAdapter = adapter

    fun isInitialized(): Boolean = ::adapter.isInitialized

    fun isUsingAsteroid(): Boolean = isInitialized() && adapter is AsteroidNMSAdapter

    private fun isAsteroidAvailable(): Boolean {
        return try {
            Class.forName("priv.seventeen.artist.blink.nms.AsteroidManager")
            val managerClass = Class.forName("priv.seventeen.artist.blink.nms.AsteroidManager")
            val isAvailable = managerClass.getField("isAvailable")
            isAvailable.getBoolean(null)
        } catch (e: Exception) {
            false
        }
    }
}
