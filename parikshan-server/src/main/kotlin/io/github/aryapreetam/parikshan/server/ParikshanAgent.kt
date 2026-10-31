package io.github.aryapreetam.parikshan.server

import java.awt.Dialog
import java.awt.FileDialog
import java.io.File
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

object ParikshanAgent {
  const val SYSTEM_PROPERTY_ACTIVE = "parikshan.picker.active"
  const val SYSTEM_PROPERTY_CANCEL = "parikshan.picker.cancel"
  const val SYSTEM_PROPERTY_PATHS = "parikshan.picker.paths"
  const val SYSTEM_PROPERTY_HANDLED = "parikshan.picker.handled"

  @JvmStatic
  fun premain(args: String?, inst: Instrumentation) {
    inst.addTransformer(object : ClassFileTransformer {
      override fun transform(
        loader: ClassLoader?,
        className: String?,
        classBeingRedefined: Class<*>?,
        protectionDomain: ProtectionDomain?,
        classfileBuffer: ByteArray
      ): ByteArray? {
        if (className == "java/awt/Dialog") {
          return transformDialog(classfileBuffer)
        }
        if (className == "io/github/vinceglb/filekit/dialogs/platform/PlatformFilePicker\$Companion") {
          return transformFileKitCompanion(classfileBuffer)
        }
        return null
      }
    }, true)

    runCatching {
      inst.retransformClasses(Dialog::class.java)
    }
  }

  private fun transformDialog(classfileBuffer: ByteArray): ByteArray? {
    return try {
      val cr = ClassReader(classfileBuffer)
      val cw = ClassWriter(cr, ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
      val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
            override fun visitMethod(
              access: Int,
              name: String?,
              descriptor: String?,
              signature: String?,
              exceptions: Array<out String>?
            ): MethodVisitor {
              val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
              if (name == "show" && descriptor == "()V") {
                return object : MethodVisitor(Opcodes.ASM9, mv) {
                  override fun visitCode() {
                    super.visitCode()
                    val normal = Label()

                    // 1. Check if this instanceof java.awt.FileDialog
                    mv.visitVarInsn(Opcodes.ALOAD, 0)
                    mv.visitTypeInsn(Opcodes.INSTANCEOF, "java/awt/FileDialog")
                    mv.visitJumpInsn(Opcodes.IFEQ, normal)

                    // 2. Check if System.getProperty(SYSTEM_PROPERTY_ACTIVE) != null
                    mv.visitLdcInsn(SYSTEM_PROPERTY_ACTIVE)
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;", false)
                    mv.visitJumpInsn(Opcodes.IFNULL, normal)

                    // 3. Call Dialog.parikshan_handle_picker((FileDialog) this)
                    mv.visitVarInsn(Opcodes.ALOAD, 0)
                    mv.visitTypeInsn(Opcodes.CHECKCAST, "java/awt/FileDialog")
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/awt/Dialog", "parikshan_handle_picker", "(Ljava/awt/FileDialog;)V", false)
                    mv.visitInsn(Opcodes.RETURN)

                    mv.visitLabel(normal)
                  }
                }
              }
              return mv
            }

            override fun visitEnd() {
              // Inject private static void parikshan_handle_picker(FileDialog fd) directly into java.awt.Dialog
              val mv = super.visitMethod(
                Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
                "parikshan_handle_picker",
                "(Ljava/awt/FileDialog;)V",
                null,
                null
              )
              mv.visitCode()

              val applyPaths = Label()
              // if ("true".equals(System.getProperty(SYSTEM_PROPERTY_CANCEL)))
              mv.visitLdcInsn("true")
              mv.visitLdcInsn(SYSTEM_PROPERTY_CANCEL)
              mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;", false)
              mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false)
              mv.visitJumpInsn(Opcodes.IFEQ, applyPaths)

              // Cancel branch: fd.setFile(null); fd.setDirectory(null);
              mv.visitVarInsn(Opcodes.ALOAD, 0)
              mv.visitInsn(Opcodes.ACONST_NULL)
              mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/awt/FileDialog", "setFile", "(Ljava/lang/String;)V", false)

              mv.visitVarInsn(Opcodes.ALOAD, 0)
              mv.visitInsn(Opcodes.ACONST_NULL)
              mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/awt/FileDialog", "setDirectory", "(Ljava/lang/String;)V", false)

              // AWTAccessor.getFileDialogAccessor().setFiles(fd, null)
              mv.visitMethodInsn(Opcodes.INVOKESTATIC, "sun/awt/AWTAccessor", "getFileDialogAccessor", "()Lsun/awt/AWTAccessor\$FileDialogAccessor;", false)
              mv.visitVarInsn(Opcodes.ALOAD, 0)
              mv.visitInsn(Opcodes.ACONST_NULL)
              mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "sun/awt/AWTAccessor\$FileDialogAccessor", "setFiles", "(Ljava/awt/FileDialog;[Ljava/io/File;)V", true)

              val cleanup = Label()
              mv.visitJumpInsn(Opcodes.GOTO, cleanup)

              // Apply paths branch:
              mv.visitLabel(applyPaths)
              mv.visitLdcInsn(SYSTEM_PROPERTY_PATHS)
              mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;", false)
              mv.visitVarInsn(Opcodes.ASTORE, 1) // pathsStr

              val noPaths = Label()
              mv.visitVarInsn(Opcodes.ALOAD, 1)
              mv.visitJumpInsn(Opcodes.IFNULL, noPaths)

              // pathsStr.split(File.pathSeparator)
              mv.visitVarInsn(Opcodes.ALOAD, 1)
              mv.visitFieldInsn(Opcodes.GETSTATIC, "java/io/File", "pathSeparator", "Ljava/lang/String;")
              mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false)
              mv.visitVarInsn(Opcodes.ASTORE, 2) // String[] paths

              // File[] files = new File[paths.length];
              mv.visitVarInsn(Opcodes.ALOAD, 2)
              mv.visitInsn(Opcodes.ARRAYLENGTH)
              mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/io/File")
              mv.visitVarInsn(Opcodes.ASTORE, 3) // File[] files

              // Loop: for (int i = 0; i < paths.length; i++) files[i] = new File(paths[i]);
              mv.visitInsn(Opcodes.ICONST_0)
              mv.visitVarInsn(Opcodes.ISTORE, 4) // i = 0

              val loopCond = Label()
              val loopBody = Label()
              mv.visitJumpInsn(Opcodes.GOTO, loopCond)

              mv.visitLabel(loopBody)
              mv.visitVarInsn(Opcodes.ALOAD, 3) // files
              mv.visitVarInsn(Opcodes.ILOAD, 4) // i
              mv.visitTypeInsn(Opcodes.NEW, "java/io/File")
              mv.visitInsn(Opcodes.DUP)
              mv.visitVarInsn(Opcodes.ALOAD, 2) // paths
              mv.visitVarInsn(Opcodes.ILOAD, 4) // i
              mv.visitInsn(Opcodes.AALOAD)
              mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false)
              mv.visitInsn(Opcodes.AASTORE)

