package priv.seventeen.artist.symphony.api

/**
 * 高级子系统扩展入口。
 *
 * 让第三方插件用统一入口注册：元素反应 / 状态层 / 环境修正 / 词条共鸣 / 天赋 / 套装 / 属性交互。
 * 实现类直接转发到 core 模块的 singleton，无需第三方插件 import core 包。
 *
 * 数据类型（如 ReactionDefinition）目前仍在 core 包，需要时通过 [SymphonyAPI.registerReaction]
 * 这类基本类型 facade，或者直接 import core 类构造。
 */
interface ISymphonyExtensions {

    /** 列出已注册的元素反应 ID。 */
    fun listReactions(): List<String>

    /** 列出已注册的状态层 ID。 */
    fun listStatusLayers(): List<String>

    /** 列出已注册的环境修正 ID。 */
    fun listEnvironmentModifiers(): List<String>

    /** 列出已注册的词条共鸣 ID。 */
    fun listResonances(): List<String>

    /** 列出已注册的天赋 ID。 */
    fun listTalents(): List<String>

    /** 列出已注册的属性交互 ID。 */
    fun listInteractions(): List<String>

    /** 列出已注册的套装 ID。 */
    fun listSets(): List<String>

    /** 通用注销入口，按命名空间分发。 */
    fun unregister(namespace: ExtensionNamespace, id: String): Boolean

    enum class ExtensionNamespace {
        REACTION, STATUS, ENVIRONMENT, RESONANCE, TALENT, INTERACTION, SET
    }
}
