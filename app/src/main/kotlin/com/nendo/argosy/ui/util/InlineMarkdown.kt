package com.nendo.argosy.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

fun parseInlineMarkdown(
    text: String,
    linkColor: Color = Color(0xFF6366F1),
    codeBackground: Color = Color(0x1A888888)
): AnnotatedString = buildAnnotatedString {
    var i = 0
    val len = text.length

    while (i < len) {
        when {
            text[i] == '\\' && i + 1 < len -> {
                append(text[i + 1])
                i += 2
            }

            text[i] == '[' -> {
                val closeBracket = text.indexOf(']', i + 1)
                if (closeBracket != -1 && closeBracket + 1 < len && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(')', closeBracket + 2)
                    if (closeParen != -1) {
                        val linkText = text.substring(i + 1, closeBracket)
                        val url = text.substring(closeBracket + 2, closeParen)
                        pushStringAnnotation(tag = "URL", annotation = url)
                        pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                        append(linkText)
                        pop()
                        pop()
                        i = closeParen + 1
                        continue
                    }
                }
                append(text[i])
                i++
            }

            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground))
                    append(text.substring(i + 1, end))
                    pop()
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }

            text[i] == '~' && i + 1 < len && text[i + 1] == '~' -> {
                val end = text.indexOf("~~", i + 2)
                if (end != -1) {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    append(text.substring(i + 2, end))
                    pop()
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }

            (text[i] == '*' && i + 1 < len && text[i + 1] == '*') ||
            (text[i] == '_' && i + 1 < len && text[i + 1] == '_') -> {
                val marker = text.substring(i, i + 2)
                val end = text.indexOf(marker, i + 2)
                if (end != -1) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(text.substring(i + 2, end))
                    pop()
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }

            text[i] == '*' || text[i] == '_' -> {
                val marker = text[i]
                val end = text.indexOf(marker, i + 1)
                if (end != -1 && end > i + 1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text.substring(i + 1, end))
                    pop()
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }

            text[i] == '\n' -> {
                append('\n')
                i++
            }

            else -> {
                append(text[i])
                i++
            }
        }
    }
}
