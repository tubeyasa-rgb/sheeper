package com.ar5as.mashwar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class ComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db=RideDb.open(applicationContext)
        @Suppress("DEPRECATION") val shared: Uri? = if (intent?.action == Intent.ACTION_SEND) intent.getParcelableExtra(Intent.EXTRA_STREAM) else null
        setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xff185b48), onPrimary = Color.White, secondary = Color(0xff668b4b), background = Color(0xfff6f7f2), surface = Color.White, onSurface = Color(0xff183a30), outline = Color(0xffbacbc0))) { App(db,shared) }
        } }
    }
}

@Composable fun App(db: RideDb, shared: Uri?) {
    val context=LocalContext.current; val dao=db.observations(); val scope=rememberCoroutineScope()
    val items by dao.observe().collectAsState(initial=emptyList())
    val pickups by dao.observePickups().collectAsState(initial=emptyList())
    val traffic by dao.observeTraffic().collectAsState(initial=emptyList())
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15000) } }
    var screen by rememberSaveable { mutableStateOf(if (shared != null) "capture" else "home") }
    var origin by rememberSaveable { mutableStateOf("") }; var destination by rememberSaveable { mutableStateOf("") }
    var locationInfo by rememberSaveable { mutableStateOf("") }; var locating by remember { mutableStateOf(false) }; var showLocationSettings by remember { mutableStateOf(false) }
    var selectedDestinationCoordinates by rememberSaveable { mutableStateOf<String?>(null) }
    var searchingPlaces by remember { mutableStateOf(false) }; var places by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }; var placesMessage by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("") }; var category by remember { mutableStateOf("") }; var price by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }; var recognized by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var deleteConfirm by remember { mutableStateOf(false) }; var deleteItem by remember { mutableStateOf<Observation?>(null) }; var source by remember { mutableStateOf("USER_ENTERED") }
    var selectedId by remember { mutableLongStateOf(0L) }; var etaInput by rememberSaveable { mutableStateOf("") }; var distanceInput by rememberSaveable { mutableStateOf("") }
    var routeText by remember { mutableStateOf("") }; var routeDurations by remember { mutableStateOf<List<ParsedMeasure>>(emptyList()) }; var routeDistances by remember { mutableStateOf<List<ParsedMeasure>>(emptyList()) }; var distanceFromImage by remember { mutableStateOf(false) }
    var paid by remember { mutableStateOf("") }; var accepted by remember { mutableStateOf("") }; var success by remember { mutableStateOf<Boolean?>(null) }
    var pickupName by remember { mutableStateOf("") }; var pickupNote by remember { mutableStateOf("") }; var pickupStatus by remember { mutableStateOf("UNKNOWN") }
    var walkMeters by remember { mutableStateOf("") }; var walkMinutes by remember { mutableStateOf("") }; var direct by remember { mutableStateOf(false) }
    var comparisonOrigin by rememberSaveable { mutableStateOf("") }; var maxWalkMeters by rememberSaveable { mutableStateOf("500") }; var maxWalkMinutes by rememberSaveable { mutableStateOf("10") }
    var valueOfTime by rememberSaveable { mutableStateOf("0") }; var maxWait by rememberSaveable { mutableIntStateOf(30) }
    var options by remember { mutableStateOf<List<DepartureOption>>(emptyList()) }; var peakHours by remember { mutableStateOf<List<PeakHour>>(emptyList()) }; var cheaperHours by remember { mutableStateOf<List<PeakHour>>(emptyList()) }; var travelPredictions by remember { mutableStateOf<Map<Int,TripTimeForecast?>>(emptyMap()) }; var analysisRunning by remember { mutableStateOf(false) }
    val reference = remember(items,comparisonOrigin,destination) { items.firstOrNull { (comparisonOrigin.isBlank() || Logic.same(it.origin,comparisonOrigin)) && (destination.isBlank() || Logic.same(it.destination,destination)) } }
    LaunchedEffect(items, traffic, now / 60000, screen, valueOfTime, maxWait, comparisonOrigin, destination) {
        if(screen == "forecast") {
            analysisRunning=true; options=emptyList(); peakHours=emptyList(); cheaperHours=emptyList(); travelPredictions=emptyMap()
            val timeValue=Logic.parseNonNegative(valueOfTime)
            val result=withContext(Dispatchers.Default) {
                if(reference==null||timeValue==null) Pair(Triple(emptyList(),emptyList(),emptyList()),emptyMap())
                else Pair(Triple(ForecastEngine.departureOptions(items,reference,now,timeValue,maxWait),ForecastEngine.peakHours(items,reference,now),ForecastEngine.cheaperHours(items,reference,now)), listOf(5,10,15,30).filter { it<=maxWait }.associateWith { TrafficEngine.forecastDuration(traffic,reference,now,now+it*60_000L) })
            }
            options=result.first.first; peakHours=result.first.second; cheaperHours=result.first.third; travelPredictions=result.second; analysisRunning=false
        }
    }
    BackHandler(enabled = screen != "home") { screen = "home"; message = "" }
    fun locate() {
        if (locating) return
        scope.launch {
            locating = true
            showLocationSettings = false
            locationInfo = "بنحدد مكانك..."
            try {
                when (val result = currentLocation(context)) {
                    is LocationResult.Found -> {
                        origin = String.format(java.util.Locale.US, "%.6f,%.6f", result.location.latitude, result.location.longitude)
                        comparisonOrigin = origin
                        locationInfo = "تم تحديد موقعك. تقدر تعدله قبل فتح الخرائط."
                    }
                    LocationResult.PermissionRequired -> locationInfo = "اسمح بالوصول للموقع عشان نحدد مكانك، أو اكتب نقطة الانطلاق يدويًا."
                    LocationResult.Disabled -> { locationInfo = "شغّل الموقع في إعدادات جهازك، أو اكتب نقطة الانطلاق يدويًا."; showLocationSettings = true }
                    LocationResult.Unavailable -> locationInfo = "ما قدرناش نحدد مكانك دلوقتي. حاول في مكان مفتوح أو اكتب نقطة الانطلاق يدويًا."
                }
            } finally { locating = false }
        }
    }
    val requestLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) locate()
        else locationInfo = "إذن الموقع غير مفعّل. تقدر تكتب نقطة الانطلاق بنفسك."
    }
    fun requestCurrentLocation() {
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (allowed) locate() else requestLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    fun findPlaces() {
        val query = destination.trim()
        if (query.isEmpty()) { placesMessage = "اكتب اسم المكان الأول."; return }
        if (query.length > 200) { placesMessage = "اسم المكان طويل جدًا."; return }
        if (searchingPlaces) return
        scope.launch {
            searchingPlaces = true; places = emptyList(); placesMessage = ""
            try {
                when (val result = searchPlaces(context, query)) {
                    is PlaceSearchResult.Found -> if (destination.trim() == query) {
                        places = result.places
                        placesMessage = if (places.isEmpty()) "ملقيناش نتائج. جرّب اسم تاني أو افتح Google Maps." else "اختار المكان من النتائج."
                    }
                    PlaceSearchResult.Unavailable -> placesMessage = "البحث مش متاح على الجهاز دلوقتي. تقدر تفتح Google Maps مباشرة."
                }
            } finally { searchingPlaces = false }
        }
    }
    fun openTripInMaps() {
        val url = MapsLinks.forTrip(origin, selectedDestinationCoordinates ?: destination)
        if (url == null) { message = "اكتب الوجهة الأول عشان نفتحها على الخريطة."; return }
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: android.content.ActivityNotFoundException) { message = "مفيش تطبيق خرائط أو متصفح متاح على الجهاز." }
    }
    fun resetRouteInput() { etaInput=""; distanceInput=""; routeText=""; routeDurations=emptyList(); routeDistances=emptyList(); distanceFromImage=false }
    fun applyRouteText(text:String) {
        val parsed=TrafficTextParser.parse(text); routeText=text.take(5000); routeDurations=parsed.durations; routeDistances=parsed.distances
        etaInput=if(parsed.durations.size==1) parsed.durations.single().value.toString() else ""
        distanceInput=if(parsed.distances.size==1) parsed.distances.single().value.toString() else ""
        distanceFromImage=parsed.distances.size==1
        message=if(parsed.durations.isNotEmpty() || parsed.distances.isNotEmpty()) "راجع المدة والمسافة واختار المسار الصحيح قبل الحفظ." else "لم نقرأ مدة أو مسافة بوضوح؛ تقدر تكتبهم يدويًا."
    }
    fun scanRoute(uri:Uri) {
        val quoteId=selectedId
        try {
            message="بنقرأ صورة المسار على الجهاز..."
            val image=InputImage.fromFilePath(context,uri)
            val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image).addOnSuccessListener { if(screen=="traffic" && selectedId==quoteId) applyRouteText(it.text) }.addOnFailureListener { if(screen=="traffic" && selectedId==quoteId) message="تعذرت قراءة الصورة؛ تقدر تكتب المدة والمسافة يدويًا." }.addOnCompleteListener { recognizer.close() }
        } catch (_:Exception) { message="تعذّر فتح الصورة؛ أدخل بيانات المسار يدويًا." }
    }
    fun scan(uri: Uri) {
        try {
            message="جارٍ قراءة الصورة على الجهاز..."
            val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(InputImage.fromFilePath(context,uri)).addOnSuccessListener { recognized=it.text.take(5000); message="راجع النص وأدخل السعر بنفسك؛ القراءة قد تخطئ ولا تدعم كل النصوص العربية."; source="USER_CONFIRMED_SCREENSHOT" }.addOnFailureListener { message="تعذرت قراءة الصورة. يمكنك إدخال العرض يدويًا." }.addOnCompleteListener { recognizer.close() }
        } catch (_: Exception) { message="تعذّر فتح الصورة؛ أدخل العرض يدويًا." }
    }
    val pick=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> if(uri != null) scan(uri) }
    val pickRoute=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> if(uri != null) scanRoute(uri) }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if(uri != null) scope.launch { try { val data=withContext(Dispatchers.IO) { Backup.encode(dao.all(),dao.allPickups(),password,dao.allTraffic()) }; withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(data) } }; message="تم حفظ النسخة المشفرة." } catch (_: Exception) { message="تعذر حفظ النسخة." } }
    }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if(uri != null) scope.launch { try { val restored=withContext(Dispatchers.IO) { val bytes=requireNotNull(context.contentResolver.openInputStream(uri)).use { input -> val output=java.io.ByteArrayOutputStream(); val chunk=ByteArray(8192); while(true) { val count=input.read(chunk); if(count<0) break; output.write(chunk,0,count); require(output.size()<=5_000_000) }; output.toByteArray() }; Backup.decode(bytes,password) }; dao.importBackup(restored); message="اكتمل الاستيراد: ${restored.observations.size} عرضًا و${restored.pickups.size} نقطة و${restored.traffic.size} مدة رحلة في الملف." } catch (_: Exception) { message="تعذر الاستيراد: كلمة المرور أو الملف غير صالح." } }
    }
    LaunchedEffect(Unit) {
        val prefs=context.getSharedPreferences("migration",0)
        if (!prefs.getBoolean("legacy_imported",false)) {
            val legacy=context.getDatabasePath("rides.db")
            if (legacy.exists()) try {
                val old=withContext(Dispatchers.IO) { SQLiteDatabase.openDatabase(legacy.path,null,SQLiteDatabase.OPEN_READONLY).use { sql -> sql.rawQuery("SELECT origin,destination,provider,category,price,captured FROM quotes",null).use { c -> buildList { while(c.moveToNext()) add(Observation(origin=c.getString(0),destination=c.getString(1),provider=c.getString(2),category=c.getString(3),price=c.getDouble(4),captured=c.getLong(5))) } } } }
                dao.importItems(old); prefs.edit().putBoolean("legacy_imported",true).apply()
            } catch (_: Exception) { message="تعذر استيراد البيانات القديمة؛ بقيت محفوظة على الجهاز." }
            else prefs.edit().putBoolean("legacy_imported",true).apply()
        }
        if (shared != null) scan(shared)
    }
    Surface(modifier=Modifier.fillMaxSize(), color=MaterialTheme.colorScheme.background) { Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal=20.dp, vertical=18.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if (screen != "home") {
            TextButton(onClick={screen="home";message=""}, contentPadding=PaddingValues(0.dp)) { Text("‹  الرئيسية", fontWeight=FontWeight.Bold) }
            Text(when(screen) { "capture" -> "إضافة عرض"; "history" -> "عروضي"; "forecast" -> "التوقعات"; "feedback" -> "نتيجة الرحلة"; "traffic" -> "مدة الرحلة والزحمة"; "trafficSelect" -> "اختار العرض"; "pickups" -> "نقاط الالتقاء"; "research" -> "البحث والخرائط"; else -> "الإعدادات" },style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        }
        if(message.isNotBlank()) Surface(color=Color(0xffe7f2e6), shape=RoundedCornerShape(12.dp), modifier=Modifier.fillMaxWidth()) { Text(message, modifier=Modifier.padding(12.dp), color=MaterialTheme.colorScheme.primary) }
        when(screen) {
            "home" -> TripHome(origin=origin, onOriginChange={ origin=it; comparisonOrigin=it; locationInfo="" }, destination=destination, onDestinationChange={destination=it; selectedDestinationCoordinates=null; places=emptyList(); placesMessage=""}, locating=locating, locationInfo=locationInfo, showLocationSettings=showLocationSettings, searchingPlaces=searchingPlaces, places=places, placesMessage=placesMessage, onLocate={requestCurrentLocation()}, onLocationSettings={ try { context.startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } catch (_: android.content.ActivityNotFoundException) { locationInfo="افتح إعدادات الجهاز وشغّل الموقع." } }, onFindPlaces={findPlaces()}, onPlaceSelected={ place -> destination=place.label; selectedDestinationCoordinates=String.format(java.util.Locale.US, "%.6f,%.6f", place.latitude, place.longitude); places=emptyList(); placesMessage="تم اختيار المكان." }, onOpenMaps={openTripInMaps()}, onNavigate={screen=it;message=""})
            "capture" -> {
                Text("سجّل السعر اللي ظهر لك في تطبيق المشاوير.", style=MaterialTheme.typography.bodyLarge, color=Color(0xff61756b))
                Surface(color=Color.White,shape=RoundedCornerShape(22.dp),shadowElevation=1.dp,modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("تفاصيل الرحلة",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        OutlinedTextField(origin,{origin=it; locationInfo=""},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("نقطة الانطلاق")},shape=RoundedCornerShape(14.dp))
                        OutlinedButton(onClick={requestCurrentLocation()},enabled=!locating,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)) { Text(if(locating) "جارٍ تحديد موقعك..." else "استخدم موقعي الحالي") }
                        if(locationInfo.isNotBlank()) Text(locationInfo,style=MaterialTheme.typography.bodySmall,color=Color(0xff536b61))
                        OutlinedTextField(destination,{destination=it;selectedDestinationCoordinates=null},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("الوجهة")},shape=RoundedCornerShape(14.dp))
                        HorizontalDivider()
                        Text("تفاصيل العرض",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        OutlinedTextField(provider,{provider=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("التطبيق")},shape=RoundedCornerShape(14.dp))
                        OutlinedTextField(category,{category=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("فئة الخدمة")},shape=RoundedCornerShape(14.dp))
                        OutlinedTextField(price,{price=it},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),label={Text("السعر بالجنيه")},shape=RoundedCornerShape(14.dp))
                        OutlinedButton(onClick={pick.launch("image/*")},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)) { Text("اختيار صورة لقراءة النص") }
                        Text("الصورة لا تُحفظ. راجع النص والسعر قبل التأكيد.",style=MaterialTheme.typography.bodySmall,color=Color(0xff6d7c72))
                        if(recognized.isNotBlank()) {
                            Text("النص المقروء للمراجعة:\n$recognized",style=MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick={message="";screen="trafficSelect"},modifier=Modifier.fillMaxWidth()) { Text("استخدم الصورة لبيانات الطريق") }
                        }
                        Button(onClick={
                            val n=Logic.parsePrice(price); val texts=listOf(origin,destination,provider,category)
                            if(n==null || texts.any { it.isBlank() || it.length>200 }) message="أكمل البيانات وأدخل سعرًا صحيحًا."
                            else { val item=Observation(origin=origin.trim(),destination=destination.trim(),provider=provider.trim(),category=category.trim(),price=n,captured=System.currentTimeMillis(),provenance=source)
                                scope.launch { if(dao.all().any { Logic.duplicate(it,item) }) message="العرض مسجل بالفعل خلال ٩٠ ثانية." else { dao.insert(item); if(comparisonOrigin.isBlank() && pickups.none { Logic.same(it.name,item.origin) }) comparisonOrigin=item.origin; message="تم تأكيد العرض وحفظه محليًا."; price=""; recognized=""; source="USER_ENTERED"; screen="history" } }
                            }
                        },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(14.dp)) { Text("تأكيد وحفظ العرض",fontWeight=FontWeight.Bold) }
                    }
                }
            }
            "history" -> {
                Text("العروض لنفس المسار والفئة. يظل العرض حديثًا لمدة ٣ دقائق من إدخاله.", color=Color(0xff61756b))
                val first=items.firstOrNull()
                if(first==null) {
                    Surface(color=Color.White,shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { Text("لسه مفيش عروض",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold); Text("أضف أول عرض عشان تبدأ المقارنة.",color=Color(0xff6d7c72)); Button(onClick={screen="capture"}) { Text("إضافة عرض") } } }
                } else {
                    val candidates=items.filter { Logic.comparable(it,first) && Logic.fresh(it,now) }
                    if(candidates.size>=2) Logic.lowest(candidates,first,now)?.let { Text("أقل عرض حديث: ${it.provider} — ${it.price} جنيه. تحقق قبل الحجز.",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold) }
                    else Text("أضف عرضين حديثين لنفس الرحلة والفئة عشان تقارن.",color=Color(0xff6d7c72))
                }
                items.forEach { item ->
                    Surface(color=Color.White,shape=RoundedCornerShape(20.dp),shadowElevation=1.dp,modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) { Text(item.provider,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold); Text(item.category,style=MaterialTheme.typography.bodySmall,color=Color(0xff718176)) }
                                Text("${item.price} جنيه",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                            }
                            Text("${item.origin} ← ${item.destination}")
                            Text("${if(Logic.fresh(item,now)) "حديث" else "قديم"} · ${DateFormat.getDateTimeInstance().format(Date(item.captured))}",style=MaterialTheme.typography.bodySmall,color=Color(0xff718176))
                            Text(if(item.provenance=="USER_CONFIRMED_SCREENSHOT") "صورة أكدها المستخدم" else "إدخال أو استيراد مستخدم",style=MaterialTheme.typography.bodySmall,color=Color(0xff718176))
                            if(item.actualPaid!=null || item.acceptedFare!=null || item.bookingSuccess!=null) Text("نتيجة الرحلة: المدفوع ${item.actualPaid ?: "غير مسجل"}، المقبول ${item.acceptedFare ?: "غير مسجل"}، الحجز ${when(item.bookingSuccess){true->"نجح";false->"لم ينجح";null->"غير مسجل"}}",style=MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick={selectedId=item.id; paid=item.actualPaid?.toString()?:""; accepted=item.acceptedFare?.toString()?:""; success=item.bookingSuccess; screen="feedback"}) { Text("تسجيل نتيجة الرحلة") }
                                TextButton(onClick={deleteItem=item}) { Text("حذف") }
                            }
                            OutlinedButton(onClick={selectedId=item.id;resetRouteInput();message="";screen="traffic"}) { Text("مدة الرحلة والزحمة") }
                        }
                    }
                }
                if(items.isNotEmpty()) TextButton(onClick={deleteConfirm=true}) { Text("حذف كل السجل") }
            }
            "feedback" -> {
                Text("نتيجة الرحلة اختيارية؛ عرض inDrive المقترح لا يعني قبول السائق. أدخل السعر المقبول والمدفوع إن عُرفا.")
                OutlinedTextField(accepted,{accepted=it},label={Text("السعر المقبول جنيه — اختياري")})
                OutlinedTextField(paid,{paid=it},label={Text("المدفوع فعليًا جنيه — اختياري")})
                Text("هل تم الحجز؟")
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=success==true,onClick={success=true},label={Text("نعم")})
                    FilterChip(selected=success==false,onClick={success=false},label={Text("لا")})
                    FilterChip(selected=success==null,onClick={success=null},label={Text("غير معروف")})
                }
                Button(onClick={
                    val p=if(paid.isBlank()) null else Logic.parsePrice(paid); val a=if(accepted.isBlank()) null else Logic.parsePrice(accepted)
                    if((paid.isNotBlank()&&p==null)||(accepted.isNotBlank()&&a==null)) message="أدخل مبلغًا صحيحًا أكبر من صفر أو اترك الحقل فارغًا."
                    else scope.launch { dao.setFeedback(selectedId,p,a,success); message="تم حفظ نتيجة الرحلة."; screen="history" }
                }) { Text("حفظ النتيجة") }
            }
            "trafficSelect" -> {
                Text("اختار العرض اللي صورة الطريق تخصه، وراجع المدة والمسافة قبل الحفظ.")
                if(items.isEmpty()) Text("سجّل عرض الرحلة أولًا، وبعدها اختار الصورة من شاشة مدة الرحلة.")
                items.forEach { item -> OutlinedButton(onClick={selectedId=item.id;resetRouteInput();applyRouteText(recognized);screen="traffic"},modifier=Modifier.fillMaxWidth()) { Text("${item.origin} ← ${item.destination} · ${item.provider} ${item.category}") } }
            }
            "traffic" -> {
                val trip=items.firstOrNull { it.id==selectedId }
                if(trip==null) Text("العرض لم يعد موجودًا.") else {
                    Text("${trip.origin} ← ${trip.destination} · ${trip.provider} ${trip.category}",fontWeight=FontWeight.Bold)
                    Text("ابدأ القياس عند تحرك الرحلة وأنهِه عند الوصول. التطبيق يحسب المدة من الوقت؛ لا يتابع موقعك في الخلفية.")
                    val routeSamples=traffic.filter { TrafficEngine.sameRoute(it,trip) }
                    val active=routeSamples.firstOrNull { it.kind=="ACTIVE" && it.startedAt!=null }
                    if(active==null) Button(onClick={scope.launch { if(dao.startMeasuredTrip(trip,System.currentTimeMillis())) message="بدأ قياس الرحلة. ارجع هنا واضغط وصلت عند الوصول." else message="في قياس شغال للرحلة دي بالفعل." }}) { Text("بدأت الرحلة · ابدأ القياس") }
                    else {
                        Text("القياس بدأ ${DateFormat.getDateTimeInstance().format(Date(active.startedAt!!))}")
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Button(onClick={scope.launch { val end=System.currentTimeMillis(); val duration=TrafficEngine.elapsedMinutes(active.startedAt!!,end); if(duration==null) message="تعذر حساب مدة صالحة؛ راجع ساعة الجهاز أو ألغِ القياس." else if(dao.finishTraffic(active.id,active.startedAt,duration,end)==1) message="اتحفظت مدة الرحلة: ${"%.1f".format(duration)} دقيقة." else message="القياس انتهى أو اتحذف بالفعل." }}) { Text("وصلت · احسب المدة") }
                            TextButton(onClick={scope.launch { dao.deleteTraffic(active.id); message="تم إلغاء القياس." }}) { Text("إلغاء") }
                        }
                    }
                    HorizontalDivider()
                    Text("مدة الطريق الحالية",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    Text("افتح المسار في Google Maps، ثم شارك صورة حديثة أو اختارها هنا. راجع المدة والمسافة؛ القراءة قد تخطئ مع العربية ووقت الحفظ لا يثبت وقت التصوير.")
                    OutlinedButton(onClick={ val link=MapsLinks.forTrip(trip.origin,trip.destination); if(link!=null) try { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(link))) } catch (_:android.content.ActivityNotFoundException) { message="لا يوجد تطبيق خرائط أو متصفح متاح." } }) { Text("افتح مسار الرحلة") }
                    OutlinedButton(onClick={pickRoute.launch("image/*")}) { Text("اقرأ صورة الطريق") }
                    if(routeText.isNotBlank()) Text("النص المقروء للمراجعة:\n$routeText",style=MaterialTheme.typography.bodySmall)
                    if(routeDurations.size>1) { Text("الصورة فيها أكثر من مدة؛ اختار مدة المسار:"); routeDurations.forEach { candidate -> OutlinedButton(onClick={etaInput=candidate.value.toString()}) { Text(candidate.text) } } }
                    if(routeDistances.size>1) { Text("الصورة فيها أكثر من مسافة؛ اختار مسافة نفس المسار:"); routeDistances.forEach { candidate -> OutlinedButton(onClick={distanceInput=candidate.value.toString();distanceFromImage=true}) { Text(candidate.text) } } }
                    OutlinedTextField(etaInput,{etaInput=it},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),label={Text("المدة المتوقعة بالدقائق")})
                    OutlinedTextField(distanceInput,{distanceInput=it;distanceFromImage=false},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),label={Text("المسافة بالكيلومتر · اختياري")})
                    Button(onClick={
                        val minutes=Logic.parsePrice(etaInput); val distance=if(distanceInput.isBlank()) null else Logic.parsePrice(distanceInput)
                        if(minutes==null||minutes>1440||(distanceInput.isNotBlank()&&(distance==null||distance>2000))) message="أدخل مدة ومسافة صحيحتين، وراجع وحدة المسافة بالكيلومتر."
                        else scope.launch { dao.insertTraffic(TrafficSample(origin=trip.origin,destination=trip.destination,provider=trip.provider,category=trip.category,kind="USER_ESTIMATE",minutes=minutes,recordedAt=System.currentTimeMillis(),distanceKm=distance,inputSource=if(distanceFromImage && distance!=null) "CONFIRMED_IMAGE" else "MANUAL")); resetRouteInput();message="اتحفظت بيانات المسار بعد تأكيدك." }
                    }) { Text("تأكيد بيانات المسار واستخدامها") }
                    val comparison=TrafficEngine.compareCurrent(traffic,trip,now)
                    val currentEstimate=TrafficEngine.currentEstimate(traffic,trip,now)
                    QuoteEvaluationCard(items,trip,currentEstimate,now)
                    if(comparison!=null) Text("المدة التي أدخلتها ${"%.1f".format(comparison.currentMinutes)} دقيقة؛ الزمن السريع في سجلك ${"%.1f".format(comparison.fastBaselineMinutes)} دقيقة. الفرق ${"%+.1f".format(comparison.differenceMinutes)} دقيقة (${"%+.0f".format(comparison.percentChange)}٪) من ${comparison.sampleCount} رحلة عبر ${comparison.days} أيام.")
                    else if(currentEstimate!=null) Text("تم حفظ المدة الحالية. نحتاج ١٠ رحلات مقاسة عبر ٥ أيام لنقارنها بالزمن السريع المعتاد.")
                    else Text("لا توجد مدة حالية مدخلة خلال آخر ٣ دقائق.")
                    Text("ده فرق مدد لنفس نقطة البداية والوجهة والتطبيق والفئة، وليس قياسًا مباشرًا للزحمة؛ المسار أو ظروف الرحلة قد تختلف.",style=MaterialTheme.typography.bodySmall,color=Color(0xff718176))
                    val past=routeSamples.filter { it.kind!="ACTIVE" }.take(10)
                    if(past.isNotEmpty()) Text("آخر المدد",fontWeight=FontWeight.Bold)
                    past.forEach { sample -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("${if(sample.kind=="MEASURED") "رحلة مقاسة" else "مدة أدخلتها"}: ${"%.1f".format(sample.minutes)} د",modifier=Modifier.weight(1f)); TextButton(onClick={scope.launch { dao.deleteTraffic(sample.id) }}) { Text("حذف") } } }
                }
            }
            "forecast" -> {
                Text("اختيار وقت الطلب",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                Text("بنحسب من عروض نفس الرحلة والتطبيق والفئة اللي اتسجلت عندك. مش قراءة لحظية لأسعار التطبيقات.",color=Color(0xff61756b))
                OutlinedTextField(valueOfTime,{valueOfTime=it},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),label={Text("قيمة دقيقة وقتك بالجنيه")},supportingText={Text("تُستخدم للانتظار ومدة الرحلة إذا توفرت بيانات كافية؛ صفر يعني المقارنة المالية فقط.")})
                Text("أقصى انتظار")
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf(5,10,15,30).forEach { minutes -> FilterChip(selected=maxWait==minutes,onClick={maxWait=minutes},label={Text("$minutes د")}) } }
                if(Logic.parseNonNegative(valueOfTime)==null) Text("أدخل قيمة انتظار صحيحة من صفر أو أكبر.")
                else if(reference==null) Text("سجّل عروض لنقطة البداية والوجهة عشان نبدأ الحساب.")
                else {
                    Text("${reference.provider} · ${reference.category} · ${reference.origin} ← ${reference.destination}",style=MaterialTheme.typography.bodySmall)
                    val road=TrafficEngine.compareCurrent(traffic,reference,now)
                    val eta=TrafficEngine.currentEstimate(traffic,reference,now)
                    QuoteEvaluationCard(items,reference,eta,now)
                    if(road!=null) Text("مدة الطريق المدخلة ${"%.1f".format(road.currentMinutes)} د؛ الفرق عن الزمن السريع في سجلك ${"%+.1f".format(road.differenceMinutes)} د (${"%+.0f".format(road.percentChange)}٪). لا يحدد سبب التأخير.")
                    else if(eta!=null) Text("مدة الطريق المدخلة ${"%.1f".format(eta.minutes)} دقيقة؛ سجل رحلات أكثر للمقارنة التاريخية.")
                    else Text("مدة الطريق الحالية غير متاحة. أدخلها من الخرائط لتدخل في مقارنة تكلفة الوقت عند توفر سجل كافٍ.")
                    OutlinedButton(onClick={selectedId=reference.id;resetRouteInput();message="";screen="traffic"}) { Text("مدة الرحلة والزحمة") }
                    if(analysisRunning) Text("بنحسب من سجل أسعارك ومدد رحلاتك…") else {
                        val fastest=TrafficEngine.fastestDeparture(traffic,reference,now,travelPredictions)
                        if(fastest!=null) Text(if(fastest.waitMinutes==0) "أسرع وصول متوقع: التحرك الآن، وفق المدة المدخلة وتاريخ رحلاتك." else "أسرع وصول متوقع: انتظر ${fastest.waitMinutes} دقيقة، بفارق ${"%.1f".format(fastest.savedMinutes)} دقيقة عن الآن. التقدير من تاريخ رحلاتك، وليس قراءة مرور مباشرة.",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                        val current=Logic.fresh(reference,now)
                        if(current) Text("العرض الحالي: ${reference.price} جنيه · وقت الإدخال ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(reference.captured))}",fontWeight=FontWeight.Bold)
                        else Text("آخر عرض قديم؛ سجّل عرضًا حديثًا لحساب احتمال التوفير والمقارنة مع الآن.")
                        val eligible=options.filter { it.chanceCheaper != null }
                        val value=Logic.parseNonNegative(valueOfTime) ?: 0.0
                        val decision=JourneyDecisionEngine.choose(reference,now,options,value,eta,travelPredictions)
                        if(decision!=null) {
                            val recommendation=if(decision.waitMinutes>0) "الاختيار الحسابي: انتظر ${decision.waitMinutes} دقيقة. الفرق المتوقع بعد تكلفة الوقت ${"%.1f".format(decision.expectedNetSaving)} جنيه." else "الاختيار الحسابي: اطلب الآن؛ الخيارات المدروسة لا تظهر توفيرًا متوقعًا بعد تكلفة الوقت."
                            Surface(color=Color(0xffe7f2e6),shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()) { Text(recommendation,Modifier.padding(16.dp),fontWeight=FontWeight.Bold) }
                            Text(if(decision.usesTripDuration) "المقارنة تشمل المدة المدخلة الآن ومددًا تاريخية متوقعة؛ احتمال السعر أدناه لا يدمج بيانات مرور لحظية." else "قيمة الدقيقة صفر؛ المقارنة تعتمد على السعر فقط ولا تشمل زمن الطريق.",style=MaterialTheme.typography.bodySmall)
                        } else if(current && eligible.isNotEmpty() && value>0) Text("لا يمكن ترتيب الخيارات بقيمة الوقت: أدخل مدة الطريق الحالية وسجّل رحلات كافية لاجتياز اختبار مدة الطريق.")
                        else if(options.isNotEmpty()) Text("نحتاج عرضًا حديثًا و٢٠ اختبارًا تاريخيًا على خمسة أيام مختلفة على الأقل لحساب احتمال التوفير واختيار وقت.")
                        if(options.isEmpty()) Text("البيانات مش كفاية أو النموذج ما اجتازش التحقق الزمني. سجّل عروض أكتر لنفس الرحلة.")
                        options.forEach { option -> Surface(color=Color.White,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text("بعد ${option.minutes} دقيقة · متوسط تقديري ${"%.1f".format(option.expectedFare)} جنيه",fontWeight=FontWeight.Bold)
                                Text(if(!current || option.chanceCheaper==null) "احتمال التوفير غير متاح بعد" else "احتمال سعر أقل من العرض الحالي: ${"%.0f".format(option.chanceCheaper*100)}٪ · ${option.forecast.residuals.size} اختبارًا عبر ${option.forecast.validationDays} أيام",style=MaterialTheme.typography.bodySmall)
                                Text("نطاق تاريخي ${"%.1f".format(option.forecast.low)}–${"%.1f".format(option.forecast.high)} جنيه · خطأ ${"%.1f".format(option.forecast.mae)} جنيه",style=MaterialTheme.typography.bodySmall)
                                travelPredictions[option.minutes]?.let { duration -> Text("مدة طريق تاريخية متوقعة ${"%.1f".format(duration.minutes)} د · خطأ الاختبار ${"%.1f".format(duration.validationMae)} د",style=MaterialTheme.typography.bodySmall) }
                            }
                        } }
                        Text("ساعات أقل سعرًا تاريخيًا",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        if(cheaperHours.isEmpty()) Text("نحتاج عروضًا موزعة على ساعات وأيام مختلفة عشان نقارن.") else cheaperHours.forEach { hour -> Text("الساعة ${hour.hour}:00 · وسيط ${"%.1f".format(hour.median)} جنيه · ${hour.samples} عرض عبر ${hour.days} أيام") }
                        Text("ساعات مرتفعة تاريخيًا",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        if(peakHours.isEmpty()) Text("لسه مفيش أيام وعروض كفاية لتحديد ساعات مرتفعة لنفس الرحلة.") else peakHours.forEach { peak -> Text("الساعة ${peak.hour}:00 · وسيط ${"%.1f".format(peak.median)} جنيه · ${peak.samples} عرض عبر ${peak.days} أيام") }
                        Text("الساعات مرتبطة بسجلك وليست قياسًا مباشرًا للطلب. التقديرات تعمل على مستوى الساعة؛ قد تتساوى فترات الدقائق، ولا يوجد ضمان للتوفير.",style=MaterialTheme.typography.bodySmall,color=Color(0xff718176))
                    }
                }
            }
            "pickups" -> {
                Text("أقل نقطة التقاط",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                Text("قارن عروضًا حديثة لنفس التطبيق والفئة والوجهة. راجع مسار المشي بنفسك في الخرائط؛ التطبيق لا يتحقق من سلامة الطريق.")
                OutlinedTextField(comparisonOrigin,{comparisonOrigin=it},modifier=Modifier.fillMaxWidth(),label={Text("بداية المشوار الأصلية")})
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(maxWalkMeters,{maxWalkMeters=it},modifier=Modifier.weight(1f),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),label={Text("أقصى مشي (م)")})
                    OutlinedTextField(maxWalkMinutes,{maxWalkMinutes=it},modifier=Modifier.weight(1f),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),label={Text("أقصى وقت (د)")})
                }
                OutlinedTextField(valueOfTime,{valueOfTime=it},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),label={Text("قيمة دقيقة المشي بالجنيه")})
                val meters=Logic.parsePrice(maxWalkMeters); val minutes=Logic.parsePrice(maxWalkMinutes); val cost=Logic.parseNonNegative(valueOfTime)
                val candidateBaseline=reference?.takeIf { comparisonOrigin.isNotBlank() && Logic.same(it.origin,comparisonOrigin) && Logic.fresh(it,now) }
                val ranked=if(candidateBaseline!=null && meters!=null && minutes!=null && cost!=null) PickupAnalysis.compare(candidateBaseline,items,pickups,now,meters,minutes,cost) else emptyList()
                if(meters==null||minutes==null||cost==null) Text("أدخل حدودًا صالحة للمشي وقيمة وقت صحيحة.")
                else if(candidateBaseline==null) Text("أدخل بداية المشوار وسجّل لها عرضًا حديثًا أولًا.")
                else if(ranked.isEmpty()) Text("لا توجد نقطة بمسار مباشر حسب مراجعتك وعرض حديث مطابق داخل حدود المشي.")
                else {
                    val best=ranked.first()
                    Text(if(best.netSavings>0) "أقل تكلفة من العروض المدخلة: ${best.point.name} — ${best.quote.price} جنيه" else "البداية الأصلية أقل تكلفة بعد حساب المشي.",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                    Text("${best.point.name}: فرق السعر ${"%.1f".format(best.savings)} جنيه، وبعد تكلفة المشي ${"%.1f".format(best.netSavings)} جنيه. الاتجاه مباشر حسب مراجعتك؛ سلامة النقطة غير متحققة، وانتظار السائق والرسوم غير المحسوبة غير داخلين في المقارنة.")
                    ranked.drop(1).forEach { Text("${it.point.name}: ${it.quote.price} جنيه · الفرق بعد المشي ${"%.1f".format(it.netSavings)} جنيه") }
                }
                HorizontalDivider()
                Text("إضافة نقطة للمقارنة",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                OutlinedTextField(pickupName,{pickupName=it},modifier=Modifier.fillMaxWidth(),label={Text("اسم النقطة كما سيظهر في العرض")})
                OutlinedTextField(pickupNote,{pickupNote=it},modifier=Modifier.fillMaxWidth(),label={Text("ملاحظة اختيارية")})
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(walkMeters,{walkMeters=it},modifier=Modifier.weight(1f),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),label={Text("مسافة المشي (م)")})
                    OutlinedTextField(walkMinutes,{walkMinutes=it},modifier=Modifier.weight(1f),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),label={Text("وقت المشي (د)")})
                }
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { Checkbox(checked=direct,onCheckedChange={direct=it}); Text("راجعت مسار المشي وأعتبره مباشرًا") }
                Text("المسافة والوقت من مراجعتك، وليسا تقديرًا بخط مستقيم.",style=MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick={
                    val link=MapsLinks.forWalkingTrip(comparisonOrigin,pickupName)
                    if(link==null) message="أدخل بداية المشوار واسم النقطة لفتح مسار المشي."
                    else try { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(link))) } catch (_: android.content.ActivityNotFoundException) { message="لا يوجد تطبيق خرائط أو متصفح متاح." }
                }) { Text("راجع مسار المشي في Google Maps") }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=pickupStatus=="UNKNOWN",onClick={pickupStatus="UNKNOWN"},label={Text("السلامة غير معروفة")})
                    FilterChip(selected=pickupStatus=="UNSAFE",onClick={pickupStatus="UNSAFE"},label={Text("غير آمنة")})
                }
                Button(onClick={
                    val distance=Logic.parsePrice(walkMeters); val duration=Logic.parsePrice(walkMinutes)
                    if(pickupName.isBlank()||pickupName.length>200||pickupNote.length>500||distance==null||distance>10000||duration==null||duration>180) message="أدخل اسم النقطة ومسافة ووقت مشي صحيحين."
                    else scope.launch { dao.insertPickup(Pickup(name=pickupName.trim(),status=pickupStatus,note=pickupNote.trim(),recorded=System.currentTimeMillis(),walkMeters=distance,walkMinutes=duration,direct=direct)); pickupName="";pickupNote="";walkMeters="";walkMinutes="";direct=false;message="تم حفظ النقطة. سجّل عرضًا لها للمقارنة." }
                }) { Text("حفظ النقطة") }
                pickups.forEach { point -> HorizontalDivider(); Text("${point.name} · ${if(point.status=="UNSAFE") "غير آمنة حسب إدخالك" else "السلامة غير معروفة"}\nالمشي: ${point.walkMeters ?: "غير مسجل"} م · ${point.walkMinutes ?: "غير مسجل"} د · ${if(point.direct==true) "مباشر حسب مراجعتك" else "المسار المباشر غير مؤكد"}\n${point.note}"); Row { OutlinedButton(onClick={origin=point.name;screen="capture"}) { Text("إضافة عرض للنقطة") }; TextButton(onClick={scope.launch{dao.deletePickup(point.id)}}) { Text("حذف") } } }
            }
            "research" -> {
                Text("البحث العام سياق فقط، وليس سعرًا موثقًا.")
                OutlinedTextField(destination,{destination=it;selectedDestinationCoordinates=null},label={Text("نص البحث")})
                Button(onClick={ try { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search").buildUpon().appendQueryParameter("q",destination).build())) } catch (_: Exception) { message="لا يوجد متصفح متاح." } }) { Text("فتح بحث عام") }
                Button(onClick={ openTripInMaps() }) { Text("افتح في Google Maps") }
            }
            "settings" -> {
                Text("البيانات على جهازك. إزالة التطبيق تحذفها؛ احتفظ بنسخة مشفرة لاستعادتها. لا تُرفع البيانات تلقائيًا.")
                OutlinedTextField(password,{password=it},label={Text("كلمة مرور النسخة الاحتياطية")},visualTransformation=PasswordVisualTransformation())
                Button(onClick={if(password.length<8) message="استخدم كلمة مرور من ٨ أحرف على الأقل." else export.launch("sheeper-backup.shp")}) { Text("تصدير نسخة مشفرة") }
                Button(onClick={if(password.length<8) message="أدخل كلمة مرور النسخة." else import.launch(arrayOf("application/octet-stream","*/*"))}) { Text("استيراد نسخة مشفرة") }
                Text("الاستيراد يضيف البيانات إلى السجل؛ احتفظ بكلمة المرور لأن استعادتها غير ممكنة دونها.")
            }
        }

    } }
    deleteItem?.let { item ->
        val lastForRoute=items.count { Logic.comparable(it,item) && Logic.same(it.provider,item.provider) }<=1
        val hasTraffic=traffic.any { TrafficEngine.sameRoute(it,item) }
        AlertDialog(onDismissRequest={deleteItem=null},title={Text("حذف العرض؟")},text={Text(if(lastForRoute && hasTraffic) "ده آخر عرض للمسار والفئة والتطبيق؛ حذفه هيحذف كمان مدد الرحلات المرتبطة به." else "سيتم حذف العرض من السجل.")},confirmButton={TextButton(onClick={scope.launch { dao.deleteWithOrphanTraffic(item);deleteItem=null }}){Text("حذف")}},dismissButton={TextButton(onClick={deleteItem=null}){Text("إلغاء")}})
    }
    if(deleteConfirm) AlertDialog(onDismissRequest={deleteConfirm=false}, title={Text("حذف كل العروض والنقاط ومدد الرحلات؟")}, text={Text("لا يمكن التراجع عن الحذف إلا باستعادة نسخة احتياطية.")},confirmButton={TextButton(onClick={scope.launch{dao.clearAll(); val legacy=context.getDatabasePath("rides.db"); val removed=!legacy.exists() || context.deleteDatabase("rides.db"); deleteConfirm=false;message=if(removed) "تم حذف السجل والنقاط ومدد الرحلات." else "حُذفت البيانات الحالية، لكن تعذر حذف قاعدة البيانات القديمة."}}){Text("حذف")}},dismissButton={TextButton(onClick={deleteConfirm=false}){Text("إلغاء")}})
}
