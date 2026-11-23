package jp.infinity.krate.katachecker.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalView
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import jp.infinity.krate.katachecker.R
import jp.infinity.krate.katachecker.player.YoutubeDownloadException
import jp.infinity.krate.katachecker.player.YoutubeDownloadFailure
import jp.infinity.krate.katachecker.player.YoutubeDownloader
import jp.infinity.krate.katachecker.player.YoutubeIds
import jp.infinity.krate.katachecker.player.YoutubeJsBridge
import jp.infinity.krate.katachecker.player.YoutubeSession
import jp.infinity.krate.katachecker.player.YoutubeStore
import jp.infinity.krate.katachecker.pose.PoseFrame
import jp.infinity.krate.katachecker.pose.PoseOverlay
import jp.infinity.krate.katachecker.pose.VideoGrid
import jp.infinity.krate.katachecker.pose.captureForPose
import jp.infinity.krate.katachecker.pose.scaledForPose
import jp.infinity.krate.katachecker.pose.toOverlay
import jp.infinity.krate.katachecker.pose.undoViewZoom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class LeftClip {
    var youtubeId by mutableStateOf<String?>(null)
    var fileUri by mutableStateOf<String?>(null)
    var fileName by mutableStateOf<String?>(null)
    var modelToken by mutableIntStateOf(0)
    var modelUrl by mutableStateOf("")
    var modelFileUri by mutableStateOf<String?>(null)
    var modelFileName by mutableStateOf<String?>(null)
    var modelPlay by mutableStateOf(false)

    fun requestModel(url: String, fileUri: String?, fileName: String?, play: Boolean) {
        modelUrl = url
        modelFileUri = fileUri
        modelFileName = fileName
        modelPlay = play
        modelToken++
    }
}

class LivePoses {
    var left by mutableStateOf<PoseFrame?>(null)
    var right by mutableStateOf<PoseFrame?>(null)
}

class VideoChrome {
    var leftTop by mutableIntStateOf(0)
    var leftBottom by mutableIntStateOf(0)
    var rightTop by mutableIntStateOf(0)
    var rightBottom by mutableIntStateOf(0)
}

enum class LinkedRun { DIFF, AI }

class LinkedScore {
    var token by mutableIntStateOf(0)
    var running by mutableStateOf(false)
    var kind by mutableStateOf(LinkedRun.DIFF)
    var result by mutableStateOf<jp.infinity.krate.katachecker.pose.ScoreSheet?>(null)
    var aiReport by mutableStateOf<jp.infinity.krate.katachecker.pose.AiReport?>(null)
    var error by mutableStateOf<String?>(null)
    var kataName: String = "形"
    var leftEnded by mutableStateOf(false)
    var pauseLeft: () -> Unit = {}
}

