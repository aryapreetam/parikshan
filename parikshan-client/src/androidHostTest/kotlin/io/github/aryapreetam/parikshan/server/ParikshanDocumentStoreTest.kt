package io.github.aryapreetam.parikshan.server

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParikshanDocumentStoreTest {

  private lateinit var tempRoot: File

  @BeforeTest
  fun setUp() {
    tempRoot = Files.createTempDirectory("parikshan-doc-store-test").toFile()
  }

  @AfterTest
  fun tearDown() {
    tempRoot.deleteRecursively()
  }

  @Test
  fun createDocumentCreatesRegularFileAndUniqueSiblingOnCollision() {
    val destDir = File(tempRoot, "dest").apply { mkdirs() }

    val first = ParikshanDocumentStore.createDocument(destDir, "ready.txt", isDirectory = false)
    assertNotNull(first)
    assertEquals("ready.txt", first.name)
    assertTrue(first.isFile)

    val second = ParikshanDocumentStore.createDocument(destDir, "ready.txt", isDirectory = false)
    assertNotNull(second)
    assertEquals("ready (1).txt", second.name)
    assertTrue(second.isFile)
  }

  @Test
  fun createDocumentCreatesSubdirectoryAndRejectsPathTraversal() {
    val destDir = File(tempRoot, "dest").apply { mkdirs() }

    val subdir = ParikshanDocumentStore.createDocument(destDir, "nested_out", isDirectory = true)
    assertNotNull(subdir)
    assertTrue(subdir.isDirectory)

    assertNull(ParikshanDocumentStore.createDocument(destDir, "../escape.txt", isDirectory = false))
    assertNull(ParikshanDocumentStore.createDocument(destDir, "a/b.txt", isDirectory = false))
    assertNull(ParikshanDocumentStore.createDocument(destDir, "..", isDirectory = true))
    assertNull(ParikshanDocumentStore.createDocument(destDir, "", isDirectory = false))
  }

  @Test
  fun renameDeleteAndIsChildDocumentWorkCorrectly() {
    val destDir = File(tempRoot, "dest").apply { mkdirs() }
    val created = ParikshanDocumentStore.createDocument(destDir, "draft.txt", isDirectory = false)!!
    created.writeText("hello")

    assertTrue(ParikshanDocumentStore.isChildDocument(destDir, created))
    assertFalse(ParikshanDocumentStore.isChildDocument(destDir, File(tempRoot, "outside.txt")))

    val renamed = ParikshanDocumentStore.renameDocument(created, "final.txt")
    assertNotNull(renamed)
    assertEquals("final.txt", renamed.name)
    assertEquals("hello", renamed.readText())
    assertFalse(created.exists())

    assertNull(ParikshanDocumentStore.renameDocument(renamed, "../escape.txt"))

    assertTrue(ParikshanDocumentStore.deleteDocument(renamed))
    assertFalse(renamed.exists())
    assertFalse(ParikshanDocumentStore.deleteDocument(renamed))
  }

  @Test
  fun mirrorToSandboxCleansStaleFilesAndPreservesEmptyDirs() {
    val stagingDir = File(tempRoot, "staging/picker-write-source").apply { mkdirs() }
    File(stagingDir, "ready.txt").writeText("Android matrix readiness\n")
    File(stagingDir, "empty_sub").mkdirs()

    val sandboxRoot = File(tempRoot, "sandbox")
    // Pre-populate stale file in destination sandbox
    val staleDest = File(sandboxRoot, "picker-write-destination").apply { mkdirs() }
    File(staleDest, "stale.txt").writeText("old")

    val mirroredSrc = ParikshanDocumentStore.mirrorToSandbox(
      sandboxRoot = sandboxRoot,
      fixtureName = "picker-write-source",
      source = stagingDir,
      isDirectory = true
    )
    assertNotNull(mirroredSrc)
    assertEquals("Android matrix readiness\n", File(mirroredSrc, "ready.txt").readText())
    assertTrue(File(mirroredSrc, "empty_sub").isDirectory)

    val emptyStagingDest = File(tempRoot, "staging/picker-write-destination").apply { mkdirs() }
    val mirroredDest = ParikshanDocumentStore.mirrorToSandbox(
      sandboxRoot = sandboxRoot,
      fixtureName = "picker-write-destination",
      source = emptyStagingDest,
      isDirectory = true
    )
    assertNotNull(mirroredDest)
    assertTrue(mirroredDest.isDirectory)
    assertEquals(emptyList(), mirroredDest.listFiles()?.toList().orEmpty())
  }

  @Test
  fun resolveRelativeInsideAndToDocumentIdNormalizePaths() {
    val sandboxRoot = File(tempRoot, "sandbox").apply { mkdirs() }
    val nestedFile = File(sandboxRoot, "dest/nested/ready.txt").apply {
      parentFile.mkdirs()
      writeText("ok")
    }

    val docId = ParikshanDocumentStore.toDocumentId(nestedFile, listOf(sandboxRoot))
    assertEquals("dest/nested/ready.txt", docId)

    val resolved = ParikshanDocumentStore.resolveRelativeInside(sandboxRoot, docId)
    assertEquals(nestedFile.canonicalFile, resolved)

    assertNull(ParikshanDocumentStore.resolveRelativeInside(sandboxRoot, "../outside"))
    assertNull(ParikshanDocumentStore.resolveRelativeInside(sandboxRoot, "dest/../../outside"))
  }
}
