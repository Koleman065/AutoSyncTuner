package com.edk.autosynctuner.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * Turns AutoSync Manager HTML pages into app data. Pure functions, no network,
 * so they can be unit-tested against saved pages (see app/src/test).
 */
object Parser {

    private val VIN_PATH = Regex("""^/tuning/([A-HJ-NPR-Z0-9]{17})/?$""")
    private val SETTING_PATH = Regex("""^/tuning/[A-HJ-NPR-Z0-9]{17}/(\d+)/?$""")

    fun isLoginPage(doc: Document): Boolean =
        doc.selectFirst("form input[name=password]") != null

    fun csrfToken(doc: Document): String? =
        doc.selectFirst("input[name=csrfmiddlewaretoken]")?.attr("value")?.takeIf { it.isNotBlank() }

    // ---- Profile page (/users/me/) -------------------------------------------------

    fun parseVehicles(html: String, baseUri: String): List<Vehicle> {
        val doc = Jsoup.parse(html, baseUri)
        return doc.select("a[href]").mapNotNull { a ->
            val path = pathOf(a)
            val vin = VIN_PATH.find(path)?.groupValues?.get(1) ?: return@mapNotNull null
            val desc = a.text().removePrefix(vin).trim()
            Vehicle(vin, desc)
        }.distinctBy { it.vin }
    }

    // ---- Vehicle page (/tuning/<VIN>/) ---------------------------------------------

    fun parseModules(html: String, baseUri: String): List<Module> {
        val doc = Jsoup.parse(html, baseUri)
        val accordion = doc.getElementById("accordion")
            ?: throw SiteChangedException("Settings list not found on vehicle page")

        return accordion.children().filter { it.hasClass("panel") }.mapNotNull { modulePanel ->
            val code = modulePanel.selectFirst("> .panel-heading .panel-title a")?.text()?.trim()
                ?: return@mapNotNull null
            val body = modulePanel.selectFirst("> .panel-collapse > .panel-body") ?: return@mapNotNull null
            val categories = body.children().filter { it.hasClass("panel") }.mapNotNull { catPanel ->
                val name = catPanel.selectFirst(".panel-title a")?.text()?.trim() ?: return@mapNotNull null
                val settings = catPanel.select("ul.list-group a[href]").mapNotNull { a ->
                    val id = SETTING_PATH.find(pathOf(a))?.groupValues?.get(1) ?: return@mapNotNull null
                    SettingRef(id, a.text().trim())
                }
                if (settings.isEmpty()) null else Category(name, settings)
            }
            if (categories.isEmpty()) null else Module(code, categories)
        }
    }

    // ---- Setting page (/tuning/<VIN>/<id>/) ----------------------------------------

    fun parseSetting(id: String, html: String, baseUri: String): SettingPage {
        val doc = Jsoup.parse(html, baseUri)
        val detail = doc.selectFirst(".col-sm-9:has(form[method=post])")
            ?: doc.selectFirst("div:has(> h3):has(form[method=post])")
            ?: throw SiteChangedException("Setting details not found")
        val form = detail.selectFirst("form[method=post]")!!

        val title = detail.selectFirst("h3")?.text()?.trim().orEmpty()

        // Description paragraphs sit between the <h3> and the form; keep <br> line breaks.
        val description = detail.select("> p").map { paragraphText(it) }.filter { it.isNotBlank() }

        var stock: String? = null
        var range: String? = null
        for (div in detail.select("> div")) {
            val label = div.selectFirst("> b")?.text()?.trim() ?: continue
            val value = div.ownText().trim()
            when {
                label.startsWith("Stock Value", ignoreCase = true) -> stock = value
                label.startsWith("Range", ignoreCase = true) -> range = value
            }
        }

        val editor = parseEditor(form)

        val enabledInput = form.selectFirst("input[name=enabled]")
        val enabledIsFixed = enabledInput?.attr("type") == "hidden"
        val enabled = when {
            enabledInput == null -> true
            enabledIsFixed -> enabledInput.attr("value").equals("True", ignoreCase = true)
            else -> enabledInput.hasAttr("checked")
        }

        val submit = form.selectFirst("input[type=submit][name=update]")
        val messages = doc.select("#msg .alert, .alert").map { it.text().trim() }
            .filter { it.isNotEmpty() }.distinct()

        return SettingPage(
            id = id,
            title = title,
            description = description,
            stockValue = stock,
            range = range,
            editor = editor,
            enabled = enabled,
            enabledIsFixed = enabledIsFixed,
            submitLabel = submit?.attr("value")?.ifBlank { null } ?: "Update",
            isCustomized = form.selectFirst("input[type=submit][name=delete]") != null,
            csrfToken = form.selectFirst("input[name=csrfmiddlewaretoken]")?.attr("value")
                ?: throw SiteChangedException("Security token missing from setting form"),
            messages = messages,
        )
    }

