package priv.seventeen.artist.symphony.plugin

import org.bukkit.Bukkit
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.blink.lifecycle.Awake
import priv.seventeen.artist.blink.lifecycle.LifeCycle
import priv.seventeen.artist.symphony.api.SymphonyAPI
import priv.seventeen.artist.symphony.core.advanced.element.ReactionSystem
import priv.seventeen.artist.symphony.core.advanced.environment.EnvironmentSystem
import priv.seventeen.artist.symphony.core.advanced.status.StatusLayerSystem
import priv.seventeen.artist.symphony.core.affix.AffixProcessor
import priv.seventeen.artist.symphony.core.attribute.AsyncRecalcScheduler
import priv.seventeen.artist.symphony.core.attribute.AttributeProviderRegistry
import priv.seventeen.artist.symphony.core.attribute.AttributeRegistry
import priv.seventeen.artist.symphony.core.attribute.provider.*
import priv.seventeen.artist.symphony.core.config.ConfigLoader
import priv.seventeen.artist.symphony.core.growth.GrowthManagerImpl
import priv.seventeen.artist.symphony.core.script.FormulaEngine
import priv.seventeen.artist.symphony.core.script.SymphonyScriptEngine
import priv.seventeen.artist.symphony.core.skill.SkillDispatcher
import priv.seventeen.artist.symphony.core.trigger.TriggerDispatcher
import priv.seventeen.artist.symphony.core.skill.SkillProviderManagerImpl
import priv.seventeen.artist.symphony.core.skill.builtin.AriaSkillProvider
import priv.seventeen.artist.symphony.core.skill.builtin.MythicMobSpawnListener
import priv.seventeen.artist.symphony.core.skill.builtin.MythicMobsBridge
import priv.seventeen.artist.symphony.core.skill.builtin.MythicMobsMechanicRegistrar
import priv.seventeen.artist.symphony.core.skill.builtin.SymphonySkillProvider
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import priv.seventeen.artist.symphony.core.storage.StorageProvider
import priv.seventeen.artist.symphony.core.storage.provider.MysqlStorageProvider
import priv.seventeen.artist.symphony.core.storage.provider.SqliteStorageProvider
import priv.seventeen.artist.symphony.core.storage.provider.YamlStorageProvider
import priv.seventeen.artist.symphony.core.trigger.TriggerManagerImpl
import priv.seventeen.artist.symphony.core.trigger.builtin.PeriodicTriggerTask
import priv.seventeen.artist.symphony.nms.NMSAdapterFactory
import priv.seventeen.artist.symphony.plugin.command.SymphonyCommands
import priv.seventeen.artist.symphony.plugin.gui.AttributeGuiProvider
import priv.seventeen.artist.symphony.plugin.gui.BukkitInventoryGuiProvider
import priv.seventeen.artist.symphony.plugin.listener.EpicFightAnimationListener
import priv.seventeen.artist.symphony.plugin.placeholder.SymphonyExpansion
import java.io.File

object SymphonyPlugin {
    lateinit var config: SymphonyConfig
    lateinit var scriptEngine: SymphonyScriptEngine
    lateinit var formulaEngine: FormulaEngine
    lateinit var triggerManager: TriggerManagerImpl
    lateinit var skillProviderManager: SkillProviderManagerImpl
    lateinit var growthManager: GrowthManagerImpl
    lateinit var apiImpl: SymphonyAPIImpl
    lateinit var guiProvider: AttributeGuiProvider

    @Awake(LifeCycle.LOAD)
    fun onLoad() {
        BlinkLog.info("正在加载 §bSymphony §f属性系统...")
        ResourceExtractor.extractDefaults()
        NMSAdapterFactory.initialize()
        BlinkLog.success("§bSymphony §f加载阶段完成")
    }

