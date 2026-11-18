package com.faraon.blockblast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
private fun palette(): List<Color> {
    val c = MaterialTheme.colorScheme
    return remember(c) {
        listOf(c.primary, c.secondary, c.tertiary, c.error, c.inversePrimary, c.primaryContainer, c.tertiaryContainer)
    }
}

private fun DrawScope.block(col: Int, row: Int, cell: Float, color: Color, scale: Float = 1f) {
    val s = cell * 0.92f * scale.coerceAtLeast(0f)
    val off = (cell - s) / 2f
    drawRoundRect(color, Offset(col * cell + off, row * cell + off), Size(s, s), CornerRadius(cell * 0.3f * scale.coerceAtLeast(0f)))
}

@Composable
fun PieceView(piece: Piece, cell: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(cell * piece.w, cell * piece.h)) {
        val px = cell.toPx()
        piece.cells.forEach { (r, c) -> block(c, r, px, color) }
    }
}

@Composable
fun GameScreen(game: GameState) {
    val cs = MaterialTheme.colorScheme
    val colors = palette()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    var boardOrigin by remember { mutableStateOf(Offset.Zero) }
    var boardPx by remember { mutableFloatStateOf(0f) }
    val slotOrigins = remember { mutableStateListOf(Offset.Zero, Offset.Zero, Offset.Zero) }
    var dragIdx by remember { mutableIntStateOf(-1) }
    var finger by remember { mutableStateOf(Offset.Zero) }

    val cell = boardPx / N
    val lift = cell * 1.8f
    val dragPiece = game.pieces.getOrNull(dragIdx)
    val target: Pair<Int, Int>? = if (dragPiece != null && cell > 0f) {
        val c = ((finger.x - dragPiece.w * cell / 2 - boardOrigin.x) / cell).roundToInt()
        val r = ((finger.y - lift - dragPiece.h * cell / 2 - boardOrigin.y) / cell).roundToInt()
        if (game.fits(dragPiece, r, c)) r to c else null
    } else null

    val drop by rememberUpdatedState {
        val t = target
        if (dragIdx >= 0 && t != null && game.place(dragIdx, t.first, t.second)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        dragIdx = -1
    }

    LaunchedEffect(target) {
        if (target != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    LaunchedEffect(game.clearing) {
        if (game.clearing.isNotEmpty()) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(280)
            game.finishClear()
        }
    }

    val clearScale by animateFloatAsState(
        if (game.clearing.isEmpty()) 1f else 0f,
        spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium), label = "clear"
    )
    val shownScore by animateIntAsState(game.score, tween(500), label = "score")

    Box(Modifier.fillMaxSize().background(cs.surface)) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.score), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    Text(shownScore.toString(), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black)
                }
                Surface(shape = RoundedCornerShape(24.dp), color = cs.tertiaryContainer) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.best), style = MaterialTheme.typography.labelMedium, color = cs.onTertiaryContainer)
                        Text(game.best.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = cs.onTertiaryContainer)
                    }
                }
                Spacer(Modifier.width(12.dp))
                FilledTonalIconButton(onClick = { game.reset() }, modifier = Modifier.size(56.dp)) {
                    Text("↻", fontSize = 26.sp)
                }
            }

            Box(Modifier.height(48.dp), contentAlignment = Alignment.CenterStart) {
                AnimatedVisibility(
                    visible = game.combo >= 2,
                    enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn()
                ) {
                    Surface(shape = CircleShape, color = cs.primary) {
                        Text(
                            stringResource(R.string.combo, game.combo),
                            Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.onPrimary
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Surface(
                shape = RoundedCornerShape(40.dp), color = cs.surfaceContainer,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            ) {
                Canvas(
                    Modifier.fillMaxSize().padding(12.dp).onGloballyPositioned {
                        boardOrigin = it.positionInRoot(); boardPx = it.size.width.toFloat()
                    }
                ) {
                    val cp = size.width / N
                    for (i in 0 until N * N) {
                        val v = game.grid[i]
                        val r = i / N
                        val c = i % N
                        block(c, r, cp, cs.surfaceContainerHighest)
                        if (v >= 0) block(c, r, cp, colors[v], if (i in game.clearing) clearScale else 1f)
                    }
                    if (dragPiece != null && target != null) {
                        dragPiece.cells.forEach { (dr, dc) ->
                            block(target.second + dc, target.first + dr, cp, colors[dragPiece.color].copy(alpha = 0.4f))
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (i in 0 until 3) {
                    val piece = game.pieces.getOrNull(i)
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .onGloballyPositioned { slotOrigins[i] = it.positionInRoot() }
                            .pointerInput(i, piece) {
                                detectDragGestures(
                                    onDragStart = { off -> dragIdx = i; finger = slotOrigins[i] + off },
                                    onDrag = { change, amount -> change.consume(); finger += amount },
                                    onDragEnd = { drop() },
                                    onDragCancel = { dragIdx = -1 }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (piece != null && dragIdx != i) PieceView(piece, 24.dp, colors[piece.color])
                    }
                }
            }
        }

        if (dragPiece != null && cell > 0f) {
            Box(
                Modifier.offset {
                    IntOffset(
                        (finger.x - dragPiece.w * cell / 2).roundToInt(),
                        (finger.y - lift - dragPiece.h * cell / 2).roundToInt()
                    )
                }
            ) {
                PieceView(dragPiece, with(density) { cell.toDp() }, colors[dragPiece.color])
            }
        }

        if (game.over) {
            AlertDialog(
                onDismissRequest = {},
                shape = RoundedCornerShape(36.dp),
                title = { Text(stringResource(R.string.game_over)) },
                text = { Text(stringResource(R.string.final_score, game.score), style = MaterialTheme.typography.titleLarge) },
                confirmButton = { Button(onClick = { game.reset() }) { Text(stringResource(R.string.new_game)) } }
            )
        }
    }
}