    private fun parseEditor(form: Element): Editor {
        form.selectFirst("input#map-data[name=custom_map], input[name=custom_map]")?.let { mapInput ->
            return parseGrid(form, mapInput.attr("value"))
        }
        val radios = form.select("input[type=radio][name=custom_value]")
        if (radios.isNotEmpty()) {
            val choices = radios.map { r ->
                val label = r.parent()?.takeIf { it.tagName() == "label" }?.text()?.trim()
                    ?: r.attr("value")
                Choice(r.attr("value"), label)
            }
            val current = radios.firstOrNull { it.hasAttr("checked") }?.attr("value")
            return Editor.Radio(choices, current)
        }
        form.selectFirst("input[type=number][name=custom_value]")?.let { n ->
            return Editor.Number(
                min = n.attr("min").toDoubleOrNull(),
                max = n.attr("max").toDoubleOrNull(),
                step = n.attr("step").toDoubleOrNull(),
                current = n.attr("value").toDoubleOrNull(),
            )
        }
        form.selectFirst("select[name=custom_type_enum]")?.let { s ->
            val options = s.select("option")
            val choices = options.filter { it.attr("value").isNotBlank() }
                .map { Choice(it.attr("value"), it.text().trim()) }
            val current = options.firstOrNull { it.hasAttr("selected") }?.attr("value")?.ifBlank { null }
            return Editor.Enum(choices, current)
        }
        val names = form.select("[name]").map { it.attr("name") }
            .filterNot { it in setOf("csrfmiddlewaretoken", "enabled", "update", "delete") }
        return Editor.Unsupported("Unrecognized fields: ${names.ifEmpty { listOf("none") }.joinToString()}")
    }

    private val COLUMN_DEF = Regex("""\{\s*field:\s*'([^']*)'\s*,\s*headerName:\s*'([^']*)'""")
    private val ROW_IDX = Regex("""'idx'\s*:\s*'([^']*)'""")
    private val ROW_DATA = Regex("""setRowData\(\s*\[(.*?)]\s*\)""", RegexOption.DOT_MATCHES_ALL)
    private val MIN = Regex("""min_value\s*=\s*(-?[\d.]+)""")
    private val MAX = Regex("""max_value\s*=\s*(-?[\d.]+)""")

    private fun parseGrid(form: Element, json: String): Editor {
        val values = parseNumberMatrix(json)
            ?: return Editor.Unsupported("Could not read table values")
        val script = form.select("script").joinToString("\n") { it.data() }

        val columns = COLUMN_DEF.findAll(script)
            .filter { it.groupValues[1] != "idx" }
            .map { it.groupValues[2] }
            .toList()
        val rowHeaders = ROW_DATA.find(script)?.groupValues?.get(1)
            ?.let { body -> ROW_IDX.findAll(body).map { it.groupValues[1] }.toList() }
            .orEmpty()

        val width = values.maxOfOrNull { it.size } ?: 0
        val labels = form.select(".row b").map { it.text().trim() }

        return Editor.Grid(
            title = labels.getOrNull(0)?.ifBlank { null },
            rowAxisLabel = labels.getOrNull(1)?.ifBlank { null },
            columnHeaders = if (columns.size == width) columns else (1..width).map { it.toString() },
            rowHeaders = if (rowHeaders.size == values.size) rowHeaders else emptyList(),
            min = MIN.find(script)?.groupValues?.get(1)?.toDoubleOrNull(),
            max = MAX.find(script)?.groupValues?.get(1)?.toDoubleOrNull(),
            values = values,
        )
    }

    /** Parses "[[0.0, 1.0], [2, 3]]" without pulling in a JSON library. */
    fun parseNumberMatrix(json: String): List<List<Double>>? {
        val trimmed = json.trim()
        if (!trimmed.startsWith("[[") || !trimmed.endsWith("]]")) return null
        val rows = trimmed.removePrefix("[").removeSuffix("]")
            .split(Regex("""]\s*,\s*\[""")).map { it.trim().removePrefix("[").removeSuffix("]") }
        return rows.map { row ->
            if (row.isBlank()) emptyList()
            else row.split(",").map { it.trim().toDoubleOrNull() ?: return null }
        }
    }

    /** Serializes a table the same way the site's own page does: "[[0,1.5],[2,3]]". */
    fun formatNumberMatrix(values: List<List<Double>>): String =
        values.joinToString(",", "[", "]") { row -> row.joinToString(",", "[", "]") { formatNumber(it) } }

    fun formatNumber(v: Double): String =
        if (v == Math.rint(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()

    private fun paragraphText(p: Element): String {
        val sb = StringBuilder()
        for (node in p.childNodes()) {
            when {
                node is TextNode -> sb.append(node.text())
                node is Element && node.tagName() == "br" -> sb.append('\n')
                node is Element -> sb.append(node.text())
            }
        }
        return sb.toString().lines().joinToString("\n") { it.trim() }.trim()
    }

    private fun pathOf(a: Element): String {
        val abs = a.absUrl("href").ifBlank { a.attr("href") }
        return runCatching { java.net.URI(abs).path ?: "" }.getOrDefault("")
    }
}
