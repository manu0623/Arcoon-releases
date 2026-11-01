package com.nendo.argosy.ui.dualscreen

class DisplayHostRegistry<T : Any> {

    private val hosts = linkedMapOf<Int, T>()

    /**
     * Makes [host] the surface for [displayId] and answers the different surface it replaced, or
     * null when the display was free or [host] was already registered there.
     */
    fun register(displayId: Int, host: T): T? =
        hosts.put(displayId, host)?.takeIf { it !== host }

    /**
     * Removes [host] from [displayId] only while it is still the instance registered there, and
     * answers whether it was.
     */
    fun unregister(displayId: Int, host: T): Boolean {
        if (hosts[displayId] !== host) return false
        hosts.remove(displayId)
        return true
    }

    fun hostFor(displayId: Int?): T? = displayId?.let { hosts[it] }

    fun all(): List<T> = hosts.values.toList()

    fun displayIds(): Set<Int> = hosts.keys.toSet()

    val isEmpty: Boolean get() = hosts.isEmpty()
}
