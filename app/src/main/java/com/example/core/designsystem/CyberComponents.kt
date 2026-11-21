package com.example.core.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.common.AppError

@Composable
fun SpaceBackground(
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit,
) {
  val palette = LocalCyberPalette.current
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        brush = Brush.verticalGradient(
          colors = listOf(
            palette.spaceBackdropStart,
            palette.spaceBackdropMid,
            palette.spaceBackdropEnd,
          ),
        ),
      )
      .drawBehind {
        // Subtle orbital radial glows in top-left (cyan) and bottom-right (purple)
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(
              palette.cyanGlow.copy(alpha = if (palette.isDark) 0.12f else 0.08f),
              Color.Transparent,
            ),
            center = Offset(size.width * 0.15f, size.height * 0.08f),
            radius = size.maxDimension * 0.45f,
          ),
          center = Offset(size.width * 0.15f, size.height * 0.08f),
          radius = size.maxDimension * 0.45f,
        )
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(
              palette.purpleGlow.copy(alpha = if (palette.isDark) 0.13f else 0.07f),
              Color.Transparent,
            ),
            center = Offset(size.width * 0.9f, size.height * 0.85f),
            radius = size.maxDimension * 0.5f,
          ),
          center = Offset(size.width * 0.9f, size.height * 0.85f),
          radius = size.maxDimension * 0.5f,
        )
      },
    content = content,
  )
}

@Composable
fun GlassCard(
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(20.dp),
  glowColor: Color? = null,
  onClick: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(16.dp),
  content: @Composable ColumnScope.() -> Unit,
) {
  val palette = LocalCyberPalette.current
  val borderBrush = Brush.linearGradient(
    colors = listOf(
      (glowColor ?: palette.cyanGlow).copy(alpha = if (palette.isDark) 0.55f else 0.35f),
      palette.purpleGlow.copy(alpha = if (palette.isDark) 0.30f else 0.20f),
    ),
  )

  val cardModifier = modifier
    .shadow(
      elevation = if (palette.isDark) 10.dp else 4.dp,
      shape = shape,
      ambientColor = (glowColor ?: palette.cyanGlow).copy(alpha = 0.28f),
      spotColor = palette.purpleGlow.copy(alpha = 0.25f),
    )
    .clip(shape)
    .background(palette.glassFill)
    .border(width = 1.dp, brush = borderBrush, shape = shape)
    .let { mod -> if (onClick != null) mod.clickable(onClick = onClick) else mod }
    .padding(contentPadding)

  Column(modifier = cardModifier, content = content)
}

@Composable
fun StatusBeacon(
  active: Boolean,
  label: String,
  activeColor: Color = LocalCyberPalette.current.successAccent,
  inactiveColor: Color = LocalCyberPalette.current.warningAccent,
  modifier: Modifier = Modifier,
) {
  val infiniteTransition = rememberInfiniteTransition(label = "beacon")
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.35f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "pulse",
  )
  val dotColor = if (active) activeColor else inactiveColor

  Surface(
    modifier = modifier,
    shape = CircleShape,
    color = dotColor.copy(alpha = 0.14f),
    border = BorderStroke(1.dp, dotColor.copy(alpha = 0.45f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Box(
        modifier = Modifier
          .size(8.dp)
          .clip(CircleShape)
          .background(dotColor.copy(alpha = if (active) pulseAlpha else 0.85f)),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = dotColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun CyberGradientProgressBar(
  progress: Float,
  modifier: Modifier = Modifier,
  height: Dp = 8.dp,
) {
  val palette = LocalCyberPalette.current
  val clamped = progress.coerceIn(0f, 1f)
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(height)
      .clip(CircleShape)
      .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth(clamped)
        .height(height)
        .clip(CircleShape)
        .background(
          brush = Brush.horizontalGradient(
            colors = listOf(palette.cyanGlow, CyberBlue, palette.purpleGlow),
          ),
        ),
    )
  }
}

@Composable
fun SkeletonShimmerBox(
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(14.dp),
) {
  val palette = LocalCyberPalette.current
  val transition = rememberInfiniteTransition(label = "shimmer")
  val shift by transition.animateFloat(
    initialValue = 0f,
    targetValue = 900f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1100),
      repeatMode = RepeatMode.Restart,
    ),
    label = "shift",
  )
  val baseColor = if (palette.isDark) Color(0xFF15203B) else Color(0xFFE2E8F0)
  val highlightColor = if (palette.isDark) Color(0xFF23345D) else Color(0xFFF8FAFC)
  Box(
    modifier = modifier
      .clip(shape)
      .background(
        brush = Brush.linearGradient(
          colors = listOf(baseColor, highlightColor, baseColor),
          start = Offset(shift - 300f, 0f),
          end = Offset(shift, 120f),
        ),
      ),
  )
}

@Composable
fun CyberBadge(
  text: String,
  color: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(8.dp),
    color = color.copy(alpha = 0.16f),
    border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = color,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
fun AppErrorCard(
  error: AppError,
  isVi: Boolean,
  onRetry: (() -> Unit)? = null,
  retryLabel: String = "Retry",
  modifier: Modifier = Modifier,
) {
  val palette = LocalCyberPalette.current
  GlassCard(
    modifier = modifier.fillMaxWidth(),
    glowColor = palette.errorAccent,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.ErrorOutline,
        contentDescription = error.code,
        tint = palette.errorAccent,
        modifier = Modifier.size(24.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "${error.code} • ${error.title(isVi)}",
          style = MaterialTheme.typography.titleMedium,
          color = palette.errorAccent,
        )
      }
    }
    Spacer(Modifier.height(8.dp))
    Text(
      text = error.cause(isVi),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(6.dp))
    Text(
      text = error.fix(isVi),
      style = MaterialTheme.typography.bodyMedium,
      color = palette.cyanGlow,
    )
    if (!error.technicalDetail.isNullOrBlank()) {
      Spacer(Modifier.height(8.dp))
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = palette.terminalBg,
      ) {
        Text(
          text = error.technicalDetail.take(280),
          style = MaterialTheme.typography.labelSmall,
          color = palette.terminalText,
          modifier = Modifier.padding(8.dp),
        )
      }
    }
    if (error.retryable && onRetry != null) {
      Spacer(Modifier.height(12.dp))
      Button(
        onClick = onRetry,
        colors = ButtonDefaults.buttonColors(
          containerColor = palette.errorAccent,
          contentColor = Color.White,
        ),
      ) {
        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(retryLabel)
      }
    }
  }
}
