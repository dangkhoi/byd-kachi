package com.byd.clusternav

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuildArtifactNamingTest {
    private val script: String by lazy { projectPath("build.gradle.kts").toFile().readText() }

    @Test
    fun `assemble tasks have no unconditional APK collection finalizer`() {
        assertFalse(script.contains("finalizedBy"))
        assertFalse(script.contains("tasks.register<Copy>(\"collectApks\")"))
        assertTrue(script.contains("tasks.register<CollectAuthorizedApk>(\"collectAuthorizedApk\")"))
    }

    @Test
    fun `collector requires slice exact source manifest and requested release variant`() {
        assertTrue(script.contains("providers.gradleProperty(\"clusterNavSlice\")"))
        assertTrue(script.contains("providers.gradleProperty(\"exactSourceId\")"))
        assertTrue(script.contains("providers.gradleProperty(\"exactSourceManifest\")"))
        assertTrue(script.contains("providers.gradleProperty(\"clusterNavVariant\")"))
        assertTrue(script.contains("variant != \"release\""))
        assertTrue(script.contains("[0-9a-f]{64}"))
    }

    @Test
    fun `no superseded exact-source manifest path is baked into the build`() {
        // Regression: the collector used to hardcode the two-track-final manifest, so the only
        // acceptable id was a superseded candidate identity and no later manifest could build.
        assertFalse(script.contains("two-track-final-exact-source.json"))
        assertFalse(Regex("""file\("docs/_handoff/[^"]*\.json"\)""").containsMatchIn(script))
        assertTrue(script.contains("Missing -PexactSourceManifest=docs/_handoff/<manifest>.json"))
        assertTrue(script.contains("docs/_handoff/[A-Za-z0-9._-]{1,120}\\\\.json"))
    }

    @Test
    fun `identity is canonical json without the self-referential sourceId member`() {
        assertTrue(script.contains("filterKeys { it != \"sourceId\" }"))
        assertTrue(script.contains("sortedBy { it.first }"))
        assertTrue(script.contains("joinToString(\",\", \"{\", \"}\")"))
        assertTrue(script.contains("joinToString(\",\", \"[\", \"]\")"))
        // Self-consistency: a manifest may not declare an identity it does not hash to.
        assertTrue(script.contains("Exact-source manifest is not self-consistent"))
        // The legacy whole-file byte hash must be named as a wrong answer, never accepted.
        assertTrue(script.contains("that value is the manifest whole-file byte hash"))
    }

    @Test
    fun `authorized collection cannot run without passing the identity gate`() {
        assertTrue(script.contains("tasks.register<VerifyExactSourceIdentity>(\"verifyExactSourceIdentity\")"))
        assertTrue(script.contains("dependsOn(\"verifyExactSourceIdentity\")"))
        assertTrue(script.contains("ExactSourceIdentity.verify("))
        // The gate must be ordered before the assemble it guards, not merely present.
        assertTrue(script.contains("mustRunAfter(\"verifyExactSourceIdentity\")"))
    }

    @Test
    fun `the gate proves the working tree matches the attested inputs`() {
        // Identity self-consistency alone would still allow building from a tree the attestation
        // does not describe, producing a candidate with a misleading identity.
        assertTrue(script.contains("fun verifyTree("))
        assertTrue(script.contains("ExactSourceIdentity.verifyTree("))
        assertTrue(script.contains("Working tree does not match"))
        assertTrue(script.contains("intendedUntracked"))
        // Exclusions cover local tooling that may legitimately change and must not gate the build.
        val verifyTree = script.substringAfter("fun verifyTree(").substringBefore("private fun verifyTrackedState(")
        assertFalse(verifyTree.contains("identityExclusions"))
    }

    @Test
    fun `the gate also covers tracked build inputs`() {
        // Product sources here are untracked, but this script, gradle.properties and the wrapper are
        // tracked and live in the recorded diff; without this the gate itself could be edited freely.
        assertTrue(script.contains("private fun verifyTrackedState("))
        assertTrue(script.contains("declares no trackedDiff"))
        assertTrue(script.contains("tracked diff drifted"))
        assertTrue(script.contains("HEAD does not match"))
        assertTrue(script.contains("GIT_OPTIONAL_LOCKS"))
        // Drain before waiting or a 300 KB diff deadlocks the pipe.
        assertTrue(script.contains("process.inputStream.use"))
        assertTrue(script.contains("waitFor(120, TimeUnit.SECONDS)"))
        assertTrue(script.contains("destroyForcibly"))
    }

    @Test
    fun `canonical json refuses keys whose ordering could diverge`() {
        assertTrue(script.contains("Canonical JSON requires ASCII object keys"))
        assertTrue(script.contains("it.code > 0x7F"))
    }

    @Test
    fun `collector binds requested source id to the named manifest identity`() {
        val collector = script.substringAfter("abstract class CollectAuthorizedApk")
        assertTrue(collector.contains("ExactSourceIdentity.verify("))
        assertFalse(collector.contains("manifestHash"))
    }

    @Test
    fun `collector names uniquely and fails closed on stale collision or ambiguous source`() {
        assertTrue(script.contains("ClusterNav-${'$'}{artifactVersion.get()}-${'$'}safeSlice-${'$'}{sourceId.take(12)}-release.apk"))
        assertTrue(script.contains("destination.exists()"))
        assertTrue(script.contains("Refusing to overwrite existing artifact"))
        assertTrue(script.contains("source.lastModified() < invocationStartedAtMillis.get()"))
        assertTrue(script.contains("candidates.size != 1"))
        assertTrue(script.contains("AtomicMoveNotSupportedException"))
        assertTrue(script.contains("StandardCopyOption.ATOMIC_MOVE"))
        assertTrue(script.contains("Files.deleteIfExists(temporary)"))
        assertTrue(script.contains("UUID.randomUUID()"))
        assertFalse(script.contains("StandardCopyOption.REPLACE_EXISTING"))
        assertFalse(script.contains("Files.copy(source.toPath(), destination.toPath())"))
    }

    @Test
    fun `collector selects release output only and is not wired to debug`() {
        assertTrue(script.contains("outputs/apk/release"))
        assertTrue(script.contains("dependsOn(\"assembleRelease\")"))
        val collector = script.substringAfter("abstract class CollectAuthorizedApk")
        assertFalse(collector.contains("outputs/apk/debug"))
        assertFalse(collector.contains("assembleDebug"))
    }

    @Test
    fun `authorized exact-source build uses isolated build directory`() {
        assertTrue(script.contains("authorizedBuildSourceId"))
        assertTrue(script.contains("layout.buildDirectory.set"))
        assertTrue(script.contains(".authorized-build/"))
        assertTrue(script.contains("value.take(12)"))
    }

    private fun projectPath(relative: String): Path {
        val current = Path.of(System.getProperty("user.dir"))
        return if (Files.exists(current.resolve(relative))) current.resolve(relative) else current.resolve("app").resolve(relative)
    }
}
