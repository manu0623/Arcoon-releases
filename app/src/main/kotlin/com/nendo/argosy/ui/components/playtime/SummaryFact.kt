package com.nendo.argosy.ui.components.playtime

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.ui.theme.LocalArgosyTheme

@Composable
fun SummaryFact(
    label: String,
    value: String,
    detail: String?,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = MaterialTheme.typography.labelSmall
) {
    val theme = LocalArgosyTheme.current
    Column(modifier = modifier) {
        Text(
            text = label,
            style = labelStyle,
            color = theme.textMute,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = theme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
