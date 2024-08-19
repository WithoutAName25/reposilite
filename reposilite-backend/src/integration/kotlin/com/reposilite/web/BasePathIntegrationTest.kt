/*
 * Copyright (c) 2023 dzikoysk
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

@file:Suppress("FunctionName")

package com.reposilite.web

import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.configuration.local.LocalConfiguration
import io.javalin.Javalin
import io.javalin.http.Context
import java.util.concurrent.CountDownLatch
import kong.unirest.core.HttpRequest
import kong.unirest.core.Unirest
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class BasePathIntegrationTest : ReposiliteSpecification() {

    private val basePath = "/custom-base-path"

    override fun overrideLocalConfiguration(localConfiguration: LocalConfiguration) {
        localConfiguration.basePath.update { basePath }
    }

    @BeforeEach
    fun setupRepository() {
        reposiliteWorkingDirectory.toPath()
            .resolve("repositories/releases/gav/file.txt")
            .also { it.parent.createDirectories() }
            .also { it.createFile() }
            .writeText("Content")
    }

    @Disabled
    @Test
    fun `run reposilite with custom base path`() {
        val await = CountDownLatch(1)

        Javalin.create()
            .get("/") { it.html("Index") }
            .get(basePath) { Unirest.get(it.reposiliteLocation()).redirect(it) }
            .get("$basePath/<uri>") { Unirest.get(it.reposiliteLocation()).redirect(it) }
            .head("$basePath/<uri>") { Unirest.head(it.reposiliteLocation()).redirect(it) }
            .post("$basePath/<uri>") { Unirest.post(it.reposiliteLocation()).redirect(it) }
            .put("$basePath/<uri>") { Unirest.put(it.reposiliteLocation()).redirect(it) }
            .delete("$basePath/<uri>") { Unirest.delete(it.reposiliteLocation()).redirect(it) }
            .options("$basePath/<uri>") { Unirest.options(it.reposiliteLocation()).redirect(it) }
            .get("/stop") { await.countDown() }
            .start(8080)

        await.await()
    }

    private val restrictedHttpHeaders = listOf(
        "Connection",
        "Host",
    )

    private fun <R : HttpRequest<*>> R.redirect(ctx: Context) {
        ctx.headerMap().filter { it.key !in restrictedHttpHeaders }.forEach { (key, value) -> header(key, value) }
        val response = this.asBytes()
        response.headers.all().forEach { ctx.header(it.name, it.value) }
        ctx.status(response.status).result(response.body)
    }

    private fun Context.reposiliteLocation(): String =
        "http://localhost:${reposilite.parameters.port}/${pathParamMap()["uri"] ?: ""}"

}
