package uz.xumoyun.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uz.xumoyun.core.ui.theme.LocalExtraColors
import uz.xumoyun.core.ui.theme.TabularNumbers

/** Javob varianti holati. */
enum class OptionState {
    /** Hali tanlanmagan. */
    IDLE,
    /** Tanlangan, lekin to'g'ri/xato ko'rsatilmaydi (imtihon rejimi). */
    SELECTED,
    /** To'g'ri javob (mashq rejimida yoki tahlilda). */
    CORRECT,
    /** Tanlangan xato javob. */
    WRONG,
}

/**
 * Katta raqamlangan javob kartasi (F1, F2 yoki A, B, C, D).
 * To'g'ri javob — yashil fon 12% shaffoflik bilan, xato — qizil.
 */
@Composable
fun AnswerOption(
    label: String,
    text: String,
    state: OptionState,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extra = LocalExtraColors.current
    val accent: Color = when (state) {
        OptionState.CORRECT -> extra.success
        OptionState.WRONG -> extra.failure
        OptionState.SELECTED -> MaterialTheme.colorScheme.primary
        OptionState.IDLE -> MaterialTheme.colorScheme.outlineVariant
    }
    val container = when (state) {
        OptionState.CORRECT -> extra.success.copy(alpha = 0.12f)
        OptionState.WRONG -> extra.failure.copy(alpha = 0.12f)
        OptionState.SELECTED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        OptionState.IDLE -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        colors = CardDefaults.cardColors(containerColor = container, disabledContainerColor = container),
        border = BorderStroke(if (state == OptionState.IDLE) 1.dp else 2.dp, accent),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (state == OptionState.IDLE) MaterialTheme.colorScheme.secondaryContainer else accent,
            ) {
                Box(Modifier.size(width = 40.dp, height = 32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (state == OptionState.IDLE) MaterialTheme.colorScheme.onSecondaryContainer else Color.White,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            when (state) {
                OptionState.CORRECT -> Icon(Icons.Rounded.CheckCircle, null, tint = extra.success)
                OptionState.WRONG -> Icon(Icons.Rounded.Cancel, null, tint = extra.failure)
                else -> Unit
            }
        }
    }
}

/** Savol raqami va taymer qatori: "5/20" ... "24:10". Oxirgi 2 daqiqada taymer qizil. */
@Composable
fun QuizHeader(
    progressText: String,
    modifier: Modifier = Modifier,
    timerText: String? = null,
    timerWarning: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(progressText, style = MaterialTheme.typography.titleMedium.merge(TabularNumbers), modifier = Modifier.weight(1f))
        trailing?.invoke()
        if (timerText != null) {
            val color = if (timerWarning) LocalExtraColors.current.failure else MaterialTheme.colorScheme.onSurface
            Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.10f)) {
                Text(
                    timerText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
                    color = color,
                )
            }
        }
    }
}

/** Natija kartasi: katta raqam va izoh. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f))) {
        androidx.compose.foundation.layout.Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineMedium.merge(TabularNumbers), color = color, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
