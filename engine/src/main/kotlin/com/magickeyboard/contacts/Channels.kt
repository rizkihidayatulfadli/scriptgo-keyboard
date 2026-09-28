package com.magickeyboard.contacts

/**
 * The built-in "Channel" field: where a contact chats with the user (WhatsApp, Instagram, …).
 * It is an ordinary choice field with a fixed key and id, created for every user; its options can
 * be edited. When a contact is created from the template form, the channel is filled in from the
 * app the keyboard is typing into. Only the app's package name is used, never what is on screen.
 */
object Channels {
    const val KEY = "channel"

    /** Same id on every device, so field definitions created offline merge into one on sync. */
    const val FIELD_ID = "builtin-channel"

    /** Stand-in "package" for WhatsApp Web in the Chrome extension. */
    const val WHATSAPP_WEB = "web.whatsapp.com"

    val DEFAULT_OPTIONS = listOf(
        "WhatsApp", "WA Business", "Instagram", "TikTok", "Shopee", "Tokopedia", "Facebook", "Telegram", "Lainnya",
    )

    private val BY_PACKAGE = mapOf(
        "com.whatsapp" to "WhatsApp",
        WHATSAPP_WEB to "WhatsApp",
        "com.whatsapp.w4b" to "WA Business",
        "com.instagram.android" to "Instagram",
        "com.instagram.barcelona" to "Instagram",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.ss.android.ugc.trill" to "TikTok",
        "com.shopee.id" to "Shopee",
        "com.shopee.sellercenter.id" to "Shopee",
        "com.tokopedia.tkpd" to "Tokopedia",
        "com.tokopedia.sellerapp" to "Tokopedia",
        "com.facebook.orca" to "Facebook",
        "com.facebook.katana" to "Facebook",
        "com.facebook.lite" to "Facebook",
        "org.telegram.messenger" to "Telegram",
    )

    /**
     * The option of [options] for the app [packageName], written as the user wrote it; null when
     * the app is not a known chat app or the user removed that option.
     */
    fun optionFor(packageName: String?, options: List<String>): String? {
        val default = BY_PACKAGE[packageName.orEmpty()] ?: return null
        return options.firstOrNull { it.trim().equals(default, ignoreCase = true) }
    }
}
