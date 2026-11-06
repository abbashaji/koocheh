package org.koocheh.disclosure

import org.koocheh.model.AgeRange
import org.koocheh.model.ContextId
import org.koocheh.model.InterestTag

/**
 * The only place tests construct model values, using the real model constants and constructors.
 */
object TestModel {
    fun tag(i: Int): InterestTag = InterestTag("tag$i")
    fun ageRange(): AgeRange = AgeRange.R18_24
    fun context(): ContextId = ContextId.WORK
}
