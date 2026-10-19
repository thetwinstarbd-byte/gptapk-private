package com.example.islamicassistant

import android.Manifest
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import android.os.Build
import android.app.AlarmManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.location.LocationManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private val Green = Color(0xFF176B4D)
private val DeepGreen = Color(0xFF104C38)
private val PaleGreen = Color(0xFFEAF5EE)
private val WarmBg = Color(0xFFF7F8F4)
private val Ink = Color(0xFF1C2923)

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    private var ttsReady by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale("bn", "BD")
        }
        setContent {
            IslamicApp(
                speak = { text ->
                    if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "islamic-ai-answer")
                }
            )
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

private data class ChatMessage(val text: String, val fromUser: Boolean, val sourceNote: String? = null)

@Composable
private fun IslamicApp(speak: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("islamic_ai", android.content.Context.MODE_PRIVATE) }
    var selectedTab by remember { mutableStateOf(0) }
    var showChat by remember { mutableStateOf(false) }
    var openChatWith by remember { mutableStateOf<String?>(null) }
    var historyLoaded by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<ChatMessage>() }

    LaunchedEffect(Unit) {
        try {
            val saved = prefs.getString("chat_history", "[]") ?: "[]"
            val array = JSONArray(saved)
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                messages.add(ChatMessage(
                    text = item.optString("text"),
                    fromUser = item.optBoolean("fromUser"),
                    sourceNote = item.optString("sourceNote").takeIf { it.isNotBlank() && it != "null" }
                ))
            }
        } catch (_: Exception) { }
        historyLoaded = true
    }

    LaunchedEffect(messages.size, historyLoaded) {
        if (historyLoaded) {
            val array = JSONArray()
            messages.forEach { message ->
                array.put(JSONObject()
                    .put("text", message.text)
                    .put("fromUser", message.fromUser)
                    .put("sourceNote", message.sourceNote ?: ""))
            }
            prefs.edit().putString("chat_history", array.toString()).apply()
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green,
            onPrimary = Color.White,
            secondary = DeepGreen,
            background = WarmBg,
            surface = Color.White
        )
    ) {
        Scaffold(
            containerColor = WarmBg,
            bottomBar = {
                if (!showChat) NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.Default.Home, contentDescription = "হোম") }, label = { Text("হোম") })
                    NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.AccessTime, contentDescription = "নামাজ") }, label = { Text("নামাজ") })
                    NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Default.MenuBook, contentDescription = "কুরআন ও দোয়া") }, label = { Text("কুরআন") })
                    NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = { Icon(Icons.Default.Favorite, contentDescription = "টুলস") }, label = { Text("আমল") })
                    NavigationBarItem(selected = selectedTab == 4, onClick = { selectedTab = 4 }, icon = { Icon(Icons.Default.Settings, contentDescription = "সেটিংস") }, label = { Text("সেটিংস") })
                }
            }
        ) { padding ->
            if (showChat) {
                ChatScreen(
                    modifier = Modifier.padding(padding), messages = messages,
                    initialPrompt = openChatWith, onInitialPromptConsumed = { openChatWith = null },
                    onClearHistory = { messages.clear() }, speak = speak,
                    onBack = { showChat = false }
                )
            } else when (selectedTab) {
                0 -> HomeScreen(modifier = Modifier.padding(padding), onAsk = { prompt -> openChatWith = prompt; showChat = true }, onOpenTab = { selectedTab = it })
                1 -> PrayerScreen(Modifier.padding(padding))
                2 -> LibraryScreen(Modifier.padding(padding))
                3 -> ToolsScreen(Modifier.padding(padding))
                else -> SettingsScreen(Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier = Modifier,
    onAsk: (String) -> Unit,
    onOpenTab: (Int) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).background(Green, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("আসসালামু আলাইকুম", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("আপনার ইসলামিক সহকারী", color = Color(0xFF63746A), fontSize = 14.sp)
                }
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(DeepGreen, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Text("জানুন, বুঝুন, আমল করুন", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "নামাজ, কুরআনিক দোয়া, নির্বাচিত হাদিস, আমল ও অফলাইন ইসলামিক প্রশ্নোত্তর—ইন্টারনেট ছাড়াও ব্যবহার করুন।",
                    color = Color(0xFFE1F1E7), fontSize = 14.sp, lineHeight = 21.sp
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onAsk("") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = DeepGreen),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("অফলাইনে প্রশ্ন করুন")
                }
            }
        }
        item {
            Text("দ্রুত প্রবেশ", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("নামাজ", Icons.Default.AccessTime, Modifier.weight(1f)) { onOpenTab(1) }
                QuickTile("দোয়া/হাদিস", Icons.Default.MenuBook, Modifier.weight(1f)) { onOpenTab(2) }
                QuickTile("আমল", Icons.Default.FavoriteBorder, Modifier.weight(1f)) { onOpenTab(3) }
            }
        }
        item {
            Text("আজকের স্মরণ", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ", fontSize = 22.sp, color = Green, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("পরম করুণাময়, অসীম দয়ালু আল্লাহর নামে।", color = Ink, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("দৈনিক স্মরণ", color = Color(0xFF7B857F), fontSize = 11.sp)
                }
            }
        }
        item {
            val hijriDate = remember {
                try {
                    java.time.chrono.HijrahDate.now().format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale("bn", "BD")))
                } catch (_: Exception) { "হিজরি তারিখ দেখানো যাচ্ছে না" }
            }
            val dailyHadith = remember {
                val day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
                HadithData.items[day % HadithData.items.size]
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("আজকের হিজরি তারিখ", fontWeight = FontWeight.Bold, color = DeepGreen)
                    Text(hijriDate, fontSize = 18.sp, color = Green, fontWeight = FontWeight.SemiBold)
                    HorizontalDivider()
                    Text("আজকের হাদিস • ${dailyHadith.title}", fontWeight = FontWeight.Bold, color = Ink)
                    Text(dailyHadith.meaning, color = Ink, lineHeight = 21.sp)
                    Text(dailyHadith.reference, color = Color(0xFF68766D), fontSize = 11.sp)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = PaleGreen), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Green, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("দলিলকে গুরুত্ব দিন", fontWeight = FontWeight.Bold, color = DeepGreen)
                        Text("AI-এর উত্তর যাচাই করুন; গুরুত্বপূর্ণ ফতোয়ার জন্য যোগ্য আলেমের পরামর্শ নিন।", fontSize = 13.sp, color = Ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTile(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = Green, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, color = Ink, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ChatScreen(
    modifier: Modifier = Modifier,
    messages: androidx.compose.runtime.snapshots.SnapshotStateList<ChatMessage>,
    initialPrompt: String?,
    onInitialPromptConsumed: () -> Unit,
    onClearHistory: () -> Unit,
    speak: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("islamic_ai", android.content.Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("ইন্টারনেট ছাড়াই সীমিত ইসলামিক জ্ঞানভাণ্ডার থেকে উত্তর") }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var reportTarget by remember { mutableStateOf<String?>(null) }
    var reportReason by remember { mutableStateOf("ভুল বা বিভ্রান্তিকর তথ্য") }
    var reportSaved by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startSpeechRecognition(context, { recognizer = it }, { result -> input = result }, { status = it })
        } else status = "ভয়েস ইনপুটের জন্য মাইক্রোফোন অনুমতি প্রয়োজন।"
    }

    LaunchedEffect(initialPrompt) {
        if (!initialPrompt.isNullOrBlank()) {
            input = initialPrompt
            onInitialPromptConsumed()
        }
    }

    Column(modifier.fillMaxSize().background(WarmBg)) {
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "ফিরে যান", tint = Green) }
            Column(Modifier.weight(1f)) {
                Text("অফলাইন ইসলামিক সহকারী", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(status, fontSize = 11.sp, color = Color(0xFF6B786F))
            }
            IconButton(onClick = {
                onClearHistory()
                status = "এই ডিভাইসের চ্যাট ইতিহাস মুছে ফেলা হয়েছে।"
            }) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "চ্যাট ইতিহাস মুছুন", tint = Green)
            }
        }
        HorizontalDivider(color = Color(0xFFE8ECE8))
        if (messages.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(72.dp).background(PaleGreen, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Green, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("কী জানতে চান?", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(8.dp))
                Text("ইসলাম সম্পর্কে প্রশ্ন লিখুন বা মাইক্রোফোনে বলুন।", textAlign = TextAlign.Center, color = Color(0xFF69766D))
                Spacer(Modifier.height(18.dp))
                listOf("নামাজ সম্পর্কে জানতে চাই", "কুরআনের কোনো আয়াত খুঁজুন", "সকাল-সন্ধ্যার যিকির").forEach { suggestion ->
                    OutlinedButton(
                        onClick = { input = suggestion },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(suggestion) }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message, onSpeak = { speak(message.text) }, onReport = { reportTarget = message.text; reportSaved = false })
                }
                if (loading) item {
                    Text("উত্তর তৈরি হচ্ছে…", color = Green, modifier = Modifier.padding(8.dp))
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(Color.White).padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().background(WarmBg, RoundedCornerShape(18.dp)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        startSpeechRecognition(context, { recognizer = it }, { input = it }, { status = it })
                    } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) {
                    Icon(Icons.Default.Mic, contentDescription = "কথা বলুন", tint = Green)
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontSize = 15.sp),
                    decorationBox = { inner ->
                        if (input.isEmpty()) Text("আপনার প্রশ্ন লিখুন…", color = Color.Gray, fontSize = 14.sp)
                        inner()
                    }
                )
                IconButton(
                    enabled = input.isNotBlank() && !loading,
                    onClick = {
                        val question = input.trim()
                        input = ""
                        messages.add(ChatMessage(question, true))
                        loading = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { askBackend(question) }
                            messages.add(ChatMessage(result.first, false, result.second))
                            status = result.second
                            loading = false
                        }
                    }
                ) {
                    Icon(Icons.Default.Send, contentDescription = "পাঠান", tint = if (input.isNotBlank()) Green else Color.LightGray)
                }
            }
            Text("AI ভুল করতে পারে। গুরুত্বপূর্ণ মাসআলা যাচাই করুন।", fontSize = 10.sp, color = Color(0xFF7B857F), modifier = Modifier.padding(start = 8.dp, top = 6.dp))
        }
    }

    if (reportTarget != null) {
        AlertDialog(
            onDismissRequest = { reportTarget = null },
            title = { Text("উত্তর রিপোর্ট করুন") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("কেন রিপোর্ট করছেন?")
                    listOf("ভুল বা বিভ্রান্তিকর তথ্য", "ভুল/অনুপস্থিত সূত্র", "অনুপযুক্ত উত্তর", "অন্যান্য").forEach { reason ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = reportReason == reason, onClick = { reportReason = reason })
                            Text(reason, modifier = Modifier.clickable { reportReason = reason })
                        }
                    }
                    if (reportSaved) Text("রিপোর্ট এই ডিভাইসে সংরক্ষিত হয়েছে।", color = Green)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val key = "local_reports"
                        val reports = JSONArray(prefs.getString(key, "[]") ?: "[]")
                        reports.put(JSONObject().put("answer", reportTarget ?: "").put("reason", reportReason).put("createdAt", System.currentTimeMillis()))
                        prefs.edit().putString(key, reports.toString()).apply()
                        reportSaved = true
                        status = "রিপোর্ট স্থানীয়ভাবে সংরক্ষিত হয়েছে; সার্ভারে পাঠানো হয়নি।"
                        reportTarget = null
                    } catch (_: Exception) {
                        status = "রিপোর্ট সংরক্ষণ করা যায়নি।"
                        reportTarget = null
                    }
                }) { Text("সংরক্ষণ") }
            },
            dismissButton = { TextButton(onClick = { reportTarget = null }) { Text("বাতিল") } }
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, onSpeak: () -> Unit, onReport: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 320.dp)
                .background(if (message.fromUser) Green else Color.White, RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Text(message.text, color = if (message.fromUser) Color.White else Ink, fontSize = 15.sp, lineHeight = 22.sp)
            if (!message.fromUser) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message.sourceNote ?: "সূত্র যাচাইয়ের ব্যবস্থা সংযুক্ত হয়নি", color = Color(0xFF78837C), fontSize = 10.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onSpeak, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "উত্তর শুনুন", tint = Green, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onReport, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Flag, contentDescription = "রিপোর্ট", tint = Green, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

private suspend fun askBackend(question: String): Pair<String, String> {
    val endpoint = BuildConfig.SUPABASE_FUNCTION_URL
    val publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    if (endpoint.isBlank() || publishableKey.isBlank()) {
        val answer = OfflineIslamicData.findAnswer(question)
        return if (answer != null) {
            Pair("${answer.title}\n\n${answer.answer}\n\nসূত্র/নোট: ${answer.source}\n\nএটি সীমিত অফলাইন সহায়ক সংগ্রহ; জটিল বা ব্যক্তিগত ফতোয়ার জন্য যোগ্য আলেমের পরামর্শ নিন।", "অফলাইন জ্ঞানভাণ্ডার • ${answer.source}")
        } else {
            Pair("এই প্রশ্নের নির্ভরযোগ্য উত্তর বর্তমান অফলাইন জ্ঞানভাণ্ডারে নেই। আমি অনুমান করে ধর্মীয় উত্তর দেব না। কুরআন-হাদিসের নির্ভরযোগ্য উৎস বা যোগ্য আলেমের সাহায্য নিন। ইন্টারনেটভিত্তিক উন্নত AI পরে যুক্ত করা যাবে।", "অফলাইন সংগ্রহে উত্তর পাওয়া যায়নি")
        }
    }

    return try {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 45000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", publishableKey)
        }
        val body = JSONObject().put("message", question).put("language", "bn").toString()
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val responseText = stream.bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) {
            val offline = OfflineIslamicData.findAnswer(question)
            if (offline != null) Pair("${offline.title}\n\n${offline.answer}\n\nসূত্র/নোট: ${offline.source}\n\nএটি সীমিত অফলাইন সহায়ক সংগ্রহ; জটিল বা ব্যক্তিগত ফতোয়ার জন্য যোগ্য আলেমের পরামর্শ নিন।", "সার্ভার ত্রুটি ${connection.responseCode} • অফলাইন জ্ঞানভাণ্ডার • ${offline.source}")
            else Pair("সার্ভার থেকে উত্তর পাওয়া যায়নি এবং অফলাইন জ্ঞানভাণ্ডারেও এই প্রশ্নের নির্ভরযোগ্য উত্তর নেই। অনুমান করে ধর্মীয় উত্তর দেওয়া হচ্ছে না।", "সার্ভার ত্রুটি ${connection.responseCode} • অফলাইন উত্তর নেই")
        } else {
            val json = JSONObject(responseText)
            Pair(
                json.optString("answer", "উত্তর পাওয়া যায়নি।"),
                json.optString("source_status", "সূত্র যাচাই হয়নি")
            )
        }
    } catch (e: Exception) {
        val offline = OfflineIslamicData.findAnswer(question)
        if (offline != null) Pair("${offline.title}\n\n${offline.answer}\n\nসূত্র/নোট: ${offline.source}\n\nএটি সীমিত অফলাইন সহায়ক সংগ্রহ; জটিল বা ব্যক্তিগত ফতোয়ার জন্য যোগ্য আলেমের পরামর্শ নিন।", "ইন্টারনেট সংযোগ হয়নি • অফলাইন জ্ঞানভাণ্ডার • ${offline.source}")
        else Pair("ইন্টারনেট সংযোগ করা যায়নি এবং এই প্রশ্নের নির্ভরযোগ্য উত্তর বর্তমান অফলাইন জ্ঞানভাণ্ডারেও নেই। অনুমান করে ধর্মীয় উত্তর দেওয়া হচ্ছে না।", "অফলাইন উত্তর পাওয়া যায়নি")
    }
}

