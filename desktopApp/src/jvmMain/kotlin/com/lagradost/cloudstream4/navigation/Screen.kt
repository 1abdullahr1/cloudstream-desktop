package com.lagradost.cloudstream4.navigation

import com.lagradost.cloudstream3.SubtitleFile

sealed class Screen {
    data object Home : Screen()
    data class Search(val initialQuery: String = "") : Screen()
    data object Library : Screen()
    data object Downloads : Screen()
    data object Extensions : Screen()
    data object Settings : Screen()
    data class Details(val url: String, val apiName: String) : Screen()
    data class Player(
        val mediaUrl: String,
        val title: String,
        val episodeTitle: String? = null,
        val apiName: String = "",
        val parentUrl: String = "",
        val episodeData: String? = null,
        val headers: Map<String, String> = emptyMap(),
        val subtitles: List<SubtitleFile> = emptyList()
    ) : Screen()
}
