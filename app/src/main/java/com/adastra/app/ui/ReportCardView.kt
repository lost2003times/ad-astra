package com.adastra.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adastra.app.data.ReportCard
import com.adastra.app.data.Report
import com.adastra.app.data.fmtDuration

/** The report card for one week: grade seal, the numbers, what stood out, what to change. */
@Composable
fun ReportCardView(r: ReportCard) {
    Card(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val sealColor = when (r.grade) { "A+", "A" -> Astra.Signal; "B" -> Astra.Ink; "C" -> Astra.Muted; else -> Astra.Rest }
            Box(
                Modifier.size(84.dp).rotate(-6f).clip(RoundedCornerShape(10.dp)).background(sealColor),
                contentAlignment = Alignment.Center,
            ) { Text(r.grade, color = Astra.Bg, fontSize = 40.sp, lineHeight = 44.sp, style = Score) }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Label(if (r.final) "Report card" else "So far · day ${r.days} of 7")
                Text("${r.score} / 100", color = Astra.Text, fontSize = 26.sp, style = Score, modifier = Modifier.padding(top = 2.dp))
                Text(r.verdict, color = Astra.Text, fontSize = 15.sp, fontFamily = Serif, lineHeight = 20.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        GoalLine("Study", r.study, r.studyGoal, r.prevStudy, Astra.Ink)
        GoalLine("Shift", r.shift, r.shiftGoal, r.prevShift, Astra.Signal)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Mini("Study days", "${r.studyDays}/${r.days}")
            Mini("Timer blocks", if (r.blocks > 0) "${r.blocks} · ${Report.fmtLen(r.avgBlock)}" else "–")
            Mini("Breaks", if (r.breaks > 0) "${r.breaks} · ${r.breakMinutes} min" else "–")
        }

        if (r.insights.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Astra.Line)
            Spacer(Modifier.height(10.dp))
            Label("Main insights")
            r.insights.forEach { Point(it) }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Astra.Line)
        Spacer(Modifier.height(10.dp))
        Label("What to improve", color = Astra.Signal)
        r.improve.forEach { Point(it, Astra.Signal) }
    }
}

/** A small "last week's grade" row that opens that week. */
@Composable
fun LastWeekLink(r: ReportCard, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(Astra.Surface)
            .clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(r.grade, color = Astra.Signal, fontSize = 20.sp, style = Score, modifier = Modifier.width(40.dp))
        Text("Last week's report card · ${fmtDuration(r.study)} study", color = Astra.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text("Open", color = Astra.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun GoalLine(name: String, have: Float, goal: Int, prev: Float, color: Color) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(name, color = Astra.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("${fmtDuration(have)} of $goal h", color = Astra.Text, fontSize = 14.sp, style = Numbers)
            val d = have - prev
            if (prev > 0f || have > 0f) Text(
                when { d >= 0.1f -> "  ▲ ${fmtDuration(d)}"; d <= -0.1f -> "  ▼ ${fmtDuration(-d)}"; else -> "  =" },
                color = if (d >= 0f) Astra.Muted else Astra.Signal, fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(5.dp))
        BrushBar(if (goal > 0) have / goal else 0f, color)
    }
}

@Composable
private fun Mini(label: String, value: String) {
    Column {
        Text(value, color = Astra.Text, fontSize = 16.sp, style = Numbers)
        Text(label, color = Astra.Muted, fontSize = 11.sp)
    }
}
