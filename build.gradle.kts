/*
 * Copyright (c) 2019 Aerospike, Inc.
 *
 * All rights reserved.
 *
 * THIS IS UNPUBLISHED PROPRIETARY SOURCE CODE. THE COPYRIGHT NOTICE ABOVE DOES
 * NOT EVIDENCE ANY ACTUAL OR INTENDED PUBLICATION.
 */
import com.aerospike.connect.gradle.AeroDeb
import com.aerospike.connect.gradle.AeroRpm
import com.aerospike.connect.gradle.DockerHub
import com.aerospike.connect.gradle.GithubReleaseConfiguration
import com.bmuschko.gradle.docker.DockerRegistryCredentials
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import com.bmuschko.gradle.docker.tasks.image.DockerTagImage
import com.netflix.gradle.plugins.deb.Deb
import com.netflix.gradle.plugins.packaging.SystemPackagingTask
import com.netflix.gradle.plugins.rpm.Rpm
import groovy.util.Node
import groovy.util.NodeList
import net.researchgate.release.ReleaseExtension
import org.apache.commons.io.FileUtils
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.redline_rpm.header.Os
import org.redline_rpm.payload.Directive
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

val kotlinVersion = "1.4.21"
val javaVersion = "1.8"

buildscript {
    repositories {
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
        jcenter()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.0")
        classpath("com.netflix.nebula:gradle-ospackage-plugin:8.3.0")
        classpath("gradle.plugin.com.lazan:java-monkey-patch:1.0")
        classpath("net.researchgate:gradle-release:2.6.0")
        classpath("io.swagger:swagger-codegen:2.3.1")
        classpath("com.github.jengelman.gradle.plugins:shadow:5.2.0")
        classpath("gradle.plugin.io.github.http-builder-ng:http-plugin:0.1.1")
        classpath("com.github.breadmoirai:github-release:2.2.12")
    }
}

plugins {
    `lifecycle-base`
    jacoco
}

allprojects {
    // Configures the Jacoco tool version to be the same for all projects that have it applied.
    pluginManager.withPlugin("jacoco") {
        // If this project has the plugin applied, configure the tool version.
        jacoco {
            toolVersion = "0.8.5"
        }
    }
}
val uuid: UUID = UUID.randomUUID()

