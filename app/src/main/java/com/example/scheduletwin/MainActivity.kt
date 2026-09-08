package com.example.scheduletwin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// ── 오늘 날짜(데모용으로 고정. 실제 배포 시 LocalDate.now()로 교체) ──
val TODAY: LocalDate = LocalDate.of(2026, 9, 7)

enum class Category(val label: String, val color: Color) {
    MEETING("회의", Color(0xFFD85A30)),
    TODAY("금일 할일", Color(0xFF1D9E75)),
    WEEK("주간 할일", Color(0xFF7F77DD))
}

data class Todo(
    val id: String,
    val date: LocalDate,
    val category: Category,
    val text: String,
    val time: String? = null,
    val done: Boolean = false
)

data class DisplayTodo(val todo: Todo, val delay: Long)

fun itemsForDate(date: LocalDate, todos: List<Todo>): List<DisplayTodo> {
    val original = todos.filter { it.date == date }.map { DisplayTodo(it, 0) }
    val carried = todos
        .filter { it.date < date && it.category != Category.MEETING && !it.done }
        .map { DisplayTodo(it, ChronoUnit.DAYS.between(it.date, date)) }
    return (original + carried).sortedWith(
        compareBy({ it.todo.category.ordinal }, { it.todo.time ?: "" })
    )
}

fun todaySectionTitle(date: LocalDate, todos: List<Todo>): String {
    var label = "${date.monthValue}/${date.dayOfMonth}일 할일"
    val prev = date.minusDays(1)
    val prevItems = todos.filter { it.date == prev && it.category == Category.TODAY }
    if (prevItems.isNotEmpty()) {
        val doneCount = prevItems.count { it.done }
        val rate = doneCount * 100 / prevItems.size
        label += " (${prev.monthValue}/${prev.dayOfMonth}일 업무 완료율 ${rate}%)"
    }
    return label
}

fun buildShareText(userName: String, date: LocalDate, todos: List<Todo>): String {
    val items = itemsForDate(date, todos)
    val grouped = Category.values().associateWith { cat -> items.filter { it.todo.category == cat } }
    val sb = StringBuilder("${userName}님의 ${date} 할일")
    Category.values().forEach { cat ->
        val catItems = grouped[cat].orEmpty()
        if (catItems.isNotEmpty()) {
            val title = if (cat == Category.TODAY) todaySectionTitle(date, todos) else cat.label
            sb.append("\n\n[$title]")
            catItems.forEach { di ->
                val t = di.todo
                val timeStr = if (cat == Category.MEETING && t.time != null) "${t.time} " else ""
                val status = if (t.done) "완료" else "미완료"
                val delayStr = if (di.delay > 0) " (지연 ${di.delay}일)" else ""
                sb.append("\n- [$status] $timeStr${t.text}$delayStr")
            }
        }
    }
    return sb.toString()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
fun AppRoot() {
    var userName by remember { mutableStateOf("") }
    var started by remember { mutableStateOf(false) }

    if (!started) {
        NameScreen(onStart = { name -> userName = name; started = true })
    } else {
        CalendarApp(userName = userName)
    }
}

@Composable
fun NameScreen(onStart: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("이름을 입력해주세요", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; error = "" },
            placeholder = { Text("이름") },
            modifier = Modifier.fillMaxWidth()
        )
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { if (input.trim().isEmpty()) error = "이름을 입력하세요." else onStart(input.trim()) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("시작하기") }
    }
}