              mv.visitIincInsn(4, 1) // i++

              mv.visitLabel(loopCond)
              mv.visitVarInsn(Opcodes.ILOAD, 4)
              mv.visitVarInsn(Opcodes.ALOAD, 2)
              mv.visitInsn(Opcodes.ARRAYLENGTH)
              mv.visitJumpInsn(Opcodes.IF_ICMPLT, loopBody)

              // if (files.length > 0)
              mv.visitVarInsn(Opcodes.ALOAD, 3)
              mv.visitInsn(Opcodes.ARRAYLENGTH)
              mv.visitJumpInsn(Opcodes.IFLE, noPaths)

              // File first = files[0];
              mv.visitVarInsn(Opcodes.ALOAD, 3)
              mv.visitInsn(Opcodes.ICONST_0)
              mv.visitInsn(Opcodes.AALOAD)
              mv.visitVarInsn(Opcodes.ASTORE, 5) // first

              // fd.setDirectory(first.getParent() != null ? first.getParent() : "")
              mv.visitVarInsn(Opcodes.ALOAD, 0) // fd
              mv.visitVarInsn(Opcodes.ALOAD, 5)
              mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/File", "getParent", "()Ljava/lang/String;", false)
              mv.visitVarInsn(Opcodes.ASTORE, 6)
              val hasParent = Label()
              mv.visitVarInsn(Opcodes.ALOAD, 6)
              mv.visitJumpInsn(Opcodes.IFNONNULL, hasParent)
              mv.visitLdcInsn("")
              mv.visitVarInsn(Opcodes.ASTORE, 6)
              mv.visitLabel(hasParent)
              mv.visitVarInsn(Opcodes.ALOAD, 6)
              mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/awt/FileDialog", "setDirectory", "(Ljava/lang/String;)V", false)

              // fd.setFile(first.getName())
              mv.visitVarInsn(Opcodes.ALOAD, 0) // fd
              mv.visitVarInsn(Opcodes.ALOAD, 5)
              mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/File", "getName", "()Ljava/lang/String;", false)
              mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/awt/FileDialog", "setFile", "(Ljava/lang/String;)V", false)