subprojects {
    apply {
        plugin(JavaPlugin::class.java)
        plugin("java-library")
        plugin("org.jetbrains.kotlin.jvm")
        plugin("application")
        plugin("jacoco")
        plugin("nebula.ospackage")
        plugin("maven-publish")
        plugin("net.researchgate.release")
        plugin("com.github.johnrengelman.shadow")
        plugin("com.bmuschko.docker-remote-api")
        plugin("com.github.breadmoirai.github-release")
    }

    val pkgRepoUser: String by project
    val pkgRepoPassword: String by project

    repositories {
        mavenLocal()
        mavenCentral()
        jcenter()
        maven {
            url =
                uri("https://maven.pkg.github.com/ashishshinde/jenkins-release")
            credentials {
                username = pkgRepoUser
                password = pkgRepoPassword
            }
        }

        maven {
            url =
                uri(
                    "https://maven.pkg.github" +
                            ".com/citrusleaf/aerospike-docker-orchestrator"
                )
            credentials {
                username = pkgRepoUser
                password = pkgRepoPassword
            }
        }

        maven {
            url = uri("https://packages.confluent.io/maven/")
        }
        maven {
            url = uri("https://maven.repository.redhat.com/earlyaccess/all/")
        }
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots")
        }
    }

    group = "com.aerospike"

    // Common dependency versions.
    extra["javaVersion"] = javaVersion
    extra["kotlinVersion"] = kotlinVersion
    extra["jacksonVersion"] = "2.12.3"
    extra["msgPackVersion"] = "0.8.16"
    extra["undertowVersion"] = "2.2.3.Final"
    extra["xnioVersion"] = "3.7.1.Final"
    extra["coroutinesVersion"] = "1.4.3"
    extra["jerseyVersion"] = "2.29"
    extra["reflectionsVersion"] = "0.9.11"
    extra["ciryaVersion"] = "3.3"
    extra["aerospikeClientVersion"] = "5.1.1"
    extra["nettyVersion"] = "4.1.50.Final"
    extra["dropWizardMetricsVersion"] = "4.1.17"
    extra["bouncyCastleVersion"] = "1.68"

    // High priority dependencies that should be before other dependencies in
    // the test classpath.
    val testPriority = configurations.create("testPriority")
    configurations["testImplementation"].extendsFrom(testPriority)

    project.extensions.configure(SourceSetContainer::class) {
        this.named("test").get().runtimeClasspath =
            testPriority + this.named("test").get().runtimeClasspath
    }

    dependencies {
        // Ensure kotlin reflect is at correct version.
        "api"("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")

        // Lombok for its @Generated annotaiton that jacoco ignores
        "api"("org.projectlombok:lombok:1.18.8")

        // Common utilities
        "api"("org.apache.commons:commons-lang3:3.9")
        "api"("org.apache.commons:commons-collections4:4.4")

        // Dependency injection
        "api"("com.google.inject:guice:4.2.2")
        "api"("com.google.inject.extensions:guice-assistedinject:4.2.2")

        // Common test dependencies.
        "testImplementation"(
            "org.junit.jupiter:junit-jupiter-api:5.4.2"
        )
        "testImplementation"(
            "org.junit.jupiter:junit-jupiter-params:5.4.2"
        )
        "testRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:5.4.2")
        "testImplementation"("io.mockk:mockk:1.9.3")
        "testImplementation"("commons-io:commons-io:2.6")
        "testImplementation"(
            "org.freemarker:freemarker:2.3.28"
        )
        // Cirya for integration testing.
        "testPriority"(
            "com.aerospike:aerospike-cirya-client:${project.extra["ciryaVersion"]}"
        ) {
            exclude(group = "com.google.inject")
            exclude(group = "org.jetbrains.kotlin")
            exclude(group = "com.fasterxml.jackson.core")
            exclude(group = "ch.qos.logback")
            exclude(group = "org.slf4j")
            exclude(group = "org.apache.commons", module = "commons-lang3")
        }
    }

    /**
     * Ensure code is compiled for java 8 target.
     */
    val compileKotlin: KotlinCompile by tasks
    compileKotlin.kotlinOptions {
        jvmTarget = project.extra["javaVersion"] as String
        allWarningsAsErrors = true
    }
    val compileTestKotlin: KotlinCompile by tasks
    compileTestKotlin.kotlinOptions {
        jvmTarget = project.extra["javaVersion"] as String
        allWarningsAsErrors = true
    }

    val compileJava: JavaCompile by tasks
    compileJava.targetCompatibility = project.extra["javaVersion"] as String
    compileJava.options.apply {
        compilerArgs.add("-Xlint:all")
        compilerArgs.add("-Werror")
    }

    val compileTestJava: JavaCompile by tasks
    compileTestJava.targetCompatibility = project.extra["javaVersion"] as String
    compileTestJava.options.apply {
        compilerArgs.add("-Xlint:all")
        compilerArgs.add("-Werror")
    }

    /**
     * Java NIO ByteBuffer API changed for Java 9. Compiling to Java 8 target
     * still causes method not found exceptions.
     *
     * For now enforce Java 8 compiler.
     *
     */
    //TODO update comment
    tasks.register("checkJavaVersion") {
        if (!JavaVersion.current().isJava8Compatible) {
            val message =
                "ERROR: Java 11 compatible required but ${JavaVersion.current()} found. Change your JAVA_HOME environment variable."
            throw IllegalStateException(message)
        }
    }
    compileKotlin.dependsOn("checkJavaVersion")
    compileTestKotlin.dependsOn("checkJavaVersion")
    compileJava.dependsOn("checkJavaVersion")
    compileTestJava.dependsOn("checkJavaVersion")

    project.extensions.configure(ReleaseExtension::class) {
        tagTemplate = "\$name-\$version"
    }

    // Publish and prepare release artifacts before tagging.
    tasks.getByName("afterReleaseBuild")
        .dependsOn("publish", "prepareGithubRelease")

    // Create "github release" only after the release tasks complete but
    // prepareGithubRelease which runs before a release is tagged and new version is commited will ensure we use
    // assets with the released version.
    tasks.getByName("githubRelease").dependsOn("release")

    /**
     * Common configuration for test tasks.
     */
    fun Test.configureTestTask() {
        val args = mutableListOf(
            "-XX:MaxPermSize=512m",
            "-Xmx4g",
            "-Xms512m",
            "-Djava.security.egd=file:/dev/./urandom",
            "-Dproject.version=${project.version}"
        )


        project.properties.forEach { (property, value) ->
            // Pass along project properties as System properties to the test.
            args += "-D$property=$value"
        }

        // Pass all project versions
        project.parent?.subprojects?.forEach {
            args += "-D${it.name.replace("\\W".toRegex(), ".")}.version=${
                it
                    .version
            }"
        }

        jvmArgs = args
    }

    tasks.create("performance", Test::class) {
        useJUnitPlatform {
            includeTags.add("performance")
        }

        configureTestTask()
    }

    tasks.create("integration", Test::class) {
        useJUnitPlatform {
            includeTags.add("integration")
        }

        configureTestTask()
    }

    /**
     * Use this task for the local run where you don't want to publish docker
     * images.
     */
    tasks.create("localRun", Test::class) {
        useJUnitPlatform {
            include("**/*.class")
        }

        configureTestTask()
    }

    /**
     * Generate test artifacts and upload.
     */
    tasks.register("testJar", Jar::class.java) {
        dependsOn("compileTestJava", "compileTestKotlin")
        val sourceSets = project.the<SourceSetContainer>()
        archiveClassifier.set("test")
        from(sourceSets["test"].output)
    }

    /**
     * Add test jar to project archives.
     */
    artifacts {
        add("archives", tasks.named<Jar>("testJar"))
    }

    /**
     * Use Junit for running tests.
     */
    tasks.getByName<Test>("test") {
        useJUnitPlatform {
            // Exclude performance tests in normal runs.
            excludeTags.add("performance")
        }

        configureTestTask()

        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    // Exclude copying integration test dependencies to build folder.
    tasks.getByName<Copy>("processTestResources") {
        exclude(
            "**/*.gz", "**/*.tgz", "**/*.zip", "**/*.tar",
            "**/*.deb", "**/*.rpm", "**/*.jar"
        )
    }

    /**
     * Use Junit build artifacts for running tests.
     */
    tasks.withType<Test> {
        dependsOn("deb", "rpm")
    }

    // Default archive types to package.
    val archiveTypes: Set<String> =
        when (val archiveTypes = project.properties["archiveTypes"]) {
            null -> emptySet()
            else -> archiveTypes.toString().toLowerCase().split(',').toSet()
        }

    val buildHttp = isSet(project, "buildHttp")

    /**
     * Exclude duplicate jars from generated distribution.
     * Some transitive dependencies cause this.
     */
    val distZip: Zip by tasks
    distZip.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    distZip.onlyIf { archiveTypes.isNotEmpty() }

    val distTar: Tar by tasks
    distTar.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    distTar.onlyIf { archiveTypes.isNotEmpty() }

    // Projects to run shadow tasks.
    val projectsToShadow = listOf("aerospike-kafka-inbound")
    val shadowTasks = listOf(
        "shadowJar", "shadowDistTar", "shadowDistZip",
        "startShadowScripts"
    )
    shadowTasks.forEach { task ->
        tasks.getByName(task) {
            onlyIf {
                projectsToShadow.contains(project.name)
            }
        }
    }

    fun setUpPackaging(
        packagingTask: SystemPackagingTask,
        installFolder: String = "install",
        debFolder: String = "deb"
    ) {
        packagingTask.onlyIf { archiveTypes.isNotEmpty() }

        packagingTask.dependsOn("distZip")

        // Mark configuration file as configuration file.
        val targetConfigFile =
            "/etc/${packagingTask.project.name}/${packagingTask.project.name}.yml"
        packagingTask.configurationFile(targetConfigFile)

        val sourceConfigFile =
            File("${projectDir}/pkg/$installFolder/$targetConfigFile")

        if (packagingTask is Rpm && sourceConfigFile.isFile) {
            packagingTask.from(sourceConfigFile) {
                // For rpm this is the way to mark configuration files.
                com.netflix.gradle.plugins.packaging.CopySpecEnhancement
                    .setFileType(
                        this,
                        Directive(
                            Directive.RPMFILE_CONFIG or Directive
                                .RPMFILE_NOREPLACE
                        )
                    )
                into(
                    sourceConfigFile.parentFile.path.replace(
                        ".*pkg/$installFolder".toRegex(), ""
                    )
                )
            }
        }

        // Package config and other dependent files.
        val installDir = File(project.projectDir, "pkg/$installFolder")
        if (installDir.isDirectory) {
            for (file in installDir.listFiles()!!) {
                packagingTask.from(file) {
                    into(
                        file.path.replace(".*pkg/$installFolder".toRegex(), "")
                    )
                    packagingTask.addParentDirs = false
                    fileMode = 0x755
                    if (packagingTask is Rpm) {
                        // We have config it separately above.
                        exclude("**/" + sourceConfigFile.name)
                    }
                }
            }
        }

        // Copy installation scripts.
        val installScriptsDir = File(project.projectDir, "pkg/$debFolder")
        if (installScriptsDir.isDirectory) {
            packagingTask.postInstall(
                packagingTask.project.file(
                    "${projectDir}/pkg/$debFolder/postInstall.sh"
                )
            )
            packagingTask.preUninstall(
                packagingTask.project.file
                    ("${projectDir}/pkg/$debFolder/preUninstall.sh")
            )
            packagingTask.postUninstall(
                packagingTask.project.file(
                    "${projectDir}/pkg/$debFolder/postUninstall.sh"
                )
            )
        }

        // Copy the installer.
        packagingTask.from(
            project.zipTree(
                distZip.archiveFile.get().asFile.absolutePath
            )
        ) {
            into("/opt/${packagingTask.project.name}/")
            packagingTask.addParentDirs = false
            eachFile {
                path =
                    path.replaceFirst(
                        "/${packagingTask.project.name}-${packagingTask.project.version}/",
                        "/"
                    )
            }
        }
    }

    /**
     * Create the Debian package.
     */
    task("deb", AeroDeb::class) {
        // Currently we cannot specify a dependency on any package providing
        // java 8+ (for e.g java 11). Exact dependency on Java 8 is the only
        // thing that works. Skip adding a packaging dependency until this
        // works reliably.
        // requires("java8-runtime").or("java8-sdk")
        setUpPackaging(this)
    }

    /**
     * Create the RPM package.
     */
    task("rpm", AeroRpm::class) {
        os = Os.LINUX
        release = "1"
        user = "root"
        packageGroup = "root"

        // Currently we cannot specify a dependency on any package providing
        // java 8+ (for e.g java 11). Exact dependency on Java 8 is the only
        // thing that works. Skip adding a packaging dependency until this
        // works reliably.
        // requires("java", "1.8", Flags.GREATER or Flags.EQUAL).or("java",
        //    "11", Flags.GREATER or Flags.EQUAL)
        setUpPackaging(this)
    }

    task("deb-http", AeroDeb::class) {
        // Currently we cannot specify a dependency on any package providing
        // java 8+ (for e.g java 11). Exact dependency on Java 8 is the only
        // thing that works. Skip adding a packaging dependency until this
        // works reliably.
        // requires("java8-runtime").or("java8-sdk")

        onlyIf {
            isSet(project, "buildHttp")
        }

        // Name this package as http
        packageName += "-http"
        setUpPackaging(this, "install-http", "deb-http")
    }

    /**
     * Create the RPM package.
     */
    task("rpm-http", AeroRpm::class) {
        os = Os.LINUX
        release = "1"
        user = "root"
        packageGroup = "root"

        onlyIf { buildHttp }

        // Currently we cannot specify a dependency on any package providing
        // java 8+ (for e.g java 11). Exact dependency on Java 8 is the only
        // thing that works. Skip adding a packaging dependency until this
        // works reliably.
        // requires("java", "1.8", Flags.GREATER or Flags.EQUAL).or("java",
        //    "11", Flags.GREATER or Flags.EQUAL)

        // Name this package as http
        packageName += "-http"
        setUpPackaging(this, "install-http", "deb-http")
    }

    fun getArtifactList(
        projectSuffix: String
    ): List<Pair<RegularFile, String>> {
        val files: MutableList<Pair<RegularFile, String>> = mutableListOf()
        if (archiveTypes.isNotEmpty()) {
            if (archiveTypes.contains("tar")) {
                files += distTar.archiveFile.get() to "tar"
            }

            if (archiveTypes.contains("zip")) {
                files += distZip.archiveFile.get() to "zip"
            }

            if (archiveTypes.contains("deb")) {
                val debTasks =
                    tasks.filterIsInstance<Deb>().filter {
                        it.name.endsWith("deb${projectSuffix}")
                    }

                debTasks.forEach {
                    files += it.archiveFile.get() to "deb"
                }
            }

            if (archiveTypes.contains("rpm")) {
                val rpmTasks =
                    tasks.filterIsInstance<Rpm>().filter {
                        it.name.endsWith("rpm${projectSuffix}")
                    }

                rpmTasks.forEach {
                    files += it.archiveFile.get() to "rpm"
                }
            }
        }
        return files
    }

    /**
     * @return true iff the package should be published after the dep/rpm
     * task has executed.
     */
    fun shouldPublishDebRpmTask(
        project: Project,
        task: SystemPackagingTask
    ): Boolean {
        return when {
            project.name.startsWith("build") -> {
                false
            }
            task.name.contains("http") -> {
                buildHttp
            }
            else -> {
                true
            }
        }
    }

    fun getProjectFlavorSuffixes(): MutableSet<String> {
        // Pom project suffix. One for each type of deb or rpm.
        // For e.g. if deb-http or rpm-http is present, then we
        // create a maven publication with name "${project.name}-http"
        // for all '-http' artifacts.
        val pomSuffixes = mutableSetOf("")

        if (archiveTypes.isNotEmpty()) {
            val debTasks =
                tasks.filterIsInstance<Deb>().filter {
                    shouldPublishDebRpmTask(project, it)
                }

            val rpmTasks =
                tasks.filterIsInstance<Rpm>().filter {
                    shouldPublishDebRpmTask(project, it)
                }

            (rpmTasks + debTasks).forEach {
                val suffix = if (it.name.contains("-")) {
                    "-" + it
                        .name.split("-")
                        .last()
                } else {
                    ""
                }

                pomSuffixes += suffix
            }
        }
        return pomSuffixes
    }

    var githubReleaseConfiguration: GithubReleaseConfiguration =
        GithubReleaseConfiguration(project = project)
    /**
     * Create the list of all assets to be uploaded to github after builds but
     * before the project version is incremented.
     */
    task("prepareGithubRelease", Task::class) {
        dependsOn("publish")
        val checkSumDir = File(project.buildDir, "checksums")
        val shouldExecute = project.hasProperty("release.releaseVersion")
                && project.version.toString() == project.property("release.releaseVersion")

        val assets = getProjectFlavorSuffixes().map {
            getArtifactList(it)
        }.flatten().map { it.first.asFile }.toMutableList()

        // Add checksum files.
        assets += assets.filterNot { it.name.endsWith("md5") }.map {
            File(checkSumDir, "${it.name}.md5")
        }

        if (shouldExecute) {
            val releaseVersion =
                project.property("release.releaseVersion")
            val releaseName = "${
                project.name.split("-")
                    .joinToString(" ") { it.capitalize() }
            } $releaseVersion"

            val body = if (project.hasProperty("releaseNotesFile")) {

                File(
                    project.property("releaseNotesFile").toString()
                ).readText()

            } else {
                ""
            }
            githubReleaseConfiguration = GithubReleaseConfiguration(
                owner = "ashishshinde",
                repo = "jenkins-release",
                accessToken = System.getenv("GITHUB_TOKEN"),
                tagName = "${project.name}-$releaseVersion",
                targetCommitish = "master",
                releaseName = releaseName,
                body = body,
                releaseAssets = assets,
                apiEndpoint = "https://api.github.com",
                project = project
            )
        }

        doLast {
            // Generate md5sums when this task executes
            FileUtils.deleteDirectory(checkSumDir)
            checkSumDir.mkdirs()
            assets.filterNot { it.name.endsWith("md5") }.filter { it.isFile }
                .forEach {
                    val checkSumFile = File(checkSumDir, "${it.name}.md5")
                    val hash = com.google.common.io.Files.asByteSource(it)
                        .hash(com.google.common.hash.Hashing.md5())
                    com.google.common.io.Files.write(
                        hash.toString().toByteArray(),
                        checkSumFile
                    )

                }
        }
    }

    /**
     * Publish Github release.
     */
    task("publishGithubRelease", Task::class) {
        // Ensure all assets are ready before publish.
        dependsOn("publish")

        doLast {
            com.aerospike.connect.gradle.GithubRelease.publishRelease(
                githubReleaseConfiguration
            )
        }
    }

    // Trigger http builds on all deb, rpm projects. Will be triggered only
    // for the appropriate projects by deb-http and rpm-http tasks.
    tasks.getByName("deb").dependsOn("deb-http")
    tasks.getByName("rpm").dependsOn("rpm-http")

    val publishing =
        (project.extensions["publishing"] as PublishingExtension)

    publishing.publications {
        /**
         * Create maven publication for a project with suffix.
         * @param projectSuffix the suffix for the project.
         */
        fun createPublication(
            publicationContainer:
            PublicationContainer,
            projectSuffix: String
        ) {
            val isMainPom = projectSuffix.isEmpty()
            val publicationName =
                if (isMainPom) "mainArtifacts" else "${
                    projectSuffix.removePrefix("-")
                }Artifacts"

            publicationContainer.create<MavenPublication>(publicationName) {
                if (isMainPom) {
                    from(components["java"])

                    artifact(tasks.named<Jar>("testJar").get()) {
                        classifier = "test"
                        extension = "jar"
                    }
                }

                // Look for a map from task name to classifier.
                @Suppress("UNCHECKED_CAST") val zipTasks =
                    if (project.extra.has("zipArtifactTasks"))
                        project.extra["zipArtifactTasks"] as Map<String, String>
                    else emptyMap()


                zipTasks.forEach {
                    artifact(tasks.named<Zip>(it.key).get()) {
                        classifier = it.value
                        extension = "zip"
                    }
                }

                getArtifactList(projectSuffix).forEach { (file, type) ->
                    artifact(
                        com.aerospike.connect.gradle.PackagingPublishArtifact(
                            file,
                            project.version.toString(),
                            type
                        )
                    )
                }

                pom {
                    artifactId = project.name + projectSuffix
                    name.set(project.name + projectSuffix)
                    if (isMainPom) {
                        withXml {
                            val dependencies =
                                (asNode().get(
                                    "dependencies"
                                ) as NodeList)[0]
                                        as Node
                            configurations["testCompileClasspath"]
                                .resolvedConfiguration
                                .firstLevelModuleDependencies.forEach {
                                    val dependency =
                                        dependencies.appendNode(
                                            "dependency"
                                        )
                                    dependency.appendNode(
                                        "groupId",
                                        it.moduleGroup
                                    )
                                    dependency.appendNode(
                                        "artifactId",
                                        it.moduleName
                                    )
                                    dependency.appendNode(
                                        "version",
                                        it.moduleVersion
                                    )
                                    dependency.appendNode("scope", "test")
                                }
                        }
                    }
                }
            }
        }

        val container = this
        project.afterEvaluate {
            val pomSuffixes = getProjectFlavorSuffixes()

            pomSuffixes.forEach {
                createPublication(container, it)
            }
        }

        publishing.repositories {
            val repositoryUrl =
                uri("https://maven.pkg.github.com/ashishshinde/jenkins-release")

            maven {
                name = "AerospikeMavenRepo"
                url = repositoryUrl
                credentials {
                    username = pkgRepoUser
                    password = pkgRepoPassword
                }
            }
        }

        tasks.withType<PublishToMavenRepository>().configureEach {
            onlyIf {
                // Upload is snap shot version.
                // If a proper release version upload only when release task is
                // present. This prevents re-releasing re builds of released
                // version. This is just sanity because our repository fails
                // re-upload of a released artifact.
                isSnapshotVersion(project.version) || hasReleaseTask()
            }

            if (archiveTypes.isNotEmpty()) {
                dependsOn(
                    "testJar", "distZip", "distTar",
                    "deb", "rpm", "deb-http", "rpm-http"
                )
            }
        }

        // Remove the packaging artifacts from library modules.
        configurations.getByName("archives").artifacts.apply {
            if (archiveTypes.isEmpty()) {
                val toRemove = setOf("deb", "rpm")
                removeAll(filter { toRemove.contains(it.type) })
            }
        }

        // Bring latest snapshots.
        configurations.all {
            resolutionStrategy {
                cacheChangingModulesFor(0, TimeUnit.SECONDS)
            }
        }
    }

    /**
     * Check if we should build a docker image for the connector.
     */
    fun buildDockerImage() =
        archiveTypes.contains("deb") && archiveTypes.contains("docker")

    /**
     * Build latest and http docker images and their Dockerfile using the deb
     * file.
     */
    getProjectFlavorSuffixes().forEach { flavourSuffix ->

        val debTaskName = "deb$flavourSuffix"
        val debTasks =
            tasks.filterIsInstance<Deb>().filter { deb ->
                shouldPublishDebRpmTask(
                    project,
                    deb
                ) && deb.name == debTaskName
            }
        if (buildDockerImage()) {
            tasks.findByName("publishDocker") ?: run {
                tasks.create("publishDocker")
                tasks.getByName("publish").dependsOn("publishDocker")
            }

            val debOutput = debTasks.first().outputs

            tasks.create("createDockerDir$flavourSuffix", Copy::class) {

                onlyIf {
                    buildDockerImage()
                }

                dependsOn(debTaskName)

                into("build/docker$flavourSuffix")

                from("pkg/install")
                from(File(project.rootDir, "Dockerfile"))
                from(debOutput)
            }

            val dockerHubRepository: String by project
            val publicImageName =
                "$dockerHubRepository/${project.name}$flavourSuffix"
            val privateImageName = "$publicImageName-private"
            val publicImageTag = project.version.toString()
            val privateTestImageTag = "$publicImageTag-$uuid"

            tasks.create(
                "buildDockerImage$flavourSuffix",
                DockerBuildImage::class
            ) {
                onlyIf {
                    buildDockerImage()
                }
                dependsOn("createDockerDir$flavourSuffix")

                // Using inputs and outputs to build image only when deb file
                // has changed.
                inputs.files(debOutput.files.first())

                val outputFile =
                    "${project.buildDir}/image$flavourSuffix.created"
                outputs.files(outputFile)

                inputDir = File("${project.buildDir}/docker$flavourSuffix")
                noCache = true
                pull = true

                tags = setOf("$publicImageName:$publicImageTag")

                buildArgs = mapOf(
                    "PROJECT_NAME" to project.name,
                    "ARCHIVE_FILE_NAME" to debOutput.files.first().name,
                    "FLAVOUR_FLAG" to if (flavourSuffix == "") "" else "--http"
                )

                doLast {
                    File(outputFile).writeText("hello_Stuti_and_Prayag")
                }
            }

            val dockerHubUsername: String by project
            val dockerHubPassword: String by project

            fun createPushImageTask(
                taskName: String, dependsOn: List<String>,
                imageName:
                String, imageTag: String,
                onlyIf: Spec<Task>
            ) {
                tasks.create(taskName, DockerPushImage::class) {
                    onlyIf(onlyIf)
                    dependsOn(dependsOn, "buildDockerImage$flavourSuffix")

                    this.imageName = imageName
                    tag = imageTag
                    registryCredentials =
                        DockerRegistryCredentials().apply {
                            username = dockerHubUsername
                            password = dockerHubPassword
                        }
                }
            }

            fun dockerImagePushTask(isPrivate: Boolean) {
                if (isPrivate) {
                    tasks.create(
                        "tagImageDockerHubTestPrivate$flavourSuffix",
                        DockerTagImage::class
                    ) {

                        onlyIf { buildDockerImage() }
                        dependsOn("buildDockerImage$flavourSuffix")
                        imageId = "$publicImageName:$publicImageTag"
                        tag = privateTestImageTag
                        repository = privateImageName
                    }
                    tasks.create(
                        "tagImageDockerHubPrivate$flavourSuffix",
                        DockerTagImage::class
                    ) {

                        onlyIf { buildDockerImage() }
                        dependsOn("buildDockerImage$flavourSuffix")
                        imageId = "$publicImageName:$publicImageTag"
                        tag = publicImageTag
                        repository = privateImageName
                    }
                    createPushImageTask(
                        "pushImageDockerHubPrivate$flavourSuffix",
                        listOf("tagImageDockerHubPrivate$flavourSuffix"),
                        privateImageName, publicImageTag
                    ) {
                        buildDockerImage() && (isPrivate || !isSnapshotVersion(
                            project.version
                        ))
                    }
                    createPushImageTask(
                        "pushImageDockerHubTestPrivate$flavourSuffix",
                        listOf("tagImageDockerHubTestPrivate$flavourSuffix"),
                        privateImageName,
                        privateTestImageTag
                    ) { buildDockerImage() }
                } else {
                    createPushImageTask(
                        "pushImageDockerHub$flavourSuffix",
                        emptyList(), publicImageName, publicImageTag
                    ) {
                        buildDockerImage() && (!isSnapshotVersion(
                            project.version
                        ))
                    }
                }
            }

            dockerImagePushTask(false)
            dockerImagePushTask(true)

            tasks.create("prepareCiryaDockerFile$flavourSuffix") {
                doLast {
                    createDockerFileForConnector(
                        privateImageName,
                        flavourSuffix, uuid
                    )
                }
            }

            tasks.getByName("test") {
                dependsOn(
                    "prepareCiryaDockerFile$flavourSuffix",
                    "pushImageDockerHubTestPrivate$flavourSuffix"
                )
            }

            tasks.getByName("publishDocker") {
                dependsOn(
                    "pushImageDockerHub$flavourSuffix",
                    "pushImageDockerHubPrivate$flavourSuffix"
                )
            }

            tasks.create("deletePrivateDockerImage$flavourSuffix") {
                onlyIf {
                    buildDockerImage()
                }
                doLast {
                    DockerHub.deleteTags(
                        dockerHubUsername, dockerHubPassword,
                        privateImageName, listOf(privateTestImageTag)
                    )
                }
            }

            tasks.create("deleteAllPrivateDockerImageTags$flavourSuffix") {
                onlyIf {
                    buildDockerImage()
                }
                doLast {
                    DockerHub.deleteAllTags(
                        dockerHubUsername,
                        dockerHubPassword, privateImageName
                    )
                }
            }

            /**
             * Images should be cleaned up even if tests fail.
             */
            tasks.getByName("test") {
                finalizedBy("deletePrivateDockerImage$flavourSuffix")
            }
        }
    }
}

