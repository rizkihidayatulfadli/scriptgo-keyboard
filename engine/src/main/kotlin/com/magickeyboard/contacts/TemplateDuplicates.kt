package com.magickeyboard.contacts

/** What duplicate detection needs from a live (not deleted) template. */
data class TemplateCopy(val id: String, val shortcut: String, val title: String, val body: String)

/**
 * Finds copies of the same template, e.g. starter templates added again by each install before its
 * first sync. Copies share shortcut, title, and body. One copy of each is kept, chosen the same way
 * on every device (a `starter-` id first, else the smallest id), so two phones cleaning up at the
 * same time never delete every copy between them.
 */
object TemplateDuplicates {
    const val STARTER_PREFIX = "starter-"

    /** Ids of the copies to delete. */
    fun toDelete(templates: List<TemplateCopy>): List<String> =
        templates
            .groupBy { Triple(it.shortcut, it.title, it.body) }
            .values
            .filter { it.size > 1 }
            .flatMap { copies ->
                val keep = copies.minWith(compareBy<TemplateCopy> { !it.id.startsWith(STARTER_PREFIX) }.thenBy { it.id })
                copies.filter { it !== keep }.map { it.id }
            }
            .sorted()
}
