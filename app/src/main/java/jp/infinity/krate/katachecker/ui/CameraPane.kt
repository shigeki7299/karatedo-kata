package jp.infinity.krate.katachecker.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.TextureView
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import jp.infinity.krate.katachecker.R
import jp.infinity.krate.katachecker.kata.KataEntry
import jp.infinity.krate.katachecker.kata.ModelVideoStore
import jp.infinity.krate.katachecker.pose.PoseFrame
import jp.infinity.krate.katachecker.pose.analyzePerformance
import jp.infinity.krate.katachecker.pose.averageScores
import jp.infinity.krate.katachecker.pose.scoreKata
import jp.infinity.krate.katachecker.pose.PoseOverlay
import jp.infinity.krate.katachecker.pose.VideoGrid
import jp.infinity.krate.katachecker.pose.scaledForPose
import jp.infinity.krate.katachecker.pose.toOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val aiRunningGreen = Color(0xFF1B7F3A)

private enum class CameraMode {
    Capture,
    Playback,
}

@Composable
fun LocalVideoPane(
    link: SliderLink,
    leftClip: LeftClip,
    livePoses: LivePoses,
    videoChrome: VideoChrome,
    linkedScore: LinkedScore,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    gridVisible: Boolean,
    onToggleGrid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(CameraMode.Playback) }
    var poseEnabled by rememberSaveable { mutableStateOf(true) }
    var uriText by rememberSaveable { mutableStateOf<String?>(null) }
    var playOnLoad by remember { mutableStateOf(false) }
    var recordedPlayToken by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeError by remember { mutableStateOf(false) }
    var checkedRestoredClip by remember { mutableStateOf(false) }
    val kataPrefs = remember {
        context.getSharedPreferences("kata_selection", Context.MODE_PRIVATE)
    }
    var kataId by rememberSaveable {
        mutableStateOf(kataMenuEntries().firstOrNull { it.id == kataPrefs.getString("kata_id", null) }?.id)
    }
    var menuOpen by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }
    var scoringStandardOpen by remember { mutableStateOf(false) }
    var kataProcedureOpen by remember { mutableStateOf(false) }
    var basicTechniqueOpen by remember { mutableStateOf(false) }
    var modelSettingsOpen by remember { mutableStateOf(false) }
    var savedTick by remember { mutableIntStateOf(0) }
    val modelVideos = remember { ModelVideoStore(context) }
    val savedMessage = stringResource(R.string.record_saved)
    val sameVideoMessage = stringResource(R.string.local_same_video)
    val playFailedMessage = stringResource(R.string.local_play_failed)
    val kataChoices = kataMenuEntries()
    val selectedKata = kataChoices.firstOrNull { it.id == kataId }
    var pendingModelId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingModelPlay by rememberSaveable { mutableStateOf(true) }
    var videoPermissionAsked by remember { mutableStateOf(false) }
    val videoPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    fun applyModel(kata: KataEntry, play: Boolean) {
        val file = modelVideos.resolveFile(kata)
        leftClip.requestModel(
            url = modelVideos.url(kata),
            fileUri = file?.first,
            fileName = file?.second,
            play = play,
        )
    }
    val requestVideos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        val id = pendingModelId
        pendingModelId = null
        val kata = kataChoices.firstOrNull { it.id == id } ?: return@rememberLauncherForActivityResult
        applyModel(kata, pendingModelPlay)
    }
    fun loadModel(kata: KataEntry, play: Boolean) {
        val allowed = ContextCompat.checkSelfPermission(context, videoPermission) == PackageManager.PERMISSION_GRANTED
        if (allowed || !play || modelVideos.savedFileReadable(kata) || videoPermissionAsked) {
            applyModel(kata, play)
            return
        }
        videoPermissionAsked = true
        pendingModelId = kata.id
        pendingModelPlay = play
        requestVideos.launch(videoPermission)
    }
    val uri = uriText?.let(Uri::parse)
    val player = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = false }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                noticeError = true
                notice = playFailedMessage
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(mode) {
        if (mode == CameraMode.Capture) {
            player.pause()
            videoChrome.rightBottom = 0
        }
    }
    LaunchedEffect(linkedScore.token) {
        if (linkedScore.token == 0 || !linkedScore.running) return@LaunchedEffect
        if (linkedScore.kind == LinkedRun.AI) {
            collectAi(player, livePoses, linkedScore)
        } else {
            collectDiff(player, livePoses, linkedScore)
        }
    }
    fun beginLinked(kind: LinkedRun) {
        menuOpen = false
        val leftReady = leftClip.fileUri != null || !leftClip.youtubeId.isNullOrBlank()
        linkedScore.kind = kind
        linkedScore.result = null
        linkedScore.aiReport = null
        val blocked = if (kind == LinkedRun.AI) uri == null else uri == null || !leftReady
        if (blocked) {
            linkedScore.error = if (kind == LinkedRun.AI) {
                "右の映像を読み込んでから解析してください。"
            } else {
                "左右の映像を読み込んでから採点してください。"
            }
            linkedScore.running = false
            return
        }
        if (kind == LinkedRun.DIFF) link.align()
        poseEnabled = true
        mode = CameraMode.Playback
        linkedScore.error = null
        linkedScore.kataName = selectedKata?.name ?: "形"
        linkedScore.running = true
        linkedScore.token++
    }
    LaunchedEffect(uri) {
        if (uri == null) {
            player.stop()
            player.clearMediaItems()
        } else {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            if (playOnLoad) {
                player.seekTo(0L)
                player.playWhenReady = true
                playOnLoad = false
            }
        }
    }
    LaunchedEffect(uriText, leftClip.youtubeId, leftClip.fileUri, leftClip.fileName) {
        if (checkedRestoredClip) return@LaunchedEffect
        val current = uriText?.let(Uri::parse) ?: return@LaunchedEffect
        val leftReady = leftClip.fileUri != null || !leftClip.youtubeId.isNullOrBlank()
        if (!leftReady) return@LaunchedEffect
        if (leftClip.fileUri != null && leftClip.fileName == null) return@LaunchedEffect
        checkedRestoredClip = true
        if (!sameAsLeft(context, leftClip, current)) return@LaunchedEffect
        playOnLoad = false
        noticeError = true
        notice = sameVideoMessage
        uriText = null
    }

    val density = LocalDensity.current
    val padTop = if (fullscreen) {
        0.dp
    } else {
        with(density) {
            (maxOf(videoChrome.leftTop, videoChrome.rightTop) - videoChrome.rightTop).coerceAtLeast(0).toDp()
        }
    }
    val padBottom = if (fullscreen) {
        0.dp
    } else {
        with(density) {
            (maxOf(videoChrome.leftBottom, videoChrome.rightBottom) - videoChrome.rightBottom).coerceAtLeast(0).toDp()
        }
    }
    Column(modifier = modifier) {
        Column(Modifier.onGloballyPositioned { videoChrome.rightTop = it.size.height }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.pane_camera),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            KataChoiceMenu(
                value = selectedKata?.let { kataOptionLabel(it, modelVideos.hasFile(it)) }.orEmpty(),
                options = kataChoices.map { kata ->
                    savedTick
                    kata.id to kataOptionLabel(kata, modelVideos.hasFile(kata))
                },
                onSelect = { id ->
                    val kata = kataChoices.first { it.id == id }
                    kataId = kata.id
                    kataPrefs.edit().putString("kata_id", kata.id).apply()
                    loadModel(kata, play = true)
                },
                emptyLabel = "形を選択",
                fillWidth = false,
            )
            OutlinedButton(
                onClick = onToggleFullscreen,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .height(36.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                border = BorderStroke(
                    1.dp,
                    if (fullscreen) aiRunningGreen else MaterialTheme.colorScheme.outline,
                ),
                colors = if (fullscreen) {
                    ButtonDefaults.buttonColors(
                        containerColor = aiRunningGreen,
                        contentColor = Color.White,
                    )
                } else {
                    ButtonDefaults.outlinedButtonColors()
                },
            ) {
                Text("全画面")
            }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "メニュー",
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("お手本設定") },
                        onClick = {
                            menuOpen = false
                            modelSettingsOpen = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (gridVisible) "グリッド表示　ON" else "グリッド表示　OFF") },
                        onClick = onToggleGrid,
                    )
                    DropdownMenuItem(
                        text = { Text("２画面差分採点") },
                        onClick = { beginLinked(LinkedRun.DIFF) },
                    )
                    DropdownMenuItem(
                        text = { Text("形競技採点基準") },
                        onClick = {
                            menuOpen = false
                            scoringStandardOpen = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("形競技実施要領") },
                        onClick = {
                            menuOpen = false
                            kataProcedureOpen = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("基本動作と基本技術") },
                        onClick = {
                            menuOpen = false
                            basicTechniqueOpen = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("HELP") },
                        onClick = {
                            menuOpen = false
                            helpOpen = true
                        },
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                SegmentedButton(
                    selected = mode == CameraMode.Capture,
                    onClick = { mode = CameraMode.Capture },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {},
                ) {
                    Text(stringResource(R.string.mode_capture))
                }
                SegmentedButton(
                    selected = mode == CameraMode.Playback,
                    onClick = { mode = CameraMode.Playback },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {},
                ) {
                    Text(stringResource(R.string.mode_playback))
                }
            }
            val analyzing = linkedScore.running && linkedScore.kind == LinkedRun.AI
            OutlinedButton(
                onClick = { beginLinked(LinkedRun.AI) },
                modifier = Modifier
                    .padding(start = 8.dp)
                    .height(40.dp),
                border = BorderStroke(1.dp, if (analyzing) aiRunningGreen else MaterialTheme.colorScheme.outline),
                colors = if (analyzing) {
                    ButtonDefaults.buttonColors(containerColor = aiRunningGreen, contentColor = Color.White)
                } else {
                    ButtonDefaults.outlinedButtonColors()
                },
            ) {
                Text("ＡＩ解析")
            }
        }
        }
        Spacer(Modifier.height(padTop))
        if (mode == CameraMode.Capture) {
            CaptureStage(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                gridVisible = gridVisible,
                kataLabel = selectedKata?.let(::kataFileLabel),
                onSaved = { saved ->
                    uriText = saved.toString()
                    playOnLoad = true
                    recordedPlayToken++
                    noticeError = false
                    notice = savedMessage
                    mode = CameraMode.Playback
                },
            )
            Spacer(Modifier.height(padBottom))
        } else {
            PlaybackStage(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                gridVisible = gridVisible,
                recordedPlayToken = recordedPlayToken,
                player = player,
                uri = uri,
                notice = notice,
                noticeError = noticeError,
                link = link,
                poseEnabled = poseEnabled,
                onTogglePose = { poseEnabled = !poseEnabled },
                onPose = { livePoses.right = it },
                bottomPad = padBottom,
                status = if (linkedScore.running && linkedScore.kind != LinkedRun.AI) {
                    "比較しています"
                } else {
                    null
                },
                analyzing = linkedScore.running && linkedScore.kind == LinkedRun.AI,
                kataId = kataId,
                onBottom = { videoChrome.rightBottom = it },
                linkedScore = linkedScore,
                onPick = { picked ->
                    if (picked != null && sameAsLeft(context, leftClip, picked)) {
                        noticeError = true
                        notice = sameVideoMessage
                    } else {
                        noticeError = false
                        notice = null
                        val text = picked?.toString()
                        if (text != null && text == uriText) {
                            player.seekTo(0L)
                            player.play()
                        } else {
                            playOnLoad = true
                            uriText = text
                        }
                    }
                },
            )
        }
        if (linkedScore.kind == LinkedRun.AI && (linkedScore.aiReport != null || linkedScore.error != null)) {
            AiReportDialog(
                report = linkedScore.aiReport,
                message = linkedScore.error,
                onDismiss = {
                    linkedScore.aiReport = null
                    linkedScore.error = null
                },
            )
        } else if (linkedScore.result != null || linkedScore.error != null) {
            ScoreDialog(
                sheet = linkedScore.result,
                message = linkedScore.error,
                onDismiss = {
                    linkedScore.result = null
                    linkedScore.error = null
                },
            )
        }
        if (helpOpen) {
            HelpDialog(onDismiss = { helpOpen = false })
        }
        if (scoringStandardOpen) {
            ScoringStandardDialog(onDismiss = { scoringStandardOpen = false })
        }
        if (kataProcedureOpen) {
            KataProcedureDialog(onDismiss = { kataProcedureOpen = false })
        }
        if (basicTechniqueOpen) {
            BasicTechniqueDialog(onDismiss = { basicTechniqueOpen = false })
        }
        if (modelSettingsOpen) {
            ModelKataDialog(
                store = modelVideos,
                onDismiss = {
                    modelSettingsOpen = false
                    selectedKata?.let { kata -> loadModel(kata, play = false) }
                },
                onChanged = { kata ->
                    savedTick++
                    if (kata.id == kataId) loadModel(kata, play = true)
                },
            )
        }
    }
}