              // AWTAccessor.getFileDialogAccessor().setFiles(fd, files)
              mv.visitMethodInsn(Opcodes.INVOKESTATIC, "sun/awt/AWTAccessor", "getFileDialogAccessor", "()Lsun/awt/AWTAccessor\$FileDialogAccessor;", false)
              mv.visitVarInsn(Opcodes.ALOAD, 0)
              mv.visitVarInsn(Opcodes.ALOAD, 3)
              mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "sun/awt/AWTAccessor\$FileDialogAccessor", "setFiles", "(Ljava/awt/FileDialog;[Ljava/io/File;)V", true)

              mv.visitLabel(noPaths)

              // cleanup
              mv.visitLabel(cleanup)
              mv.visitLdcInsn(SYSTEM_PROPERTY_HANDLED)
              mv.visitLdcInsn("true")
              mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false)
              mv.visitInsn(Opcodes.POP)

              mv.visitLdcInsn(SYSTEM_PROPERTY_ACTIVE)
              mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "clearProperty", "(Ljava/lang/String;)Ljava/lang/String;", false)
              mv.visitInsn(Opcodes.POP)

              mv.visitInsn(Opcodes.RETURN)
              mv.visitMaxs(0, 0)
              mv.visitEnd()

              super.visitEnd()
            }
          }
          cr.accept(cv, ClassReader.EXPAND_FRAMES)
          cw.toByteArray()
    } catch (t: Throwable) {
      System.err.println("ParikshanAgent: Failed to transform Dialog: ${t.message}")
      null
    }
  }

  private fun transformFileKitCompanion(classfileBuffer: ByteArray): ByteArray? {
    return try {
      val cr = ClassReader(classfileBuffer)
      val cw = ClassWriter(cr, ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
      val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
        override fun visitMethod(
          access: Int,
          name: String?,
          descriptor: String?,
          signature: String?,
          exceptions: Array<out String>?
        ): MethodVisitor {
          val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
          if (name == "createPlatformFilePicker" && descriptor?.endsWith("PlatformFilePicker;") == true) {
            return object : MethodVisitor(Opcodes.ASM9, mv) {
              override fun visitInsn(opcode: Int) {
                if (opcode == Opcodes.ARETURN) {
                  mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "io/github/aryapreetam/parikshan/server/ParikshanAgent",
                    "wrapFileKitPicker",
                    "(Ljava/lang/Object;)Ljava/lang/Object;",
                    false
                  )
                  mv.visitTypeInsn(Opcodes.CHECKCAST, "io/github/vinceglb/filekit/dialogs/platform/PlatformFilePicker")
                }
                super.visitInsn(opcode)
              }
            }
          }
          return mv
        }
      }
      cr.accept(cv, ClassReader.EXPAND_FRAMES)
      cw.toByteArray()
    } catch (t: Throwable) {
      System.err.println("ParikshanAgent: Failed to transform FileKit PlatformFilePicker: ${t.message}")
      null
    }
  }

  @JvmStatic
  fun wrapFileKitPicker(original: Any): Any {
    val iface = runCatching {
      Class.forName("io.github.vinceglb.filekit.dialogs.platform.PlatformFilePicker", true, original.javaClass.classLoader)
    }.getOrNull() ?: return original

    return java.lang.reflect.Proxy.newProxyInstance(
      original.javaClass.classLoader,
      arrayOf(iface)
    ) { _, method, args ->
      val methodName = method.name
      val active = System.getProperty(SYSTEM_PROPERTY_ACTIVE) != null
      if (active) {
        if (methodName == "openFilePicker") {
          System.setProperty(SYSTEM_PROPERTY_HANDLED, "true")
          if ("true" == System.getProperty(SYSTEM_PROPERTY_CANCEL)) {
            return@newProxyInstance null
          }
          val paths = System.getProperty(SYSTEM_PROPERTY_PATHS)
          if (!paths.isNullOrEmpty()) {
            val first = paths.split(File.pathSeparator).firstOrNull()
            if (first != null) return@newProxyInstance File(first)
          }
          return@newProxyInstance null
        }
        if (methodName == "openFilesPicker") {
          System.setProperty(SYSTEM_PROPERTY_HANDLED, "true")
          if ("true" == System.getProperty(SYSTEM_PROPERTY_CANCEL)) {
            return@newProxyInstance null
          }
          val paths = System.getProperty(SYSTEM_PROPERTY_PATHS)
          if (!paths.isNullOrEmpty()) {
            val list = paths.split(File.pathSeparator).map { File(it) }
            return@newProxyInstance list
          }
          return@newProxyInstance null
        }
        if (methodName == "openDirectoryPicker") {
          System.setProperty(SYSTEM_PROPERTY_HANDLED, "true")
          if ("true" == System.getProperty(SYSTEM_PROPERTY_CANCEL)) {
            return@newProxyInstance null
          }
          val paths = System.getProperty(SYSTEM_PROPERTY_PATHS)
          if (!paths.isNullOrEmpty()) {
            val first = paths.split(File.pathSeparator).firstOrNull()
            if (first != null) return@newProxyInstance File(first)
          }
          return@newProxyInstance null
        }
      }
      method.invoke(original, *(args ?: emptyArray()))
    }
  }
}
