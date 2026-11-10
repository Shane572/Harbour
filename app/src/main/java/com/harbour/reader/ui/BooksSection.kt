package com.harbour.reader.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harbour.reader.Book
import com.harbour.reader.BookProgress
import com.harbour.reader.MainViewModel
import com.harbour.reader.SortOrder
import kotlin.math.roundToInt

/** All your EPUB books, with the ones you're reading up front. */
@Composable
fun BooksSection(vm: MainViewModel) {
    val shown = remember(vm.books, vm.sort) { vm.sortedBooks(vm.books) }
    val reading = vm.continueBooks
    var sortMenu by remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = vm.coverSize.minCell()),
        contentPadding = sectionPadding(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Books", style = MaterialTheme.typography.displaySmall, color = HarbourColors.Text)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (vm.books.size == 1) "1 book" else "${vm.books.size} books",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HarbourColors.Muted,
                    )
                }
                Box {
                    TextButton(onClick = { sortMenu = true }) {
                        Text(vm.sort.label, color = HarbourColors.Muted)
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Sort", tint = HarbourColors.Muted)
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortOrder.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label, color = if (s == vm.sort) HarbourColors.Accent else HarbourColors.Text) },
                                onClick = {
                                    vm.changeSort(s)
                                    sortMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }

        if (reading.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "continue") {
                Column {
                    SectionLabel("Continue reading")
                    Spacer(Modifier.height(14.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(reading, key = { it.uri }) { book ->
                            ContinueBookCard(book, vm.bookProgress[book.key]) { vm.openBook(book) }
                        }
                    }
                }
            }
        }

        if (shown.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                Hint(
                    if (vm.scanning) "Looking for books…" else "No books yet",
                    "Harbour finds .epub and .pdf files in the same folders as your comics. Add a folder in Settings, " +
                        "or tap refresh on Home after adding books.",
                )
            }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }, key = "all-label") { SectionLabel("All books") }
        }

        items(shown, key = { it.uri }) { book -> BookCard(vm, book) }
    }
}

@Composable
private fun ContinueBookCard(book: Book, progress: BookProgress?, onClick: () -> Unit) {
    Row(
        Modifier
            .width(320.dp)
            .height(148.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HarbourColors.Surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Column(Modifier.width(68.dp).fillMaxHeight()) {
            CoverImage(book, Modifier.fillMaxWidth().weight(1f), corner = 8.dp)
            if (progress != null) {
                Spacer(Modifier.height(6.dp))
                CoverProgress(progress.overall)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).fillMaxHeight().padding(vertical = 4.dp)) {
            if (book.author.isNotBlank()) {
                Text(book.author, style = MaterialTheme.typography.labelSmall, color = HarbourColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
            }
            Text(book.title, style = MaterialTheme.typography.titleSmall, color = HarbourColors.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A book in a grid. Long-press for mark read/unread and delete. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCard(vm: MainViewModel, book: Book) {
    val progress = vm.bookProgress[book.key]
    val onClick = { vm.openBook(book) }
    val onMarkRead = { read: Boolean -> vm.setBookRead(listOf(book.key), read) }
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this book from the tablet?",
            text = "This permanently deletes \"${book.title}\" from your storage, not just from Harbour. It can't be undone.",
            confirmLabel = "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                vm.deleteFiles(listOf(book))
            },
        )
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menu = true
                },
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            CoverImage(book, Modifier.fillMaxSize())
            if (progress?.finished == true) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Mark as read") }, onClick = {
                    menu = false
                    onMarkRead(true)
                })
                DropdownMenuItem(text = { Text("Mark as unread") }, onClick = {
                    menu = false
                    onMarkRead(false)
                })
                DropdownMenuItem(text = { Text("Delete from device…") }, onClick = {
                    menu = false
                    confirmDelete = true
                })
            }
        }
        if (progress != null && !progress.finished) {
            Spacer(Modifier.height(6.dp))
            CoverProgress(progress.overall)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            book.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (progress?.finished == true) HarbourColors.Muted else HarbourColors.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (book.author.isNotBlank()) {
            Text(book.author, style = MaterialTheme.typography.labelSmall, color = HarbourColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
