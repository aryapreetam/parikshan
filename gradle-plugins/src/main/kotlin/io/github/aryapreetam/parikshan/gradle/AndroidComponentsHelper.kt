package io.github.aryapreetam.parikshan.gradle

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider

internal object AndroidComponentsHelper {
  fun getMergedManifestDirectory(project: Project): Provider<Directory> {
    val mergedManifestDirectory: DirectoryProperty = project.objects.directoryProperty()

    // 1. Try modern AndroidComponentsExtension
    val components = project.extensions.findByName("androidComponents")
    if (components != null) {
      try {
        val onVariantsMethod = components.javaClass.methods.firstOrNull {
          it.name == "onVariants" && it.parameterCount == 1 && it.parameterTypes[0] == Action::class.java
        } ?: components.javaClass.methods.firstOrNull {
          it.name == "onVariants" && it.parameterCount == 2 && it.parameterTypes[1] == Action::class.java
        }

        if (onVariantsMethod != null) {
          val action = object : Action<Any> {
            override fun execute(variant: Any) {
              try {
                val getNameMethod = variant.javaClass.methods.firstOrNull { it.name == "getName" && it.parameterCount == 0 }
                val name = getNameMethod?.invoke(variant) as? String
                if (name == "debug") {
                  val getArtifactsMethod = variant.javaClass.methods.firstOrNull { it.name == "getArtifacts" && it.parameterCount == 0 }
                  val artifacts = getArtifactsMethod?.invoke(variant) ?: return

                  val mergedManifestObj = try {
                    val clazz = Class.forName("com.android.build.api.artifact.SingleArtifact\$MERGED_MANIFEST")
                    clazz.getField("INSTANCE").get(null)
                  } catch (e: Exception) {
                    try {
                      val clazz = Class.forName("com.android.build.api.artifact.SingleArtifact")
                      clazz.getField("MERGED_MANIFEST").get(null)
                    } catch (e2: Exception) {
                      null
                    }
                  }

                  if (mergedManifestObj != null) {
                    val getMethod = artifacts.javaClass.methods.firstOrNull {
                      it.name == "get" && it.parameterCount == 1
                    }
                    @Suppress("UNCHECKED_CAST")
                    val provider = getMethod?.invoke(artifacts, mergedManifestObj) as? Provider<Directory>
                    if (provider != null) {
                      mergedManifestDirectory.set(provider)
                    }
                  }
                }
              } catch (e: Exception) {
                project.logger.debug("Parikshan AndroidComponentsHelper: Error resolving merged manifest inside onVariants", e)
              }
            }
          }

          if (onVariantsMethod.parameterCount == 1) {
            onVariantsMethod.invoke(components, action)
            return mergedManifestDirectory
          } else if (onVariantsMethod.parameterCount == 2) {
            val selectorMethod = components.javaClass.getMethod("selector")
            val selector = selectorMethod.invoke(components)
            val allMethod = selector.javaClass.getMethod("all")
            val allSelector = allMethod.invoke(selector)
            onVariantsMethod.invoke(components, allSelector, action)
            return mergedManifestDirectory
          }
        }
      } catch (e: Exception) {
        project.logger.debug("Parikshan AndroidComponentsHelper: Error registering onVariants listener", e)
      }
    }

    // 2. Try legacy applicationVariants fallback
    val android = project.extensions.findByName("android")
    if (android != null) {
      try {
        val getApplicationVariants = android.javaClass.methods.firstOrNull { it.name == "getApplicationVariants" }
        if (getApplicationVariants != null) {
          val variants = getApplicationVariants.invoke(android) as? Iterable<*>
          if (variants != null) {
            for (variant in variants) {
              if (variant == null) continue
              val name = variant.javaClass.getMethod("getName").invoke(variant) as? String
              if (name == "debug") {
                val getOutputs = variant.javaClass.getMethod("getOutputs")
                val outputs = getOutputs.invoke(variant) as? List<*>
                val output = outputs?.firstOrNull()
                if (output != null) {
                  val getProcessManifestProvider = output.javaClass.getMethod("getProcessManifestProvider")
                  val processManifestProvider = getProcessManifestProvider.invoke(output)
                  
                  if (processManifestProvider is Provider<*>) {
                    val mappedProvider = processManifestProvider.flatMap { task ->
                      if (task == null) throw GradleException("Task is null")
                      val getManifestOutputDirectory = task.javaClass.getMethod("getManifestOutputDirectory")
                      @Suppress("UNCHECKED_CAST")
                      getManifestOutputDirectory.invoke(task) as Provider<Directory>
                    }
                    mergedManifestDirectory.set(mappedProvider)
                    return mergedManifestDirectory
                  }
                }
              }
            }
          }
        }
      } catch (e: Exception) {
        project.logger.debug("Parikshan AndroidComponentsHelper: Legacy applicationVariants fallback failed", e)
      }
    }

    return mergedManifestDirectory
  }
}
