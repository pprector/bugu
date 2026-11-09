package com.bugu.kuikly

import android.content.res.AssetManager
import android.util.Log
import android.graphics.Typeface
import com.tencent.kuikly.core.render.android.adapter.IKRFontAdapter

/**
 * 把 assets/fonts/<fontFamily>.ttf 真正加载成 Typeface。
 * Kuikly Compose 侧 BuguFonts 里的 KuiklyFont(fontName) 会作为 CSS font-family
 * 传到这里的 fontFamily,因此两侧文件名必须一一对应。
 */
class BuguFontAdapter(private val assets: AssetManager) : IKRFontAdapter {

    override fun getTypeface(fontFamily: String, result: (Typeface?) -> Unit) {
        if (fontFamily.isBlank()) {
            result(null)
            return
        }
        val typeface = runCatching {
            Typeface.createFromAsset(assets, "fonts/$fontFamily.ttf")
        }.getOrNull()
        Log.i("BuguFontAdapter", "getTypeface family=$fontFamily loaded=${typeface != null}")
        result(typeface)
    }
}
