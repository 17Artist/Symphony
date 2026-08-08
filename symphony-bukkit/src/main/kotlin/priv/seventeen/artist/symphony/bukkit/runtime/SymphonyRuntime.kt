/*
 * Copyright 2026 17Artist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package priv.seventeen.artist.symphony.bukkit.runtime

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.ServicePriority
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.overture.api.OvertureAPI
import priv.seventeen.artist.symphony.api.service.SymphonyApi
import priv.seventeen.artist.symphony.api.event.SetCountsChangedEvent
import priv.seventeen.artist.symphony.bukkit.combat.BukkitDamageService
import priv.seventeen.artist.symphony.bukkit.equipment.EquipmentReconciler
import priv.seventeen.artist.symphony.bukkit.integration.OptionalIntegrations
import priv.seventeen.artist.symphony.bukkit.gui.GuiService
import priv.seventeen.artist.symphony.bukkit.gui.ItemWorkshopService
import priv.seventeen.artist.symphony.bukkit.gui.GuiLayoutRepository
import priv.seventeen.artist.symphony.bukkit.lifecycle.DefaultResources
import priv.seventeen.artist.symphony.bukkit.service.BukkitAttributeService
import priv.seventeen.artist.symphony.bukkit.service.BukkitAttributeSourceService
import priv.seventeen.artist.symphony.bukkit.service.BukkitAttributeStateObserver
import priv.seventeen.artist.symphony.bukkit.service.AttributeCacheEvictionRuntime
import priv.seventeen.artist.symphony.bukkit.service.BukkitMetadataService
import priv.seventeen.artist.symphony.bukkit.service.BukkitAffixService
import priv.seventeen.artist.symphony.bukkit.service.BukkitDefinitionService
import priv.seventeen.artist.symphony.bukkit.service.BukkitLevelService
import priv.seventeen.artist.symphony.bukkit.service.BukkitTriggerService
import priv.seventeen.artist.symphony.bukkit.service.BukkitCombatPowerService
import priv.seventeen.artist.symphony.engine.attribute.AttributeStateStore
import priv.seventeen.artist.symphony.engine.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.engine.attribute.CalculateHook
import priv.seventeen.artist.symphony.engine.attribute.InteractionCalculator
import priv.seventeen.artist.symphony.engine.attribute.SourceLineParser
import priv.seventeen.artist.symphony.engine.config.DefinitionLoadResult
import priv.seventeen.artist.symphony.engine.config.DefinitionLoader
import priv.seventeen.artist.symphony.engine.config.LanguageBundle
import priv.seventeen.artist.symphony.engine.config.ItemDisplayFormats
import priv.seventeen.artist.symphony.engine.config.SymphonySettings
import priv.seventeen.artist.symphony.integrations.epicfight.EpicFightIntegration
import priv.seventeen.artist.symphony.engine.definition.DefinitionRepository
import priv.seventeen.artist.symphony.overture.OvertureBridge
import priv.seventeen.artist.symphony.overture.item.OvertureItemAttributeService
import priv.seventeen.artist.symphony.overture.item.OvertureItemSourceCompiler
import priv.seventeen.artist.symphony.overture.item.OvertureMutationGateway
import priv.seventeen.artist.symphony.bukkit.script.AriaCallbackRuntime
import priv.seventeen.artist.symphony.bukkit.script.DefaultCallbackActivationResolver
import priv.seventeen.artist.symphony.bukkit.script.DefaultConfiguredCallbackRuntime
import priv.seventeen.artist.symphony.bukkit.gameplay.HealthRegenerationRuntime
import priv.seventeen.artist.symphony.bukkit.gameplay.RuntimeSkillService
import priv.seventeen.artist.symphony.bukkit.gameplay.StatusRuntime
import priv.seventeen.artist.symphony.bukkit.gameplay.EnvironmentRuntime
import priv.seventeen.artist.symphony.bukkit.gameplay.TriggerTimerRuntime
import priv.seventeen.artist.symphony.bukkit.gameplay.PassiveRuleRuntime
import priv.seventeen.artist.symphony.bukkit.gameplay.ElementReactionRuntime
import priv.seventeen.artist.symphony.engine.validation.Severity
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID

object SymphonyRuntime {
    private enum class State { NEW, LOADED, ENABLED, DISABLED }

    @Volatile private var state = State.NEW
    private lateinit var plugin: Plugin
    private lateinit var settings: SymphonySettings
    private lateinit var definitions: DefinitionRepository
    private lateinit var definitionService: BukkitDefinitionService
    private lateinit var store: AttributeStateStore
    private lateinit var attributeService: BukkitAttributeService
    private lateinit var sourceService: BukkitAttributeSourceService
    private lateinit var triggerService: BukkitTriggerService
    private lateinit var levelService: BukkitLevelService
    private lateinit var combatPowerService: BukkitCombatPowerService
    private lateinit var damageService: BukkitDamageService
    private lateinit var equipmentReconciler: EquipmentReconciler
    private lateinit var guiService: GuiService
    private lateinit var overtureBridge: OvertureBridge
    private lateinit var serviceApi: SymphonyApi
    private lateinit var ariaCallbacks: AriaCallbackRuntime
    private lateinit var configuredCallbacks: DefaultConfiguredCallbackRuntime
    private lateinit var healthRegenerationRuntime: HealthRegenerationRuntime
    private lateinit var skillService: RuntimeSkillService
    private lateinit var statusRuntime: StatusRuntime
    private lateinit var environmentRuntime: EnvironmentRuntime
    private lateinit var triggerTimerRuntime: TriggerTimerRuntime
    private lateinit var passiveRuleRuntime: PassiveRuleRuntime
    private lateinit var elementReactionRuntime: ElementReactionRuntime
    private lateinit var attributeStateObserver: BukkitAttributeStateObserver
    private lateinit var cacheEvictionRuntime: AttributeCacheEvictionRuntime
    private lateinit var epicFightCompatibility: EpicFightIntegration
    private val languageReference = AtomicReference<LanguageBundle>()
    private val displayFormatReference = AtomicReference<ItemDisplayFormats>()

    @Synchronized
    fun load(plugin: Plugin) {
        check(state == State.NEW || state == State.DISABLED) { "Symphony 运行时无法从状态 $state 进入加载阶段" }
        this.plugin = plugin
        val defaultResources = DefaultResources.install(plugin)
        languageReference.set(loadLanguage())
        if (defaultResources.showcase.installed) {
            BlinkLog.success(
                text(
                    "console.showcase-installed",
                    "symphony" to defaultResources.showcase.copiedSymphonyFiles,
                    "overture" to defaultResources.showcase.copiedOvertureFiles
                )
            )
        }
        displayFormatReference.set(loadDisplayFormats())
        val guiLayouts = GuiLayoutRepository.load(plugin.dataFolder.toPath())
        val loaded = DefinitionLoader(plugin.dataFolder.toPath()).load()
        logValidation(loaded)
        val snapshot = requireNotNull(loaded.snapshot) { "Symphony 候选定义无效" }
        settings = requireNotNull(loaded.settings) { "Symphony 配置无效" }
        definitions = DefinitionRepository(snapshot)
        definitionService = BukkitDefinitionService(definitions, snapshot)
        val ariaReference = AtomicReference<AriaCallbackRuntime?>()
        val interactionCalculator = InteractionCalculator(definitions, settings.features.interactions)
        epicFightCompatibility = EpicFightIntegration(plugin, settings.compatibility.epicFight, languageReference::get)
        attributeStateObserver = BukkitAttributeStateObserver(epicFightCompatibility)
        store = AttributeStateStore(
            definitions,
            AttributeCalculator(CalculateHook { entityId, definition, standardValue, resolved ->
                val entity = Bukkit.getEntity(entityId) as? LivingEntity ?: return@CalculateHook standardValue
                val interacted = interactionCalculator.apply(definition.key, standardValue, resolved)
                ariaReference.get()?.calculate(entity, definition, interacted, resolved) ?: interacted
            }),
            observer = attributeStateObserver
        )
        val itemCompiler = OvertureItemSourceCompiler(
            definitions,
            affixesEnabled = settings.features.affixes,
            itemFeaturesEnabled = settings.features.gems || settings.features.sockets || settings.features.enhancement
        )
        attributeService = BukkitAttributeService(store, definitions)
        sourceService = BukkitAttributeSourceService(store, SourceLineParser(), itemCompiler)
        triggerService = BukkitTriggerService(
            settings.scripts.failureWindowSeconds,
            settings.scripts.disableAfterFailures
        ) { callback, error -> BlinkLog.error(text("console.callback-failed", "callback" to callback), error) }
        levelService = BukkitLevelService(attributeService, triggerService)
        combatPowerService = BukkitCombatPowerService(
            definitions,
            store,
            levelService,
            onError = { entity, error ->
                BlinkLog.error(text("console.combat-power-failed", "entity" to entity.uniqueId, "message" to error.message), error)
            }
        )
        attributeStateObserver.afterCommit = combatPowerService::refreshIfCached
        cacheEvictionRuntime = AttributeCacheEvictionRuntime(
            plugin,
            store,
            settings.performance.cacheIdleSeconds,
            onMaintenance = ::maintainRuntimeState
        )
        damageService = BukkitDamageService(
            plugin,
            definitions,
            attributeService,
            store,
            triggerService,
            settings.combat.enabled,
            settings.combat.mappedEnvironmentalCauses,
            settings.combat.minimumDamage,
            settings.combat.maxTransactionDepth,
            settings.combat.confirmationDelayTicks
        )
        elementReactionRuntime = ElementReactionRuntime(
            definitions,
            settings.features.elements,
            onEffectFailure = { reaction, error -> BlinkLog.error(text("console.reaction-effect-failed", "reaction" to reaction), error) }
        )
            .also { damageService.reactionPlanner = it }
        healthRegenerationRuntime = HealthRegenerationRuntime(plugin, attributeService)
        statusRuntime = StatusRuntime(
            plugin,
            definitions,
            sourceService,
            triggerService,
            settings.performance.timerBucketTicks,
            settings.features.statuses
        )
        environmentRuntime = EnvironmentRuntime(
            plugin,
            definitions,
            sourceService,
            settings.performance.timerBucketTicks,
            settings.features.environments
        )
        triggerTimerRuntime = TriggerTimerRuntime(plugin, triggerService, settings.performance.timerBucketTicks)
        passiveRuleRuntime = PassiveRuleRuntime(
            plugin,
            definitions,
            store,
            attributeService,
            sourceService,
            settings.features.resonances,
            settings.features.talents
        )
        val skillReference = AtomicReference<RuntimeSkillService?>()
        configuredCallbacks = DefaultConfiguredCallbackRuntime(
            plugin,
            attributeService,
            sourceService,
            damageService,
            skillCaster = { caster, skill, target ->
                val key = NamespacedKey.fromString(skill)
                if (key == null) false else skillReference.get()?.cast(caster, key, target) ?: false
            },
            levelReader = { entity -> levelService.snapshot(entity)?.level },
            statusApplier = statusRuntime::apply
        )
        ariaCallbacks = AriaCallbackRuntime(
            plugin.dataFolder.toPath().resolve("scripts"),
            attributeService,
            damageService,
            configuredCallbacks,
            DefaultCallbackActivationResolver(
                store,
                settings.features,
                statusVariables = statusRuntime::callbackVariables,
                environmentVariables = environmentRuntime::callbackVariables,
                passiveVariables = passiveRuleRuntime::callbackVariables
            ),
            settings.scripts.slowCallbackWarningMillis,
            damageService::isInCombat
        ).also { runtime ->
            runtime.dispatchCallback = { trigger, context -> triggerService.dispatch(trigger, context).result }
            val prepared = runtime.prepare(snapshot)
            triggerService.dispatcher().replaceCallbacks(prepared.callbacks)
            runtime.commit(prepared)
            ariaReference.set(runtime)
        }
        skillService = RuntimeSkillService(
            definitions,
            sourceService,
            ariaCallbacks,
            triggerService,
            settings.features.skills
        )
        skillReference.set(skillService)
        equipmentReconciler = EquipmentReconciler(
            plugin,
            sourceService,
            triggerService,
            settings.equipment.offhand,
            settings.equipment.coalesceTicks
        )
        equipmentReconciler.onSetCountsChanged = { entity ->
            publishSetCountsChanged(entity)
        }
        sourceService.onSetCountsChanged = { entity ->
            if (entity is org.bukkit.entity.Player) equipmentReconciler.refreshLore(entity)
            publishSetCountsChanged(entity)
        }
        overtureBridge = OvertureBridge(
            plugin,
            definitions,
            languageReference::get,
            displayFormatReference::get
        ) { player, setId -> store.stateIfPresent(player.uniqueId)?.setResolution?.counts?.get(setId) ?: 0 }
            .also { it.register() }
        serviceApi = SymphonyApiImpl(
            attributeService,
            sourceService,
            damageService,
            triggerService,
            definitionService,
            OvertureItemAttributeService(itemCompiler),
            skillService,
            levelService,
            BukkitMetadataService(statusRuntime, elementReactionRuntime, passiveRuleRuntime, store, damageService),
            BukkitAffixService(definitions, store, settings.features.affixes),
            combatPowerService
        )
        guiService = GuiService(
            plugin,
            serviceApi,
            store,
            triggerService,
            equipmentReconciler,
            ItemWorkshopService(
                definitions,
                languageReference::get,
                OvertureMutationGateway(),
                affixesEnabled = settings.features.affixes,
                gemsEnabled = settings.features.gems,
                socketsEnabled = settings.features.sockets,
                enhancementEnabled = settings.features.enhancement
            ),
            guiLayouts,
            languageReference::get,
            definitions
        )
        state = State.LOADED
        BlinkLog.success(text("console.loaded", "attributes" to snapshot.attributes.size, "channels" to snapshot.damageChannels.size, "sets" to snapshot.sets.size))
    }

    @Synchronized
    fun enable() {
        check(state == State.LOADED) { "Symphony 运行时无法从状态 $state 进入启用阶段" }
        Bukkit.getServicesManager().register(SymphonyApi::class.java, serviceApi, plugin, ServicePriority.Normal)
        OptionalIntegrations.installPlaceholderApi(serviceApi, plugin.description.version)
        OptionalIntegrations.installMythicMobs(plugin, serviceApi)
        epicFightCompatibility.start()
        damageService.start()
        cacheEvictionRuntime.start()
        healthRegenerationRuntime.start()
        statusRuntime.start()
        environmentRuntime.start()
        triggerTimerRuntime.start()
        guiService.start()
        state = State.ENABLED
    }

    private fun publishSetCountsChanged(entity: LivingEntity) {
        val counts = sourceService.setCounts(entity).mapNotNull { (id, count) ->
            org.bukkit.NamespacedKey.fromString(id)?.let { it to count }
        }.toMap()
        Bukkit.getPluginManager().callEvent(SetCountsChangedEvent(entity, counts))
    }

    fun activate() {
        check(state == State.ENABLED) { "Symphony 运行时尚未启用" }
        Bukkit.getWorlds().flatMap { it.livingEntities }.forEach {
            equipmentReconciler.reconcile(it)
            environmentRuntime.mark(it)
            passiveRuleRuntime.mark(it)
        }
    }

    @Synchronized
    fun disable() {
        if (state == State.NEW || state == State.DISABLED) return
        Bukkit.getServicesManager().unregisterAll(plugin)
        OptionalIntegrations.close()
        if (::guiService.isInitialized) guiService.close()
        if (::equipmentReconciler.isInitialized) equipmentReconciler.close()
        if (::damageService.isInitialized) damageService.clear()
        if (::configuredCallbacks.isInitialized) configuredCallbacks.clear()
        if (::skillService.isInitialized) skillService.clear()
        if (::healthRegenerationRuntime.isInitialized) healthRegenerationRuntime.close()
        if (::levelService.isInitialized) levelService.clear()
        if (::statusRuntime.isInitialized) statusRuntime.close()
        if (::environmentRuntime.isInitialized) environmentRuntime.close()
        if (::triggerTimerRuntime.isInitialized) triggerTimerRuntime.close()
        if (::passiveRuleRuntime.isInitialized) passiveRuleRuntime.close()
        if (::elementReactionRuntime.isInitialized) elementReactionRuntime.close()
        if (::cacheEvictionRuntime.isInitialized) cacheEvictionRuntime.close()
        if (::epicFightCompatibility.isInitialized) epicFightCompatibility.close()
        if (::attributeStateObserver.isInitialized) attributeStateObserver.close()
        if (::combatPowerService.isInitialized) combatPowerService.close()
        if (::store.isInitialized) store.clear()
        if (::overtureBridge.isInitialized) overtureBridge.close()
        if (::definitionService.isInitialized) definitionService.clearExtensions()
        if (::attributeService.isInitialized) attributeService.clearProviders()
        if (::levelService.isInitialized) levelService.clearProviders()
        if (::triggerService.isInitialized) triggerService.clear()
        state = State.DISABLED
    }

    @Synchronized
    fun reload(): DefinitionLoadResult {
        check(state == State.ENABLED) { "Symphony 运行时必须处于启用状态才能重载" }
        val loaded = DefinitionLoader(plugin.dataFolder.toPath()).load()
        logValidation(loaded)
        val candidate = loaded.snapshot ?: return loaded
        val newSettings = loaded.settings ?: return loaded
        if (newSettings != settings) {
            BlinkLog.error(text("console.reload-restart-required"))
            return loaded.copy(snapshot = null)
        }
        val previous = definitionService.baseSnapshot()
        val preparedLanguage = try {
            loadLanguage()
        } catch (error: Throwable) {
            BlinkLog.error(text("console.language-invalid", "message" to error.message), error)
            return loaded.copy(snapshot = null)
        }
        val preparedDisplayFormats = try {
            loadDisplayFormats()
        } catch (error: Throwable) {
            BlinkLog.error(text("console.display-invalid", "message" to error.message), error)
            return loaded.copy(snapshot = null)
        }
        val preparedLayouts = try {
            GuiLayoutRepository.load(plugin.dataFolder.toPath())
        } catch (error: Throwable) {
            BlinkLog.error(text("console.gui-invalid", "message" to error.message), error)
            return loaded.copy(snapshot = null)
        }
        val preparedScripts = try {
            ariaCallbacks.prepare(candidate)
        } catch (error: Throwable) {
            BlinkLog.error(text("console.scripts-invalid", "message" to error.message), error)
            return loaded.copy(snapshot = null)
        }
        try {
            definitionService.reloadBase(candidate)
            val overtureReport = OvertureAPI.reloadWithReport()
            if (!overtureReport.success) {
                definitionService.reloadBase(previous)
                throw IllegalStateException("Overture 拒绝候选物品快照并已回滚，共 ${overtureReport.errorCount} 个错误")
            }
            triggerService.dispatcher().replaceCallbacks(preparedScripts.callbacks)
            ariaCallbacks.commit(preparedScripts)
            configuredCallbacks.retainCallbacks(preparedScripts.callbacks.mapTo(hashSetOf()) { it.id })
            skillService.retainSkills(candidate.skills.keys)
            guiService.replaceLayouts(preparedLayouts)
            languageReference.set(preparedLanguage)
            displayFormatReference.set(preparedDisplayFormats)
            settings = newSettings
            store.entityIds().forEach { entityId ->
                (Bukkit.getEntity(entityId) as? org.bukkit.entity.LivingEntity)?.let(::recalculateSafely)
                    ?: store.removeEntity(entityId)
            }
            Bukkit.getOnlinePlayers().forEach(equipmentReconciler::refreshLore)
            BlinkLog.success(text("console.reload-complete", "revision" to definitions.current().snapshot.revision))
        } catch (error: Throwable) {
            if (definitions.current().snapshot !== previous) runCatching { definitionService.reloadBase(previous) }
            BlinkLog.error(text("console.reload-failed", "message" to error.message), error)
            return loaded.copy(snapshot = null)
        }
        return loaded
    }

    fun validateDefinitions(): DefinitionLoadResult {
        val loaded = DefinitionLoader(plugin.dataFolder.toPath()).load()
        logValidation(loaded)
        val snapshot = loaded.snapshot ?: return loaded
        return runCatching { ariaCallbacks.prepare(snapshot); loaded }
            .getOrElse { error ->
                BlinkLog.error(text("console.validation-script-failed", "message" to error.message), error)
                loaded.copy(snapshot = null)
            }
    }

    fun api(): SymphonyApi {
        check(state == State.ENABLED) { "Symphony API 尚未启用" }
        return serviceApi
    }

    fun damageOrNull(): BukkitDamageService? =
        if (state == State.ENABLED && ::damageService.isInitialized) damageService else null

    fun skillOrNull(): RuntimeSkillService? =
        if (state == State.ENABLED && ::skillService.isInitialized) skillService else null

    fun sourceOrNull(): BukkitAttributeSourceService? =
        if (state == State.ENABLED && ::sourceService.isInitialized) sourceService else null

    fun attributesOrNull(): BukkitAttributeService? =
        if (state == State.ENABLED && ::attributeService.isInitialized) attributeService else null

    fun storeOrNull(): AttributeStateStore? = if (::store.isInitialized) store else null
    fun settingsOrNull(): SymphonySettings? = if (::settings.isInitialized) settings else null
    fun language(): LanguageBundle = requireNotNull(languageReference.get()) { "语言文件尚未加载" }
    fun languageOrNull(): LanguageBundle? = languageReference.get()
    fun apiOrNull(): SymphonyApi? = if (state == State.ENABLED && ::serviceApi.isInitialized) serviceApi else null
    fun triggerOrNull(): BukkitTriggerService? = if (::triggerService.isInitialized) triggerService else null
    fun equipmentOrNull(): EquipmentReconciler? =
        if (state == State.ENABLED && ::equipmentReconciler.isInitialized) equipmentReconciler else null
    fun guiOrNull(): GuiService? = if (state == State.ENABLED && ::guiService.isInitialized) guiService else null
    fun statusOrNull(): StatusRuntime? = if (state == State.ENABLED && ::statusRuntime.isInitialized) statusRuntime else null
    fun environmentOrNull(): EnvironmentRuntime? =
        if (state == State.ENABLED && ::environmentRuntime.isInitialized) environmentRuntime else null
    fun levelsOrNull(): BukkitLevelService? = if (state == State.ENABLED && ::levelService.isInitialized) levelService else null
    fun combatPowerOrNull(): BukkitCombatPowerService? =
        if (state == State.ENABLED && ::combatPowerService.isInitialized) combatPowerService else null
    fun passiveRulesOrNull(): PassiveRuleRuntime? =
        if (state == State.ENABLED && ::passiveRuleRuntime.isInitialized) passiveRuleRuntime else null
    fun reactionsOrNull(): ElementReactionRuntime? =
        if (state == State.ENABLED && ::elementReactionRuntime.isInitialized) elementReactionRuntime else null
    fun epicFightOrNull(): EpicFightIntegration? =
        if (state == State.ENABLED && ::epicFightCompatibility.isInitialized) epicFightCompatibility else null

    fun forgetEntity(entity: LivingEntity) {
        if (state != State.ENABLED) return
        if (entity is org.bukkit.entity.Player) {
            epicFightCompatibility.onQuit(entity)
            guiService.closeSession(entity.uniqueId, false)
        }
        forgetRuntimeState(entity.uniqueId, entity)
    }

    fun forgetEntity(entityId: UUID) {
        if (state != State.ENABLED) return
        forgetRuntimeState(entityId, Bukkit.getEntity(entityId) as? LivingEntity)
    }

    fun scheduleForgetEntity(entityId: UUID) {
        if (state != State.ENABLED) return
        val delay = settings.combat.confirmationDelayTicks + 1L
        Bukkit.getScheduler().runTaskLater(plugin, Runnable { forgetEntity(entityId) }, delay)
    }

    fun removeExternalOwner(owner: Plugin) {
        if (state != State.ENABLED || owner === plugin) return
        definitionService.removeOwner(owner)
        attributeService.removeOwner(owner)
        levelService.removeOwner(owner)
        triggerService.removeOwner(owner)
        store.entityIds().forEach { entityId ->
            (Bukkit.getEntity(entityId) as? LivingEntity)?.let(::recalculateSafely)
                ?: forgetRuntimeState(entityId, null)
        }
    }

    private fun maintainRuntimeState(evicted: List<UUID>, now: Long) {
        evicted.forEach { forgetRuntimeState(it, null) }
        skillService.maintenance(now)
        configuredCallbacks.maintenance(now)
        ariaCallbacks.maintenance(now)
        elementReactionRuntime.maintenance(now)
    }

    private fun forgetRuntimeState(entityId: UUID, entity: LivingEntity?) {
        equipmentReconciler.forget(entityId)
        statusRuntime.forget(entityId)
        environmentRuntime.forget(entityId)
        passiveRuleRuntime.forget(entityId)
        levelService.remove(entityId)
        combatPowerService.invalidate(entityId)
        damageService.forget(entityId)
        skillService.forget(entityId)
        configuredCallbacks.forget(entityId)
        ariaCallbacks.forget(entityId)
        elementReactionRuntime.forget(entityId)
        attributeStateObserver.forget(entityId, entity)
        store.removeEntity(entityId)
    }

    private fun recalculateSafely(entity: LivingEntity) {
        runCatching { attributeService.recalculate(entity) }
            .onFailure { BlinkLog.error(text("console.recalculate-failed", "entity" to entity.uniqueId), it) }
    }

    private fun loadLanguage(): LanguageBundle {
        val custom = LanguageBundle.load(plugin.dataFolder.toPath().resolve("language.yml"))
        val bundled = requireNotNull(plugin.getResource("assets/language.yml")) {
            "发布 JAR 缺少默认资源 assets/language.yml"
        }.use { input -> LanguageBundle.load(input, "内置资源 assets/language.yml") }
        return custom.withFallback(bundled)
    }

    private fun text(key: String, vararg variables: Pair<String, Any?>): String =
        languageReference.get()?.text(key, *variables) ?: key

    private fun loadDisplayFormats(): ItemDisplayFormats {
        val custom = ItemDisplayFormats.load(plugin.dataFolder.toPath().resolve("display.yml"))
        val bundled = requireNotNull(plugin.getResource("assets/display.yml")) {
            "发布 JAR 缺少默认资源 assets/display.yml"
        }.use { input -> ItemDisplayFormats.load(input, "内置资源 assets/display.yml") }
        return custom.withFallback(bundled)
    }

    private fun logValidation(result: DefinitionLoadResult) {
        result.report.issues.forEach { issue ->
            val location = listOfNotNull(issue.source?.toString(), issue.path.takeIf(String::isNotBlank)).joinToString(":")
            val message = "$location ${issue.message}".trim()
            if (issue.severity == Severity.ERROR) {
                BlinkLog.error(message)
            } else {
                BlinkLog.warn(message)
            }
        }
    }
}
