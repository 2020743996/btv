package com.example.iptvplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiActionsTest {

    @Test
    fun orderActions_placesPrimaryLast() {
        val actions = listOf(
            action("保存", ActionRole.PRIMARY),
            action("取消", ActionRole.SECONDARY),
            action("删除", ActionRole.DESTRUCTIVE)
        )

        assertEquals(listOf("删除", "取消", "保存"), orderActions(actions).map { it.label })
    }

    @Test
    fun actionHierarchy_allowsOnlyOnePrimary() {
        assertTrue(
            hasValidActionHierarchy(
                listOf(action("取消", ActionRole.SECONDARY), action("保存", ActionRole.PRIMARY))
            )
        )
        assertFalse(
            hasValidActionHierarchy(
                listOf(action("添加", ActionRole.PRIMARY), action("保存", ActionRole.PRIMARY))
            )
        )
    }

    @Test
    fun disabledPrimary_usesDisabledVisualState() {
        assertEquals(
            ActionVisualState.DISABLED,
            actionVisualState(enabled = false, highlighted = true, focused = false)
        )
        assertEquals(
            ActionVisualState.HIGHLIGHTED,
            actionVisualState(enabled = true, highlighted = true, focused = false)
        )
    }

    private fun action(label: String, role: ActionRole) = UiAction(
        label = label,
        role = role,
        onClick = {}
    )
}
