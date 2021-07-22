package org.schabi.newpipe.local.feed.service

class FeedResultsHolder {
    /**
     * List of errors that may have happen during loading.
     */
    val itemsErrors: List<Throwable>
        get() = itemsErrorsHolder

    private val itemsErrorsHolder: MutableList<Throwable> = ArrayList()

    fun addError(error: Throwable) {
        itemsErrorsHolder.add(error)
    }

    fun addErrors(errors: List<Throwable>) {
        itemsErrorsHolder.addAll(errors)
    }
}
