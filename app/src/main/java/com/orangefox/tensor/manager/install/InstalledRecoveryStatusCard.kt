package com.orangefox.tensor.manager.install

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orangefox.tensor.manager.ui.ExpressiveLinearProgress
import com.orangefox.tensor.manager.ui.edgeOutline
import com.orangefox.tensor.manager.R
import com.orangefox.tensor.manager.data.InstalledRecoveryType
import com.orangefox.tensor.manager.data.RecoveryStateCache

@Composable
fun InstalledRecoveryStatusCard(
    onManualRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeSlot = RecoveryStateCache.activeSlot
    val isInspecting = RecoveryStateCache.isInspecting
    val hasRoot = RecoveryStateCache.hasRoot
    val slotsMap = RecoveryStateCache.slotInfo

    val initialPage = if (activeSlot.equals("b", ignoreCase = true)) 1 else 0
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 2 })

    LaunchedEffect(activeSlot) {
        val targetPage = if (activeSlot.equals("b", ignoreCase = true)) 1 else 0
        pagerState.animateScrollToPage(targetPage)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "RecInspectSpin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "RecSpinAngle",
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.surfaceContainerLow.edgeOutline(),
                RoundedCornerShape(22.dp),
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.recovery_status_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // Two slot indicator dots (Slot A / Slot B)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(2) { pageIdx ->
                        val selected = pagerState.currentPage == pageIdx
                        val dotWidth by animateDpAsState(
                            targetValue = if (selected) 20.dp else 8.dp,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "SlotDotWidth",
                        )
                        val dotColor by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            label = "SlotDotColor",
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(dotWidth)
                                .clip(CircleShape)
                                .background(dotColor),
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                IconButton(
                    enabled = !isInspecting,
                    onClick = onManualRefresh,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh),
                        contentDescription = stringResource(R.string.action_recheck_recovery),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(if (isInspecting) spinAngle else 0f),
                    )
                }
            }

            if (isInspecting) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExpressiveLinearProgress()
                    Text(
                        text = stringResource(R.string.recovery_inspecting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (hasRoot == false && slotsMap.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.errorContainer.edgeOutline(), RoundedCornerShape(14.dp)),
                ) {
                    Text(
                        text = stringResource(R.string.recovery_no_root_cached_warn),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            } else if (hasRoot == false && slotsMap.isEmpty() && !isInspecting) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest.edgeOutline(), RoundedCornerShape(14.dp)),
                ) {
                    Text(
                        text = stringResource(R.string.recovery_no_root_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            if (slotsMap.isNotEmpty()) {
                HorizontalPager(
                    state = pagerState,
                    pageSpacing = 12.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    val slotKey = if (page == 0) "a" else "b"
                    val info = slotsMap[slotKey]
                    val isCurrentActive = activeSlot.equals(slotKey, ignoreCase = true)

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (isCurrentActive) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh.edgeOutline()
                                },
                                shape = RoundedCornerShape(16.dp),
                            ),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.recovery_slot_label, slotKey.uppercase()),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (isCurrentActive) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primaryContainer.edgeOutline(), RoundedCornerShape(6.dp)),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.recovery_slot_active_badge),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                if (info != null && RecoveryStateCache.matchesLiveSystem(info)) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.tertiaryContainer.edgeOutline(), RoundedCornerShape(6.dp)),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.recovery_running_now),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }

                            if (info != null) {
                                val typeLabel = when (info.type) {
                                    InstalledRecoveryType.MY_OFOX -> stringResource(R.string.recovery_type_my_ofox)
                                    InstalledRecoveryType.OTHER_OFOX -> stringResource(R.string.recovery_type_other_ofox)
                                    InstalledRecoveryType.OTHER_TWRP -> stringResource(R.string.recovery_type_twrp)
                                    InstalledRecoveryType.LOS_BASED -> if (info.romName == "LineageOS") {
                                        stringResource(R.string.recovery_type_los)
                                    } else {
                                        stringResource(R.string.recovery_custom_title, info.romName)
                                    }
                                    InstalledRecoveryType.STOCK -> if (info.romName == "Stock" || info.romName == "AOSP") {
                                        stringResource(R.string.recovery_type_stock)
                                    } else {
                                        stringResource(R.string.recovery_custom_title, info.romName)
                                    }
                                    InstalledRecoveryType.UNKNOWN -> stringResource(R.string.recovery_type_unknown)
                                }
                                Text(
                                    text = typeLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = info.versionName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    autoSize = TextAutoSize.StepBased(
                                        minFontSize = 10.sp,
                                        maxFontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                        stepSize = 1.sp,
                                    ),
                                )
                                Text(
                                    text = stringResource(
                                        R.string.recovery_base_fmt,
                                        when (info.type) {
                                            InstalledRecoveryType.MY_OFOX,
                                            InstalledRecoveryType.OTHER_OFOX,
                                            InstalledRecoveryType.OTHER_TWRP,
                                            -> stringResource(R.string.recovery_base_twrp)
                                            else -> if (info.losBased) {
                                                stringResource(R.string.recovery_base_los)
                                            } else {
                                                stringResource(R.string.recovery_base_aosp)
                                            }
                                        },
                                        info.romName,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = stringResource(R.string.recovery_build_date, info.formattedBuildDate),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    text = "—",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
