package com.chepkall.gymlog.util

import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Простые диалоги с полем ввода. */
object TextInputDialog {

    fun prompt(
        fragment: Fragment,
        title: String,
        hint: String,
        preset: String = "",
        multiline: Boolean = false,
        onOk: (String) -> Unit,
    ) {
        val ctx = fragment.requireContext()
        val outer = FrameLayout(ctx)
        val padding = (16 * ctx.resources.displayMetrics.density).toInt()
        outer.setPadding(padding, padding / 2, padding, 0)
        val input = EditText(ctx)
        input.hint = hint
        input.setText(preset)
        input.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
        if (multiline) {
            input.minLines = 2
            input.maxLines = 6
            input.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        } else {
            input.inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        outer.addView(
            input,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setView(outer)
            .setPositiveButton("ОК") { _, _ -> onOk(input.text.toString().trim()) }
            .setNegativeButton("Отмена", null)
            .show()
    }
}