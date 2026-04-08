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
        private val DEFAULT_REPOS = """
          |[
          |    {"url": "https://dl.google.com/dl/android/maven2", "name": "Google Maven"},
          |    {"url": "https://repo.maven.apache.org/maven2", "name": "Maven Central"},
          |    {"url": "https://oss.sonatype.org/content/repositories/releases", "name": "Sonatype Releases"},
          |    {"url": "https://jitpack.io", "name": "JitPack"}
          |]
        """.trimMargin()
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
        if (Files.notExists(repositoriesJson)) {
            Files.createDirectories(repositoriesJson.parent)
            repositoriesJson.writeText(DEFAULT_REPOS)
        }
        Gson().fromJson(repositoriesJson.readText(), Helper.TYPE_MAP_LIST).forEach {
            val url: String? = it["url"] as String?
            if (url != null) {
                repositories.add(object : Repository {
                    override fun getName(): String {
                        return it["name"] as String
                    }

                    override fun getURL(): String {
                        return if (url.endsWith("/")) {
                            url.substringBeforeLast("/")
                        } else {
                            url
                        }
                    }
                })
            }
        }
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

        val libraryJars = mutableListOf<Path>()
        libraryJars.add(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH.toPath().resolve("core-lambda-stubs.jar"))
        libraryJars.add(Paths.get(
            buildSettings.getValue(
                BuildSettings.SETTING_ANDROID_JAR_PATH,
                BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH.resolve("android.jar").absolutePath
            )
        ))

        val baseClasspath = linkedSetOf<Path>()
        buildSettings.getValue(BuildSettings.SETTING_CLASSPATH, "")
            .split(":")
            .filter { it.isNotEmpty() }
            .mapTo(baseClasspath) { Paths.get(it) }

        val resolvedArtifactNames = linkedSetOf<String>()
        val downloadedArtifactJars = linkedSetOf<Path>()

        val rootJar = prepareArtifact(dependency, callback)
        if (rootJar == null) {
            callback.onDependenciesNotFound(dependency)
            return@runBlocking
        }
        downloadedArtifactJars.add(rootJar)
        resolvedArtifactNames.add("${dependency.artifactId}-v${dependency.version}")

        if (!skipDependencies) {
            dependency.resolveDependencyTree()
            dependency.getAllDependencies().forEach { dep ->
                println("Resolving dependency: ${dep.artifactId} v${dep.version}")
                if (dep.extension != "jar" && dep.extension != "aar") {
                    callback.invalidPackaging(dep)
                    return@forEach
                }
                if (dep.version.isEmpty()) {
                    callback.onVersionNotFound(dep)
                    return@forEach
                }

                val jar = prepareArtifact(dep, callback)
                if (jar == null) {
                    callback.onDependenciesNotFound(dep)
                    return@forEach
                }

                downloadedArtifactJars.add(jar)
                resolvedArtifactNames.add("${dep.artifactId}-v${dep.version}")
            }
        }

        val compileTargets = linkedMapOf<Artifact, Path>()
        compileTargets[dependency] = rootJar
        if (!skipDependencies) {
            dependency.getAllDependencies().forEach { dep ->
                val jar = Paths.get(downloadPath, "${dep.artifactId}-v${dep.version}", "classes.jar")
                if (Files.exists(jar)) {
                    compileTargets[dep] = jar
                }
            }
        }

        compileTargets.forEach { (artifact, jar) ->
            callback.dexing(artifact)
            try {
                val compileClasspath = linkedSetOf<Path>()
                compileClasspath.addAll(baseClasspath)
                compileClasspath.addAll(downloadedArtifactJars.filter { it != jar })
                compileJar(jar, compileClasspath.toList(), libraryJars)
                callback.onResolutionComplete(artifact)
            } catch (e: Exception) {
                callback.dexingFailed(artifact, e)
            }
        }

        if (skipDependencies) {
            callback.onSkippingResolution(dependency)
        }
        callback.onTaskCompleted(resolvedArtifactNames.toList())
    }

    private fun prepareArtifact(artifact: Artifact, callback: DependencyResolverCallback): Path? {
        val artifactDirectory = Paths.get(downloadPath, "${artifact.artifactId}-v${artifact.version}")
        val archivePath = artifactDirectory.resolve("classes.${artifact.extension}")
        Files.createDirectories(artifactDirectory)

        artifact.downloadTo(archivePath.toFile())

        if (artifact.extension == "aar") {
            callback.unzipping(artifact)
            unzip(archivePath)
            Files.deleteIfExists(archivePath)
            val packageName = findPackageName(artifactDirectory.toAbsolutePath().toString(), artifact.groupId)
            artifactDirectory.resolve("config").writeText(packageName)
        }

        val jar = artifactDirectory.resolve("classes.jar")
        return if (Files.exists(jar)) jar else null
    }

    private fun findPackageName(path: String, defaultValue: String): String {
        val manifest =
            File(path).walk().filter { it.isFile && it.name == "AndroidManifest.xml" }.firstOrNull()
        val content = manifest?.readText() ?: return defaultValue
        val p = Pattern.compile("<manifest.*package=\"(.*?)\"", Pattern.DOTALL)
        val m = p.matcher(content)
        if (m.find()) {
            return m.group(1)!!
        }

        return defaultValue
    }

    private fun unzip(path: Path) {
        val zipFile = ZipFile(path.toFile())
        zipFile.use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val entryDestination = path.parent.resolve(entry.name)
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
                .setMinApiLevel(buildSettings.minSdkVersion)
                .addProgramFiles(jarFile)
                .addLibraryFiles(libraryJars)
                .addClasspathFiles(jars)
                .setOutput(jarFile.parent, OutputMode.DexIndexed)
                .build()
        )
    }
}
