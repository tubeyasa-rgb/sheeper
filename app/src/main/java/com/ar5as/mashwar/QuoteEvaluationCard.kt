package com.ar5as.mashwar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable fun QuoteEvaluationCard(items:List<Observation>,quote:Observation,route:TrafficSample?,now:Long) {
    val result=TripEvaluation.evaluate(items,quote,route,now)
    if(result==null) { Text("العرض أقدم من ٣ دقائق؛ سجّل عرضًا حديثًا عشان تقييم السعر يكون على عرض حديث.",style=MaterialTheme.typography.bodySmall); return }
    Surface(color=Color.White,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text("تقييم العرض",fontWeight=FontWeight.Bold)
            if(result.pricePerKm!=null && route!=null) {
                Text("السعر ÷ المسافة: ${"%.1f".format(result.pricePerKm)} جنيه/كم لمسافة ${"%.1f".format(route.distanceKm)} كم.")
                Text("المسافة ${if(route.inputSource=="CONFIRMED_IMAGE") "من صورة راجعتها" else "من إدخالك"} · وقت التأكيد ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(route.recordedAt))}. قد يشمل السعر رسومًا ووقتًا؛ ده مش السعر الرسمي للكيلومتر.",style=MaterialTheme.typography.bodySmall)
            } else Text("أدخل مسافة المسار لحساب السعر مقابل الكيلومتر.",style=MaterialTheme.typography.bodySmall)
            if(result.historicalMedian!=null && result.percentDifference!=null) Text("مقارنة بعروض نفس المسار والتطبيق والفئة: وسيط ${"%.1f".format(result.historicalMedian)} جنيه، والفرق ${"%+.0f".format(result.percentDifference)}٪ من ${result.sampleCount} عرض عبر ${result.sampleDays} أيام.")
            else Text("المقارنة التاريخية تحتاج ١٠ عروض على ٥ أيام لنفس المسار والتطبيق والفئة.",style=MaterialTheme.typography.bodySmall)
            if(result.historicalMedian!=null && result.percentDifference!=null) Text("المسار والرسوم والعروض الترويجية قد تختلف؛ المقارنة لا تحدد سعرًا عادلًا مضمونًا.",style=MaterialTheme.typography.bodySmall)
        }
    }
}
