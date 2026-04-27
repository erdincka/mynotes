package com.mynotes.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun NavGraph(
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "folders",
        modifier = modifier
    ) {
        composable("folders") {
            FolderListScreen(
                onNoteClick = { noteId ->
                    navController.navigate("note/$noteId")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                }
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = "note/{noteId}",
            arguments = listOf(
                navArgument("noteId") {
                    type = NavType.LongType
                  }
               )
           ) { backStackEntry ->
               val noteId = backStackEntry.arguments?.getLong("noteId") ?: return@composable
               NoteScreen(
                   noteId = noteId,
                   onBack = { navController.popBackStack() }
                )
            }
       }
}