/**
 * Check if current project version is a snapshot version.
 */
fun isSnapshotVersion(version: Any): Boolean {
    return version.toString().endsWith("-SNAPSHOT")
}

/**
 * Check if we are running a release task.
 */
fun hasReleaseTask(): Boolean {
    val releaseTaskName = "afterReleaseBuild"
    var hasRelease = false
    gradle.taskGraph.allTasks.forEach {
        if (it.name == releaseTaskName) {
            hasRelease = true
        }
    }

    return hasRelease
}

/**
 * Check if boolean property is set or not.
 */
fun isSet(project: Project, property: String) =
    (project.findProperty(property) as? String).equals("true", true)

/**
 * Create a docker file for the connector.
 */
fun Project.createDockerFileForConnector(
    privateImageName: String,
    flavourSuffix: String,
    uuid: UUID
) {
    val templateDir = "${project.rootDir}/test/src/test/data/docker/"
    val templateFile = Paths.get(
        "${templateDir}connect-debian-10-connector.dockerfile.ftl"
    )
    val charset = Charsets.UTF_8
    var template = String(Files.readAllBytes(templateFile), charset)
    template =
        template.replace("connector-image-name", privateImageName)
    template =
        template.replace(
            "connector-image-version",
            project.version.toString().plus("-$uuid")
        )
    Files.write(
        Paths.get(
            templateDir + "connect-debian-10-${project.name}$flavourSuffix-connector.dockerfile"
        ),
        template.toByteArray(charset)
    )
}

