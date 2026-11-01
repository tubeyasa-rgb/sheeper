package com.ar5as.mashwar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xff123a32)
private val Accent = Color(0xffd6fa70)

@Composable
fun TripHome(
    origin: String, onOriginChange: (String) -> Unit,
    destination: String, onDestinationChange: (String) -> Unit,
    locating: Boolean, locationInfo: String, showLocationSettings: Boolean,
    searchingPlaces: Boolean, places: List<PlaceSuggestion>, placesMessage: String,
    onLocate: () -> Unit, onLocationSettings: () -> Unit, onFindPlaces: () -> Unit, onPlaceSelected: (PlaceSuggestion) -> Unit,
    onOpenMaps: () -> Unit, onNavigate: (String) -> Unit
) {
    Surface(color = Ink, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(35.dp).background(Accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = Ink, modifier = Modifier.size(21.dp))
                }
                Text("أرخص مشوار", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text("مشوارك يبدأ من هنا", color = Color.White, fontSize = 25.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
            Text("حدد وجهتك، وقارن العروض على راحتك.", color = Color(0xffc9ded6), fontSize = 14.sp, lineHeight = 22.sp)
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column { Text("رحلتك الجاية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("حدد البداية والوجهة", style = MaterialTheme.typography.bodySmall, color = Color(0xff667970)) }
        Surface(color = Color(0xffe9efe4), shape = RoundedCornerShape(50)) { Text("مسار جديد", modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Ink, style = MaterialTheme.typography.labelMedium) }
    }
    Surface(color = Color.White, shape = RoundedCornerShape(24.dp), shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = origin, onValueChange = onOriginChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("نقطة الانطلاق") }, placeholder = { Text("اكتب مكان البداية") },
                leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, tint = Ink) }, shape = RoundedCornerShape(15.dp))
            OutlinedButton(onClick = onLocate, enabled = !locating, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text(if (locating) "جارٍ تحديد موقعك..." else "استخدم موقعي الحالي")
            }
            if (locationInfo.isNotBlank()) Text(locationInfo, color = Color(0xff536b61), style = MaterialTheme.typography.bodySmall, lineHeight = 20.sp)
            if (showLocationSettings) TextButton(onClick = onLocationSettings) { Text("فتح إعدادات الموقع") }
            OutlinedTextField(value = destination, onValueChange = onDestinationChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("الوجهة") }, placeholder = { Text("رايح فين؟") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Ink) }, shape = RoundedCornerShape(15.dp))
            OutlinedButton(onClick = onFindPlaces, enabled = !searchingPlaces, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text(if (searchingPlaces) "بندور على الأماكن..." else "ابحث عن مكان")
            }
            if (placesMessage.isNotBlank()) Text(placesMessage, color = Color(0xff536b61), style = MaterialTheme.typography.bodySmall, lineHeight = 20.sp)
            places.forEach { place ->
                Surface(onClick = { onPlaceSelected(place) }, color = Color(0xfff4f7ef), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(place.label, modifier = Modifier.padding(12.dp), color = Ink, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Button(onClick = onOpenMaps, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(15.dp)) {
                Text("افتح المسار في Google Maps", fontWeight = FontWeight.Bold)
            }
        }
    }
    Spacer(Modifier.height(3.dp))
    Text("كل اللي تحتاجه لمشوارك", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickAction("إضافة عرض", "صوّر أو سجّل السعر", "＋", Modifier.weight(1f)) { onNavigate("capture") }
        QuickAction("عروضي", "قارن الأسعار المحفوظة", "≋", Modifier.weight(1f)) { onNavigate("history") }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickAction("التوقعات", "شوف الأوقات المتاحة", "↗", Modifier.weight(1f)) { onNavigate("forecast") }
        QuickAction("نقاط الالتقاء", "احفظ الأماكن المهمة", "⌖", Modifier.weight(1f)) { onNavigate("pickups") }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickAction("البحث", "ابحث عن مكان", "⌕", Modifier.weight(1f)) { onNavigate("research") }
        QuickAction("الإعدادات", "البيانات والنسخ الاحتياطي", "⚙", Modifier.weight(1f)) { onNavigate("settings") }
    }
    Text("أسعارك محفوظة على جهازك، وقارن دائمًا قبل الحجز.", color = Color(0xff78877e), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
}

@Composable
private fun QuickAction(title: String, description: String, symbol: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 112.dp), color = Color.White, shape = RoundedCornerShape(20.dp), shadowElevation = 1.dp) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(34.dp).background(Color(0xffeaf4db), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                Text(symbol, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Ink)
            }
            Text(title, color = Ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(description, color = Color(0xff738278), style = MaterialTheme.typography.bodySmall, lineHeight = 17.sp)
        }
    }
}
