package priv.seventeen.artist.symphony.plugin

import priv.seventeen.artist.symphony.api.ISymphonyExtensions
import priv.seventeen.artist.symphony.api.ISymphonyExtensions.ExtensionNamespace
import priv.seventeen.artist.symphony.core.advanced.element.ReactionSystem
import priv.seventeen.artist.symphony.core.advanced.environment.EnvironmentSystem
import priv.seventeen.artist.symphony.core.advanced.interaction.InteractionNetwork
import priv.seventeen.artist.symphony.core.advanced.resonance.ResonanceManager
import priv.seventeen.artist.symphony.core.advanced.status.StatusLayerSystem
import priv.seventeen.artist.symphony.core.advanced.talent.TalentManager

/**
 * [ISymphonyExtensions] 实现 — 转发到 core 模块各 singleton。
 */
object SymphonyExtensionsImpl : ISymphonyExtensions {

    override fun listReactions(): List<String> = ReactionSystem.getAll().map { it.id }
    override fun listStatusLayers(): List<String> = StatusLayerSystem.getAll().map { it.id }
    override fun listEnvironmentModifiers(): List<String> = EnvironmentSystem.getAll().map { it.id }
    override fun listResonances(): List<String> = ResonanceManager.getAll().map { it.id }
    override fun listTalents(): List<String> = TalentManager.getAll().map { it.id }
    override fun listInteractions(): List<String> = InteractionNetwork.getAll().map { it.id }
    override fun listSets(): List<String> = SymphonyPlugin.growthManager.setManager.getAllDefinitions().map { it.id }

    override fun unregister(namespace: ExtensionNamespace, id: String): Boolean {
        return when (namespace) {
            ExtensionNamespace.REACTION -> { ReactionSystem.unregister(id); true }
            ExtensionNamespace.STATUS -> { StatusLayerSystem.unregister(id); true }
            ExtensionNamespace.ENVIRONMENT -> { EnvironmentSystem.unregister(id); true }
            ExtensionNamespace.RESONANCE -> { ResonanceManager.unregister(id); true }
            ExtensionNamespace.TALENT -> { TalentManager.unregister(id); true }
            ExtensionNamespace.INTERACTION -> { InteractionNetwork.unregister(id); true }
            ExtensionNamespace.SET -> SymphonyPlugin.growthManager.setManager.unregister(id)
        }
    }
}