private suspend fun collectDiff(
    player: ExoPlayer,
    livePoses: LivePoses,
    linkedScore: LinkedScore,
) {
    val samples = mutableListOf<jp.infinity.krate.katachecker.pose.ScoreSheet>()
    var quiet = 0
    var ticks = 0
    while (linkedScore.running) {
        delay(200)
        ticks++
        val leftPose = livePoses.left
        val rightPose = livePoses.right
        val sheet = if (leftPose != null && rightPose != null) scoreKata(leftPose, rightPose) else null
        if (sheet != null) {
            samples += sheet
            quiet = 0
        } else {
            quiet++
        }
        val rightEnded = player.playbackState == Player.STATE_ENDED && player.duration > 0L
        if (ticks >= 4 && samples.isNotEmpty() && (rightEnded || linkedScore.leftEnded)) break
        if (quiet > 40 && samples.isEmpty()) {
            linkedScore.error = "左右の骨格が取れていません。両方に骨格が出ているときに採点してください。"
            stopLinked(player, linkedScore)
            return
        }
    }
    linkedScore.result = averageScores(samples)
    if (linkedScore.result == null) {
        linkedScore.error = "左右の骨格が取れていません。両方に骨格が出ているときに採点してください。"
    }
    stopLinked(player, linkedScore)
}

