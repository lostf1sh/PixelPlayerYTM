package com.lostf1sh.pixelplayerytm.ui.explore

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayerytm.data.repository.YouTubeRepository
import com.lostf1sh.pixelplayerytm.domain.model.MoodsPage
import com.lostf1sh.pixelplayerytm.domain.model.Shelf
import com.lostf1sh.pixelplayerytm.ui.components.ErrorBox
import com.lostf1sh.pixelplayerytm.ui.components.ItemActions
import com.lostf1sh.pixelplayerytm.ui.components.LoadingBox
import com.lostf1sh.pixelplayerytm.ui.components.MoodChip
import com.lostf1sh.pixelplayerytm.ui.components.ShelfSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExploreUiState(
    val shelves: List<Shelf> = emptyList(),
    val moods: MoodsPage? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val repository: YouTubeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = ExploreUiState(isLoading = true)
        viewModelScope.launch {
            val explore = async { repository.explore() }
            val moods = async { repository.moods() }
            val exploreResult = explore.await()
            val moodsResult = moods.await()
            _state.value = ExploreUiState(
                shelves = exploreResult.getOrNull()?.shelves.orEmpty(),
                moods = moodsResult.getOrNull(),
                isLoading = false,
                error = if (exploreResult.isFailure && moodsResult.isFailure) {
                    exploreResult.exceptionOrNull()?.message ?: "Failed to load"
                } else {
                    null
                },
            )
        }
    }
}

@Composable
fun ExploreScreen(
    actions: ItemActions,
    onMoreClick: (browseId: String, params: String?, title: String) -> Unit,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    when {
        state.isLoading -> LoadingBox(Modifier.statusBarsPadding())
        state.error != null -> ErrorBox(
            message = state.error ?: "Something went wrong",
            modifier = Modifier.statusBarsPadding(),
            onRetry = viewModel::load,
        )

        else -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 160.dp),
        ) {
            item(key = "title") {
                Text(
                    text = "Explore",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            itemsIndexed(
                state.shelves,
                key = { index, shelf -> "$index/${shelf.title}" },
            ) { _, shelf ->
                ShelfSection(
                    shelf = shelf,
                    actions = actions,
                    onMoreClick = shelf.moreBrowseId?.let { browseId ->
                        { onMoreClick(browseId, shelf.moreParams, shelf.title) }
                    },
                )
            }
            state.moods?.sections.orEmpty().forEach { section ->
                item(key = "mood-title-${section.title}") {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                item(key = "mood-grid-${section.title}") {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(((section.items.size + 1) / 2 * 48).dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        userScrollEnabled = false,
                    ) {
                        items(section.items, key = { it.id }) { mood ->
                            MoodChip(mood = mood, onClick = { actions.onMood(mood) })
                        }
                    }
                }
            }
        }
    }
}
