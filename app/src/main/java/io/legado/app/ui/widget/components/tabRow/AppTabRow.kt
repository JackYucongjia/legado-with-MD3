package io.legado.app.ui.widget.components.tabRow

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.text.hiddenAttributeUnderline
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour

@Composable
fun AppTabRow(
    tabTitles: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isScrollable: Boolean = true,
    hiddenTabIndices: Set<Int> = emptySet()
) {
    val composeEngine = LegadoTheme.composeEngine

    if (ThemeResolver.isMiuixEngine(composeEngine) && hiddenTabIndices.isEmpty()) {
        TabRowWithContour(
            tabs = tabTitles,
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            modifier = modifier
                .padding(vertical = 4.dp),
            colors = TabRowDefaults.tabRowColors(
                backgroundColor = Color.Transparent
            )
        )
    } else {
        if (isScrollable) {
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 0.dp,
                divider = { },
                containerColor = Color.Transparent,
                minTabWidth = 0.dp,
                modifier = modifier
            ) {
                tabTitles.forEachIndexed { index, title ->
                    AppTab(
                        selected = selectedTabIndex == index,
                        onClick = { onTabSelected(index) },
                        title = title,
                        hidden = index in hiddenTabIndices
                    )
                }
            }
        } else {
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                divider = { },
                containerColor = Color.Transparent,
                modifier = modifier
            ) {
                tabTitles.forEachIndexed { index, title ->
                    AppTab(
                        selected = selectedTabIndex == index,
                        onClick = { onTabSelected(index) },
                        title = title,
                        hidden = index in hiddenTabIndices
                    )
                }
            }
        }
    }
}

@Composable
private fun AppTab(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    hidden: Boolean
) {
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            AppText(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = LegadoTheme.typography.labelLargeEmphasized,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .hiddenAttributeUnderline(
                        hidden,
                        if (selected) LegadoTheme.colorScheme.primary
                        else LegadoTheme.colorScheme.onSurfaceVariant
                    ),
                color = if (selected) LegadoTheme.colorScheme.primary else LegadoTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}
