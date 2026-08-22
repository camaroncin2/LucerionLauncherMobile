package com.lucerion.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ═════════════════════════════════════════════════════════════════════════════
// Sistema de marca "Fusión" — paleta oficial del brief de Create Cretania.
// Regla 60/30/10: 60 % fondos navy, 30 % dorado (marcos, títulos, CTA),
// 10 % pops azul/naranja Create para energía.
// ═════════════════════════════════════════════════════════════════════════════

// Base (fondos y estructura — dominante)
val Bg = Color(0xFF060B16)        // azul-negro profundo (fondo principal)
val Bg2 = Color(0xFF0C1424)       // navy oscuro (paneles)
val Bg3 = Color(0xFF111C31)       // navy medio (tarjetas)
val Texto = Color(0xFFE8EDF7)     // casi blanco
val TextoSuave = Color(0xFFC8D0E0)

// Dorado (acento principal)
val Oro = Color(0xFFD5A84B)
val OroClaro = Color(0xFFE8C06A)
val OroProfundo = Color(0xFFB8902F)
val Ambar = Color(0xFFFFB51D)     // highlights / botones
val AmbarCta1 = Color(0xFFFFC646) // gradiente CTA (arriba)
val AmbarCta2 = Color(0xFFFFAD1D) // gradiente CTA (abajo)

// Create Cretania (pops de energía)
val CreateAzul = Color(0xFF2E9BD6)
val CreateNaranja = Color(0xFFE8871C)

// Verde de confirmación: sólo para "esto ya está" (etiquetas de estado).
val VerdeListo = Color(0xFF57C878)

private val EsquemaLucerion = darkColorScheme(
    primary = Oro,
    onPrimary = Bg,
    primaryContainer = Bg3,
    onPrimaryContainer = OroClaro,
    secondary = CreateAzul,
    onSecondary = Bg,
    tertiary = CreateNaranja,
    background = Bg,
    onBackground = Texto,
    surface = Bg2,
    onSurface = Texto,
    surfaceVariant = Bg3,
    onSurfaceVariant = TextoSuave,
    outline = OroProfundo,
    error = Color(0xFFFF6B6B),
)

/**
 * Tema único del launcher: steampunk oscuro con acentos dorados.
 * No hay variante clara a propósito — la identidad de Cretania es nocturna.
 */
@Composable
fun LucerionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EsquemaLucerion,
        typography = TipografiaLucerion,
        content = content,
    )
}
