package com.example.util

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.scrollToTopOnFocus(): Modifier = composed {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    this
        .bringIntoViewRequester(bringIntoViewRequester)
        .onFocusEvent { focusState ->
            if (focusState.isFocused) {
                coroutineScope.launch {
                    // Try to bring a rect that is shifted upwards into view, forcing the item to the top
                    // A rect with a large negative top usually forces the scroll view to scroll down
                    bringIntoViewRequester.bringIntoView(Rect(0f, -600f, 1f, 1f))
                }
            }
        }
}
