package com.example.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class CyberPalette(
  val isDark: Boolean,
  val spaceBackdropStart: Color,
  val spaceBackdropMid: Color,
  val spaceBackdropEnd: Color,
  val glassFill: Color,
  val glassBorder: Color,
  val cyanGlow: Color,
  val purpleGlow: Color,
  val successAccent: Color,
  val warningAccent: Color,
  val errorAccent: Color,
  val terminalBg: Color,
  val terminalText: Color,
)

val DarkCyberPalette = CyberPalette(
  isDark = true,
  spaceBackdropStart = DeepSpaceBlack,
  spaceBackdropMid = DeepSpaceBlack,
  spaceBackdropEnd = Color(0xFF0A0D12),
  glassFill = GlassCardDark,
  glassBorder = GlassBorderDark,
  cyanGlow = ElectricCyan,
  purpleGlow = NeonPurple,
  successAccent = EmeraldMatrix,
  warningAccent = SolarAmber,
  errorAccent = CrimsonAlert,
  terminalBg = Color(0xFF090C10),
  terminalText = Color(0xFFC9D1D9),
)

val LightCyberPalette = CyberPalette(
  isDark = false,
  spaceBackdropStart = LabFrostBackground,
  spaceBackdropMid = LabFrostMid,
  spaceBackdropEnd = LabFrostEnd,
  glassFill = GlassCardLight,
  glassBorder = GlassBorderLight,
  cyanGlow = LabCyanPrimary,
  purpleGlow = LabPurpleSecondary,
  successAccent = Color(0xFF059669),
  warningAccent = Color(0xFFD97706),
  errorAccent = Color(0xFFDC2626),
  terminalBg = Color(0xFF0F172A),
  terminalText = Color(0xFF38BDF8),
)

val LocalCyberPalette = staticCompositionLocalOf { DarkCyberPalette }

private val QusinDarkColorScheme = darkColorScheme(
  primary = ElectricCyan,
  onPrimary = Color(0xFF08111E),
  primaryContainer = Color(0xFF1F334D),
  onPrimaryContainer = Color(0xFFDCEBFF),
  secondary = NebulaGray,
  onSecondary = DeepSpaceBlack,
  secondaryContainer = SpaceSurfaceElevated,
  onSecondaryContainer = StarlightWhite,
  tertiary = CyberBlue,
  onTertiary = Color.White,
  background = DeepSpaceBlack,
  onBackground = StarlightWhite,
  surface = MidnightNavy,
  onSurface = StarlightWhite,
  surfaceVariant = SpaceSurface,
  onSurfaceVariant = NebulaGray,
  surfaceContainer = MidnightNavy,
  surfaceContainerHigh = SpaceSurfaceElevated,
  outline = GlassBorderDark,
  outlineVariant = Color(0xFF21262D),
  error = CrimsonAlert,
  onError = Color.White,
)

private val QusinLightColorScheme = lightColorScheme(
  primary = LabCyanPrimary,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFD6EEFF),
  onPrimaryContainer = Color(0xFF00324D),
  secondary = LabPurpleSecondary,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFEDE9FE),
  onSecondaryContainer = Color(0xFF3B0764),
  tertiary = CyberBlue,
  onTertiary = Color.White,
  background = LabFrostBackground,
  onBackground = LabInkPrimary,
  surface = LabPureSurface,
  onSurface = LabInkPrimary,
  surfaceVariant = LabElevatedSurface,
  onSurfaceVariant = LabInkSecondary,
  surfaceContainer = LabPureSurface,
  surfaceContainerHigh = LabElevatedSurface,
  outline = Color(0xFFCBD5E1),
  outlineVariant = Color(0xFFE2E8F0),
  error = Color(0xFFDC2626),
  onError = Color.White,
)

private val QusinShapes = Shapes(
  extraSmall = RoundedCornerShape(6.dp),
  small = RoundedCornerShape(8.dp),
  medium = RoundedCornerShape(12.dp),
  large = RoundedCornerShape(16.dp),
  extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun QusinTheme(
  darkTheme: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) QusinDarkColorScheme else QusinLightColorScheme
  val cyberPalette = if (darkTheme) DarkCyberPalette else LightCyberPalette

  CompositionLocalProvider(LocalCyberPalette provides cyberPalette) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = QusinTypography,
      shapes = QusinShapes,
      content = content,
    )
  }
}
