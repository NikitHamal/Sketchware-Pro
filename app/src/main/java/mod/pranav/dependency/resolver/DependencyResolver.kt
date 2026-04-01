package mod.pranav.dependency.resolver

import android.os.Environment
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import mod.hey.studios.build.BuildSettings
import mod.hey.studios.util.Helper
import mod.jbk.build.BuiltInLibraries
import org.cosmic.ide.dependency.resolver.api.Artifact
import org.cosmic.ide.dependency.resolver.api.EventReciever
import org.cosmic.ide.dependency.resolver.api.Repository
import org.cosmic.ide.dependency.resolver.eventReciever
import org.cosmic.ide.dependency.resolver.getArtifact
import org.cosmic.ide.dependency.resolver.repositories
import pro.sketchware.utility.FileUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.LinkedHashSet
import java.util.regex.Pattern
import java.util.zip.ZipFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

class DependencyResolver(
    private val groupId: String,
    private val artifactId: String,
    private val version: String,
    private val skipDependencies: Boolean,
    private val buildSettings: BuildSettings
) {
    companion object {
        private val DEFAULT_REPOS = listOf(
            mapOf("url" to "https://dl.google.com/dl/android/maven2", "name" to "Google Maven"),
            mapOf("url" to "https://repo.maven.apache.org/maven2", "name" to "Maven Central"),
            mapOf("url" to "https://jitpack.io", "name" to "JitPack"),
            mapOf("url" to "https://s01.oss.sonatype.org/content/repositories/releases", "name" to "Sonatype OSS")
        )

        private val BLOCKED_REPOSITORIES = setOf(
            "https://jcenter.bintray.com",
            "https://repo.hortonworks.com/content/repositories/releases",
            "https://maven.atlassian.com/content/repositories/atlassian-public",
            "https://repo.spring.io/plugins-release",
            "https://repo.spring.io/libs-milestone"
        )
    }

    private val downloadPath: String =
        FileUtil.getExternalStorageDir() + "/.sketchware/libs/local_libs"

    private val repositoriesJson = Paths.get(
        Environment.getExternalStorageDirectory().absolutePath,
        ".sketchware",
        "libs",
        "repositories.json"
    )

    init {
        ensureRepositoriesFile()
        installRepositories(sanitizeRepositories(readRepositories()))
    }

    open class DependencyResolverCallback : EventReciever() {
        override fun artifactFound(artifact: Artifact) {}
        override fun onArtifactNotFound(artifact: Artifact) {}
        override fun onFetchingLatestVersion(artifact: Artifact) {}
        override fun onFetchedLatestVersion(artifact: Artifact, version: String) {}
        override fun onResolving(artifact: Artifact, dependency: Artifact) {}
        override fun onResolutionComplete(artifact: Artifact) {}
        override fun onSkippingResolution(artifact: Artifact) {}
        override fun onVersionNotFound(artifact: Artifact) {}
        override fun onDependenciesNotFound(artifact: Artifact) {}
        override fun onInvalidScope(artifact: Artifact, scope: String) {}
        override fun onInvalidPOM(artifact: Artifact) {}
        override fun onDownloadStart(artifact: Artifact) {}
        override fun onDownloadEnd(artifact: Artifact) {}
        override fun onDownloadError(artifact: Artifact, error: Throwable) {}
        open fun unzipping(artifact: Artifact) {}
        open fun dexing(artifact: Artifact) {}
        open fun onTaskCompleted(artifacts: List<String>) {}
        open fun dexingFailed(artifact: Artifact, e: Exception) {}
        open fun invalidPackaging(artifact: Artifact) {}
    }

    fun resolveDependency(callback: DependencyResolverCallback) = runBlocking {
        eventReciever = callback
        val dependency = getArtifact(groupId, artifactId, version) ?: return@runBlocking

        if (dependency.extension != "jar" && dependency.extension != "aar") {
            callback.invalidPackaging(dependency)
            return@runBlocking
        }

        val libraryJars = listOf(
            BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH.toPath().resolve("core-lambda-stubs.jar"),
            Paths.get(
                buildSettings.getValue(
                    BuildSettings.SETTING_ANDROID_JAR_PATH,
                    BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH.resolve("android.jar").absolutePath
                )
            )
        )
        val dependencyClasspath = LinkedHashSet<Path>()
        buildSettings.getValue(BuildSettings.SETTING_CLASSPATH, "")
            .split(":")
            .filter { it.isNotEmpty() }
            .mapTo(dependencyClasspath) { Paths.get(it) }

        val mainArtifactDirectory = Paths.get(downloadPath, "${dependency.artifactId}-v${dependency.version}")
        val mainArtifactFile = mainArtifactDirectory.resolve("classes.${dependency.extension}")
        Files.createDirectories(mainArtifactDirectory)
        dependency.downloadTo(mainArtifactFile.toFile())

        val mainJar = prepareArtifact(dependency, mainArtifactFile, callback)
        callback.dexing(dependency)
        try {
            compileJar(mainJar, dependencyClasspath.toList(), libraryJars)
            callback.onResolutionComplete(dependency)
        } catch (e: Exception) {
            callback.dexingFailed(dependency, e)
        }

        if (skipDependencies) {
            callback.onSkippingResolution(dependency)
            callback.onTaskCompleted(listOf("${dependency.artifactId}-v${dependency.version}"))
            return@runBlocking
        }

        dependency.resolveDependencyTree()

        dependency.getAllDependencies().forEach { dep ->
            if (dep.extension != "jar" && dep.extension != "aar") {
                callback.invalidPackaging(dep)
                return@forEach
            }
            if (dep.version.isEmpty()) {
                callback.onVersionNotFound(dep)
                return@forEach
            }

            val artifactDirectory = Paths.get(downloadPath, "${dep.artifactId}-v${dep.version}")
            val downloadedArtifact = artifactDirectory.resolve("classes.${dep.extension}")
            Files.createDirectories(artifactDirectory)
            dep.downloadTo(downloadedArtifact.toFile())

            val jar = prepareArtifact(dep, downloadedArtifact, callback)
            if (Files.notExists(jar)) {
                callback.onDependenciesNotFound(dep)
                return@forEach
            }
            dependencyClasspath.add(jar)
        }

        dependency.getAllDependencies().forEach { dep ->
            val jar = Paths.get(downloadPath, "${dep.artifactId}-v${dep.version}", "classes.jar")
            callback.dexing(dep)
            try {
                compileJar(jar, dependencyClasspath.toMutableList().apply { remove(jar) }, libraryJars)
                callback.onResolutionComplete(dep)
            } catch (e: Exception) {
                callback.dexingFailed(dep, e)
                return@forEach
            }
        }

        callback.onTaskCompleted(dependency.getAllDependencies().map { "${it.artifactId}-v${it.version}" })
    }

    private fun ensureRepositoriesFile() {
        if (Files.notExists(repositoriesJson)) {
            Files.createDirectories(repositoriesJson.parent)
            repositoriesJson.writeText(Gson().toJson(DEFAULT_REPOS))
            return
        }

        val sanitized = sanitizeRepositories(readRepositories())
        repositoriesJson.writeText(Gson().toJson(sanitized))
    }

    private fun readRepositories(): List<Map<String, Any?>> {
        return try {
            Gson().fromJson(repositoriesJson.readText(), Helper.TYPE_MAP_LIST)
        } catch (_: Exception) {
            DEFAULT_REPOS
        }
    }

    private fun sanitizeRepositories(rawRepositories: List<Map<String, Any?>>): List<Map<String, String>> {
        val ordered = LinkedHashMap<String, String>()
        (DEFAULT_REPOS + rawRepositories).forEach { repo ->
            val rawUrl = (repo["url"] as? String).orEmpty().trim().removeSuffix("/")
            val rawName = (repo["name"] as? String).orEmpty().trim()
            if (rawUrl.isBlank() || rawUrl in BLOCKED_REPOSITORIES) {
                return@forEach
            }
            ordered.putIfAbsent(rawUrl, if (rawName.isBlank()) rawUrl else rawName)
        }
        return ordered.map { mapOf("url" to it.key, "name" to it.value) }
    }

    private fun installRepositories(repoDefinitions: List<Map<String, String>>) {
        repositories.clear()
        repoDefinitions.forEach { repo ->
            val url = repo["url"] ?: return@forEach
            val name = repo["name"] ?: url
            repositories.add(object : Repository {
                override fun getName(): String = name
                override fun getURL(): String = url
            })
        }
    }

    private fun prepareArtifact(
        artifact: Artifact,
        downloadedFile: Path,
        callback: DependencyResolverCallback
    ): Path {
        if (artifact.extension == "aar") {
            callback.unzipping(artifact)
            unzip(downloadedFile)
            Files.deleteIfExists(downloadedFile)
            val packageName = findPackageName(downloadedFile.parent.toAbsolutePath().toString(), artifact.groupId)
            downloadedFile.parent.resolve("config").writeText(packageName)
        }
        return if (artifact.extension == "jar") downloadedFile else downloadedFile.parent.resolve("classes.jar")
    }

    private fun findPackageName(path: String, defaultValue: String): String {
        val manifest = File(path).walk().filter { it.isFile && it.name == "AndroidManifest.xml" }.firstOrNull()
        val content = manifest?.readText() ?: return defaultValue
        val matcher = Pattern.compile("<manifest.*package=\"(.*?)\"", Pattern.DOTALL).matcher(content)
        return if (matcher.find()) matcher.group(1) ?: defaultValue else defaultValue
    }

    private fun unzip(path: Path) {
        ZipFile(path.toFile()).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val entryDestination = FileUtil.getSafeZipEntryTarget(path.parent.toFile(), entry.name).toPath()
                if (entry.isDirectory) {
                    Files.createDirectories(entryDestination)
                } else {
                    Files.createDirectories(entryDestination.parent)
                    zip.getInputStream(entry).use { input ->
                        Files.newOutputStream(entryDestination).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    private fun compileJar(jarFile: Path, jars: List<Path>, libraryJars: List<Path>) {
        Files.createDirectories(jarFile.parent)
        D8.run(
            D8Command.builder()
                .setIntermediate(true)
                .setMode(CompilationMode.RELEASE)
                .addProgramFiles(jarFile)
                .addLibraryFiles(libraryJars)
                .addClasspathFiles(jars)
                .setOutput(jarFile.parent, OutputMode.DexIndexed)
                .build()
        )
    }
}
