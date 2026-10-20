package com.rk.ai.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rk.ai.api.AiExtensions
import com.rk.ai.provider.AiModel
import com.rk.ai.provider.AiModelCatalog
import com.rk.ai.provider.AiProvider
import com.rk.ai.provider.AiProviderRegistry
import com.rk.ai.provider.AiProviderRuntime
import com.rk.ai.provider.CustomAiProvider
import com.rk.ai.provider.CustomAiProviders
import com.rk.ai.provider.CustomProviderSpec
import com.rk.ai.provider.FetchedModels
import com.rk.ai.provider.OpenAiCompatibleProvider
import com.rk.ai.provider.OpenAiEndpoint
import com.rk.ai.tools.AiTool
import com.rk.components.NextScreenCard
import com.rk.components.SettingsItem
import com.rk.components.SingleInputDialog
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.utils.toast
import kotlinx.coroutines.launch

@Composable
fun AiSettingsScreen(modifier: Modifier = Modifier, onOpenMemory: () -> Unit = {}) {
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    var providerMenuOpen by remember { mutableStateOf(false) }
    var customDialog by remember { mutableStateOf<CustomProviderSpec?>(null) }
    var deleting by remember { mutableStateOf<CustomAiProvider?>(null) }
    var pickingModel by remember { mutableStateOf(false) }
    var fetchingModels by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val providers by AiExtensions.providers.collectAsState()
    val tools by AiExtensions.tools.collectAsState()
    val provider = providers.firstOrNull { it.id == AiSettings.providerId } ?: providers.firstOrNull()
    val models = provider?.let { AiModelCatalog.modelsFor(it.id) }.orEmpty()
    val apiKey = provider?.let { AiSettings.apiKey(it.id) }.orEmpty()

    PreferenceLayout(label = stringResource(strings.ai_feature_label), modifier = modifier) {
        PreferenceGroup(heading = stringResource(strings.ai_provider)) {
            Box {
                SettingsItem(
                    label = stringResource(strings.ai_provider),
                    description = provider?.displayName,
                    singleLineDescription = true,
                    showSwitch = false,
                    startWidget = { provider?.let { ProviderIcon(it) } },
                    endWidget = {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    },
                    sideEffect = { providerMenuOpen = true },
                )

                DropdownMenu(
                    expanded = providerMenuOpen,
                    onDismissRequest = { providerMenuOpen = false },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    providers.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(candidate.displayName) },
                            leadingIcon = { ProviderIcon(candidate) },
                            trailingIcon = {
                                if (candidate.id == provider?.id) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            },
                            modifier =
                                Modifier.padding(horizontal = 6.dp, vertical = 2.dp).clip(RoundedCornerShape(12.dp)),
                            onClick = {
                                selectProvider(candidate)
                                providerMenuOpen = false
                            },
                        )
                    }
                }
            }

            if (provider is CustomAiProvider) {
                SettingsItem(
                    label = stringResource(strings.ai_edit_custom_provider),
                    description = provider.apiRoot,
                    singleLineDescription = true,
                    showSwitch = false,
                    sideEffect = { customDialog = provider.spec },
                )
                SettingsItem(
                    label = stringResource(strings.ai_delete_custom_provider),
                    showSwitch = false,
                    sideEffect = { deleting = provider },
                )
            }

            SettingsItem(
                label = stringResource(strings.ai_add_custom_provider),
                description = stringResource(strings.ai_add_custom_provider_desc),
                showSwitch = false,
                startWidget = {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 16.dp).size(24.dp),
                    )
                },
                sideEffect = { customDialog = CustomProviderSpec(CustomAiProviders.newId(), "", "", emptyList()) },
            )
        }

        PreferenceGroup(heading = stringResource(strings.ai_connection)) {
            if (provider is OpenAiCompatibleProvider && provider !is CustomAiProvider) {
                SettingsItem(
                    label = stringResource(strings.ai_endpoint),
                    description = provider.apiRoot,
                    singleLineDescription = true,
                    showSwitch = false,
                )
            }

            if (provider != null) {
                SettingsItem(
                    label =
                        stringResource(
                            if (provider.requiresApiKey) strings.ai_api_key else strings.ai_api_key_optional
                        ),
                    description = if (apiKey.isBlank()) stringResource(strings.ai_not_set) else maskKey(apiKey),
                    showSwitch = false,
                    sideEffect = { editing = EditTarget.ApiKey },
                )
            }

            if (provider is OpenAiCompatibleProvider) {
                val fetchedCount = FetchedModels.count(provider.id)
                SettingsItem(
                    label = stringResource(strings.ai_fetch_models),
                    description =
                        when {
                            fetchingModels -> stringResource(strings.ai_fetching_models)
                            fetchedCount > 0 -> stringResource(strings.ai_fetch_models_count, fetchedCount)
                            else -> stringResource(strings.ai_fetch_models_desc)
                        },
                    showSwitch = false,
                    isEnabled = !fetchingModels,
                    sideEffect = {
                        fetchingModels = true
                        scope.launch {
                            FetchedModels.refresh(provider, AiSettings.apiKey(provider.id))
                                .onSuccess { toast(strings.ai_fetch_models_success.getString().format(it)) }
                                .onFailure {
                                    toast(
                                        strings.ai_fetch_models_failed.getString().format(it.message ?: it.toString())
                                    )
                                }
                            fetchingModels = false
                        }
                    },
                )
            }

            SettingsItem(
                label = stringResource(strings.ai_model),
                description = AiSettings.modelId.ifBlank { stringResource(strings.ai_not_set) },
                singleLineDescription = true,
                showSwitch = false,
                sideEffect = { pickingModel = true },
            )
        }

        PreferenceGroup(heading = stringResource(strings.ai_agent)) {
            SettingsItem(
                label = stringResource(strings.ai_system_prompt),
                description = AiSettings.systemPrompt,
                singleLineDescription = true,
                showSwitch = false,
                sideEffect = { editing = EditTarget.SystemPrompt },
            )
        }

        PreferenceGroup(heading = stringResource(strings.ai_memory)) {
            val count = AiMemory.entries.size
            NextScreenCard(
                label = stringResource(strings.ai_long_term_memory),
                description =
                    when (count) {
                        0 -> stringResource(strings.ai_memory_empty)
                        1 -> stringResource(strings.ai_memory_one_note)
                        else -> stringResource(strings.ai_memory_notes, count)
                    },
                onClick = { onOpenMemory() },
            )
        }

        PreferenceGroup(heading = stringResource(strings.ai_tool_permissions)) {
            PermissionMode.entries.forEach { mode ->
                ChoiceRow(
                    title = mode.title(),
                    selected = AiSettings.currentPermissionMode() == mode,
                    onSelect = { AiSettings.permissionMode = mode.name },
                )
            }
        }

        PreferenceGroup(heading = stringResource(strings.ai_tools_count, tools.size)) {
            tools.forEach { tool -> ToolRow(tool) }
        }
    }

    when (editing) {
        EditTarget.ApiKey -> {
            var keyText by remember { mutableStateOf(apiKey) }
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text(stringResource(strings.ai_api_key)) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = keyText,
                            onValueChange = { keyText = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            provider?.let { AiSettings.setApiKey(it.id, keyText.trim()) }
                            editing = null
                        }
                    ) {
                        Text(stringResource(strings.apply))
                    }
                },
                dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(strings.cancel)) } },
            )
        }

        EditTarget.SystemPrompt -> {
            var promptText by remember { mutableStateOf(AiSettings.systemPrompt) }
            SingleInputDialog(
                title = stringResource(strings.ai_system_prompt),
                inputLabel = stringResource(strings.ai_system_prompt),
                inputValue = promptText,
                onInputValueChange = { promptText = it },
                onConfirm = { AiSettings.systemPrompt = promptText.trim() },
                onDismiss = { editing = null },
                singleLineMode = false,
                message = {
                    Text(
                        text = stringResource(strings.ai_system_prompt_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }

        null -> Unit
    }

    customDialog?.let { spec ->
        CustomProviderDialog(
            initial = spec,
            initialApiKey = AiSettings.apiKey(spec.id),
            onDismiss = { customDialog = null },
            onSave = { saved, key ->
                val isNew = CustomAiProviders.specs.none { it.id == saved.id }
                CustomAiProviders.save(saved)
                AiSettings.setApiKey(saved.id, key)
                if (isNew) AiProviderRegistry.find(saved.id)?.let(::selectProvider)
                customDialog = null
            },
        )
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(strings.ai_delete_custom_provider)) },
            text = { Text(stringResource(strings.ai_delete_custom_provider_confirm, target.displayName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        CustomAiProviders.remove(target.id)
                        AiProviderRegistry.providers.value.firstOrNull()?.let(::selectProvider)
                        deleting = null
                    }
                ) {
                    Text(stringResource(strings.delete))
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(strings.cancel)) } },
        )
    }

    if (pickingModel) {
        ModelPickerDialog(
            models = models,
            selected = AiSettings.modelId,
            onDismiss = { pickingModel = false },
            onSelect = { modelId ->
                AiSettings.modelId = modelId
                AiProviderRuntime.invalidate()
                pickingModel = false
            },
        )
    }
}

