package app.revanced.patches.bilibili.misc.theme.patch

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patches.bilibili.misc.theme.fingerprints.*
import app.revanced.patches.bilibili.utils.*
import app.revanced.util.exception
import app.revanced.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Patch(
    name = "Custom theme color",
    description = "自定义主题色",
    compatiblePackages = [
        CompatiblePackage(name = "tv.danmaku.bili")
    ]
)
object CustomThemePatch : BytecodePatch(
    setOf(
        BuiltInThemesFingerprint,
        BuiltInThemesFingerprint2,
        SkinListFingerprint,
        ThemeClickFingerprint,
        ThemeColorsFingerprint,
        ThemeHelperFingerprint,
        ThemeNameFingerprint,
        ThemeProcessorFingerprint,
        WebActivityBuildUriFingerprint
    )
) {
    override fun execute(context: BytecodeContext) {
        val patchClass = context.findClass("Lapp/revanced/bilibili/patches/CustomThemePatch;")?.mutableClass ?: return

        ThemeNameFingerprint.result?.mutableClass?.fields?.firstOrNull {
            it.type == "Ljava/util/Map;"
        }?.let { field ->
            field.accessFlags = field.accessFlags.toPublic().removeFinal()
            patchClass.methods.run {
                firstOrNull { it.name == "getThemeNames" }?.also { remove(it) }?.cloneMutable(
                    registerCount = 1, clearImplementation = true
                )?.apply {
                    addInstructions(
                        """
                        sget-object v0, $field
                        return-object v0
                    """.trimIndent()
                    )
                }?.also { add(it) }
            }
        }

        ThemeHelperFingerprint.result?.let { r ->
            r.mutableClass.fields.firstOrNull { it.name == r.method.name }
        }?.let { field ->
            field.accessFlags = field.accessFlags.toPublic().removeFinal()
            patchClass.methods.run {
                firstOrNull { it.name == "getColorArray" }?.also { remove(it) }?.cloneMutable(
                    registerCount = 1, clearImplementation = true
                )?.apply {
                    addInstructions(
                        """
                        sget-object v0, $field
                        return-object v0
                    """.trimIndent()
                    )
                }?.also { add(it) }
            }
        }

        (BuiltInThemesFingerprint.result ?: BuiltInThemesFingerprint2.result)?.mutableClass?.fields?.firstOrNull {
            it.type == "Ljava/util/Map;"
        }?.let { field ->
            field.accessFlags = field.accessFlags.toPublic().removeFinal()
            patchClass.methods.run {
                firstOrNull { it.name == "getAllThemes" }?.also { remove(it) }?.cloneMutable(
                    registerCount = 1, clearImplementation = true
                )?.apply {
                    addInstructions(
                        """
                        sget-object v0, $field
                        return-object v0
                    """.trimIndent()
                    )
                }?.also { add(it) }
            }
        }

        ThemeColorsFingerprint.result?.mutableClass?.methods?.firstOrNull {
            it.name == "<init>" && it.accessFlags.isPrivate()
        }?.let { method ->
            method.accessFlags = method.accessFlags.toPublic()
            val garbType = method.parameterTypes.getOrNull(0)?.toString()
            if (garbType != null) {
                garbType.toClassDef(context).proxy(context).addDefaultConstructorIfNeeded()
                patchClass.methods.run {
                    firstOrNull { it.name == "newTheme" }?.also { remove(it) }?.cloneMutable(
                        registerCount = 35, clearImplementation = true
                    )?.apply {
                        addInstructions(
                            """
                            new-instance v0, ${method.definingClass}
                            move-object v1, v0
                            new-instance v2, $garbType
                            invoke-direct {v2}, $garbType-><init>()V
                            move-object/from16 v3, p0
                            move-wide/from16 v4, p1
                            move-wide/from16 v6, p3
                            move-wide/from16 v8, p5
                            move-wide/from16 v10, p7
                            move-wide/from16 v12, p9
                            move-wide/from16 v14, p11
                            move-wide/from16 v16, p13
                            move/from16 v18, p15
                            invoke-direct/range {v1 .. v18}, $method
                            return-object v0
                        """.trimIndent()
                        )
                    }?.also { add(it) }
                }
            }
        }

        val onSetSkinListMethod = patchClass.methods.firstOrNull { it.name == "onSetSkinList" }
        if (onSetSkinListMethod != null) {
            SkinListFingerprint.result?.mutableMethod?.addInstructions(
                0, """
                invoke-static {p1}, $onSetSkinListMethod
            """.trimIndent()
            )
        }

        val onClickOriginListenerType = "Lapp/revanced/bilibili/widget/OnClickOriginListener;"
        val onThemeClickMethod = patchClass.methods.firstOrNull { it.name == "onThemeClick" }
        if (onThemeClickMethod != null) {
            ThemeClickFingerprint.result?.run {
                mutableClass.interfaces.add(onClickOriginListenerType)
                mutableMethod.also {
                    mutableClass.methods.add(it.cloneMutable(name = "onClick_Origin"))
                }.addInstructionsWithLabels(
                    0, """
                    invoke-static {p0, p1}, $onThemeClickMethod
                    move-result v0
                    if-eqz v0, :jump
                    return-void
                    :jump
                    nop
                """.trimIndent()
                )
            }
        }

        val onThemeResetMethod = patchClass.methods.firstOrNull { it.name == "onThemeReset" }
        if (onThemeResetMethod != null) {
            ThemeProcessorFingerprint.result?.mutableClass?.methods?.filter { m ->
                m.parameterTypes.isEmpty() && m.accessFlags == 0 && m.returnType == "V"
            }?.forEach { m ->
                m.addInstructionsWithLabels(
                    0, """
                    invoke-static {}, $onThemeResetMethod
                    move-result v0
                    if-eqz v0, :jump
                    return-void
                    :jump
                    nop
                """.trimIndent()
                )
            }
        }

        WebActivityBuildUriFingerprint.result?.method?.implementation?.instructions?.firstNotNullOfOrNull { inst ->
            if (inst.opcode == Opcode.INVOKE_STATIC) inst.getReference<MethodReference>().let {
                if (it.parameterTypes.isEmpty() && it.returnType == "I") it.definingClass else null
            } else null
        }?.let { context.findClass(it) }?.mutableClass?.fields?.find {
            it.type == "Landroid/util/SparseArray;"
        }?.let { field ->
            field.accessFlags = field.accessFlags.toPublic().removeFinal()
            patchClass.methods.run {
                firstOrNull { it.name == "getColorIds" }?.also { remove(it) }?.cloneMutable(
                    registerCount = 1, clearImplementation = true
                )?.apply {
                    addInstructions(
                        """
                        sget-object v0, $field
                        return-object v0
                    """.trimIndent()
                    )
                }?.also { add(it) }
            }
        }
    }
}
