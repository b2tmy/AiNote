package com.ainote.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ainote.app.feature.ai.AiChatScreen
import com.ainote.app.feature.calendar.CalendarScreen
import com.ainote.app.feature.memo.MemoScreen
import com.ainote.app.feature.note.NoteEditScreen
import com.ainote.app.feature.note.NoteListScreen
import com.ainote.app.feature.screenshot.ScreenshotDetailScreen
import com.ainote.app.feature.screenshot.ScreenshotListScreen
import com.ainote.app.feature.settings.SettingsScreen

enum class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    Screenshot("screenshot", "截图", Icons.AutoMirrored.Filled.List),
    Note("note", "笔记", Icons.Filled.Create),
    Memo("memo", "备忘", Icons.Filled.CheckCircle),
    Calendar("calendar", "日历", Icons.Filled.DateRange),
    Ai("ai", "AI", Icons.Filled.Star)
}

object Routes {
    const val NOTE_EDIT = "note_edit/{noteId}"
    const val SCREENSHOT_DETAIL = "screenshot_detail/{recordId}"
    const val SETTINGS = "settings"

    fun noteEdit(noteId: Long) = "note_edit/$noteId"
    fun screenshotDetail(recordId: Long) = "screenshot_detail/$recordId"
}

@Composable
fun AiNoteApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val topRoutes = TopDestination.entries.map { it.route }
    val showBottomBar = currentDestination?.route in topRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopDestination.entries.forEach { dest ->
                        val selected =
                            currentDestination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopDestination.Screenshot.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(TopDestination.Screenshot.route) {
                ScreenshotListScreen(
                    onOpenDetail = { recordId ->
                        navController.navigate(Routes.screenshotDetail(recordId))
                    }
                )
            }

            composable(TopDestination.Note.route) {
                NoteListScreen(
                    onOpenNote = { noteId -> navController.navigate(Routes.noteEdit(noteId)) }
                )
            }

            composable(TopDestination.Memo.route) { MemoScreen() }

            composable(TopDestination.Calendar.route) { CalendarScreen() }

            composable(TopDestination.Ai.route) {
                AiChatScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
            }

            composable(
                route = Routes.NOTE_EDIT,
                arguments = listOf(navArgument("noteId") { type = NavType.LongType })
            ) {
                NoteEditScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.SCREENSHOT_DETAIL,
                arguments = listOf(navArgument("recordId") { type = NavType.LongType })
            ) {
                ScreenshotDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenNote = { noteId -> navController.navigate(Routes.noteEdit(noteId)) }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenNote = { noteId -> navController.navigate(Routes.noteEdit(noteId)) }
                )
            }
        }
    }
}