private suspend fun collectAi(
    player: ExoPlayer,
    livePoses: LivePoses,
    linkedScore: LinkedScore,
) {
    player.setPlaybackSpeed(1f)
    val restart = player.playbackState == Player.STATE_ENDED
    if (restart) player.seekTo(0L)
    val origin = if (restart) 0L else player.currentPosition
    player.play()
    val rightSeries = mutableListOf<PoseFrame>()
    var lastPose: PoseFrame? = null
    var started = false
    var quiet = 0
    var waitTicks = 0
    while (linkedScore.running) {
        delay(200)
        if (!started) {
            if (player.isPlaying) {
                started = true
            } else {
                waitTicks++
                if (waitTicks > 40) {
                    linkedScore.error = "右の骨格が取れていません。右の映像に人が映っているときに解析してください。"
                    linkedScore.running = false
                    player.pause()
                    return
                }
                continue
            }
        }
        val pose = livePoses.right
        if (pose != null && pose !== lastPose) {
            rightSeries += pose
            lastPose = pose
            quiet = 0
        } else if (pose == null) {
            quiet++
        }
        val state = player.playbackState
        val passed = player.currentPosition > origin + 300L
        val paused = !player.isPlaying && state == Player.STATE_READY && passed
        val finished = state == Player.STATE_ENDED && passed
        if (started && (paused || finished)) break
        if (quiet > 40 && rightSeries.isEmpty()) {
            linkedScore.error = "右の骨格が取れていません。右の映像に人が映っているときに解析してください。"
            linkedScore.running = false
            player.pause()
            return
        }
    }
    linkedScore.aiReport = analyzePerformance(rightSeries, linkedScore.kataName)
    if (linkedScore.aiReport == null && linkedScore.running) {
        linkedScore.error = "右の骨格が取れていません。右の映像に人が映っているときに解析してください。"
    }
    linkedScore.running = false
    player.pause()
}

