package com.tanvoid0.portallauncher.ui.assistant

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanvoid0.portallauncher.PortalLauncherApplication
import com.tanvoid0.portallauncher.R
import com.tanvoid0.portallauncher.ai.agent.LauncherAgent
import com.tanvoid0.portallauncher.ai.agent.LauncherTool
import com.tanvoid0.portallauncher.ai.agent.LauncherToolCall
import com.tanvoid0.portallauncher.ai.agent.launcherTools
import com.tanvoid0.portallauncher.ui.kit.EmptyState
import com.tanvoid0.portallauncher.ui.kit.Spacing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One line of the conversation shown in the sheet. */
data class AiChatTurn(val isUser: Boolean, val text: String)

/** A [LauncherTool.mutates] call waiting on the user's tap in the confirm dialog. */
data class PendingConfirmation(
    val tool: LauncherTool,
    val call: LauncherToolCall,
    val approved: CompletableDeferred<Boolean>
)

/**
 * Owns one chat run at a time over [LauncherAgent].
 *
 * [PendingConfirmation.approved] is how the confirm dialog answers back into the
 * suspended `confirm` callback the agent loop is waiting on -- the loop does not
 * resume until the user taps Allow or Cancel.
 */
class AiAssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PortalLauncherApplication
    private val agent = LauncherAgent(
        client = { prompt -> app.aiCategorizer.generate(prompt) },
        tools = launcherTools(app),
        appDescription = "It can switch profiles and change display, Do Not Disturb and " +
            "schedule settings."
    )

    private val _turns = MutableStateFlow<List<AiChatTurn>>(emptyList())
    val turns: StateFlow<List<AiChatTurn>> = _turns.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || _running.value) return
        _turns.update { it + AiChatTurn(isUser = true, text = prompt) }
        _running.value = true
        viewModelScope.launch {
            val result = agent.run(
                prompt = prompt,
                confirm = { tool, call ->
                    val approved = CompletableDeferred<Boolean>()
                    _pendingConfirmation.value = PendingConfirmation(tool, call, approved)
                    approved.await()
                }
            )
            _turns.update { it + AiChatTurn(isUser = false, text = result.message) }
            _running.value = false
        }
    }

    fun respondToConfirmation(approve: Boolean) {
        _pendingConfirmation.value?.approved?.complete(approve)
        _pendingConfirmation.value = null
    }
}

/**
 * The assistant's chat sheet, opened from the Settings row -- see [SettingsScreen].
 *
 * Same [ModalBottomSheet] shape [AppDrawerSheet] and the home-widget picker already
 * use in this app. The confirm dialog before a mutating tool runs follows the phase-F
 * restore-confirmation pattern in `SettingsScreen` -- an [AlertDialog] gating one
 * action, declining feeds a refusal back to the model rather than closing the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantSheet(
    onDismiss: () -> Unit,
    viewModel: AiAssistantViewModel = viewModel()
) {
    val sheetState = rememberModalBottomSheetState()
    val turns by viewModel.turns.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val pending by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(turns.size, running) {
        val lastIndex = turns.size - 1 + if (running) 1 else 0
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    pending?.let { confirmation ->
        AlertDialog(
            onDismissRequest = { viewModel.respondToConfirmation(false) },
            title = { Text(stringResource(R.string.assistant_confirm_title)) },
            text = {
                Column {
                    Text(confirmation.tool.description)
                    Text(
                        text = confirmation.call.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.sm)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.respondToConfirmation(true) }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.respondToConfirmation(false) }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(ASSISTANT_SHEET_HEIGHT_FRACTION)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding()
        ) {
            Text(
                text = stringResource(R.string.ai_assistant_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(
                    horizontal = Spacing.gutter,
                    vertical = Spacing.md
                )
            )
            if (turns.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.assistant_empty_title),
                    body = stringResource(R.string.assistant_empty_body),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(turns) { turn -> ChatBubble(turn) }
                    if (running) item { TypingIndicator() }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.gutter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.assistant_input_hint)) },
                    singleLine = true,
                    enabled = !running,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { viewModel.send(input); input = "" })
                )
                IconButton(
                    onClick = { viewModel.send(input); input = "" },
                    enabled = !running && input.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send))
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(turn: AiChatTurn) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (turn.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = if (turn.isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ) {
            Text(
                text = turn.text,
                color = if (turn.isUser) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        CircularProgressIndicator(modifier = Modifier.size(TYPING_INDICATOR_SIZE))
    }
}

private const val ASSISTANT_SHEET_HEIGHT_FRACTION = 0.85f
private val TYPING_INDICATOR_SIZE = 20.dp
