
        // Scientific function rows (shown only in scientific mode)
        if (isScientific) {
            val scientificRows = listOf(
                listOf("sin", "cos", "tan", "âˆڑ"),
                listOf("xآ²", "xت¸", "log", "ln"),
                listOf("د€", "e", "(", ")")
            )
            scientificRows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { label ->
                        CalcButton(
                            label = label,
                            backgroundColor = Color(0xFF1C1C1E),
                            textColor = Color(0xFFFF9500),
                            height = 52.dp,
                            modifier = Modifier.weight(1f),
                            onClick = { handleClick(label) }
                        )
                    }
                }
            }
        }

        // Standard grid
        val standardRows = listOf(
            listOf("AC", "âŒ«", "%", "أ·"),
            listOf("7", "8", "9", "أ—"),
            listOf("4", "5", "6", "âˆ’"),
            listOf("1", "2", "3", "+"),
            listOf("+/-", "0", ".", "=")
        )
        standardRows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { label ->
                    val bg = when (label) {
                        "أ·", "أ—", "âˆ’", "+", "=" -> Color(0xFFFF9500)
                        "AC", "âŒ«", "%", "+/-" -> Color(0xFFA5A5A5)
                        else -> Color(0xFF333333)
                    }
                    val fg = when (label) {
                        "AC", "âŒ«", "%", "+/-" -> Color.Black
                        else -> Color.White
                    }
                    CalcButton(
                        label = label,
                        backgroundColor = bg,
                        textColor = fg,
                        height = 68.dp,
                        modifier = Modifier.weight(1f),
                        onClick = { handleClick(label) }
                    )
                }
            }
        }
    }
}

@Composable
fun CalcButton(
    label: String,
    backgroundColor: Color,
    textColor: Color,
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .padding(6.dp)
            .height(height)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------- Expression engine ----------
// Grammar (highest to lowest binding):
//   primary    := NUMBER | 'د€' | 'e' | '(' expression ')' | ('-'|'+') primary | FUNCTION ['('] expression [')']
//   postfix    := primary '%'*
//   factor     := postfix ('^' factor)?      (right-associative power)
//   term       := factor (('*'|'/') factor)*
//   expression := term (('+'|'-') term)*

private fun tokenize(expr: String): List<String> {
    val tokens = mutableListOf<String>()
    var i = 0
    while (i < expr.length) {
        val c = expr[i]
        when {
            c.isWhitespace() -> i++
            c.isDigit() || c == '.' -> {
                val start = i
                while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) i++
                tokens.add(expr.substring(start, i))
            }
            c == 'د€' -> {
                tokens.add("د€"); i++
            }
            c == 'e' && !(i + 1 < expr.length && expr[i + 1].isLetter()) -> {
                tokens.add("e"); i++
            }
            FUNCTION_NAMES.any { expr.startsWith(it, i) } -> {
                val fn = FUNCTION_NAMES.first { expr.startsWith(it, i) }
                tokens.add(fn)
                i += fn.length
            }
            "+âˆ’-أ—أ·^()%".contains(c) -> {
                tokens.add(
                    when (c) {
                        'أ—' -> "*"
                        'أ·' -> "/"
                        'âˆ’' -> "-"
                        else -> c.toString()
                    }
                )
                i++
            }
            else -> i++ // skip unrecognized characters defensively
        }
    }
    return tokens
}

/** Inserts an implicit "*" between tokens like "2د€", "3(4+5)", "2sin(30)". */
private fun insertImplicitMultiplication(tokens: List<String>): List<String> {
    if (tokens.isEmpty()) return tokens
    val result = mutableListOf<String>()
    for (i in tokens.indices) {
        val token = tokens[i]
        if (i > 0) {
            val prev = tokens[i - 1]
            val prevEndsValue = prev.toDoubleOrNull() != null || prev == ")" || prev == "د€" || prev == "e"
            val currStartsValue = token.toDoubleOrNull() != null || token == "(" ||
                token == "د€" || token == "e" || token in FUNCTION_NAMES
            if (prevEndsValue && currStartsValue) result.add("*")
        }
        result.add(token)
    }
    return result
}

private class ExpressionParser(private val tokens: List<String>) {
    private var pos = 0

    private fun peek(): String? = tokens.getOrNull(pos)
    private fun consume(): String? = tokens.getOrNull(pos++)

    fun parse(): Double = parseExpression()

    private fun parseExpression(): Double {
        var value = parseTerm()
        while (peek() == "+" || peek() == "-") {
            val op = consume()
            val rhs = parseTerm()
            value = if (op == "+") value + rhs else value - rhs
        }
        return value
    }

    private fun parseTerm(): Double {
        var value = parseFactor()
        while (peek() == "*" || peek() == "/") {
            val op = consume()
            val rhs = parseFactor()
            value = if (op == "*") value * rhs else value / rhs
        }
        return value
    }

    private fun parseFactor(): Double {
        val base = parsePostfix()
        if (peek() == "^") {
            consume()
            val exponent = parseFactor() // right-associative
            return base.pow(exponent)
        }
        return base
    }

    private fun parsePostfix(): Double {
        var value = parsePrimary()
        while (peek() == "%") {
            consume()
            value /= 100.0
        }
        return value
    }

    private fun parsePrimary(): Double {
        val token = peek() ?: throw IllegalArgumentException("Unexpected end of expression")
        return when {
            token == "(" -> {
                consume()
                val value = parseExpression()
                if (peek() == ")") consume()
                value
            }
            token == "-" -> {
                consume(); -parsePrimary()
            }
            token == "+" -> {
                consume(); parsePrimary()
            }
            token == "د€" -> {
                consume(); PI
            }
            token == "e" -> {
                consume(); E
            }
            token in FUNCTION_NAMES -> {
                consume()
                val arg = if (peek() == "(") {
                    consume()
                    val v = parseExpression()
                    if (peek() == ")") consume()
                    v
                } else {
                    parsePrimary()
                }
                when (token) {
                    "sin" -> sin(Math.toRadians(arg))
                    "cos" -> cos(Math.toRadians(arg))
                    "tan" -> tan(Math.toRadians(arg))
                    "log" -> log10(arg)
                    "ln" -> ln(arg)
                    "sqrt" -> sqrt(arg)
                    else -> 0.0
                }
            }
            else -> {
                consume()
                token.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number: $token")
            }
        }
    }
}

private fun formatResult(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "ط®ط·ط£"
    return if (abs(value - value.toLong()) < 1e-9 && abs(value) < 1e15) {
        value.toLong().toString()
    } else {
        var s = "%.8f".format(value)
        s = s.trimEnd('0').trimEnd('.')
        s
    }
}