private fun stopLinked(player: ExoPlayer, linkedScore: LinkedScore) {
    linkedScore.running = false
    player.pause()
    linkedScore.pauseLeft()
}

@Composable
private fun CaptureStage(
    onSaved: (Uri) -> Unit,
    kataLabel: String?,
    gridVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var audioGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        cameraGranted = result[Manifest.permission.CAMERA] == true
        audioGranted = result[Manifest.permission.RECORD_AUDIO] == true
    }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var saving by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val failedMessage = stringResource(R.string.record_failed)
    val cameraUnavailable = stringResource(R.string.camera_unavailable)
    val mounted = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    val providerHolder = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }

    LaunchedEffect(Unit) {
        val missing = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    LaunchedEffect(cameraGranted, lensFacing) {
        if (!cameraGranted) return@LaunchedEffect
        val provider = ProcessCameraProvider.getInstance(context).await(context)
        providerHolder.value = provider
        val preview = androidx.camera.core.Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.fromOrderedList(
                    listOf(Quality.HD, Quality.SD),
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                ),
            )
            .build()
        val capture = VideoCapture.withOutput(recorder)
        previewView.display?.rotation?.let { rotation ->
            preview.targetRotation = rotation
            capture.targetRotation = rotation
        }
        val bound = bindCamera(provider, lifecycleOwner, lensFacing, preview, capture)
        if (bound) {
            videoCapture = capture
            errorText = null
        } else {
            videoCapture = null
            errorText = cameraUnavailable
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mounted.set(false)
            val recording = activeRecording
            if (recording == null) {
                providerHolder.value?.unbindAll()
            } else {
                recording.stop()
            }
        }
    }

    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (cameraGranted) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            if (activeRecording != null) {
                Text(
                    text = formatPlaybackTime(elapsedMs),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (gridVisible) VideoGrid(Modifier.fillMaxSize())
            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                enabled = activeRecording == null && !saving,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    imageVector = Icons.Filled.Cameraswitch,
                    contentDescription = stringResource(R.string.switch_camera),
                    tint = Color.White,
                )
            }
            RecordButton(
                recording = activeRecording != null,
                enabled = videoCapture != null && !saving,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                onClick = {
                    val current = activeRecording
                    if (current != null) {
                        saving = true
                        activeRecording = null
                        current.stop()
                    } else {
                        val capture = videoCapture ?: return@RecordButton
                        previewView.display?.rotation?.let { capture.targetRotation = it }
                        elapsedMs = 0L
                        errorText = null
                        activeRecording = startRecording(
                            context = context,
                            capture = capture,
                            kataLabel = kataLabel,
                            withAudio = audioGranted,
                            onStatus = { elapsedMs = it },
                            onFinished = { result ->
                                if (!mounted.get()) {
                                    providerHolder.value?.unbindAll()
                                } else {
                                    saving = false
                                    activeRecording = null
                                    if (result == null) errorText = failedMessage
                                }
                                if (result != null) onSaved(result)
                            },
                        )
                    }
                },
            )
            if (saving) {
                Text(
                    text = stringResource(R.string.record_saving),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 96.dp),
                    color = Color.White,
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.camera_permission),
                    modifier = Modifier.padding(16.dp),
                    color = Color.White,
                )
                TextButton(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                        )
                    },
                ) {
                    Text(stringResource(R.string.camera_permission_button))
                }
            }
        }
        errorText?.let { message ->
            Text(
                text = message,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun RecordButton(
    recording: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(if (recording) R.string.record_stop else R.string.record_start)
    Box(
        modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(64.dp)) {
            val ring = if (enabled) Color.White else Color.Gray
            val fill = if (enabled) Color(0xFFE53935) else Color.DarkGray
            drawCircle(ring, radius = size.minDimension / 2f)
            if (recording) {
                val inset = size.minDimension * 0.32f
                drawRect(
                    color = fill,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(
                        size.width - inset * 2f,
                        size.height - inset * 2f,
                    ),
                )
            } else {
                drawCircle(fill, radius = size.minDimension / 2f - 8.dp.toPx())
            }
        }
    }
}

@Composable
private fun PlaybackStage(
    gridVisible: Boolean,
    recordedPlayToken: Int,
    player: ExoPlayer,
    uri: Uri?,
    notice: String?,
    noticeError: Boolean,
    link: SliderLink,
    poseEnabled: Boolean,
    onTogglePose: () -> Unit,
    onPose: (jp.infinity.krate.katachecker.pose.PoseFrame?) -> Unit,
    bottomPad: Dp,
    status: String?,
    analyzing: Boolean,
    kataId: String?,
    onBottom: (Int) -> Unit,
    linkedScore: LinkedScore,
    onPick: (Uri?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var playbackSpeed by rememberSaveable { mutableFloatStateOf(1f) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var canPlay by remember { mutableStateOf(false) }
    var pose by remember { mutableStateOf<PoseFrame?>(null) }
    val textureHolder = remember { mutableStateOf<TextureView?>(null) }
    val zoom = remember { ZoomState() }
    val detector = remember {
        PoseDetection.getClient(
            PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build(),
        )
    }
    var videoCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val bookmarkName = remember(uri) { uri?.let { videoDisplayName(context, it) } }
    val bookmarks = rememberVideoBookmarks(
        youtubeId = null,
        uri = uri?.toString(),
        displayName = bookmarkName,
        kataId = kataId,
    )
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                picked,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        pose = null
        zoom.reset()
        onPick(picked)
    }
    val openVideoFile = {
        picker.launch(arrayOf("video/*"))
    }

    DisposableEffect(detector) {
        onDispose { detector.close() }
    }
    DisposableEffect(Unit) {
        onDispose { onPose(null) }
    }
    SideEffect { onPose(if (poseEnabled) pose else null) }
    LaunchedEffect(playbackSpeed, uri) {
        if (uri != null) player.setPlaybackSpeed(playbackSpeed)
    }
    LaunchedEffect(recordedPlayToken) {
        if (recordedPlayToken == 0 || uri == null) return@LaunchedEffect
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.seekTo(0L)
        player.playWhenReady = true
    }
    LaunchedEffect(linkedScore.token) {
        if (linkedScore.token == 0 || !linkedScore.running || uri == null) return@LaunchedEffect
        playbackSpeed = 1f
        player.setPlaybackSpeed(1f)
        if (linkedScore.kind == LinkedRun.AI && player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0L)
        }
        player.play()
    }
    LaunchedEffect(player, uri) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            val reported = player.duration
            if (reported > 0L) durationMs = reported
            playing = player.isPlaying
            canPlay = uri != null && player.playbackState != Player.STATE_IDLE
            delay(200)
        }
    }
    LaunchedEffect(uri) {
        pose = null
        positionMs = 0L
        durationMs = 0L
    }
    LaunchedEffect(playing, uri, poseEnabled) {
        if (!poseEnabled) {
            pose = null
            return@LaunchedEffect
        }
        if (!playing || uri == null) return@LaunchedEffect
        var busy = false
        while (playing) {
            val view = textureHolder.value
            if (!busy && view != null && view.width > 0 && view.height > 0) {
                val frame = view.bitmap
                if (frame != null) {
                    busy = true
                    val scaled = frame.scaledForPose()
                    if (scaled !== frame) frame.recycle()
                    val image = InputImage.fromBitmap(scaled, 0)
                    detector.process(image)
                        .addOnSuccessListener { detected ->
                            pose = detected.toOverlay(scaled.width, scaled.height)
                            scaled.recycle()
                            busy = false
                        }
                        .addOnFailureListener {
                            scaled.recycle()
                            busy = false
                        }
                }
            }
            delay(160)
        }
    }

    SideEffect {
        link.playLocal = {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
            player.play()
            playing = true
        }
        link.pauseLocal = {
            player.pause()
            playing = false
        }
    }
    Column(modifier) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
                .clipToBounds()
                .onGloballyPositioned { videoCoordinates = it },
            contentAlignment = Alignment.Center,
        ) {
            if (uri != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        val view = LayoutInflater.from(viewContext)
                            .inflate(R.layout.playback_player, null) as PlayerView
                        view.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        view.player = player
                        view.setOnTouchListener(
                            zoomTouchListener(
                                viewContext,
                                zoom,
                                VideoTaps().also { taps ->
                                    taps.onDoubleTap = { if (!zoom.zoomed) openVideoFile() }
                                },
                            ),
                        )
                        view.addOnLayoutChangeListener { changed, _, _, _, _, _, _, _, _ ->
                            textureHolder.value = (changed as PlayerView).videoSurfaceView as? TextureView
                        }
                        textureHolder.value = view.videoSurfaceView as? TextureView
                        view
                    },
                    update = { view ->
                        textureHolder.value = view.videoSurfaceView as? TextureView
                        val scale = zoom.scale
                        val panX = zoom.panX
                        val panY = zoom.panY
                        view.applyZoom(zoom)
                        view.scaleX = scale
                        view.scaleY = scale
                        view.translationX = panX
                        view.translationY = panY
                    },
                    onRelease = { view ->
                        textureHolder.value = null
                        view.player = null
                    },
                )
                if (poseEnabled) {
                    PoseOverlay(pose, Modifier.fillMaxSize().zoomDraw(zoom))
                }
            } else {
                Text(
                    text = stringResource(R.string.local_empty),
                    modifier = Modifier.padding(16.dp),
                    color = Color.White,
                )
            }
            if (gridVisible) VideoGrid(Modifier.fillMaxSize())
        }
        val coordinates = videoCoordinates
        if (coordinates != null && coordinates.isAttached) {
            VideoGesturePopup(
                coordinates = coordinates,
                zoom = zoom,
                onTap = { link.withLocal(!player.isPlaying) },
                onDoubleTap = openVideoFile,
            )
        }
        if (bottomPad > 0.dp) {
            Spacer(Modifier.height(bottomPad))
        }
        Column(
            Modifier
                .onGloballyPositioned { onBottom(it.size.height) }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            notice?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (noticeError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            if (uri != null) {
                Text(
                    text = if (analyzing) "AI解析中" else stringResource(R.string.local_change),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (analyzing) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            PlaybackControls(
                playing = playing,
                positionMs = positionMs,
                durationMs = durationMs,
                canPlay = canPlay,
                poseEnabled = poseEnabled,
                playbackSpeed = playbackSpeed,
                linked = link.enabled,
                onToggle = { link.withLocal(!player.isPlaying) },
                onCycleSpeed = { playbackSpeed = nextPlaybackSpeed(playbackSpeed) },
                onTogglePose = onTogglePose,
                onToggleLink = link::toggle,
                onSeek = { position -> player.scrubTo(position, finished = true) },
                onPreviewSeek = { position -> player.scrubTo(position, finished = false) },
                bookmarksMs = bookmarks.marks,
                onToggleBookmark = bookmarks.toggle,
            )
        }
    }
}

private fun startRecording(
    context: android.content.Context,
    capture: VideoCapture<Recorder>,
    kataLabel: String?,
    withAudio: Boolean,
    onStatus: (Long) -> Unit,
    onFinished: (Uri?) -> Unit,
): Recording {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.JAPAN).format(Date())
    val base = if (kataLabel.isNullOrBlank()) stamp else "${stamp}_$kataLabel"
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, "$base.mp4")
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
        }
    }
    val options = MediaStoreOutputOptions.Builder(
        context.contentResolver,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
    ).setContentValues(values).build()
    val prepared = capture.output.prepareRecording(context, options).let { pending ->
        if (withAudio) pending.withAudioEnabled() else pending
    }
    return prepared.start(ContextCompat.getMainExecutor(context)) { event ->
        when (event) {
            is VideoRecordEvent.Status -> onStatus(event.recordingStats.recordedDurationNanos / 1_000_000L)
            is VideoRecordEvent.Finalize -> {
                if (event.hasError()) onFinished(null) else onFinished(event.outputResults.outputUri)
            }
            else -> Unit
        }
    }
}

