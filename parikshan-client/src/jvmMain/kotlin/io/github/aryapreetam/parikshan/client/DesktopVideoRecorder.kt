package io.github.aryapreetam.parikshan.client

import io.github.aryapreetam.parikshan.TestDriver
import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.Response
import java.io.File

internal class DesktopVideoRecorder(
  @Volatile private var driver: TestDriver,
  private val config: ParikshanVideoConfig
) : VideoRecorder {
  @Volatile private var isRecording = false
  @Volatile private var currentOutputPath: String? = null
  @Volatile private var currentOutputDir: String? = null
  @Volatile private var activeSessionName: String? = null
  @Volatile private var relaunchIndex = 0
  private val segments = mutableListOf<String>()

  fun getSegments(): List<String> = synchronized(segments) {
    segments.toList()
  }

  fun updateDriver(newDriver: TestDriver) {
    this.driver = newDriver
    if (isRecording) {
      val session = activeSessionName
      val outputDir = currentOutputDir
      if (session != null && outputDir != null) {
        relaunchIndex++
        val segmentName = "${session}_relaunch$relaunchIndex"
        val segmentPath = File(outputDir, "$segmentName.mp4").absolutePath
        println("[PARIKSHAN_VIDEO_PATH] $segmentPath")
        synchronized(segments) {
          segments.add(segmentPath)
        }

        kotlinx.coroutines.runBlocking {
          runCatching {
            driver.send(
              Command.StartRecording(
                id = "relaunch-start-rec",
                sessionName = segmentName,
                path = segmentPath,
                fps = config.fps,
                showCursor = config.showCursor
              )
            )
          }
        }
      }
    }
  }

  fun pause() {
    // No-op for server-side recording, driver coordinates itself
  }

  fun resume() {
    // No-op for server-side recording, driver coordinates itself
  }

  override suspend fun start(sessionName: String, outputDirectory: String) {
    val outputPath = File(outputDirectory, "$sessionName.mp4").absolutePath
    currentOutputPath = outputPath
    currentOutputDir = outputDirectory
    activeSessionName = sessionName
    isRecording = true
    relaunchIndex = 0
    synchronized(segments) {
      segments.clear()
      segments.add(outputPath)
    }
    
    driver.send(
      Command.StartRecording(
        id = "start-rec",
        sessionName = sessionName,
        path = outputPath,
        fps = config.fps,
        showCursor = config.showCursor
      )
    )
  }

  override suspend fun stop(): String? {
    if (!isRecording) return null
    isRecording = false
    
    if (config.postRollMs > 0) {
      kotlinx.coroutines.delay(config.postRollMs)
    }

    val path = currentOutputPath
    val outputDir = currentOutputDir
    val session = activeSessionName
    
    currentOutputPath = null
    currentOutputDir = null
    activeSessionName = null
    
    runCatching {
      driver.send(
        Command.StopRecording(
          id = "stop-rec",
          sessionName = "session"
        )
      )
    }

    // Now merge any temporary segments!
    val segmentPaths = getSegments()
    if (segmentPaths.size > 1 && path != null && outputDir != null) {
      try {
        // Wait 2 seconds for the server process to fully write and close the last segment MP4 file before merging
        kotlinx.coroutines.delay(2000)
        
        val tempMergedFile = File(outputDir, "${session}_merged.mp4")
        mergeMp4Files(segmentPaths.map { File(it) }, tempMergedFile)
        
        // Delete all segment files
        for (segPath in segmentPaths) {
          File(segPath).delete()
        }
        
        // Rename merged file to the final output path
        tempMergedFile.renameTo(File(path))
      } catch (e: Exception) {
        System.err.println("WARN: Failed to merge video segments: ${e.message}")
        e.printStackTrace()
      }
    }
    
    return path
  }

  private fun mergeMp4Files(files: List<File>, outputFile: File) {
    val movies = files.mapNotNull { file ->
      if (!file.exists() || file.length() < 100) return@mapNotNull null
      
      var movie: org.mp4parser.muxer.Movie? = null
      var delayMs = 500L
      for (attempt in 1..4) {
        try {
          movie = org.mp4parser.muxer.container.mp4.MovieCreator.build(file.absolutePath)
          if (movie != null) break
        } catch (e: Exception) {
          if (attempt == 4) {
            System.err.println("ERROR: Failed to build movie for ${file.name} after 4 attempts: ${e.message}")
            e.printStackTrace()
          } else {
            System.err.println("WARN: Temporary failure building movie for ${file.name} (attempt $attempt), retrying in ${delayMs}ms...")
            Thread.sleep(delayMs)
            delayMs *= 2
          }
        }
      }
      movie
    }
    val videoTracks = mutableListOf<org.mp4parser.muxer.Track>()
    val audioTracks = mutableListOf<org.mp4parser.muxer.Track>()

    for (movie in movies) {
      for (track in movie.tracks) {
        if (track.handler == "vide") {
          videoTracks.add(track)
        } else if (track.handler == "soun") {
          audioTracks.add(track)
        }
      }
    }

    val finalMovie = org.mp4parser.muxer.Movie()
    if (videoTracks.isNotEmpty()) {
      finalMovie.addTrack(org.mp4parser.muxer.tracks.AppendTrack(*videoTracks.toTypedArray()))
    }
    if (audioTracks.isNotEmpty()) {
      finalMovie.addTrack(org.mp4parser.muxer.tracks.AppendTrack(*audioTracks.toTypedArray()))
    }

    val container = org.mp4parser.muxer.builder.DefaultMp4Builder().build(finalMovie)
    java.io.FileOutputStream(outputFile).use { fos ->
      val fc = fos.channel
      container.writeContainer(fc)
    }
  }
}