@Composable
fun CalendarApp(userName: String) {
    val todos = remember {
        mutableStateListOf(
            Todo("seed1", LocalDate.of(2026, 9, 7), Category.TODAY, "보고서 초안 작성", done = true),
            Todo("seed3", LocalDate.of(2026, 9, 7), Category.TODAY, "거래처 미팅 준비", done = false),
            Todo("seed2", LocalDate.of(2026, 9, 6), Category.WEEK, "주간 회의 자료 준비", done = false)
        )
    }
    var viewMonth by remember { mutableStateOf(YearMonth.of(2026, 9)) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("${userName}님의 일정", fontSize = 14.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))

        val overdueCount = todos.count { it.date < TODAY && !it.done && it.category != Category.MEETING }
        if (overdueCount > 0) {
            Surface(color = Color(0xFFFCEBEB), shape = RoundedCornerShape(8.dp)) {
                Text(
                    "지연된 할일이 ${overdueCount}건 있어요.",
                    color = Color(0xFFA32D2D),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { viewMonth = viewMonth.minusMonths(1); selectedDate = null }) { Text("〈") }
            Text("${viewMonth.year}년 ${viewMonth.monthValue}월", fontWeight = FontWeight.Medium, fontSize = 16.sp)
            TextButton(onClick = { viewMonth = viewMonth.plusMonths(1); selectedDate = null }) { Text("〉") }
        }

        MonthGrid(viewMonth, todos) { date -> selectedDate = date }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Category.values().forEach { cat ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(cat.color, RoundedCornerShape(50)))
                    Spacer(Modifier.width(4.dp))
                    Text(cat.label, fontSize = 12.sp, color = Color.Gray)
                }
            }
        }

        selectedDate?.let { ds ->
            Spacer(Modifier.height(16.dp))
            DayPanel(userName = userName, date = ds, todos = todos)
        }
    }
}

