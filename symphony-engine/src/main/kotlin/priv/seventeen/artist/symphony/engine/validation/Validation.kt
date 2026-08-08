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

package priv.seventeen.artist.symphony.engine.validation

import java.nio.file.Path

enum class Severity {
    WARNING,
    ERROR
}

data class ValidationIssue(
    val severity: Severity,
    val source: Path?,
    val path: String,
    val message: String,
    val cause: Throwable? = null
)

class ValidationReport internal constructor(
    issues: List<ValidationIssue>
) {
    val issues: List<ValidationIssue> = issues.toList()
    val errors: List<ValidationIssue> get() = issues.filter { it.severity == Severity.ERROR }
    val warnings: List<ValidationIssue> get() = issues.filter { it.severity == Severity.WARNING }
    val valid: Boolean get() = errors.isEmpty()

    fun throwIfInvalid() {
        if (!valid) throw DefinitionValidationException(this)
    }
}

class ValidationCollector {
    private val issues = mutableListOf<ValidationIssue>()

    fun error(source: Path?, path: String, message: String, cause: Throwable? = null) {
        issues += ValidationIssue(Severity.ERROR, source, path, message, cause)
    }

    fun warning(source: Path?, path: String, message: String) {
        issues += ValidationIssue(Severity.WARNING, source, path, message)
    }

    fun merge(report: ValidationReport) {
        issues += report.issues
    }

    fun report(): ValidationReport = ValidationReport(issues)
}

class DefinitionValidationException(
    val report: ValidationReport
) : IllegalArgumentException(
    report.errors.joinToString(prefix = "定义校验失败：", separator = "；") {
        val source = it.source?.toString()?.let { path -> "$path:" }.orEmpty()
        "$source${it.path}: ${it.message}"
    }
)