@Composable
fun CompareScreen() {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        val link = remember { SliderLink() }
        val leftClip = remember { LeftClip() }
        val livePoses = remember { LivePoses() }
        val videoChrome = remember { VideoChrome() }
        val linkedScore = remember { LinkedScore() }
        val context = LocalContext.current
        val displayPrefs = remember {
            context.getSharedPreferences("display_settings", Context.MODE_PRIVATE)
        }
        var fullscreen by rememberSaveable { mutableStateOf(false) }
        var gridVisible by rememberSaveable {
            mutableStateOf(displayPrefs.getBoolean("grid", true))
        }
        if (!fullscreen) {
            YoutubePane(
                link = link,
                leftClip = leftClip,
                livePoses = livePoses,
                videoChrome = videoChrome,
                linkedScore = linkedScore,
                gridVisible = gridVisible,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
        LocalVideoPane(
            link = link,
            leftClip = leftClip,
            livePoses = livePoses,
            videoChrome = videoChrome,
            linkedScore = linkedScore,
            fullscreen = fullscreen,
            onToggleFullscreen = { fullscreen = !fullscreen },
            gridVisible = gridVisible,
            onToggleGrid = {
                val next = !gridVisible
                gridVisible = next
                displayPrefs.edit().putBoolean("grid", next).apply()
            },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun YoutubePane(
    link: SliderLink,
    leftClip: LeftClip,
    livePoses: LivePoses,
    videoChrome: VideoChrome,
    linkedScore: LinkedScore,
    gridVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val session = remember { YoutubeSession() }
    var urlText by rememberSaveable { mutableStateOf(YoutubeStore.url(context)) }
    var videoId by rememberSaveable { mutableStateOf(YoutubeStore.videoId(context)) }
    var playOnCue by remember { mutableStateOf(videoId != null) }
    var poseEnabled by rememberSaveable { mutableStateOf(true) }
    var playbackSpeed by rememberSaveable { mutableFloatStateOf(1f) }
    var savedUriText by rememberSaveable { mutableStateOf<String?>(null) }
    var savedPosition by remember { mutableLongStateOf(0L) }
    var savedDuration by remember { mutableLongStateOf(0L) }
    var savedPlaying by remember { mutableStateOf(false) }
    var savedCanPlay by remember { mutableStateOf(false) }
    val zoom = remember { ZoomState() }
    val savedPlayer = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = false }
    }
    val focusManager = LocalFocusManager.current
    val webViewHolder = remember { mutableStateOf<WebView?>(null) }
    var videoCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var pose by remember { mutableStateOf<PoseFrame?>(null) }
    var poseLeft by remember { mutableStateOf(0) }
    var poseTop by remember { mutableStateOf(0) }
    var poseWidth by remember { mutableStateOf(0) }
    var poseHeight by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var savePercent by remember { mutableStateOf(-1) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val downloadJob = remember { mutableStateOf<Job?>(null) }
    val detector = remember {
        PoseDetection.getClient(
            PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build(),
        )
    }
    var savedName by remember { mutableStateOf<String?>(null) }
    val bookmarks = rememberVideoBookmarks(
        youtubeId = if (savedUriText == null) videoId else null,
        uri = savedUriText,
        displayName = savedName,
        kataId = null,
    )
    var scrubbingYoutube by remember { mutableStateOf(false) }
    fun seekLeft(position: Long, finished: Boolean) {
        if (savedUriText != null) {
            savedPlayer.scrubTo(position, finished)
            return
        }
        if (!finished && !scrubbingYoutube) {
            session.beginScrub()
            scrubbingYoutube = true
        }
        session.seekTo(position)
        if (finished && scrubbingYoutube) {
            session.endScrub()
            scrubbingYoutube = false
        }
    }
    fun playSaved(uri: Uri) {
        pose = null
        zoom.reset()
        if (session.playing) session.togglePlay()
        val text = uri.toString()
        if (savedUriText == text) {
            savedPlayer.seekTo(0L)
            savedPlayer.play()
        } else {
            savedUriText = text
        }
    }
    val openVideoFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                picked,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        playSaved(picked)
    }
    val openSavedVideo = { openVideoFile.launch(arrayOf("video/*")) }
    LaunchedEffect(leftClip.modelToken) {
        if (leftClip.modelToken == 0) return@LaunchedEffect
        urlText = leftClip.modelUrl
        if (!leftClip.modelPlay) return@LaunchedEffect
        val file = leftClip.modelFileUri
        if (!file.isNullOrBlank()) {
            session.pause()
            playSaved(Uri.parse(file))
            return@LaunchedEffect
        }
        val id = YoutubeIds.parse(leftClip.modelUrl)
        if (id == null) return@LaunchedEffect
        YoutubeStore.save(context, leftClip.modelUrl.trim(), id)
        savedUriText = null
        playOnCue = true
        if (videoId == id) {
            session.cue(id, autoplay = true)
            playOnCue = false
        } else {
            videoId = id
        }
    }

    DisposableEffect(detector, savedPlayer) {
        onDispose {
            downloadJob.value?.cancel()
            detector.close()
            savedPlayer.release()
        }
    }
    LaunchedEffect(videoId) {
        pose = null
        val id = videoId ?: return@LaunchedEffect
        session.cue(id, playOnCue)
        playOnCue = false
    }
    fun submitYoutube() {
        focusManager.clearFocus()
        val id = YoutubeIds.parse(urlText)
        if (id == null) {
            session.showInvalidUrl()
            return
        }
        YoutubeStore.save(context, urlText.trim(), id)
        savedUriText = null
        playOnCue = true
        if (videoId == id) {
            session.cue(id, autoplay = true)
            playOnCue = false
        } else {
            videoId = id
        }
    }
    fun startDownload() {
        val id = videoId ?: return
        downloadJob.value?.cancel()
        saving = true
        saveError = false
        saveMessage = null
        savePercent = -1
        downloadJob.value = scope.launch {
            try {
                YoutubeDownloader(context.applicationContext).download(id) { percent ->
                    savePercent = percent
                }
                saveError = false
                saveMessage = context.getString(R.string.youtube_saved)
            } catch (error: CancellationException) {
                saveMessage = null
                throw error
            } catch (error: YoutubeDownloadException) {
                saveError = true
                saveMessage = context.getString(
                    when (error.failure) {
                        YoutubeDownloadFailure.Unavailable -> R.string.youtube_save_unavailable
                        YoutubeDownloadFailure.Failed -> R.string.youtube_save_failed
                    },
                )
            } catch (_: Exception) {
                saveError = true
                saveMessage = context.getString(R.string.youtube_save_failed)
            } finally {
                saving = false
                savePercent = -1
            }
        }
    }
    LaunchedEffect(savedUriText) {
        val uri = savedUriText?.let(Uri::parse)
        savedName = uri?.let { videoDisplayName(context, it) }
        pose = null
        if (uri == null) {
            savedPlayer.pause()
            savedPlayer.clearMediaItems()
            savedPlaying = false
            savedCanPlay = false
            return@LaunchedEffect
        }
        if (session.playing) session.togglePlay()
        zoom.reset()
        savedPlayer.setMediaItem(MediaItem.fromUri(uri))
        savedPlayer.prepare()
        savedPlayer.setPlaybackSpeed(playbackSpeed)
        savedPlayer.play()
    }
    LaunchedEffect(playbackSpeed, savedUriText) {
        if (savedUriText != null) {
            savedPlayer.setPlaybackSpeed(playbackSpeed)
        } else {
            session.setPlaybackSpeed(playbackSpeed)
        }
    }
    LaunchedEffect(linkedScore.token) {
        if (linkedScore.token == 0 || linkedScore.kind == LinkedRun.AI) return@LaunchedEffect
        poseEnabled = true
        playbackSpeed = 1f
        if (savedUriText != null) {
            savedPlayer.setPlaybackSpeed(1f)
            savedPlayer.play()
        } else if (videoId != null) {
            session.setPlaybackSpeed(1f)
            session.play()
        }
    }
    LaunchedEffect(savedPlayer, savedUriText) {
        if (savedUriText == null) return@LaunchedEffect
        while (true) {
            savedPosition = savedPlayer.currentPosition.coerceAtLeast(0L)
            val reported = savedPlayer.duration
            if (reported > 0L) savedDuration = reported
            savedPlaying = savedPlayer.isPlaying
            savedCanPlay = savedPlayer.playbackState != Player.STATE_IDLE
            delay(200)
        }
    }
    LaunchedEffect(session.playing, videoId, poseEnabled, savedUriText) {
        if (!poseEnabled || savedUriText != null) {
            if (savedUriText != null) pose = null
            if (!poseEnabled) pose = null
            return@LaunchedEffect
        }
        if (!session.playing || videoId == null) return@LaunchedEffect
        var busy = false
        while (true) {
            val view = webViewHolder.value
            if (!busy && view != null) {
                val shot = view.captureForPose()
                if (shot != null) {
                    busy = true
                    val scaled = shot.bitmap.scaledForPose()
                    if (scaled !== shot.bitmap) shot.bitmap.recycle()
                    val image = InputImage.fromBitmap(scaled, 0)
                    detector.process(image)
                        .addOnSuccessListener { detected ->
                            val overlay = detected.toOverlay(scaled.width, scaled.height)
                            pose = if (shot.screenCrop) overlay?.undoViewZoom(view) else overlay
                            poseLeft = shot.left
                            poseTop = shot.top
                            poseWidth = shot.width
                            poseHeight = shot.height
                            scaled.recycle()
                            busy = false
                        }
                        .addOnFailureListener {
                            scaled.recycle()
                            busy = false
                        }
                }
            }
            delay(180)
        }
    }

    val density = LocalDensity.current
    val padTop = with(density) {
        (maxOf(videoChrome.leftTop, videoChrome.rightTop) - videoChrome.leftTop).coerceAtLeast(0).toDp()
    }
    val padBottom = with(density) {
        (maxOf(videoChrome.leftBottom, videoChrome.rightBottom) - videoChrome.leftBottom).coerceAtLeast(0).toDp()
    }
    Column(modifier = modifier) {
        Column(Modifier.onGloballyPositioned { videoChrome.leftTop = it.size.height }) {
        Text(
            text = stringResource(R.string.pane_youtube),
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    placeholder = { Text(stringResource(R.string.youtube_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = { submitYoutube() },
                    ),
                )
                TextButton(
                    onClick = { submitYoutube() },
                    modifier = Modifier.padding(start = 4.dp),
                ) {
                    Text(stringResource(R.string.youtube_load))
                }
                TextButton(
                    onClick = {
                        if (saving) downloadJob.value?.cancel() else startDownload()
                    },
                    enabled = videoId != null,
                    modifier = Modifier.padding(start = 4.dp),
                ) {
                    Text(
                        stringResource(
                            if (saving) R.string.youtube_save_cancel else R.string.youtube_save,
                        ),
                    )
                }
            }
            YoutubeErrorText(session.errorCode)
            if (saving) {
                LinearProgressIndicator(
                    progress = { if (savePercent < 0) 0f else savePercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
                Text(
                    text = if (savePercent < 0) {
                        stringResource(R.string.youtube_saving)
                    } else {
                        stringResource(R.string.youtube_saving_progress, savePercent)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (saveMessage != null) {
                Text(
                    text = saveMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (saveError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
        }
        SideEffect {
            if (savedUriText != null) {
                leftClip.youtubeId = null
                leftClip.fileUri = savedUriText
                leftClip.fileName = savedName
            } else {
                livePoses.left = if (poseEnabled) pose else null
                leftClip.youtubeId = videoId
                leftClip.fileUri = null
                leftClip.fileName = null
            }
            linkedScore.leftEnded = if (savedUriText != null) {
                savedDuration > 0L && !savedPlaying && savedPosition >= savedDuration - 400L
            } else {
                session.durationMs > 0L && !session.playing && session.positionMs >= session.durationMs - 400L
            }
            linkedScore.pauseLeft = {
                if (savedUriText != null) savedPlayer.pause() else session.pause()
            }
            link.playYoutube = {
                if (savedUriText != null) {
                    if (savedPlayer.playbackState == Player.STATE_ENDED) savedPlayer.seekTo(0L)
                    savedPlayer.play()
                } else {
                    if (!session.playing && session.positionMs >= session.durationMs && session.durationMs > 0L) {
                        session.seekTo(0L)
                    }
                    session.play()
                }
            }
            link.pauseYoutube = {
                if (savedUriText != null) savedPlayer.pause() else session.pause()
            }
        }
        Spacer(Modifier.height(padTop))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
                .clipToBounds()
                .onGloballyPositioned { videoCoordinates = it },
            contentAlignment = Alignment.Center,
        ) {
            if (savedUriText != null) {
                YoutubeSavedPlayer(
                    player = savedPlayer,
                    zoom = zoom,
                    poseEnabled = poseEnabled,
                    onDoubleTap = openSavedVideo,
                    onPose = { livePoses.left = it },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                YoutubePlayer(
                    session = session,
                    zoom = zoom,
                    onDoubleTap = openSavedVideo,
                    onWebView = { webViewHolder.value = it },
                    modifier = Modifier.fillMaxSize(),
                )
                if (videoId == null) {
                    Text(
                        text = stringResource(R.string.youtube_empty),
                        modifier = Modifier.padding(16.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (gridVisible) VideoGrid(Modifier.fillMaxSize())
        }
        val coordinates = videoCoordinates
        if (coordinates != null && coordinates.isAttached) {
            VideoGesturePopup(
                coordinates = coordinates,
                zoom = zoom,
                onTap = {
                    val playingNow = if (savedUriText != null) savedPlayer.isPlaying else session.playing
                    link.withYoutube(!playingNow)
                },
                onDoubleTap = openSavedVideo,
            )
        }
        val detected = pose
        if (
            savedUriText == null &&
            poseEnabled &&
            detected != null &&
            coordinates != null &&
            coordinates.isAttached &&
            poseWidth > 0 &&
            poseHeight > 0
        ) {
            YoutubePosePopup(
                pose = detected,
                coordinates = coordinates,
                left = poseLeft,
                top = poseTop,
                width = poseWidth,
                height = poseHeight,
                zoom = zoom,
            )
        }
        Spacer(Modifier.height(padBottom))
        Column(
            Modifier
                .onGloballyPositioned { videoChrome.leftBottom = it.size.height }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = when {
                    linkedScore.running && linkedScore.kind == LinkedRun.AI && savedUriText != null -> "AI解析中"
                    savedUriText == null -> stringResource(R.string.local_empty)
                    else -> stringResource(R.string.local_change)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PlaybackControls(
                playing = if (savedUriText != null) savedPlaying else session.playing,
                positionMs = if (savedUriText != null) savedPosition else session.positionMs,
                durationMs = if (savedUriText != null) savedDuration else session.durationMs,
                canPlay = if (savedUriText != null) savedCanPlay else session.ready && videoId != null,
                poseEnabled = poseEnabled,
                playbackSpeed = playbackSpeed,
                linked = link.enabled,
                onToggle = {
                    val playingNow = if (savedUriText != null) savedPlayer.isPlaying else session.playing
                    link.withYoutube(!playingNow)
                },
                onCycleSpeed = { playbackSpeed = nextPlaybackSpeed(playbackSpeed) },
                onTogglePose = { poseEnabled = !poseEnabled },
                onToggleLink = link::toggle,
                onSeek = { position -> seekLeft(position, finished = true) },
                onPreviewSeek = { position -> seekLeft(position, finished = false) },
                bookmarksMs = bookmarks.marks,
                onToggleBookmark = bookmarks.toggle,
            )
        }
    }
}

@Composable
private fun YoutubeErrorText(errorCode: Int) {
    val message = when (errorCode) {
        0 -> null
        -1 -> stringResource(R.string.youtube_invalid)
        100, 101, 150 -> stringResource(R.string.youtube_unavailable)
        else -> stringResource(R.string.youtube_error, errorCode)
    }
    if (message != null) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun VideoGesturePopup(
    coordinates: LayoutCoordinates,
    zoom: ZoomState,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
) {
    val taps = remember { VideoTaps() }
    val scope = rememberCoroutineScope()
    taps.onTap = onTap
    taps.onDoubleTap = onDoubleTap
    val origin = coordinates.positionInWindow()
    val box = coordinates.size
    Popup(
        popupPositionProvider = remember(origin, box) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt())
            }
        },
        properties = PopupProperties(
            focusable = false,
            clippingEnabled = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(Constraints.fixed(box.width, box.height))
                    layout(box.width, box.height) { placeable.place(0, 0) }
                }
                .pinchToZoom(zoom, taps, scope),
        )
    }
}

@Composable
private fun YoutubePosePopup(
    pose: PoseFrame,
    coordinates: LayoutCoordinates,
    left: Int,
    top: Int,
    width: Int,
    height: Int,
    zoom: ZoomState,
) {
    val origin = coordinates.positionInWindow()
    val box = coordinates.size
    Popup(
        popupPositionProvider = remember(origin, box) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset {
                    return IntOffset(origin.x.roundToInt(), origin.y.roundToInt())
                }
            }
        },
        properties = PopupProperties(
            focusable = false,
            clippingEnabled = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        val popupView = LocalView.current
        DisposableEffect(popupView) {
            val root = popupView.rootView
            val params = root.layoutParams
            if (params is WindowManager.LayoutParams) {
                val manager = popupView.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                runCatching { manager.updateViewLayout(root, params) }
            }
            onDispose { }
        }
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(Constraints.fixed(box.width, box.height))
                    layout(box.width, box.height) { placeable.place(0, 0) }
                }
                .clipToBounds()
                .zoomDraw(zoom),
        ) {
            Box(
                Modifier
                    .offset { IntOffset(left, top) }
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(Constraints.fixed(width, height))
                        layout(width, height) { placeable.place(0, 0) }
                    },
            ) {
                PoseOverlay(pose, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun YoutubeSavedPlayer(
    player: ExoPlayer,
    zoom: ZoomState,
    poseEnabled: Boolean,
    onDoubleTap: () -> Unit,
    onPose: (PoseFrame?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pose by remember { mutableStateOf<PoseFrame?>(null) }
    val textureHolder = remember { mutableStateOf<TextureView?>(null) }
    val detector = remember {
        PoseDetection.getClient(
            PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build(),
        )
    }
    DisposableEffect(detector) {
        onDispose {
            detector.close()
            onPose(null)
        }
    }
    LaunchedEffect(player, poseEnabled) {
        if (!poseEnabled) {
            pose = null
            return@LaunchedEffect
        }
        var busy = false
        while (true) {
            val view = textureHolder.value
            if (!busy && player.isPlaying && view != null && view.width > 0 && view.height > 0) {
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
    SideEffect { onPose(if (poseEnabled) pose else null) }
    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                val view = LayoutInflater.from(viewContext)
                    .inflate(R.layout.playback_player, null) as PlayerView
                view.player = player
                view.addOnLayoutChangeListener { changed, _, _, _, _, _, _, _, _ ->
                    textureHolder.value = (changed as PlayerView).videoSurfaceView as? TextureView
                }
                textureHolder.value = view.videoSurfaceView as? TextureView
                ZoomTouchLayout(viewContext).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    addView(
                        view,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    sharedTouch = zoomTouchListener(
                        viewContext,
                        zoom,
                        VideoTaps().also { it.onDoubleTap = onDoubleTap },
                    )
                    attachSharedTouch()
                }
            },
            update = { root ->
                root.attachSharedTouch()
                val view = root.getChildAt(0) as PlayerView
                textureHolder.value = view.videoSurfaceView as? TextureView
                view.applyZoom(zoom)
                view.scaleX = zoom.scale
                view.scaleY = zoom.scale
                view.translationX = zoom.panX
                view.translationY = zoom.panY
            },
            onRelease = { root ->
                textureHolder.value = null
                (root.getChildAt(0) as PlayerView).player = null
            },
        )
        if (poseEnabled) {
            PoseOverlay(pose, Modifier.fillMaxSize().zoomDraw(zoom))
        }
    }
}

@Composable
private fun YoutubePlayer(
    session: YoutubeSession,
    zoom: ZoomState,
    onDoubleTap: () -> Unit,
    onWebView: (WebView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = zoom.scale
    val panX = zoom.panX
    val panY = zoom.panY
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val assetLoader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()
            val webView = WebView(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.userAgentString = CHROME_MOBILE_USER_AGENT
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ) = assetLoader.shouldInterceptRequest(request.url)

                    override fun onPageFinished(view: WebView, url: String) {
                        if (url.startsWith(PLAYER_PAGE)) {
                            session.onPageLoaded()
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        return request.isForMainFrame &&
                            request.url.host != "appassets.androidplatform.net"
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        Log.d(LOG_TAG, consoleMessage.message())
                        return true
                    }
                }
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                addJavascriptInterface(YoutubeJsBridge(session), "Android")
                session.bind(this)
                loadUrl(PLAYER_PAGE)
                onWebView(this)
            }
            ZoomTouchLayout(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                addView(
                    webView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                sharedTouch = zoomTouchListener(
                    context,
                    zoom,
                    VideoTaps().also { it.onDoubleTap = onDoubleTap },
                )
                webView.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                    override fun onChildViewAdded(parent: View, child: View) {
                        attachSharedTouch()
                    }

                    override fun onChildViewRemoved(parent: View, child: View) = Unit
                })
                attachSharedTouch()
            }
        },
        update = { view ->
            view.attachSharedTouch()
            val webView = view.getChildAt(0)
            webView.applyZoom(zoom)
            webView.scaleX = scale
            webView.scaleY = scale
            webView.translationX = panX
            webView.translationY = panY
        },
        onRelease = { view ->
            val webView = view.getChildAt(0) as WebView
            session.unbind()
            webView.removeJavascriptInterface("Android")
            webView.stopLoading()
            webView.destroy()
        },
    )
}

private const val PLAYER_PAGE =
    "https://appassets.androidplatform.net/assets/youtube_player.html"
private const val CHROME_MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
private const val LOG_TAG = "YoutubeWeb"
