package priv.seventeen.artist.symphony.plugin

import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.blink.config.BlinkConfig
import priv.seventeen.artist.blink.config.Comment
import priv.seventeen.artist.blink.config.ConfigKey

class SymphonyConfig : BlinkConfig(bukkitPlugin, "config") {
    @Comment("调试模式")
    var debug = false

    @Comment("自动保存间隔（秒）")
    @ConfigKey("auto-save-interval")
    var autoSaveInterval = 300

    @Comment("存储类型: yaml / sqlite / mysql")
    @ConfigKey("storage-type")
    var storageType = "yaml"

    @Comment("SQLite 数据库文件名（相对插件数据目录）")
    @ConfigKey("storage.sqlite.file")
    var sqliteFile = "data/symphony.db"

    @Comment("MySQL 主机")
    @ConfigKey("storage.mysql.host")
    var mysqlHost = "localhost"

    @Comment("MySQL 端口")
    @ConfigKey("storage.mysql.port")
    var mysqlPort = 3306

    @Comment("MySQL 数据库名")
    @ConfigKey("storage.mysql.database")
    var mysqlDatabase = "symphony"

    @Comment("MySQL 用户名")
    @ConfigKey("storage.mysql.username")
    var mysqlUsername = "root"

    @Comment("MySQL 密码")
    @ConfigKey("storage.mysql.password")
    var mysqlPassword = ""

    @Comment("最大等级")
    @ConfigKey("max-level")
    var maxLevel = 100

    @Comment("战斗超时时间（毫秒）")
    @ConfigKey("combat-timeout")
    var combatTimeout = 10000L

    @Comment("连击超时时间（毫秒）")
    @ConfigKey("combo-timeout")
    var comboTimeout = 3000L

    @Comment("启用属性交互网络")
    @ConfigKey("advanced.interaction-enabled")
    var interactionEnabled = true

    @Comment("启用元素反应系统")
    @ConfigKey("advanced.element-enabled")
    var elementEnabled = true

    @Comment("启用词条共鸣")
    @ConfigKey("advanced.resonance-enabled")
    var resonanceEnabled = true

    @Comment("启用天赋门")
    @ConfigKey("advanced.talent-enabled")
    var talentEnabled = true

    @Comment("启用状态层")
    @ConfigKey("advanced.status-enabled")
    var statusEnabled = true

    @Comment("启用环境修正")
    @ConfigKey("advanced.environment-enabled")
    var environmentEnabled = true

    @Comment("属性重算异步采集（仅 isAsync=true 的 Provider 进入异步池；事件 apply 仍在主线）")
    @ConfigKey("performance.async-recalc")
    var asyncRecalc = false

    companion object {
        lateinit var instance: SymphonyConfig private set
        fun load() { instance = SymphonyConfig(); instance.load() }
        fun reload() = instance.reload()
    }
}
