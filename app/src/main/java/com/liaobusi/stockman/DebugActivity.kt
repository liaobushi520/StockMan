package com.liaobusi.stockman

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Typeface
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.sqlite.db.SupportSQLiteDatabase
import com.liaobusi.stockman.databinding.ActivityDebugBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.sqlite.db.SimpleSQLiteQuery

/**
 * 调试：浏览 Room 数据库各表内容（仅白名单表名，避免注入）。
 */
class DebugActivity : AppCompatActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, DebugActivity::class.java))
        }

        /** 与 [com.liaobusi.stockman.db.AppDatabase] entities / schema 一致 */
        val TABLES = listOf(
            "Stock",
            "HistoryStock",
            "BK",
            "HistoryBK",
            "BKStock",
            "Follow",
            "StockLinkage",
            "GDRS",
            "Hide",
            "AnalysisBean",
            "ZTReplayBean",
            "DIYBk",
            "PopularityRank",
            "DragonTigerRank",
            "ExpectHot",
            "UnusualActionHistory",
        )

        /** 每页行数；多查 1 行用于判断是否还有下一页 */
        private const val PAGE_SIZE = 300
        private const val MAX_CELL_CHARS = 800
    }

    private lateinit var binding: ActivityDebugBinding

    /** 从 1 开始 */
    private var pageNumber: Int = 1

    private var isLoading: Boolean = false

    /** 表名 -> (列名小写 -> PRAGMA 中的真实列名) */
    private val tableColumnMapCache = mutableMapOf<String, Map<String, String>>()

    /** 当前页内数据行下标（0 起，不含表头）；-1 表示未选中 */
    private var selectedDataRowIndex: Int = -1

    private data class TableGrid(
        val columns: Array<String>,
        val rows: List<Array<String>>,
        val hasNextPage: Boolean,
        val displayedRows: Int,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.hintTv.text =
            getString(R.string.debug_db_hint, TABLES.size, PAGE_SIZE)

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            TABLES,
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.tableSpinner.adapter = adapter
        binding.tableSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long,
                ) {
                    binding.filterDateEt.setText("")
                    binding.filterCodeEt.setText("")
                    pageNumber = 1
                    loadPage()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        binding.filterApplyBtn.setOnClickListener {
            if (!isLoading) {
                pageNumber = 1
                loadPage()
            }
        }

        binding.prevPageBtn.setOnClickListener {
            if (pageNumber > 1 && !isLoading) {
                pageNumber -= 1
                loadPage()
            }
        }
        binding.nextPageBtn.setOnClickListener {
            if (!isLoading && binding.nextPageBtn.isEnabled) {
                pageNumber += 1
                loadPage()
            }
        }

        binding.tableSpinner.post {
            loadPage()
        }
    }

    private fun selectedTableName(): String = TABLES[binding.tableSpinner.selectedItemPosition]

    private fun loadColumnMap(db: SupportSQLiteDatabase, tableName: String): Map<String, String> {
        return tableColumnMapCache.getOrPut(tableName) {
            buildMap {
                db.query(SimpleSQLiteQuery("PRAGMA table_info(`$tableName`)")).use { c ->
                    val nameIdx = c.getColumnIndex("name")
                    while (c.moveToNext()) {
                        val name = c.getString(nameIdx) ?: continue
                        put(name.lowercase(), name)
                    }
                }
            }
        }
    }

    /**
     * 仅当表中存在对应列时生效：[date] 精确匹配（数值）；[code] 模糊匹配。
     */
    private fun buildWhereClause(
        columnMap: Map<String, String>,
        dateInput: String,
        codeInput: String,
    ): Pair<String, Array<Any>> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()
        val dateCol = columnMap["date"]
        if (dateCol != null && dateInput.isNotBlank()) {
            val num = dateInput.trim().toLongOrNull()
            if (num != null) {
                clauses.add("`$dateCol` = ?")
                args.add(num)
            }
        }
        val codeCol = columnMap["code"]
        if (codeCol != null && codeInput.isNotBlank()) {
            clauses.add("`$codeCol` LIKE ?")
            args.add("%${codeInput.trim()}%")
        }
        val where =
            if (clauses.isEmpty()) "" else " WHERE ${clauses.joinToString(" AND ")}"
        return Pair(where, args.toTypedArray())
    }

    private fun loadPage() {
        val tableName = selectedTableName()
        if (tableName !in TABLES) return

        isLoading = true
        binding.prevPageBtn.isEnabled = false
        binding.nextPageBtn.isEnabled = false

        binding.toolbar.subtitle = getString(R.string.debug_db_loading)
        binding.table.removeAllViews()
        binding.pageInfoTv.text =
            getString(R.string.debug_db_page_info, pageNumber, 0)

        val offset = (pageNumber - 1) * PAGE_SIZE
        val dateFilter = binding.filterDateEt.text?.toString().orEmpty()
        val codeFilter = binding.filterCodeEt.text?.toString().orEmpty()

        lifecycleScope.launch(Dispatchers.IO) {
            val db = Injector.appDatabase.openHelper.readableDatabase
            val columnMap = loadColumnMap(db, tableName)
            val (whereSql, bindArgs) =
                buildWhereClause(columnMap, dateFilter, codeFilter)

            val sql =
                "SELECT * FROM `$tableName`$whereSql LIMIT ${PAGE_SIZE + 1} OFFSET $offset"

            val grid = (
                if (bindArgs.isEmpty()) {
                    db.query(SimpleSQLiteQuery(sql))
                } else {
                    db.query(SimpleSQLiteQuery(sql, bindArgs))
                }
                ).use { cursor ->
                    cursorToGrid(cursor)
                }

            withContext(Dispatchers.Main) {
                val hasDate = columnMap.containsKey("date")
                val hasCode = columnMap.containsKey("code")
                binding.filterSection.isVisible = hasDate || hasCode
                binding.filterDateRow.isVisible = hasDate
                binding.filterCodeRow.isVisible = hasCode

                renderGrid(grid)
                binding.pageInfoTv.text =
                    getString(R.string.debug_db_page_info, pageNumber, grid.displayedRows)
                binding.toolbar.subtitle =
                    getString(R.string.debug_db_rows, grid.displayedRows)
                binding.prevPageBtn.isEnabled = pageNumber > 1
                binding.nextPageBtn.isEnabled = grid.hasNextPage
                isLoading = false
            }
        }
    }

    private fun cursorToGrid(cursor: Cursor): TableGrid {
        val cols = cursor.columnNames
        val rows = mutableListOf<Array<String>>()
        while (cursor.moveToNext()) {
            rows += Array(cols.size) { i -> formatCell(cursor, i) }
        }

        val hasNext = rows.size > PAGE_SIZE
        val displayRows = if (hasNext) rows.take(PAGE_SIZE) else rows
        return TableGrid(cols, displayRows, hasNext, displayRows.size)
    }

    private fun renderGrid(grid: TableGrid) {
        binding.table.removeAllViews()
        selectedDataRowIndex = -1
        val pad = (8 * resources.displayMetrics.density).toInt()
        val headerBg = ContextCompat.getColor(this, R.color.debug_table_header_bg)
        val cellBg = ContextCompat.getColor(this, R.color.debug_table_cell_bg)

        fun cellTv(text: CharSequence, bold: Boolean, backgroundColor: Int): TextView {
            return TextView(this).apply {
                this.text = text
                setPadding(pad, pad / 2, pad, pad / 2)
                textSize = 10f
                setTextColor(0xFF333333.toInt())
                setBackgroundColor(backgroundColor)
                if (bold) setTypeface(null, Typeface.BOLD)
                maxLines = 12
            }
        }

        val headerRow = TableRow(this).apply {
            grid.columns.forEach { name ->
                addView(cellTv(name, bold = true, backgroundColor = headerBg))
            }
        }
        binding.table.addView(headerRow)

        grid.rows.forEachIndexed { dataIndex, cells ->
            val row = TableRow(this).apply {
                isClickable = true
                isFocusable = true
            }
            cells.forEach { cell ->
                row.addView(cellTv(cell, bold = false, backgroundColor = cellBg))
            }
            row.setOnClickListener {
                val newSel =
                    if (selectedDataRowIndex == dataIndex) -1 else dataIndex
                selectedDataRowIndex = newSel
                applyDataRowHighlight()
            }
            binding.table.addView(row)
        }
    }

    private fun applyDataRowHighlight() {
        val defaultBg = ContextCompat.getColor(this, R.color.debug_table_cell_bg)
        val selectedBg = ContextCompat.getColor(this, R.color.debug_table_row_selected)
        // child 0 为表头
        for (i in 1 until binding.table.childCount) {
            val tr = binding.table.getChildAt(i) as? TableRow ?: continue
            val selected = selectedDataRowIndex >= 0 && (i - 1) == selectedDataRowIndex
            val bg = if (selected) selectedBg else defaultBg
            for (j in 0 until tr.childCount) {
                (tr.getChildAt(j) as? TextView)?.setBackgroundColor(bg)
            }
        }
    }

    private fun formatCell(cursor: Cursor, index: Int): String {
        if (cursor.isNull(index)) return "null"
        val raw = when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> return "null"
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index).toString()
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index).toString()
            Cursor.FIELD_TYPE_STRING -> cursor.getString(index) ?: ""
            Cursor.FIELD_TYPE_BLOB ->
                "BLOB(${cursor.getBlob(index)?.size ?: 0} bytes)"
            else -> cursor.getString(index) ?: ""
        }
        return if (raw.length > MAX_CELL_CHARS) {
            raw.take(MAX_CELL_CHARS) + "…"
        } else {
            raw
        }
    }
}