private fun startSpeechRecognition(
    context: android.content.Context,
    onRecognizer: (SpeechRecognizer) -> Unit,
    onResult: (String) -> Unit,
    onStatus: (String) -> Unit
) {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        onStatus("এই ডিভাইসে speech recognition পাওয়া যায়নি।")
        return
    }
    try {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        onRecognizer(recognizer)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onStatus("শুনছি… কথা বলুন।") }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { onStatus("কথা শনাক্ত করা হচ্ছে…") }
            override fun onError(error: Int) { onStatus("ভয়েস শনাক্ত করা যায়নি। আবার চেষ্টা করুন বা লিখে প্রশ্ন করুন।"); recognizer.destroy() }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) onResult(text)
                onStatus(if (text.isNullOrBlank()) "কথা শনাক্ত হয়নি।" else "ভয়েস থেকে লেখা তৈরি হয়েছে।")
                recognizer.destroy()
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "আপনার প্রশ্ন বলুন")
        }
        recognizer.startListening(intent)
    } catch (e: Exception) {
        onStatus("মাইক্রোফোন চালু করা যায়নি।")
    }
}

@Composable
private fun LibraryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("islamic_ai", android.content.Context.MODE_PRIVATE) }
    var query by remember { mutableStateOf("") }
    var selectedEntry by remember { mutableStateOf<OfflineDua?>(null) }
    var selectedHadith by remember { mutableStateOf<OfflineHadith?>(null) }
    var selectedQuran by remember { mutableStateOf<OfflineQuranPassage?>(null) }
    var libraryMode by remember { mutableStateOf("কুরআন") }
    var showPronunciation by remember { mutableStateOf(true) }
    var onlyFavorites by remember { mutableStateOf(false) }
    var favoriteIds by remember { mutableStateOf(prefs.getStringSet("dua_favorites", emptySet())?.toSet() ?: emptySet()) }
    val largeArabic = prefs.getBoolean("pref_large_arabic", false)
    val filtered = OfflineIslamicData.duas.filter { d ->
        (!onlyFavorites || favoriteIds.contains(d.title)) &&
            (query.isBlank() || listOf(d.title, d.category, d.meaning, d.reference, d.keywords, d.arabic, d.banglaPronunciation).any { it.contains(query.trim(), ignoreCase = true) })
    }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("ইসলামিক লাইব্রেরি", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("নির্বাচিত কুরআনিক পাঠ, দোয়া-যিকির ও হাদিস—অফলাইনে পড়ুন।", color = Color(0xFF68766D), fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("কুরআন", "দোয়া", "হাদিস").forEach { mode ->
                FilterChip(selected = libraryMode == mode, onClick = { libraryMode = mode }, label = { Text(when (mode) { "হাদিস" -> "হাদিস (${HadithData.items.size})"; "দোয়া" -> "দোয়া (${OfflineIslamicData.duas.size})"; else -> "কুরআন (${OfflineQuranData.passages.size})" }) })
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(when (libraryMode) { "হাদিস" -> "হাদিস, বিষয়, অর্থ বা সূত্র খুঁজুন"; "দোয়া" -> "দোয়া, অর্থ, উচ্চারণ বা সূত্র খুঁজুন"; else -> "সূরা, আয়াত, অর্থ বা উচ্চারণ খুঁজুন" }) }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }, shape = RoundedCornerShape(14.dp))
        if (libraryMode != "হাদিস") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("বাংলা উচ্চারণ দেখান", modifier = Modifier.weight(1f), fontSize = 13.sp)
                Switch(checked = showPronunciation, onCheckedChange = { showPronunciation = it })
            }
        }
        if (libraryMode == "দোয়া") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("শুধু পছন্দের দোয়া", modifier = Modifier.weight(1f), fontSize = 13.sp)
                Switch(checked = onlyFavorites, onCheckedChange = { onlyFavorites = it })
            }
        }
        if (libraryMode == "কুরআন") {
            val filteredQuran = OfflineQuranData.passages.filter { q -> query.isBlank() || listOf(q.title, q.arabic, q.banglaPronunciation, q.meaning, q.reference).any { it.contains(query.trim(), ignoreCase = true) } }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                items(filteredQuran) { q ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { selectedQuran = q }, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(q.title, fontWeight = FontWeight.Bold, color = Ink, fontSize = 16.sp)
                            Text(q.arabic, fontSize = 25.sp, color = Green, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(), lineHeight = 40.sp)
                            if (showPronunciation) Text("বাংলা উচ্চারণ: ${q.banglaPronunciation}", color = DeepGreen, fontSize = 14.sp, lineHeight = 22.sp)
                            Text(q.meaning, color = Ink, fontSize = 14.sp, lineHeight = 21.sp)
                            Text(q.reference, color = Color(0xFF68766D), fontSize = 11.sp)
                        }
                    }
                }
            }
        } else if (libraryMode == "হাদিস") {
            val filteredHadith = HadithData.items.filter { h -> query.isBlank() || listOf(h.title, h.arabic, h.banglaPronunciation, h.meaning, h.reference, h.topics).any { it.contains(query.trim(), ignoreCase = true) } }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                items(filteredHadith) { h ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { selectedHadith = h }, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(h.title, fontWeight = FontWeight.Bold, color = Ink, fontSize = 16.sp)
                            Text(h.arabic, fontSize = 25.sp, color = Green, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(), lineHeight = 39.sp)
                            if (showPronunciation) Text("বাংলা উচ্চারণ: ${h.banglaPronunciation}", color = DeepGreen, fontSize = 14.sp, lineHeight = 22.sp)
                            Text(h.meaning, color = Ink, fontSize = 14.sp, lineHeight = 21.sp)
                            Text(h.reference, color = Color(0xFF68766D), fontSize = 11.sp)
                        }
                    }
                }
            }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
            items(filtered) { entry ->
                Card(modifier = Modifier.fillMaxWidth().clickable { selectedEntry = entry }, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.category, color = Green, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text(entry.title, fontWeight = FontWeight.Bold, color = Ink, fontSize = 16.sp)
                            }
                            IconButton(onClick = {
                                favoriteIds = if (favoriteIds.contains(entry.title)) favoriteIds - entry.title else favoriteIds + entry.title
                                prefs.edit().putStringSet("dua_favorites", favoriteIds).apply()
                            }) { Icon(if (favoriteIds.contains(entry.title)) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "পছন্দে রাখুন", tint = Green) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(entry.arabic, fontSize = if (largeArabic) 30.sp else 24.sp, color = Green, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(), lineHeight = if (largeArabic) 44.sp else 38.sp)
                        if (showPronunciation) { Spacer(Modifier.height(6.dp)); Text("বাংলা উচ্চারণ: ${entry.banglaPronunciation}", color = DeepGreen, fontSize = 14.sp, lineHeight = 22.sp) }
                        Spacer(Modifier.height(6.dp)); Text(entry.meaning, color = Ink, fontSize = 14.sp, lineHeight = 21.sp)
                        Spacer(Modifier.height(6.dp)); Text(entry.reference, color = Color(0xFF68766D), fontSize = 11.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { selectedEntry = entry }) { Text("বিস্তারিত") }
                        }
                    }
                }
            }
        }
    }
    selectedEntry?.let { entry ->
        AlertDialog(onDismissRequest = { selectedEntry = null }, title = { Text(entry.title) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(entry.arabic, fontSize = 25.sp, color = Green, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                Text("বাংলা উচ্চারণ: ${entry.banglaPronunciation}", color = DeepGreen)
                Text(entry.meaning)
                Text("সূত্র: ${entry.reference}", fontSize = 12.sp, color = Color(0xFF68766D))
            }
        }, confirmButton = { TextButton(onClick = { selectedEntry = null }) { Text("বন্ধ করুন") } })
    }
    selectedQuran?.let { q ->
        AlertDialog(onDismissRequest = { selectedQuran = null }, title = { Text(q.title) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(q.arabic, fontSize = 25.sp, color = Green, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(), lineHeight = 40.sp)
                Text("বাংলা উচ্চারণ: ${q.banglaPronunciation}", color = DeepGreen)
                Text(q.meaning)
                Text("সূত্র: ${q.reference}", fontSize = 12.sp, color = Color(0xFF68766D))
                Text("বাংলা উচ্চারণ আনুমানিক; শুদ্ধ তিলাওয়াত শেখার জন্য আরবি শিক্ষক/বিশ্বস্ত অডিও ব্যবহার করুন। এটি পূর্ণ কুরআন নয়, নির্বাচিত পাঠসমূহ।", fontSize = 11.sp, color = Color.Gray)
            }
        }, confirmButton = { TextButton(onClick = { selectedQuran = null }) { Text("বন্ধ করুন") } })
    }
    selectedHadith?.let { h ->
        AlertDialog(onDismissRequest = { selectedHadith = null }, title = { Text(h.title) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(h.arabic, fontSize = 26.sp, color = Green, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                Text("বাংলা উচ্চারণ: ${h.banglaPronunciation}", color = DeepGreen)
                Text(h.meaning)
                Text("সূত্র: ${h.reference}", fontSize = 12.sp, color = Color(0xFF68766D))
                Text("বাংলা উচ্চারণ শেখার সহায়ক; আরবি পাঠ ও হাদিসের সংস্করণ নির্ভরযোগ্য উৎসে যাচাই করুন।", fontSize = 11.sp, color = Color.Gray)
            }
        }, confirmButton = { TextButton(onClick = { selectedHadith = null }) { Text("বন্ধ করুন") } })
    }
}

