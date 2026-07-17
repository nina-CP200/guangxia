package com.guangxia.filmtools.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guangxia.filmtools.data.CameraEntity
import com.guangxia.filmtools.data.CameraWithRolls
import com.guangxia.filmtools.data.FilmRollEntity
import com.guangxia.filmtools.ui.MainViewModel
import com.guangxia.filmtools.ui.components.InstrumentPanel
import com.guangxia.filmtools.ui.components.ScreenHeader
import com.guangxia.filmtools.ui.theme.Carbon
import com.guangxia.filmtools.ui.theme.Danger
import com.guangxia.filmtools.ui.theme.LocalToolAccent
import com.guangxia.filmtools.ui.theme.Muted
import com.guangxia.filmtools.ui.theme.Panel
import com.guangxia.filmtools.ui.theme.PanelRaised
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

@Composable
fun FilmScreen(cameras: List<CameraWithRolls>, viewModel: MainViewModel) {
    val accent = LocalToolAccent.current
    var showNewCamera by rememberSaveable { mutableStateOf(false) }
    var editingCamera by remember { mutableStateOf<CameraEntity?>(null) }
    var deletingCamera by remember { mutableStateOf<CameraEntity?>(null) }
    var loadingFor by remember { mutableStateOf<CameraWithRolls?>(null) }
    var editingFilm by remember { mutableStateOf<FilmRollEntity?>(null) }
    var unloadingFilm by remember { mutableStateOf<FilmRollEntity?>(null) }
    var expandedCameraId by rememberSaveable { mutableLongStateOf(-1L) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                ScreenHeader("胶卷", "相机与在机胶卷") {
                    IconButton(
                        onClick = { showNewCamera = true },
                        modifier = Modifier.background(accent, CircleShape),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "添加相机", tint = Carbon)
                    }
                }
            }
            if (cameras.isEmpty()) {
                item {
                    InstrumentPanel(Modifier.padding(horizontal = 16.dp)) {
                        Text("还没有相机", style = MaterialTheme.typography.headlineMedium)
                        Text("先建立一张相机卡片，再记录装入的胶卷。", color = Muted, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            } else {
                items(cameras, key = { it.camera.id }) { item ->
                    CameraCard(
                        item = item,
                        expanded = expandedCameraId == item.camera.id,
                        onToggleHistory = { expandedCameraId = if (expandedCameraId == item.camera.id) -1 else item.camera.id },
                        onLoad = { loadingFor = item },
                        onUnload = { unloadingFilm = item.currentRoll },
                        onEditCamera = { editingCamera = item.camera },
                        onDeleteCamera = { deletingCamera = item.camera },
                        onEditFilm = { editingFilm = it },
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    if (showNewCamera) CameraDialog(null, onDismiss = { showNewCamera = false }) { name, model ->
        viewModel.addCamera(name, model); showNewCamera = false
    }
    editingCamera?.let { camera ->
        CameraDialog(camera, onDismiss = { editingCamera = null }) { name, model ->
            viewModel.updateCamera(camera.copy(name = name, model = model)); editingCamera = null
        }
    }
    deletingCamera?.let { camera ->
        AlertDialog(
            onDismissRequest = { deletingCamera = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = Panel,
            tonalElevation = 0.dp,
            title = { Text("删除 ${camera.name}？") },
            text = { Text("这会同时删除该相机的当前胶卷与全部历史记录，无法撤销。") },
            confirmButton = { TextButton(onClick = { viewModel.deleteCamera(camera); deletingCamera = null }, colors = ButtonDefaults.textButtonColors(contentColor = Danger)) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deletingCamera = null }) { Text("取消") } },
        )
    }
    loadingFor?.let { target ->
        FilmDialog(target.camera.id, null, onDismiss = { loadingFor = null }) { film ->
            viewModel.loadFilm(film) { scope.launch { snackbar.showSnackbar(it) } }
            loadingFor = null
        }
    }
    editingFilm?.let { film ->
        FilmDialog(film.cameraId, film, onDismiss = { editingFilm = null }) { updated ->
            viewModel.updateFilm(updated); editingFilm = null
        }
    }
    unloadingFilm?.let { film ->
        UnloadDialog(film, onDismiss = { unloadingFilm = null }) { epochDay ->
            viewModel.unloadFilm(film, epochDay); unloadingFilm = null
        }
    }
}

@Composable
private fun CameraCard(
    item: CameraWithRolls,
    expanded: Boolean,
    onToggleHistory: () -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onEditCamera: () -> Unit,
    onDeleteCamera: () -> Unit,
    onEditFilm: (FilmRollEntity) -> Unit,
) {
    val accent = LocalToolAccent.current
    val current = item.currentRoll
    InstrumentPanel(Modifier.padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(item.camera.name, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.camera.model.isNotBlank()) Text(item.camera.model, color = Muted)
            }
            IconButton(onClick = onEditCamera) { Icon(Icons.Rounded.Edit, contentDescription = "编辑相机", tint = Muted) }
        }
        Spacer(Modifier.height(14.dp))
        if (current == null) {
            Box(Modifier.fillMaxWidth().background(PanelRaised, RoundedCornerShape(14.dp)).padding(16.dp)) {
                Column {
                    Text("空仓", color = Muted, style = MaterialTheme.typography.labelLarge)
                    Text("当前未安装胶卷", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Button(
                onClick = onLoad,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Carbon),
            ) { Text("装入胶卷") }
        } else {
            Box(Modifier.fillMaxWidth().background(PanelRaised, RoundedCornerShape(14.dp)).padding(16.dp)) {
                Column {
                    Text("在机 · ISO ${current.iso}", color = accent, style = MaterialTheme.typography.labelLarge)
                    Text(current.filmType, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 4.dp))
                    Text("装卷 ${formatDate(current.loadedEpochDay)} · 已 ${daysLoaded(current.loadedEpochDay)} 天", color = Muted)
                    if (current.notes.isNotBlank()) Text(current.notes, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onEditFilm(current) }, modifier = Modifier.weight(1f)) { Text("编辑胶卷") }
                Button(onClick = onUnload, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Carbon)) { Text("标记卸卷") }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onToggleHistory) {
                Text("历史 ${item.rolls.count { it.unloadedEpochDay != null }} 卷")
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
            }
            TextButton(onClick = onDeleteCamera, colors = ButtonDefaults.textButtonColors(contentColor = Danger)) { Text("删除相机") }
        }
        AnimatedVisibility(expanded) {
            Column {
                HorizontalDivider(color = PanelRaised)
                val history = item.history.filter { it.unloadedEpochDay != null }
                if (history.isEmpty()) Text("暂无已卸胶卷", color = Muted, modifier = Modifier.padding(vertical = 14.dp))
                history.forEach { film ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${film.filmType} · ISO ${film.iso}", style = MaterialTheme.typography.titleMedium)
                            Text("${formatDate(film.loadedEpochDay)} — ${film.unloadedEpochDay?.let(::formatDate)}", color = Muted)
                        }
                        IconButton(onClick = { onEditFilm(film) }) { Icon(Icons.Rounded.Edit, contentDescription = "编辑胶卷记录", tint = accent) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraDialog(existing: CameraEntity?, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var model by rememberSaveable(existing?.id) { mutableStateOf(existing?.model ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Panel,
        tonalElevation = 0.dp,
        title = { Text(if (existing == null) "添加相机" else "编辑相机") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(30) }, label = { Text("卡片名称 *") }, singleLine = true, shape = RoundedCornerShape(12.dp), colors = iosFieldColors())
                OutlinedTextField(model, { model = it.take(40) }, label = { Text("品牌 / 型号") }, singleLine = true, shape = RoundedCornerShape(12.dp), colors = iosFieldColors())
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim(), model.trim()) }, enabled = name.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun FilmDialog(cameraId: Long, existing: FilmRollEntity?, onDismiss: () -> Unit, onConfirm: (FilmRollEntity) -> Unit) {
    var filmType by rememberSaveable(existing?.id) { mutableStateOf(existing?.filmType ?: "") }
    var iso by rememberSaveable(existing?.id) { mutableStateOf(existing?.iso?.toString() ?: "400") }
    var loadedDate by rememberSaveable(existing?.id) { mutableStateOf(existing?.loadedEpochDay?.let(::formatIsoDate) ?: LocalDate.now().toString()) }
    var unloadedDate by rememberSaveable(existing?.id) { mutableStateOf(existing?.unloadedEpochDay?.let(::formatIsoDate) ?: "") }
    var notes by rememberSaveable(existing?.id) { mutableStateOf(existing?.notes ?: "") }
    val epochDay = parseEpochDay(loadedDate)
    val unloadedEpochDay = if (existing?.unloadedEpochDay == null) null else parseEpochDay(unloadedDate)
    val isoValue = iso.toIntOrNull()
    val unloadValid = existing?.unloadedEpochDay == null || (unloadedEpochDay != null && epochDay != null && unloadedEpochDay >= epochDay)
    val valid = filmType.isNotBlank() && isoValue != null && isoValue in 1..25600 && epochDay != null && unloadValid
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Panel,
        tonalElevation = 0.dp,
        title = { Text(if (existing == null) "装入胶卷" else "编辑胶卷") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(filmType, { filmType = it.take(50) }, label = { Text("胶卷类型 *") }, placeholder = { Text("例如 Kodak Gold 200") }, singleLine = true, shape = RoundedCornerShape(12.dp), colors = iosFieldColors())
                OutlinedTextField(iso, { iso = it.filter(Char::isDigit).take(5) }, label = { Text("ISO *") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = iso.isNotBlank() && (isoValue == null || isoValue !in 1..25600), shape = RoundedCornerShape(12.dp), colors = iosFieldColors())
                OutlinedTextField(loadedDate, { loadedDate = it.take(10) }, label = { Text("安装日期 *") }, supportingText = { Text("YYYY-MM-DD") }, singleLine = true, isError = loadedDate.isNotBlank() && epochDay == null, shape = RoundedCornerShape(12.dp), colors = iosFieldColors())
                if (existing?.unloadedEpochDay != null) {
                    OutlinedTextField(unloadedDate, { unloadedDate = it.take(10) }, label = { Text("卸卷日期 *") }, supportingText = { Text(if (unloadedEpochDay != null && epochDay != null && unloadedEpochDay < epochDay) "不能早于安装日期" else "YYYY-MM-DD") }, singleLine = true, isError = !unloadValid, shape = RoundedCornerShape(12.dp), colors = iosFieldColors())
                }
                OutlinedTextField(notes, { notes = it.take(200) }, label = { Text("备注") }, minLines = 2, maxLines = 4, shape = RoundedCornerShape(12.dp), colors = iosFieldColors())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(FilmRollEntity(existing?.id ?: 0, cameraId, filmType.trim(), isoValue!!, epochDay!!, unloadedEpochDay, notes.trim()))
            }, enabled = valid) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun UnloadDialog(film: FilmRollEntity, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    var date by rememberSaveable(film.id) { mutableStateOf(LocalDate.now().toString()) }
    val epochDay = parseEpochDay(date)
    val valid = epochDay != null && epochDay >= film.loadedEpochDay
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Panel,
        tonalElevation = 0.dp,
        title = { Text("卸下 ${film.filmType}") },
        text = {
            Column {
                Text("记录会移入该相机的胶卷历史。", color = Muted, modifier = Modifier.padding(bottom = 10.dp))
                OutlinedTextField(date, { date = it.take(10) }, label = { Text("卸卷日期") }, supportingText = { Text(if (epochDay != null && epochDay < film.loadedEpochDay) "不能早于安装日期" else "YYYY-MM-DD") }, isError = !valid, singleLine = true, shape = RoundedCornerShape(12.dp), colors = iosFieldColors())
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(epochDay!!) }, enabled = valid) { Text("确认卸卷") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun iosFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalToolAccent.current,
    unfocusedBorderColor = Color.Transparent,
    focusedContainerColor = PanelRaised,
    unfocusedContainerColor = PanelRaised,
)

private val displayDateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
private fun formatDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(displayDateFormatter)
private fun formatIsoDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).toString()
private fun parseEpochDay(value: String): Long? = try { LocalDate.parse(value).toEpochDay() } catch (_: DateTimeParseException) { null }
private fun daysLoaded(epochDay: Long): Long = ChronoUnit.DAYS.between(LocalDate.ofEpochDay(epochDay), LocalDate.now()).coerceAtLeast(0)
