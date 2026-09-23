package com.example.iptvplayer

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun normalizedM3uUrl(value: String): String? {
    val text = value.trim()
    if (!text.startsWith("http://", true) && !text.startsWith("https://", true)) return null
    if (text.any { it.isWhitespace() || it.isISOControl() }) return null
    return text.toHttpUrlOrNull()?.toString()
}

internal fun isSupportedM3uUrl(value: String): Boolean = normalizedM3uUrl(value) != null

internal enum class AddressConfirmation { NONE, DISCARD_EDIT, DELETE }

internal data class AddressEditorState(
    val savedUrls: List<String>,
    val urls: List<String> = savedUrls,
    val editingIndex: Int = -1,
    val draft: String = "",
    val error: String? = null,
    val confirmation: AddressConfirmation = AddressConfirmation.NONE,
    val deleteIndex: Int = -1
) {
    val editing get() = editingIndex >= 0
    val adding get() = editingIndex == urls.size
    val dirty get() = urls != savedUrls
    val draftChanged get() = draft != urls.getOrNull(editingIndex).orEmpty()

    fun edit(index: Int) = copy(editingIndex = index, draft = urls.getOrNull(index).orEmpty(), error = null)
    fun changeDraft(value: String) = copy(draft = value, error = null)
    fun closeEditor() = copy(editingIndex = -1, draft = "", error = null, confirmation = AddressConfirmation.NONE)

    fun submit(): AddressEditorState {
        if (!editing) return this
        val url = normalizedM3uUrl(draft)
            ?: return copy(error = "请输入完整的 HTTP/HTTPS 地址，地址中不能包含空格")
        if (urls.withIndex().any { (i, existing) -> i != editingIndex && normalizedM3uUrl(existing) == url }) {
            return copy(error = "这个地址已在列表中")
        }
        val updated = if (adding) urls + url else urls.mapIndexed { i, old -> if (i == editingIndex) url else old }
        return copy(urls = updated).closeEditor()
    }

    fun requestBack(): AddressEditorState = when {
        editing && draftChanged -> copy(confirmation = AddressConfirmation.DISCARD_EDIT)
        editing -> closeEditor()
        else -> this
    }

    fun confirmDelete() = copy(
        urls = urls.filterIndexed { index, _ -> index != deleteIndex },
        deleteIndex = -1,
        confirmation = AddressConfirmation.NONE
    )
}