@Composable
private fun PrayerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("islamic_ai", android.content.Context.MODE_PRIVATE) }
    var latText by remember { mutableStateOf(prefs.getString("latitude", "23.8103") ?: "23.8103") }
    var lonText by remember { mutableStateOf(prefs.getString("longitude", "90.4125") ?: "90.4125") }
    var hanafi by remember { mutableStateOf(prefs.getBoolean("hanafi_asr", true)) }
    var adhanEnabled by remember { mutableStateOf(prefs.getBoolean("adhan_enabled", false)) }
    var saved by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { prefs.edit().putBoolean("adhan_enabled", true).apply(); adhanEnabled = true; PrayerAlarmScheduler.scheduleNext(context) }
    }
    val lat = latText.toDoubleOrNull()?.coerceIn(-90.0, 90.0) ?: 23.8103
    val lon = lonText.toDoubleOrNull()?.coerceIn(-180.0, 180.0) ?: 90.4125
    val schedule = remember(lat, lon, hanafi) { PrayerCalculator.calculate(lat, lon, hanafiAsr = hanafi) }
    val bearing = remember(lat, lon) { PrayerCalculator.qiblaBearing(lat, lon) }
    LaunchedEffect(adhanEnabled, lat, lon, hanafi) { if (adhanEnabled) PrayerAlarmScheduler.scheduleNext(context) }
    Column(modifier.fillMaxSize().padding(18.dp)) {
        Text("নামাজের সময় ও কিবলা", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("স্থানীয় সূর্য-হিসাবের আনুমানিক সময়। আপনার এলাকার মসজিদ/ইসলামিক কর্তৃপক্ষের সময়সূচির সঙ্গে মিলিয়ে নিন।", color = Color(0xFF68766D), fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Card(colors = CardDefaults.cardColors(containerColor = if (adhanEnabled) PaleGreen else Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("অফলাইন আজান রিমাইন্ডার", fontWeight = FontWeight.Bold, color = Ink)
                        Text("সময় হলে তোমার দেওয়া রেকর্ড করা আজান চালাবে। ফজরের জন্য আলাদা অডিও ব্যবহার হবে।", fontSize = 12.sp, color = Color(0xFF68766D), lineHeight = 18.sp)
                    }
                    Switch(checked = adhanEnabled, onCheckedChange = { enabled ->
                        if (enabled) {
                            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                prefs.edit().putBoolean("adhan_enabled", true).apply(); adhanEnabled = true; PrayerAlarmScheduler.scheduleNext(context)
                            }
                        } else {
                            adhanEnabled = false; prefs.edit().putBoolean("adhan_enabled", false).apply(); PrayerAlarmScheduler.cancel(context)
                        }
                    })
                }
                if (adhanEnabled) Text("আজান চালু আছে। ফজরে আলাদা অডিও, অন্য ওয়াক্তে সাধারণ আজান বাজবে। ফোনের অ্যালার্ম/ব্যাটারি সেটিংসের কারণে সময় কিছুটা বদলাতে পারে।", color = Green, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        try {
                            val testIntent = Intent(context, AdhanPlaybackService::class.java).putExtra("prayer_name", "ফজর")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(testIntent) else context.startService(testIntent)
                        } catch (_: Exception) { AdhanNotifications.show(context, "আজান টেস্ট", "ডিভাইসে আজান চালু করা যায়নি।") }
                    }) { Text("আজান টেস্ট করুন") }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        TextButton(onClick = {
                            try { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) } catch (_: Exception) { }
                        }) { Text("অ্যালার্ম অনুমতি") }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(colors = CardDefaults.cardColors(containerColor = DeepGreen), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("আজকের নামাজের সময়", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(10.dp))
                listOf("ফজর" to schedule.fajr, "সূর্যোদয়" to schedule.sunrise, "যোহর" to schedule.dhuhr, "আসর" to schedule.asr, "মাগরিব" to schedule.maghrib, "ইশা" to schedule.isha).forEach { (name, time) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, color = Color.White, modifier = Modifier.weight(1f))
                        Text(time, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("আপনার অবস্থান", fontWeight = FontWeight.Bold, color = Ink)
                Text("ডিফল্ট: ঢাকা। আপনার এলাকার অক্ষাংশ ও দ্রাঘিমাংশ লিখুন।", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = latText, onValueChange = { latText = it; saved = false }, modifier = Modifier.weight(1f), label = { Text("অক্ষাংশ") }, singleLine = true)
                    OutlinedTextField(value = lonText, onValueChange = { lonText = it; saved = false }, modifier = Modifier.weight(1f), label = { Text("দ্রাঘিমাংশ") }, singleLine = true)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("হানাফি আসর", fontWeight = FontWeight.Medium); Text("অন্য হিসাব চাইলে বন্ধ করুন", color = Color.Gray, fontSize = 11.sp) }
                    Switch(checked = hanafi, onCheckedChange = { hanafi = it; prefs.edit().putBoolean("hanafi_asr", it).apply() })
                }
                Button(onClick = {
                    val parsedLat = latText.toDoubleOrNull()
                    val parsedLon = lonText.toDoubleOrNull()
                    if (parsedLat != null && parsedLon != null && parsedLat in -90.0..90.0 && parsedLon in -180.0..180.0) {
                        prefs.edit().putString("latitude", latText).putString("longitude", lonText).apply(); saved = true
                        if (adhanEnabled) PrayerAlarmScheduler.scheduleNext(context)
                    } else saved = false
                }, modifier = Modifier.fillMaxWidth()) { Text("অবস্থান সংরক্ষণ") }
                if (saved) Text("অবস্থান এই ফোনে সংরক্ষিত হয়েছে।", color = Green, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(colors = CardDefaults.cardColors(containerColor = PaleGreen), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("কিবলার দিক", fontWeight = FontWeight.Bold, color = DeepGreen)
                Text("উত্তর দিক থেকে ঘড়ির কাঁটার দিকে ${"%.1f".format(java.util.Locale.US, bearing)}°", fontSize = 20.sp, color = Green, fontWeight = FontWeight.Bold)
                Text("এটি অবস্থানভিত্তিক দিকের হিসাব, সেন্সর-ভিত্তিক ঘূর্ণায়মান কম্পাস নয়। ফোনের কম্পাস/ম্যাপ দিয়ে যাচাই করুন।", fontSize = 12.sp, color = Ink)
            }
        }
    }
}