@Composable
fun MonthGrid(viewMonth: YearMonth, todos: List<Todo>, onDayClick: (LocalDate) -> Unit) {
    val firstOfMonth = viewMonth.atDay(1)
    val startOffset = firstOfMonth.dayOfWeek.value % 7 // 일요일=0 시작 기준 오프셋
    val startDate = firstOfMonth.minusDays(startOffset.toLong())
    val weekdays = listOf("일", "월", "화", "수", "목", "금", "토")

    Row(Modifier.fillMaxWidth()) {
        weekdays.forEachIndexed { i, d ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    d, fontSize = 12.sp,
                    color = when (i) { 0 -> Color(0xFFE24B4A); 6 -> Color(0xFF378ADD); else -> Color.Gray }
                )
            }
        }
    }
    Column {
        for (row in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val date = startDate.plusDays((row * 7 + col).toLong())
                    val inMonth = date.month == viewMonth.month
                    DayCell(date, inMonth, todos, onDayClick, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun DayCell(date: LocalDate, inMonth: Boolean, todos: List<Todo>, onDayClick: (LocalDate) -> Unit, modifier: Modifier) {
    val isToday = date == TODAY
    Box(
        modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .background(Color(0xFFF6F5F0), RoundedCornerShape(8.dp))
            .then(if (isToday) Modifier.border(1.5.dp, Color(0xFF378ADD), RoundedCornerShape(8.dp)) else Modifier)
            .alpha(if (inMonth) 1f else 0.35f)
            .clickable(enabled = inMonth) { onDayClick(date) }
            .padding(4.dp)
    ) {
        Column {
            Text("${date.dayOfMonth}", fontSize = 12.sp)
            if (inMonth) {
                val cats = itemsForDate(date, todos).map { it.todo.category }.distinct()
                Row {
                    cats.forEach { c ->
                        Box(
                            Modifier
                                .padding(end = 2.dp)
                                .size(5.dp)
                                .background(c.color, RoundedCornerShape(50))
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPanel(userName: String, date: LocalDate, todos: androidx.compose.runtime.snapshots.SnapshotStateList<Todo>) {
    val context = LocalContext.current
    var addCategory by remember(date) { mutableStateOf(Category.TODAY) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var todoText by remember(date) { mutableStateOf("") }
    var todoTime by remember(date) { mutableStateOf("") }
    var formError by remember(date) { mutableStateOf("") }

    fun updateTodo(id: String, transform: (Todo) -> Todo) {
        val idx = todos.indexOfFirst { it.id == id }
        if (idx >= 0) todos[idx] = transform(todos[idx])
    }

    val items = itemsForDate(date, todos)
    val grouped = Category.values().associateWith { cat -> items.filter { it.todo.category == cat } }

    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${date} 할일", fontWeight = FontWeight.Medium, fontSize = 14.sp)
            TextButton(onClick = {
                val summary = buildShareText(userName, date, todos)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, summary)
                }
                context.startActivity(Intent.createChooser(intent, "공유하기"))
            }) { Text("카카오톡 공유") }
        }

        if (items.isEmpty()) {
            Text("등록된 할일이 없어요.", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 12.dp))
        }

        Category.values().forEach { cat ->
            val catItems = grouped[cat].orEmpty()
            if (catItems.isNotEmpty()) {
                val title = if (cat == Category.TODAY) todaySectionTitle(date, todos) else cat.label
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(cat.color, RoundedCornerShape(50)))
                    Spacer(Modifier.width(4.dp))
                    Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
                }
                catItems.forEach { di ->
                    TodoRow(
                        display = di,
                        isEditing = editingId == di.todo.id,
                        onToggle = { updateTodo(di.todo.id) { it.copy(done = !it.done) } },
                        onEditStart = { editingId = di.todo.id },
                        onEditCancel = { editingId = null },
                        onEditSave = { newText, newTime ->
                            updateTodo(di.todo.id) {
                                it.copy(text = newText, time = if (it.category == Category.MEETING) newTime else it.time)
                            }
                            editingId = null
                        },
                        onDelete = { todos.removeAll { t -> t.id == di.todo.id } }
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Category.values().forEach { cat ->
                FilterChip(
                    selected = addCategory == cat,
                    onClick = { addCategory = cat },
                    label = { Text(cat.label, fontSize = 13.sp) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = todoText,
                onValueChange = { todoText = it; formError = "" },
                placeholder = { Text("할일 내용") },
                modifier = Modifier.weight(1f)
            )
            if (addCategory == Category.MEETING) {
                OutlinedTextField(
                    value = todoTime,
                    onValueChange = { todoTime = it; formError = "" },
                    placeholder = { Text("HH:mm") },
                    modifier = Modifier.width(90.dp)
                )
            }
            Button(onClick = {
                if (todoText.trim().isEmpty()) { formError = "내용을 입력하세요."; return@Button }
                if (addCategory == Category.MEETING && todoTime.trim().isEmpty()) {
                    formError = "회의 시간을 입력하세요."; return@Button
                }
                todos.add(
                    Todo(
                        id = "t${System.currentTimeMillis()}",
                        date = date,
                        category = addCategory,
                        text = todoText.trim(),
                        time = if (addCategory == Category.MEETING) todoTime.trim() else null
                    )
                )
                todoText = ""; todoTime = ""; formError = ""
            }) { Text("추가") }
        }
        if (formError.isNotEmpty()) {
            Text(formError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }
}

@Composable
fun TodoRow(
    display: DisplayTodo,
    isEditing: Boolean,
    onToggle: () -> Unit,
    onEditStart: () -> Unit,
    onEditCancel: () -> Unit,
    onEditSave: (String, String?) -> Unit,
    onDelete: () -> Unit
) {
    val todo = display.todo
    if (isEditing) {
        var text by remember(todo.id) { mutableStateOf(todo.text) }
        var time by remember(todo.id) { mutableStateOf(todo.time ?: "") }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            if (todo.category == Category.MEETING) {
                OutlinedTextField(value = time, onValueChange = { time = it }, modifier = Modifier.width(80.dp))
            }
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f))
            TextButton(onClick = { onEditSave(text, if (todo.category == Category.MEETING) time else null) }) { Text("저장") }
            TextButton(onClick = onEditCancel) { Text("취소") }
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Checkbox(checked = todo.done, onCheckedChange = { onToggle() })
            val displayText = if (todo.category == Category.MEETING && todo.time != null) {
                "${todo.time} · ${todo.text}"
            } else todo.text
            Text(
                displayText,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                color = if (todo.done) Color.Gray else Color.Black
            )
            if (display.delay > 0) {
                Surface(color = Color(0xFFFCEBEB), shape = RoundedCornerShape(10.dp)) {
                    Text(
                        "지연 ${display.delay}일",
                        color = Color(0xFFA32D2D),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            TextButton(onClick = onEditStart) { Text("수정") }
            TextButton(onClick = onDelete) { Text("삭제") }
        }
    }
}
