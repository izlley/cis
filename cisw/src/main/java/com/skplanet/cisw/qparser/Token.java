package com.skplanet.cisw.qparser;

public enum Token
{
    /* token for reserving next token */
    X,
    /* special symbols */
    SEMI(";"),
    COMMA(","),
    ASSIGN("="),
    LBRACE("{"),
    RBRACE("}"),
    LPAREN("("),
    RPAREN(")"),
    /* reserved words */
    GET("GET"),
    PERIOD("PERIOD"),
    TO("TO"),
    PLOT("PLOT"),
    AGGREGATOR("AGGREGATOR"),
    OF("OF"),
    RATE("RATE"),
    DOWNSAMPLING("DOWNSAMPLING"),
    DOWNSAMPLINGVAL("[0-9]+(s|m|h|d|y)-(sum|min|max|avg|dev)"),
    WHERE("WHERE"),
    VALUE("VALUE"),
    OPTION("OPTION"),
    IGNORE("IGNORE"),
    UNION("UNION"),
    WHEN("WHEN"),
    DATA("DATA"),
    NON("NON"),
    EXISTS("EXISTS"),
    NOTIFY("NOTIFY"),
    BY("BY"),
    EMAIL("EMAIL"),
    WITH("WITH"),
    LEGEND("LEGEND"),
    /* operators */
    AND("AND"),
    OR("OR"),
    EITHER("|"),
    LT("<"),
    LE("<="),
    MT(">"),
    ME(">="),
    EQ("=="),
    NE("!="),
    TIMES("*"),
    OVER("/"),
    PLUS("+"),
    MINUS("-"),
    /* aggregation funcs */
    SUM("SUM"),
    MIN("MIN"),
    MAX("MAX"),
    AVG("AVG"),
    DEV("DEV"),
    /* get type */
    PNG("PNG"),
    ASCII("ASCII"),
    JSON("JSON"),
    HTML("HTML"),
    /* plot option key*/
    WXH("WXH"),
    YRANGE("YRANGE"),
    Y2RANGE("Y2RANGE"),
    ZRANGE("ZRANGE"),
    YLABEL("YLABEL"),
    Y2LABEL("Y2LABEL"),
    ZLABEL("ZLABEL"),
    YFORMAT("YFORMAT"),
    Y2FORMAT("Y2FORMAT"),
    ZFORMAT("ZFORMAT"),
    YLOG("YLOG"),
    Y2LOG("Y2LOG"),
    ZLOG("ZLOG"),
    TAGAXIS("TAGAXIS"),
    KEY("KEY"),
    GRAPHTYPE("GRAPHTYPE"),
    NOKEY("NOKEY"),
    NOCACHE("NOCACHE"),
    /* plot option value */
    ON("ON"),
    AXISY2("AXISY2"),
    DIMENSION("[0-9]+x[0-9]+"),
    RANGE("\\[([-+.0-9a-zA-Z\\(\\)]+|\\*)?:([-+.0-9a-zA-Z\\(\\)]+|\\*)?\\]"),
    LABEL("[^=;]+"),
    FORMATSTR("^(|.*%..*)$"),
    TAGAXISVAL("\\((x|y),[^,=\\{\\};]+\\)"),
    KEYVAL("\\((in,|out,)?(left|center|right|top|bottom|center)(-(left|center|right|top|bottom|center))?(,horiz)?(,box)?\\)"),
    // Graph types
    FILLEDLINE("FILLEDLINE"),
    LINE("LINE"),
    LINEPOINT("LINEPOINT"),
    STACK("STACK"),
    CIRCLE("CIRCLE"),
    IMPULSE("IMPULSE"),
    POINT("POINT"),
    BOX("BOX"),
    /* date */
    RELTIME("[0-9]{1,10}([s]|[m]|[h]|[d]|[w]|[y])-ago"),
    ABSTIME("[0-9]{4}/[0-9]{1,2}/[0-9]{1,2}-[0-9]{1,2}:[0-9]{1,2}:[0-9]{1,2}"),
    UNIXTIME("[0-9]{9,12}"),
    /* others */
    LEGENDSTR("\"(\"|(([^\"]|\\\"|(<metric>|<tagk>\\[[0-9]+\\]|<tagv>\\[[0-9]+\\]))+\"))"),
    EMAILADDR("[^@]+[@][^@]+"),
    METRICID("[^\\.,=\\{\\};]+(\\.[^\\.,=\\{\\};]+)*"),
    OPTIONKV("([^=\\(\\);]+=[^=;]+|nokey|nocache)"),
    TAGLIST("\\{[^,=:\\{\\};]+=[^,=\\{\\};]+(,[^,=\\{\\};]+=[^,=\\{\\};]+)*\\}"),
    NUM("(\\+|-)?[0-9]+((\\.[0-9]+)|(\\.))?"),
    ID(),
    EOI();

    private String token;

    Token(String s)
    {
        token = s;
    }

    Token()
    {
        token = null;
    }

    String getToken()
    {
        return token;
    }
}
