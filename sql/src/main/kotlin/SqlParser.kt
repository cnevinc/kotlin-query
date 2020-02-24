package io.andygrove.kquery.sql

import java.sql.SQLException
import java.util.logging.Logger

/** SQL Expression */
interface SqlExpr

/** Simple SQL identifier such as a table or column name */
data class SqlIdentifier(val id: String) : SqlExpr {
    override fun toString() = id
}

/** Binary expression */
data class SqlBinaryExpr(val l: SqlExpr, val op: String, val r: SqlExpr) : SqlExpr {
    override fun toString(): String = "$l $op $r"
}

/** SQL literal string */
data class SqlString(val value: String) : SqlExpr {
    override fun toString() = "'$value'"
}

/** SQL literal long */
data class SqlLong(val value: Long) : SqlExpr {
    override fun toString() = "$value"
}

/** SQL literal double */
data class SqlDouble(val value: Double) : SqlExpr {
    override fun toString() = "$value"
}

/** SQL aliased expression */
data class SqlAlias(val expr: SqlExpr, val alias: SqlIdentifier) : SqlExpr

//TODO: support other expression types

//data class Function() : SqlExpr
//data class UnaryExpr() : SqlExpr
//data class CastExpr() : SqlExpr


interface SqlRelation : SqlExpr

//TODO: GROUP BY, ORDER BY, LIMIT, OFFSET
data class SqlSelect(val projection: List<SqlExpr>, val selection: SqlExpr?, val tableName: String) : SqlRelation

/**
 * Pratt Top Down Operator Precedence Parser. See https://tdop.github.io/ for paper.
 */
interface PrattParser {

    /** Parse an expression */
    fun parse(precedence: Int = 0): SqlExpr? {
        var expr = parsePrefix() ?: return null
        while ( precedence < nextPrecedence()) {
            expr = parseInfix(expr, nextPrecedence())
        }
        return expr
    }

    /** Get the precedence of the next token */
    fun nextPrecedence(): Int

    /** Parse the next prefix expression */
    fun parsePrefix(): SqlExpr?

    /** Parse the next infix expression */
    fun parseInfix(left: SqlExpr, precedence: Int): SqlExpr

}

class SqlParser(val tokens: TokenStream) : PrattParser {

    private val logger = Logger.getLogger(SqlParser::class.simpleName)

    override fun nextPrecedence(): Int {
        val token = tokens.peek() ?: return 0
        val precedence = when (token) {
            is KeywordToken -> {
                when (token.text) {
                    "AS" -> 10
                    "OR" -> 20
                    "AND" -> 30
                    else -> 0
                }
            }
            is OperatorToken -> {
                when (token.text) {
                    "<", "<=", "=", "!=", ">=", ">" -> 40
                    "+", "-" -> 50
                    "*", "/" -> 60
                    else -> 0
                }
            }
            else -> 0
        }
        logger.fine("nextPrecedence($token) returning $precedence")
        return precedence
    }

    override fun parsePrefix(): SqlExpr? {
        logger.fine("parsePrefix() next token = ${tokens.peek()}")
        val token = tokens.next() ?: return null
        val expr = when (token) {
            is KeywordToken -> {
              when (token.text) {
                  "SELECT" -> parseSelect()
                  else -> throw IllegalStateException("Unexpected keyword ${token.text}")
              }
            }
            is IdentifierToken -> SqlIdentifier(token.text)
            is LiteralStringToken -> SqlString(token.text)
            is LiteralLongToken -> SqlLong(token.text.toLong())
            is LiteralDoubleToken -> SqlDouble(token.text.toDouble())
            else -> throw IllegalStateException("Unexpected token $token")
        }
        logger.fine("parsePrefix() returning $expr")
        return expr
    }

    override fun parseInfix(left: SqlExpr, precedence: Int): SqlExpr {
        logger.fine("parseInfix() next token = ${tokens.peek()}")
        val token = tokens.peek()
        val expr = when (token) {
            is OperatorToken -> {
                tokens.next() // consume the token
                SqlBinaryExpr(left, token.text, parse(precedence) ?: throw SQLException("Error parsing infix"))
            }
            is KeywordToken -> {
                when (token.text) {
                    "AS" -> {
                        tokens.next() // consume the token
                        SqlAlias(left, parseIdentifier())
                    }
                    "AND", "OR" -> {
                        tokens.next() // consume the token
                        SqlBinaryExpr(left, token.text, parse(precedence) ?: throw SQLException("Error parsing infix"))
                    }
                    else -> throw IllegalStateException("Unexpected infix token $token")
                }
            }
            else -> throw IllegalStateException("Unexpected infix token $token")
        }
        logger.fine("parseInfix() returning $expr")
        return expr
    }

    private fun parseSelect() : SqlSelect {
        val projection = parseExprList()
        if (tokens.consumeKeyword("FROM")) {
            val table = parseExpr() as SqlIdentifier
            if (tokens.consumeKeyword("WHERE")) {
                return SqlSelect(projection, parseExpr(), table.id)
            } else {
                return SqlSelect(projection, null, table.id)
            }
        } else {
            throw IllegalStateException("Expected FROM keyword")
        }
    }

    private fun parseExprList() : List<SqlExpr> {
        logger.fine("parseExprList()")
        val list = mutableListOf<SqlExpr>()
        var expr = parseExpr()
        while (expr != null) {
            //logger.fine("parseExprList parsed $expr")
            list.add(expr)
            if (tokens.peek() == PunctuationToken(",")) {
                tokens.next()
            } else {
                break
            }
            expr = parseExpr()
        }
        logger.fine("parseExprList() returning $list")
        return list
    }

    private fun parseExpr() = parse(0)

    /** Parse the next token as an identifier, throwing an exception if the next token is not an identifier. */
    private fun parseIdentifier() : SqlIdentifier {
        val expr = parseExpr() ?: throw SQLException("Expected identifier, found EOF")
        return when (expr) {
            is SqlIdentifier -> expr
            else -> throw SQLException("Expected identifier, found $expr")
        }
    }

}