private fun selectProvider(provider: AiProvider) {
    AiSettings.providerId = provider.id
    AiSettings.modelId = AiModelCatalog.defaultFor(provider)?.id.orEmpty()
    AiProviderRuntime.invalidate()
}

@Composable
private fun CustomProviderDialog(
    initial: CustomProviderSpec,
    initialApiKey: String,
    onDismiss: () -> Unit,
    onSave: (CustomProviderSpec, String) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var apiRoot by remember { mutableStateOf(initial.apiRoot) }
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var modelsText by remember { mutableStateOf(initial.models.joinToString("\n")) }
    val urlValid = OpenAiEndpoint.parse(apiRoot) != null && apiRoot.trim().startsWith("http")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial.name.isBlank()) strings.ai_add_custom_provider else strings.ai_edit_custom_provider
                )
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(strings.ai_provider_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiRoot,
                    onValueChange = { apiRoot = it },
                    label = { Text(stringResource(strings.ai_base_url)) },
                    placeholder = { Text(stringResource(strings.ai_base_url_hint)) },
                    isError = apiRoot.isNotBlank() && !urlValid,
                    supportingText =
                        if (apiRoot.isNotBlank() && !urlValid) {
                            { Text(stringResource(strings.ai_base_url_invalid)) }
                        } else {
                            null
                        },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(strings.ai_api_key_optional)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = modelsText,
                    onValueChange = { modelsText = it },
                    label = { Text(stringResource(strings.ai_models_list)) },
                    supportingText = { Text(stringResource(strings.ai_models_list_hint)) },
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && urlValid,
                onClick = {
                    val models = modelsText.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                    onSave(initial.copy(name = name.trim(), apiRoot = apiRoot.trim(), models = models), apiKey.trim())
                },
            ) {
                Text(stringResource(strings.apply))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(strings.cancel)) } },
    )
}