@Composable
private fun ToolsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("islamic_ai", android.content.Context.MODE_PRIVATE) }
    var tool by remember { mutableStateOf("তাসবিহ") }
    var count by remember { mutableIntStateOf(prefs.getInt("tasbih_count", 0)) }
    var target by remember { mutableIntStateOf(prefs.getInt("tasbih_target", 100)) }
    var zakatAssets by remember { mutableStateOf("") }
    var zakatDebts by remember { mutableStateOf("") }
    var nisab by remember { mutableStateOf("0") }
    var note by remember { mutableStateOf("") }
    val todayKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    val prayerKey = "prayer_track_$todayKey"
    var prayerBits by remember { mutableStateOf(prefs.getString(prayerKey, "00000") ?: "00000") }
    var fastDone by remember { mutableStateOf(prefs.getBoolean("fast_$todayKey", false)) }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("আমল ও প্রয়োজনীয় টুলস", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("তাসবিহ", "নামাজ", "রোজা", "যাকাত", "শেখা").forEach { name ->
                FilterChip(selected = tool == name, onClick = { tool = name }, label = { Text(name) })
            }
        }
        Spacer(Modifier.height(12.dp))
        when (tool) {
            "তাসবিহ" -> {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ডিজিটাল তাসবিহ", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text("সুবহানাল্লাহ • আলহামদুলিল্লাহ • আল্লাহু আকবার", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(20.dp))
                        Box(Modifier.size(190.dp).background(PaleGreen, CircleShape).clickable {
                            count += 1; prefs.edit().putInt("tasbih_count", count).apply()
                        }, contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(count.toString(), fontSize = 52.sp, fontWeight = FontWeight.Bold, color = Green)
                                Text("ট্যাপ করে গণনা", color = DeepGreen, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { count = 0; prefs.edit().putInt("tasbih_count", 0).apply() }) { Text("রিসেট") }
                            OutlinedButton(onClick = { target = if (target == 33) 100 else 33; prefs.edit().putInt("tasbih_target", target).apply() }) { Text("লক্ষ্য: $target") }
                        }
                        Text(if (count >= target) "লক্ষ্য পূর্ণ হয়েছে—আলহামদুলিল্লাহ" else "লক্ষ্য পূরণে বাকি: ${(target - count).coerceAtLeast(0)}", color = Green, modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
            "নামাজ" -> {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("আজকের নামাজ ট্র্যাকার", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("এটি ব্যক্তিগত নোট; স্বয়ংক্রিয়ভাবে নামাজ যাচাই করে না।", color = Color.Gray, fontSize = 12.sp)
                        listOf("ফজর", "যোহর", "আসর", "মাগরিব", "ইশা").forEachIndexed { i, name ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = prayerBits.getOrNull(i) == '1', onCheckedChange = { checked ->
                                    prayerBits = prayerBits.toCharArray().also { it[i] = if (checked) '1' else '0' }.concatToString()
                                    prefs.edit().putString(prayerKey, prayerBits).apply()
                                })
                                Text(name, modifier = Modifier.weight(1f))
                                Text(if (prayerBits.getOrNull(i) == '1') "সম্পন্ন" else "বাকি", color = if (prayerBits.getOrNull(i) == '1') Green else Color.Gray, fontSize = 12.sp)
                            }
                        }
                        Text("সম্পন্ন: ${prayerBits.count { it == '1' }} / 5", fontWeight = FontWeight.Bold, color = Green)
                    }
                }
            }
            "রোজা" -> {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("ব্যক্তিগত রোজা ট্র্যাকার", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("আজ রোজা রেখেছেন কি না নিজের জন্য চিহ্নিত করুন। এটি স্বয়ংক্রিয়ভাবে যাচাই করে না এবং রোজার শরয়ি বৈধতার সিদ্ধান্ত দেয় না।", color = Color.Gray, fontSize = 12.sp, lineHeight = 18.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("আজ রোজা রেখেছি", modifier = Modifier.weight(1f))
                            Switch(checked = fastDone, onCheckedChange = { checked -> fastDone = checked; prefs.edit().putBoolean("fast_$todayKey", checked).apply() })
                        }
                        Text(if (fastDone) "আজকের রেকর্ড সংরক্ষিত হয়েছে।" else "আজকের রোজার রেকর্ড এখনো চিহ্নিত হয়নি।", color = Green, fontWeight = FontWeight.Medium)
                        Text("সফর, অসুস্থতা, ঋতুস্রাব ও অন্যান্য পরিস্থিতিতে রোজার বিধান আলাদা হতে পারে; নির্দিষ্ট বিষয়ে আলেমের পরামর্শ নিন।", color = Color.Gray, fontSize = 11.sp)
                    }
                }
            }
            "যাকাত" -> {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("যাকাতের আনুমানিক হিসাব", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("টাকায় লিখুন। নিসাবের মান স্থানীয় স্বর্ণ/রূপার দাম ও অনুসৃত ফিকহ অনুযায়ী নিজে যাচাই করে দিন।", fontSize = 12.sp, color = Color.Gray)
                        OutlinedTextField(value = zakatAssets, onValueChange = { zakatAssets = it }, modifier = Modifier.fillMaxWidth(), label = { Text("যাকাতযোগ্য সম্পদ (৳)") }, singleLine = true)
                        OutlinedTextField(value = zakatDebts, onValueChange = { zakatDebts = it }, modifier = Modifier.fillMaxWidth(), label = { Text("বাদযোগ্য তাৎক্ষণিক দায় (৳)") }, singleLine = true)
                        OutlinedTextField(value = nisab, onValueChange = { nisab = it }, modifier = Modifier.fillMaxWidth(), label = { Text("নিসাবের মান (৳) — নিজে যাচাই করুন") }, singleLine = true)
                        val net = ((zakatAssets.toDoubleOrNull() ?: 0.0) - (zakatDebts.toDoubleOrNull() ?: 0.0)).coerceAtLeast(0.0)
                        val threshold = nisab.toDoubleOrNull() ?: 0.0
                        val due = if (threshold > 0 && net >= threshold) net * 0.025 else 0.0
                        Text("নিট হিসাব: ৳${"%.2f".format(java.util.Locale.US, net)}", fontWeight = FontWeight.Medium)
                        Text(if (threshold <= 0) "নিসাবের মান দিন; এটি ছাড়া ফল নির্ধারণ করা যাবে না।" else "আনুমানিক যাকাত: ৳${"%.2f".format(java.util.Locale.US, due)}", color = Green, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("এটি সরল আনুমানিক ক্যালকুলেটর—সব সম্পদের বিধান এতে নেই। ব্যক্তিগত হিসাব আলেম/যাকাত বিশেষজ্ঞ দিয়ে যাচাই করুন।", color = Color.Gray, fontSize = 11.sp)
                    }
                }
            }
            else -> {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("দ্রুত শেখার গাইড", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("অজুর সাধারণ ধাপ", fontWeight = FontWeight.SemiBold, color = Green)
                        Text("১. নিয়ত করে আল্লাহর নামে শুরু করুন। ২. হাত ধুয়ে কুলি ও নাকে পানি দিন। ৩. মুখ ধুয়ে দুই হাত কনুইসহ ধুয়ে নিন। ৪. মাথা মাসেহ করুন। ৫. দুই পা টাখনুসহ ধুয়ে নিন। বিস্তারিত সুন্নাহ ও মাজহাবভেদে বিশ্বস্ত শিক্ষক থেকে শিখুন।", lineHeight = 22.sp)
                        HorizontalDivider()
                        Text("নামাজ শেখা", fontWeight = FontWeight.SemiBold, color = Green)
                        Text("নামাজের শর্ত, ফরজ, ওয়াজিব ও সুন্নাহর বিস্তারিত শেখার জন্য নির্ভরযোগ্য আলেম/শিক্ষকের নির্দেশনা নিন। অ্যাপের অফলাইন FAQ পূর্ণ ফিকহ গ্রন্থ নয়।", lineHeight = 22.sp)
                        HorizontalDivider()
                        Text("দোয়া ও যিকির", fontWeight = FontWeight.SemiBold, color = Green)
                        Text("কুরআন ট্যাবে আরবি পাঠের সঙ্গে বাংলা উচ্চারণ, অর্থ ও সূত্র আছে। বাংলা উচ্চারণ আনুমানিক; সম্ভব হলে আরবি মাখরাজ শিক্ষক/অডিও থেকে শিখুন।", lineHeight = 22.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("islamic_ai", android.content.Context.MODE_PRIVATE) }
    var largeArabic by remember { mutableStateOf(prefs.getBoolean("pref_large_arabic", false)) }
    LaunchedEffect(largeArabic) { prefs.edit().putBoolean("pref_large_arabic", largeArabic).apply() }
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("সেটিংস", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp)) {
                SettingToggle("বড় আরবি পাঠ", "কুরআন ও দোয়া লাইব্রেরিতে আরবি লেখা বড় হবে", largeArabic) { largeArabic = it }
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = PaleGreen), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("এই সংস্করণে যা অফলাইনে আছে", fontWeight = FontWeight.Bold, color = DeepGreen)
                Text("• ${OfflineQuranData.passages.size}টি নির্বাচিত কুরআনিক পাঠ, আরবি, বাংলা উচ্চারণ ও অর্থ")
                Text("• ${OfflineIslamicData.duas.size}টি বাছাই করা দোয়া/যিকির, আরবি পাঠ, বাংলা উচ্চারণ, অর্থ ও সূত্র")
                Text("• ${OfflineIslamicData.answers.size}টি বিষয়ভিত্তিক ইসলামি প্রশ্নোত্তর")
                Text("• ${HadithData.items.size}টি নির্বাচিত হাদিস: আরবি, বাংলা উচ্চারণ, অর্থ ও সূত্র")
                Text("• নামাজের আনুমানিক সময়, কিবলা-বিয়ারিং ও আজান রিমাইন্ডার")
                Text("• তাসবিহ, নামাজ/রোজা ট্র্যাকার, হিজরি তারিখ ও সরল যাকাত হিসাব")
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("সীমাবদ্ধতা ও সতর্কতা", fontWeight = FontWeight.Bold, color = Ink)
        Text("এটি পূর্ণ কুরআন, পূর্ণ হাদিস-গ্রন্থ বা সব মাসআলার অফলাইন ভাণ্ডার নয়। বাংলা উচ্চারণ আরবি মাখরাজের বিকল্প নয়। নামাজের সময় স্থানীয় মসজিদের সময়সূচির সঙ্গে মিলিয়ে নিন; ব্যক্তিগত ফতোয়া, উত্তরাধিকার ও জটিল যাকাতের জন্য যোগ্য আলেমের পরামর্শ নিন।", color = Color(0xFF68766D), fontSize = 13.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(12.dp))
        Text("গোপনীয়তা", fontWeight = FontWeight.Bold, color = Ink)
        Text("চ্যাট, পছন্দের দোয়া, অবস্থান ও ট্র্যাকার এই ফোনে সংরক্ষিত হয়। এই সংস্করণে ক্লাউড ব্যাকআপ নেই। AI API key কখনো Android অ্যাপে রাখা উচিত নয়।", color = Color(0xFF68766D), fontSize = 13.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun SettingToggle(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color.Gray, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
