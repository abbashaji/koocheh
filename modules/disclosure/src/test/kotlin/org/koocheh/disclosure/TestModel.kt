package org.koocheh.disclosure

import org.koocheh.model.AgeRange
import org.koocheh.model.ContextId
import org.koocheh.model.InterestTag

/**
 * The only place tests construct model values. The model types are treated as opaque, so this
 * builds them reflectively (enum constant by index, otherwise the first constructor with simple
 * parameters). If the model gains factory functions, replace the bodies here.
 */
object TestModel {
    private fun <T : Any> make(cls: Class<T>, index: Int, text: String): T {
        cls.enumConstants?.let { return it[index % it.size] }
        val ctor = cls.declaredConstructors.first { c ->
            c.parameterTypes.all { it == String::class.java || it == Int::class.javaPrimitiveType || it == Long::class.javaPrimitiveType }
        }
        ctor.isAccessible = true
        val args = ctor.parameterTypes.map { t ->
            when (t) {
                String::class.java -> text
                Int::class.javaPrimitiveType -> 18 + index
                else -> (18 + index).toLong()
            }
        }
        @Suppress("UNCHECKED_CAST")
        return ctor.newInstance(*args.toTypedArray()) as T
    }

    fun tag(i: Int): InterestTag = make(InterestTag::class.java, i, "tag$i")
    fun ageRange(): AgeRange = make(AgeRange::class.java, 0, "18-24")
    fun context(): ContextId = make(ContextId::class.java, 0, "ctx-test")
}