    @Awake(LifeCycle.ENABLE)
    fun onEnable() {
        config = SymphonyConfig()
        config.load()

        PlayerDataManager.initialize(createStorageProvider(), bukkitPlugin)

        scriptEngine = SymphonyScriptEngine()
        scriptEngine.initialize(bukkitPlugin.dataFolder)

        AsyncRecalcScheduler.init(bukkitPlugin, config.asyncRecalc)

        formulaEngine = FormulaEngine()
        triggerManager = TriggerManagerImpl()

        skillProviderManager = SkillProviderManagerImpl()
        SkillDispatcher.providerManager = skillProviderManager
        skillProviderManager.registerProvider(SymphonySkillProvider())
        skillProviderManager.registerProvider(AriaSkillProvider())

        val mmBridge = MythicMobsBridge()
        if (mmBridge.isAvailable()) {
            skillProviderManager.registerProvider(mmBridge)
            MythicMobsMechanicRegistrar.register(bukkitPlugin)
            MythicMobSpawnListener.register(bukkitPlugin)
        }

        AffixProcessor.init()

        growthManager = GrowthManagerImpl()
        growthManager.enhanceManager.loadDefaults()
        TriggerDispatcher.setManager = growthManager.setManager

        AttributeProviderRegistry.register(BaseProvider())
        AttributeProviderRegistry.register(LevelProvider(growthManager.levelManager))
        AttributeProviderRegistry.register(EquipmentProvider())
        AttributeProviderRegistry.register(GemProvider(growthManager.gemManager))
        AttributeProviderRegistry.register(EnhanceProvider(growthManager.enhanceManager))
        AttributeProviderRegistry.register(SetProvider(growthManager.setManager))
        AttributeProviderRegistry.register(RuneProvider())
        AttributeProviderRegistry.register(AffixPassiveProvider())
        AttributeProviderRegistry.register(ResonanceProvider())
        AttributeProviderRegistry.register(TalentProvider())
        AttributeProviderRegistry.register(StatusProvider())
        AttributeProviderRegistry.register(BuffProvider())
        AttributeProviderRegistry.register(EnvironmentProvider())
        AttributeProviderRegistry.register(MythicMobAttributeProvider())

        if (config.elementEnabled) {
            ReactionSystem.registerDefaults()
            BlinkLog.info("已加载 §b${ReactionSystem.getAll().size} §f个元素反应")
        }
        if (config.statusEnabled) {
            StatusLayerSystem.registerDefaults()
            BlinkLog.info("已加载 §b${StatusLayerSystem.getAll().size} §f个状态层")
        }
        if (config.environmentEnabled) {
            EnvironmentSystem.registerDefaults()
            BlinkLog.info("已加载 §b${EnvironmentSystem.getAll().size} §f个环境修正器")
        }

        // Epic Fight 动画兼容初始化（动画播放期间延迟属性同步，避免动画卡住）
        EpicFightAnimationListener.init(config.epicFightEnabled)
        when {
            !config.epicFightEnabled ->
                BlinkLog.info("Epic Fight 兼容已在配置中关闭")
            !isEpicFightLoaded() ->
                BlinkLog.info("Epic Fight 未检测到，跳过动画兼容初始化")
            EpicFightAnimationListener.available ->
                BlinkLog.success("§bEpic Fight §f动画兼容已启用")
            else ->
                BlinkLog.warn("§bEpic Fight §f已安装但反射 API 不兼容，动画兼容未启用")
        }

        scriptEngine.loadAttributeScripts(bukkitPlugin.dataFolder)
        scriptEngine.loadFormulaScripts(bukkitPlugin.dataFolder, formulaEngine)
        scriptEngine.loadMechanicsScripts(bukkitPlugin.dataFolder)

        apiImpl = SymphonyAPIImpl()
        SymphonyAPI.setInstance(apiImpl)

        val symphonyProvider = skillProviderManager.getProvider("symphony") as? SymphonySkillProvider ?: SymphonySkillProvider()
        val ariaProvider = skillProviderManager.getProvider("aria") as? AriaSkillProvider
        val gemProvider = AttributeProviderRegistry.getAll().filterIsInstance<GemProvider>().firstOrNull()
        ConfigLoader.loadAll(
            bukkitPlugin.dataFolder,
            apiImpl.affixManagerInstance,
            symphonyProvider,
            growthManager.setManager,
            ariaProvider,
            growthManager.levelManager,
            growthManager.enhanceManager,
            gemProvider
        )

        SymphonyCommands.register()

        guiProvider = BukkitInventoryGuiProvider().also {
            bukkitPlugin.server.pluginManager.registerEvents(it, bukkitPlugin)
        }

        // PlaceholderAPI 前置检测后注册
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            if (SymphonyExpansion().register()) {
                BlinkLog.info("已注册 §bPlaceholderAPI §f扩展")
            } else {
                BlinkLog.warn("§bPlaceholderAPI §f扩展注册失败")
            }
        }

        BlinkLog.success("§bSymphony §f已启用 — 已加载 §b${AttributeRegistry.ids().size} §f个属性")
    }

    @Awake(LifeCycle.ACTIVE)
    fun onActive() {
        PeriodicTriggerTask().runTaskTimer(bukkitPlugin, 1L, 1L)
        RuntimeTickTask().runTaskTimer(bukkitPlugin, 1L, 1L)

        val saveInterval = (config.autoSaveInterval * 20).toLong()
        Bukkit.getScheduler().runTaskTimerAsynchronously(bukkitPlugin, Runnable {
            PlayerDataManager.saveAllDirty()
        }, saveInterval, saveInterval)

        BlinkLog.success("§bSymphony §f定时任务已启动")
    }

    @Awake(LifeCycle.DISABLE)
    fun onDisable() {
        PlayerDataManager.shutdown()
        AsyncRecalcScheduler.shutdown()
        if (::scriptEngine.isInitialized) scriptEngine.shutdown()
        BlinkLog.info("§bSymphony §f已禁用")
    }

    private fun createStorageProvider(): StorageProvider {
        return when (config.storageType.lowercase()) {
            "sqlite" -> try {
                SqliteStorageProvider(File(bukkitPlugin.dataFolder, config.sqliteFile))
            } catch (e: Throwable) {
                BlinkLog.error("SQLite 初始化失败（驱动缺失？），回退到 YAML: ${e.message}")
                YamlStorageProvider(bukkitPlugin.dataFolder)
            }
            "mysql" -> try {
                MysqlStorageProvider(
                    config.mysqlHost, config.mysqlPort, config.mysqlDatabase,
                    config.mysqlUsername, config.mysqlPassword
                )
            } catch (e: Throwable) {
                BlinkLog.error("MySQL 初始化失败（驱动缺失？），回退到 YAML: ${e.message}")
                YamlStorageProvider(bukkitPlugin.dataFolder)
            }
            else -> YamlStorageProvider(bukkitPlugin.dataFolder)
        }
    }

    /**
     * 检查 Epic Fight 模组是否已加载
     */
    private fun isEpicFightLoaded(): Boolean {
        return bukkitPlugin.server.pluginManager.getPlugin("EpicFight") != null
    }
}
