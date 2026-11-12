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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.IconButton
import com.harbour.reader.Group
import com.harbour.reader.Section
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

        val groups = vm.bookGroups
        if (groups.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "groups-label") { SectionLabel("Groups") }
            items(groups, key = { "group:" + it.id }) { g ->
                BookGroupCard(vm, g) { vm.show(Section.InBookGroup(g.id)) }
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
    var picking by remember { mutableStateOf(false) }
    val inGroup = vm.groupOfKey[book.key]
    if (picking) {
        AddToGroupDialog(
            count = 1,
            groups = vm.bookGroups,
            onDismiss = { picking = false },
            onPick = { g ->
                picking = false
                vm.addToGroup(g.id, listOf(book.key))
                vm.message = "Added to ${g.name}"
            },
            onCreate = { name ->
                picking = false
                val g = vm.createGroup(name, listOf(book.key), forBooks = true)
                vm.message = "Made the group ${g.name}"
            },
        )
    }
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
                ReadBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Add to group…") }, onClick = {
                    menu = false
                    picking = true
                })
                if (inGroup != null) {
                    DropdownMenuItem(text = { Text("Remove from ${inGroup.name}") }, onClick = {
                        menu = false
                        vm.removeFromGroups(listOf(book.key))
                    })
                    DropdownMenuItem(text = { Text("Set as group cover") }, onClick = {
                        menu = false
                        vm.setGroupCover(book.key)
                    })
                }
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
        if (progress != null) {
            Spacer(Modifier.height(6.dp))
            CoverProgress(if (progress.finished) 1f else progress.overall)
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

/** A book group on the Books page, shown as a little stack of covers. */
@Composable
private fun BookGroupCard(vm: MainViewModel, group: Group, onClick: () -> Unit) {
    val books = remember(group, vm.books) { vm.booksIn(group) }
    val cover = remember(group, vm.books) { vm.bookCoverOf(group) }
    val read = books.count { vm.bookProgress[it.key]?.finished == true }
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.64f),
        ) {
            if (books.size > 1) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 9.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(HarbourColors.SurfaceHighest),
                )
            }
            CoverImage(
                cover,
                Modifier
                    .fillMaxSize()
                    .padding(top = if (books.size > 1) 7.dp else 0.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(group.name, style = MaterialTheme.typography.titleSmall, color = HarbourColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            buildString {
                append(if (books.size == 1) "1 book" else "${books.size} books")
                if (read > 0) append(" · $read read")
            },
            style = MaterialTheme.typography.labelSmall,
            color = HarbourColors.Muted,
        )
    }
}

/** One book group: its books, with a back arrow and rename / delete. */
@Composable
fun BookGroupSection(vm: MainViewModel, group: Group) {
    val books = remember(group, vm.books, vm.sort) { vm.sortedBooks(vm.booksIn(group)) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = vm.coverSize.minCell()),
        contentPadding = sectionPadding(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = vm::back, modifier = Modifier.padding(end = 4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = HarbourColors.Text)
                }
                Column(Modifier.weight(1f)) {
                    Text(group.name, style = MaterialTheme.typography.headlineSmall, color = HarbourColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (books.size == 1) "1 book" else "${books.size} books", style = MaterialTheme.typography.labelSmall, color = HarbourColors.Muted)
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Group options", tint = HarbourColors.Muted)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename group") }, onClick = {
                            menu = false
                            renaming = true
                        })
                        DropdownMenuItem(text = { Text("Delete group") }, onClick = {
                            menu = false
                            deleting = true
                        })
                    }
                }
            }
        }
        if (books.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                Hint("This group is empty", "Long-press a book on the Books page and tap Add to group.")
            }
        }
        items(books, key = { it.uri }) { book -> BookCard(vm, book) }
    }

    if (renaming) {
        NameDialog(
            title = "Rename group",
            confirmLabel = "Rename",
            initial = group.name,
            onDismiss = { renaming = false },
            onConfirm = {
                vm.renameGroup(group.id, it)
                renaming = false
            },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = "Delete ${group.name}?",
            text = "The books stay in your library.",
            confirmLabel = "Delete",
            onDismiss = { deleting = false },
            onConfirm = {
                deleting = false
                vm.deleteGroup(group.id)
            },
        )
    }
}