private fun bindCamera(
    provider: ProcessCameraProvider,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    lensFacing: Int,
    preview: androidx.camera.core.Preview,
    capture: VideoCapture<Recorder>,
): Boolean {
    val preferred = listOf(lensFacing, oppositeLens(lensFacing))
    for (facing in preferred) {
        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.Builder().requireLensFacing(facing).build(),
                preview,
                capture,
            )
            return true
        } catch (_: IllegalArgumentException) {
            continue
        }
    }
    return false
}

private fun oppositeLens(facing: Int): Int {
    return if (facing == CameraSelector.LENS_FACING_BACK) {
        CameraSelector.LENS_FACING_FRONT
    } else {
        CameraSelector.LENS_FACING_BACK
    }
}

private suspend fun <T> ListenableFuture<T>.await(context: android.content.Context): T {
    return suspendCancellableCoroutine { continuation ->
        addListener(
            {
                if (!continuation.isActive) return@addListener
                try {
                    continuation.resume(get())
                } catch (error: Throwable) {
                    continuation.resumeWithException(error)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }
}

private val downloadedYoutubeId = Regex("""空手形_youtube_([A-Za-z0-9_-]{11})_""")

internal fun videoDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }
}

internal fun sameAsLeft(context: Context, left: LeftClip, picked: Uri): Boolean {
    val pickedText = Uri.decode(picked.toString())
    val pickedName = videoDisplayName(context, picked)
    val leftUri = left.fileUri
    if (leftUri != null) {
        if (leftUri == picked.toString() || Uri.decode(leftUri) == pickedText) return true
        val leftKey = mediaKey(Uri.decode(leftUri))
        val pickedKey = mediaKey(pickedText)
        if (leftKey != null && leftKey == pickedKey) return true
        val leftName = left.fileName
        if (!leftName.isNullOrBlank() && leftName == pickedName) return true
        val leftId = youtubeIdInName(leftName)
        val pickedId = youtubeIdInName(pickedName)
        if (leftId != null && leftId == pickedId) return true
    }
    val showing = left.youtubeId
    if (!showing.isNullOrBlank() && youtubeIdInName(pickedName) == showing) return true
    return false
}

internal fun ExoPlayer.scrubTo(positionMs: Long, finished: Boolean) {
    if (playbackState == Player.STATE_IDLE) return
    if (!finished && !isScrubbingModeEnabled) {
        setScrubbingModeEnabled(true)
        setSeekParameters(SeekParameters.EXACT)
    }
    seekTo(positionMs.coerceAtLeast(0L))
    if (finished) {
        if (isScrubbingModeEnabled) setScrubbingModeEnabled(false)
        setSeekParameters(SeekParameters.DEFAULT)
    }
}

private fun youtubeIdInName(name: String?): String? {
    if (name.isNullOrBlank()) return null
    return downloadedYoutubeId.find(name)?.groupValues?.get(1)
}

private fun mediaKey(decodedUri: String): String? {
    return Regex("""(?:video:|/media/)(\d+)""").find(decodedUri)?.groupValues?.get(1)
}
