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

package priv.seventeen.artist.symphony.bukkit

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

class PublishWorkflowContractTest {
    private val projectRoot = Path.of("..").toAbsolutePath().normalize()
    private val loader = Load(LoadSettings.builder().setAllowDuplicateKeys(false).build())

    @Test
    fun `GitHub Actions 工作流可以解析且不依赖内部测试模块`() {
        listOf("ci.yml", "publish.yml").forEach { fileName ->
            val path = projectRoot.resolve(".github/workflows/$fileName")
            val source = Files.readString(path)
            val root = loader.loadFromString(source) as? Map<*, *>
            assertTrue(root != null && "on" in root && "jobs" in root, "$fileName 不是有效的 GitHub Actions 工作流")
            assertFalse(source.contains("symphony-testkit"), "$fileName 不得依赖未进入仓库的 testkit")
        }
    }

    @Test
    fun `发布工作流覆盖插件与公共 API 的远程验收`() {
        val source = Files.readString(projectRoot.resolve(".github/workflows/publish.yml"))
        listOf(
            ":symphony-api:publishMavenJavaPublicationToArcartXRepository",
            ":symphony-bukkit:publishPluginPublicationToArcartXRepository",
            "priv/seventeen/artist/symphony/api/service/SymphonyApi.class",
            "MAVEN_REPO_PASSWORD",
            "publish_or_verify",
            "部分发布状态",
            "published-verification"
        ).forEach { required -> assertTrue(required in source, "发布工作流缺少契约：$required") }
        assertTrue(
            source.indexOf(":symphony-bukkit:publishPluginPublicationToArcartXRepository") <
                source.indexOf(":symphony-api:publishMavenJavaPublicationToArcartXRepository"),
            "发布工作流应先上传服务端插件，避免插件失败后留下新的 API 半发布版本"
        )
    }
}
