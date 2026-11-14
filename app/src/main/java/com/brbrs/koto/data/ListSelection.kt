package com.brbrs.koto.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which list the Tasks screen is scoped to.
 *
 * It lives outside both view models because two screens set it: the scope chips on the Tasks
 * screen, and tapping a list on the Lists screen. Passing it as a navigation argument would
 * mean the Tasks tab has two routes, which breaks the bottom bar's saved back stack, so a
 * small shared holder is the simpler honest answer.
 *
 * Deliberately not persisted: a scope is a thing you set for the next minute, not a preference.
 */
@Singleton
class ListSelection @Inject constructor() {
    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()

    private val _showAllRequests = MutableStateFlow(0)
    /** Bumped when a list is opened from the Lists screen; the Tasks screen widens its filter. */
    val showAllRequests: StateFlow<Int> = _showAllRequests.asStateFlow()

    fun select(href: String?) { _selected.value = href }

    /**
     * Opening a list means "show me this list", so the Tasks screen must widen its filter.
     * Landing there on the Today filter shows a near-empty screen and reads as missing tasks.
     */
    fun open(href: String) {
        _selected.value = href
        _showAllRequests.value += 1
    }

    fun toggle(href: String) {
        _selected.value = if (_selected.value == href) null else href
    }
}
