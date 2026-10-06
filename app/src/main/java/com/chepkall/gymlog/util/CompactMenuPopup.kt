package com.chepkall.gymlog.util

import android.annotation.SuppressLint
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chepkall.gymlog.R

/**
 * Всплывающий список в стилистике приложения (замена системного dropdown).
 * Прокручиваемый, на экране виден максимум [maxVisible] пунктов.
 */
class CompactMenuPopup(
    private val anchor: View,
    private val items: List<String>,
    private val maxVisible: Int = 6,
    private val onPick: (Int, String) -> Unit,
) {
    private var popup: PopupWindow? = null

    @SuppressLint("InflateParams")
    fun show() {
        if (popup != null) { dismiss(); return } // повторный тап — закрыть

        val ctx = anchor.context
        val content = LayoutInflater.from(ctx).inflate(R.layout.compact_menu_list, null)
        val list = content.findViewById<RecyclerView>(R.id.menu_list)

        val selectedIdx = anchor.tag as? Int ?: 0
        val selectedText = items.getOrNull(selectedIdx)
        val itemHeight = ctx.resources.getDimensionPixelSize(R.dimen.menu_item_height)
        // Показываем ровно максимум 6 пунктов, дальше — прокрутка
        val visibleCount = items.size.coerceAtMost(maxVisible)
        val popupHeight = itemHeight * visibleCount + ctx.resources.getDimensionPixelSize(R.dimen.menu_padding) * 2

        // Ширина — минимум 260dp (длинные названия должны влезать целиком)
        val minWidth = ctx.resources.getDimensionPixelSize(R.dimen.menu_min_width)
        val popupWidth = maxOf(minWidth, anchor.width)

        list.layoutManager = LinearLayoutManager(ctx)
        list.adapter = CompactMenuAdapter(items, selectedText) { pos, text ->
            dismiss()
            onPick(pos, text)
        }

        popup = PopupWindow(
            content,
            popupWidth,
            popupHeight,
            true,
        ).apply {
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = ctx.resources.getDimension(R.dimen.menu_elevation)
            showAsDropDown(anchor, anchor.width - popupWidth, ctx.resources.getDimensionPixelSize(R.dimen.menu_offset), Gravity.NO_GRAVITY)
            // Прокрутим к выбранному пункту, если он не в первых 6
            if (selectedIdx >= visibleCount) {
                content.post {
                    val lm = list.layoutManager as LinearLayoutManager
                    lm.scrollToPositionWithOffset((selectedIdx - visibleCount + 1).coerceIn(0, items.size - 1), 0)
                }
            }
        }
    }

    fun dismiss() {
        popup?.dismiss()
        popup = null
    }
}

private class CompactMenuAdapter(
    private val items: List<String>,
    private val selected: String?,
    private val onClick: (Int, String) -> Unit,
) : RecyclerView.Adapter<CompactMenuAdapter.VH>() {

    class VH(val root: View) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_menu_text, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val text = items[position]
        val tv = holder.root as android.widget.TextView
        tv.text = text
        tv.setTypeface(null, if (text == selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        holder.root.setOnClickListener { onClick(position, text) }
    }
}
