package com.chepkall.gymlog.util

import android.widget.EditText

/** Короткий extension для TextInputEditText/EditText: колбэк на каждое изменение текста. */
fun EditText.doAfterTextChanged(block: (android.text.Editable?) -> Unit) {
    addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) = block(s)
    })
}