/** A searchable model list; providers like OpenRouter expose hundreds of models. */
@Composable
private fun ModelPickerDialog(
    models: List<AiModel>,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var choice by remember { mutableStateOf(selected) }
    var customId by remember { mutableStateOf(if (models.any { it.id == selected }) "" else selected) }
    val filtered =
        remember(query, models) {
            val q = query.trim()
            if (q.isEmpty()) models
            else models.filter { it.id.contains(q, ignoreCase = true) || it.displayName.contains(q, ignoreCase = true) }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(strings.ai_model)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (models.size > 8) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(strings.ai_search_models)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (filtered.isEmpty() && models.isNotEmpty()) {
                    Text(
                        text = stringResource(strings.ai_no_models_match),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(max = 360.dp)) {
                    items(filtered, key = { it.id }) { model ->
                        PreferenceTemplate(
                            modifier =
                                Modifier.clip(MaterialTheme.shapes.large).clickable {
                                    choice = model.id
                                    customId = ""
                                },
                            title = { Text(text = model.displayName) },
                            description = { if (model.displayName != model.id) Text(text = model.id) },
                            startWidget = {
                                RadioButton(selected = choice == model.id && customId.isBlank(), onClick = null)
                            },
                            verticalPadding = 8.dp,
                        )
                    }
                }

                OutlinedTextField(
                    value = customId,
                    onValueChange = { customId = it },
                    label = { Text(stringResource(strings.ai_custom_model_id)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            val result = customId.trim().ifEmpty { choice }
            TextButton(enabled = result.isNotBlank(), onClick = { onSelect(result) }) {
                Text(stringResource(strings.apply))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(strings.cancel)) } },
    )
}

@Composable
private fun ProviderIcon(provider: AiProvider) {
    Icon(
        imageVector = provider.icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp).size(24.dp),
    )
}

@Composable
private fun ChoiceRow(title: String, selected: Boolean, onSelect: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    PreferenceTemplate(
        modifier = Modifier.clickable(indication = ripple(), interactionSource = interactionSource) { onSelect() },
        title = { Text(fontWeight = FontWeight.Bold, text = title) },
        enabled = true,
        applyPaddings = false,
        startWidget = { RadioButton(selected = selected, onClick = onSelect) },
    )
}

@Composable
private fun ToolRow(tool: AiTool) {
    SettingsItem(
        label = tool.name,
        description =
            stringResource(
                strings.ai_tool_row_description,
                tool.kind,
                if (tool.isDestructive) {
                    stringResource(strings.ai_tool_asks_first)
                } else {
                    stringResource(strings.ai_tool_runs_freely)
                },
            ),
        singleLineDescription = true,
        state = remember(tool.name) { mutableStateOf(AiSettings.isToolEnabled(tool.name)) },
        sideEffect = { AiSettings.setToolEnabled(tool.name, it) },
    )
}

private enum class EditTarget {
    ApiKey,
    SystemPrompt,
}

private fun maskKey(key: String): String =
    if (key.length <= 4) {
        "•".repeat(key.length.coerceAtLeast(4))
    } else {
        "•".repeat(10) + key.takeLast(4)
    }

private fun PermissionMode.title(): String =
    when (this) {
        PermissionMode.ASK -> strings.ai_mode_ask.getString()
        PermissionMode.ACCEPT_EDITS -> strings.ai_mode_accept_edits.getString()
        PermissionMode.PLAN -> strings.ai_mode_plan.getString()
        PermissionMode.YOLO -> strings.ai_mode_autonomous.getString()
    }
