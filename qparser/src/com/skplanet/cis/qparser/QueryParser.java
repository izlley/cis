package com.skplanet.cis.qparser;

import java.util.*;
import java.text.SimpleDateFormat;

/**
 * 
 * It's a simple recursive-descent parser
 * 
 * @author leejy
 * 
 */
final class QueryParser
{
    private Scanner     mScan;
    private SymbolTable mSymbolT;
    
    private int           mCurtoknum = 0;
    private int           mCurPlotind  = -1;
    private static String mErrmsg    = "";
    private boolean       mCaseless  = true;
    private Token         mNextTok   = Token.X;
    private ArrayList<Token> mSuggestToklist;
    private Lexer         mExprLexer;

    public void doParse( String aQuery )
    {
        try
        {
            mScan = new Scanner( System.in );
            mScan.useDelimiter("\\s+");
            mSymbolT = new SymbolTable();
            mSuggestToklist = new ArrayList<Token>();
            //test();
            cisQuery();
        }
        catch(NoSuchElementException e)
        {
            System.out.println("Syntax error > " + mErrmsg);
        }
        catch(IllegalArgumentException e)
        {
            System.out.println("Syntax error > " + mErrmsg);
        }
        catch(IllegalStateException e)
        {
            System.out.println("Scanner state error > " + e.getMessage());
        }
        catch(Exception e)
        {
            System.out.println("Fatal error > " + e.getMessage());
        }
        finally
        {
            // debug
            mSymbolT.printAll();
            //
            mScan.close();
        }
    }

    public static void setErrmsg( String aMsg )
    {
        mErrmsg = aMsg;
    }
    
    private void cisQuery() throws Exception
    {
        /*
         * cis_query -> get_clause period_clause plot_clause_list plotoption_clause?';'
         */
        getClause();
        periodClause();
        plotClauseList();
        plotoptionClause();
        match(Token.SEMI);
    }

    private void getClause() throws Exception
    {
        /*
         *  get_clause -> 'GET' 'png'|'ascii'|'json'|'html'
         */

        String sGetType;

        match(Token.GET);

        if (hasnext(Token.PNG))
        {
            sGetType = match(Token.PNG);
            /* add to symbol table */
            mSymbolT.setGetType( sGetType.toLowerCase() );
        }
        else if (hasnext(Token.ASCII))
        {
            sGetType = match(Token.ASCII);
            /* add to symbol table */
            mSymbolT.setGetType( sGetType.toLowerCase() );
        }
        else if (hasnext(Token.JSON))
        {
            sGetType = match(Token.JSON);
            /* add to symbol table */
            mSymbolT.setGetType( sGetType.toLowerCase() );
        }
        else if (hasnext(Token.HTML))
        {
            sGetType = match(Token.HTML);
            /* add to symbol table */
            mSymbolT.setGetType( sGetType.toLowerCase() );
        }
        else
        {
            /*
            mErrmsg = "Error token : " + mScan.next()
                    + "\n => Suggested tokens: " + Token.PNG.getToken() + ", "
                    + Token.ASCII.getToken() + ", " + Token.JSON.getToken()
                    + ", " + Token.HTML.getToken();
            throw new NoSuchElementException();
            */
        }
    }

    private void periodClause() throws Exception
    {
        /* period_clause -> 'PERIOD' start_time ('TO' end_time)? */
        /* start_time -> date */
        /* end_time -> date */

        long sTimestamp;
        
        match(Token.PERIOD);
        sTimestamp = date();
        /* add start_time to symbol table */
        mSymbolT.setPeriodStartTime( sTimestamp );

        if( hasnext(Token.TO) )
        {
            match(Token.TO);
            sTimestamp = date();
            /* add end_time to symbol table */
            mSymbolT.setPeriodEndTime( sTimestamp );
        }
    }

    private void plotClauseList() throws Exception
    {
        /*
         * plot_clause_list -> plot_clause ('WITH' plot_clause)
         */
        plotClause();

        while( hasnext(Token.WITH) )
        {
            match(Token.WITH);
            plotClause();
        }
    }
    
    private void plotClause() throws Exception
    {
        /*
         * plot_clause -> 'PLOT' metric ('ON' 'AXISY2')? aggregator_clause? where_clause?
         * aggregator_clause -> 'AGGREGATOR' aggregation_function (rate_option)? (downsample_option)?
         * aggregation_function -> 'sum' | 'min' | 'max' | 'avg' | 'dev'
         */
        match(Token.PLOT);
        
        mCurPlotind++;
        
        metric();

        String sStr;
        if( hasnext(Token.ON) )
        {
            match(Token.ON);
            match(Token.AXISY2);
            mSymbolT.setAxisy2(mCurPlotind, true);
        }
        if( hasnext(Token.AGGREGATOR) )
        {
            match(Token.AGGREGATOR);
            if( hasnext(Token.SUM) )
            {
                sStr = match(Token.SUM);
                /* add aggregator to symbol table */
                mSymbolT.setPlotAggfunc(mCurPlotind, sStr);
                
                checkRateDownSample();
            }
            else if( hasnext(Token.MIN) )
            {
                sStr = match(Token.MIN);
                /* add aggregator to symbol table */
                mSymbolT.setPlotAggfunc(mCurPlotind, sStr);
                
                checkRateDownSample();
            }
            else if( hasnext(Token.MAX) )
            {
                sStr = match(Token.MAX);
                /* add aggregator to symbol table */
                mSymbolT.setPlotAggfunc(mCurPlotind, sStr);
                
                checkRateDownSample();
            }
            else if( hasnext(Token.AVG) )
            {
                sStr = match(Token.AVG);
                /* add aggregator to symbol table */
                mSymbolT.setPlotAggfunc(mCurPlotind, sStr);
                
                checkRateDownSample();
            }
            else if( hasnext(Token.DEV) )
            {
                sStr = match(Token.DEV);
                /* add aggregator to symbol table */
                mSymbolT.setPlotAggfunc(mCurPlotind, sStr);
                
                checkRateDownSample();
            }
            else
            {
                // WTF
            }
        }
        if( hasnext(Token.WHERE) )
        {
            match(Token.WHERE);
            ASTNode sRoot = comparisionExpr();
            ASTNode sRelOpNode = null;
            while( true )
            {
                if( hasnext(Token.AND))
                {
                    match(Token.AND);
                    ASTNode sRight = comparisionExpr();
                    sRelOpNode = new ASTNode( Token.AND, null, sRoot, sRight );
                    sRoot = sRelOpNode;
                }
                else if( hasnext(Token.OR) )
                {
                    match(Token.OR);
                    ASTNode sRight = comparisionExpr();
                    sRelOpNode =  new ASTNode( Token.OR, null, sRoot, sRight );
                    sRoot = sRelOpNode;
                }
                else
                {
                    break;
                }
            }
            mSymbolT.setWhereAST( mCurPlotind, sRoot );
        }
    }

    private void plotoptionClause() throws Exception
    {
        if( hasnext(Token.OPTION) )
        {
            match(Token.OPTION);
            
            while( hasnext(Token.OPTIONKV))
            {
                String[] sOpt = match(Token.OPTIONKV).split("=");
                verifyOptionSyntax(sOpt[0], (sOpt.length==2) ? sOpt[1] : null );
                /* add option to the symbol table */
                mSymbolT.setPlotOption( sOpt[0], (sOpt.length==2) ? sOpt[1] : null );
            }
        }
    }

    private void verifyOptionSyntax( String aK, String aV ) throws Exception
    {
        /*
         * plot_option -> 'wxy'=dimensions
         *                    | 'yrange'=range
         *                    | 'y2range'=range
         *                    | 'zrange'=range
         *                    | 'ylabel'=label
         *                    | 'y2label'=label
         *                    | 'zlabel'=label
         *                    | 'yformat'=formatstr
         *                    | 'y2format'=formatstr
         *                    | 'zformat'=formatstr
         *                    | 'ylog'=num
         *                    | 'y2log'=num
         *                    | 'zlog'=num
         *                    | 'tagaxis'=tagaxisval
         *                    | 'key'=keyval
         *                    | 'graphtype'=graphtypeval
         *                    | 'nokey'
         *                    | 'nocache'
         */
        Token sTokv = null;
        
        try
        {
            addOptionkeytoSuggestlist();
            Token sKey = Token.valueOf(aK.toUpperCase());
            
            switch( sKey )
            {
                case WXY:
                    sTokv = Token.DIMENSION;
                    if( aV == null )
                    {
                        throw new NoSuchElementException();
                    }
                    if( aV.matches("(?i)" + sTokv.getToken()) != true )
                    {
                        throw new NoSuchElementException();
                    }
                    break;
                case YRANGE:
                case Y2RANGE:
                case ZRANGE:
                    sTokv = Token.RANGE;
                    if( aV == null )
                    {
                        throw new NoSuchElementException();
                    }
                    if( aV.matches("(?i)" + sTokv.getToken()) != true )
                    {
                        throw new NoSuchElementException();
                    }
                    break;
                case YLABEL:
                case Y2LABEL:
                case ZLABEL:
                    // BUGBUG: LABEL should not contain a whitespace!
                    sTokv = Token.LABEL;
                    if( aV == null )
                    {
                        throw new NoSuchElementException();
                    }
                    if( aV.matches("(?i)" + sTokv.getToken()) != true )
                    {
                        throw new NoSuchElementException();
                    }
                    break;
                case YFORMAT:
                case Y2FORMAT:
                case ZFORMAT:
                    sTokv = Token.FORMATSTR;
                    if( aV == null )
                    {
                        throw new NoSuchElementException();
                    }
                    if( aV.matches("(?i)" + sTokv.getToken()) != true )
                    {
                        throw new NoSuchElementException();
                    }
                    break;
                case YLOG:
                case Y2LOG:
                case ZLOG:
                    sTokv = Token.NUM;
                    if( aV == null )
                    {
                        throw new NoSuchElementException();
                    }
                    if( aV.matches("(?i)" + sTokv.getToken()) != true )
                    {
                        throw new NoSuchElementException();
                    }
                    break;
                case TAGAXIS:
                    sTokv = Token.TAGAXISVAL;
                    if( aV == null )
                    {
                        throw new NoSuchElementException();
                    }
                    if( aV.matches("(?i)" + sTokv.getToken()) != true )
                    {
                        throw new NoSuchElementException();
                    }
                    break;
                case KEY:
                    sTokv = Token.KEYVAL;
                    if( aV == null )
                    {
                        throw new NoSuchElementException();
                    }
                    if( aV.matches("(?i)" + sTokv.getToken()) != true )
                    {
                        throw new NoSuchElementException();
                    }
                    break;
                case GRAPHTYPE:
                    sTokv = Token.GRAPHTYPEVAL;
                    if( aV == null )
                    {
                        throw new NoSuchElementException();
                    }
                    if( aV.matches("(?i)" + sTokv.getToken()) != true )
                    {
                        throw new NoSuchElementException();
                    }
                    break;
                case NOKEY:
                case NOCACHE:
                    sTokv = Token.X;
                    if( aV != null )
                    {
                        throw new NoSuchElementException();
                    }
                    break;
                default:
                    throw new IllegalArgumentException();
            }
        }
        catch( IllegalArgumentException e )
        {
            int i = 1;
            mErrmsg = "Error token : " + aK + "\n => Tok type(Regular expr) list of suggested tokens : [ ";
            for( Token sTok : mSuggestToklist )
            {
                mErrmsg += sTok.name() + "(" + sTok.getToken() + ")";
                if( mSuggestToklist.size() > i++ )
                {
                    mErrmsg += ", ";
                }
            }
            mErrmsg += " ]";
            mSuggestToklist.clear();
            throw e;
        }
        catch (NoSuchElementException e)
        {
            mErrmsg = "Error token : " + aV + "\n => Tok type(Regular expr) list of suggested tokens : [ " +
                             sTokv.name() + "(" + sTokv.getToken() + ") ]";
            mSuggestToklist.clear();
            throw e;
        }
    }

    private void addOptionkeytoSuggestlist() throws Exception
    {
        final Token[] sToks = { Token.WXY, Token.YRANGE, Token.Y2RANGE, Token.ZRANGE,
                                Token.YFORMAT, Token.Y2FORMAT, Token.ZFORMAT, Token.YLOG, Token.Y2LOG,
                                Token.ZLOG, Token.TAGAXIS, Token.KEY, Token.GRAPHTYPE, Token.NOKEY,
                                Token.NOCACHE };
        for( Token sTok : sToks )
        {
            mSuggestToklist.add(sTok);
        }
    }

    private void checkRateDownSample() throws Exception
    {
        /*
         * rate_option -> 'OF RATE'
         * downsample_option -> 'DOWNSAMPLING' downsample_value
         * downsample_value -> number('s'|'m'|'h'|'d'|'y')'-'aggregation_function
         */
        if( hasnext(Token.OF) )
        {
            match(Token.OF);
            match(Token.RATE);
            /* add to symbol table */
            mSymbolT.setRateOption( mCurPlotind );
        }
        
        if( hasnext(Token.DOWNSAMPLING) )
        {
            match(Token.DOWNSAMPLING);
            mSymbolT.setDownSampleOption( mCurPlotind, match(Token.DOWNSAMPLINGVAL) );
        }
    }

    private ASTNode comparisionExpr() throws Exception
    {
        /*
         * comparision_expr -> 'VALUE' relop arithmatic_expr
         * relop -> '<=' | '<' | '>' | '>=' | '=' | '!='
         */
        String sExprStr = "";
        while( !hasnext(Token.AND) && !hasnext(Token.OR) &&
                 !hasnext(Token.SEMI) && !hasnext(Token.OPTION) &&
                 !hasnext(Token.WITH))
        {
            sExprStr += next();
        }
        
        mExprLexer = new Lexer();
        mExprLexer.setExpr(sExprStr);
        
        mExprLexer.match(Token.VALUE);
        ASTNode sLeft = new ASTNode( Token.VALUE );
        ASTNode sRoot = null;
        ASTNode sRight = null;
        
        if( mExprLexer.hasnext(Token.LT) )
        {
            mExprLexer.advance();
            sRoot = new ASTNode( Token.LT );
            sRight = new ASTNode( Token.NUM, arithmatic_expr() );
        }
        else if( mExprLexer.hasnext(Token.LE) )
        {
            mExprLexer.advance();
            sRoot = new ASTNode( Token.LE );
            sRight = new ASTNode( Token.NUM, arithmatic_expr() );
        }
        else if( mExprLexer.hasnext(Token.MT) )
        {
            mExprLexer.advance();
            sRoot = new ASTNode( Token.MT );
            sRight = new ASTNode( Token.NUM, arithmatic_expr() );
        }
        else if( mExprLexer.hasnext(Token.ME) )
        {
            mExprLexer.advance();
            sRoot = new ASTNode( Token.ME );
            sRight = new ASTNode( Token.NUM, arithmatic_expr() );
        }
        else if( mExprLexer.hasnext(Token.EQ) )
        {
            mExprLexer.advance();
            sRoot = new ASTNode( Token.EQ );
            sRight = new ASTNode( Token.NUM, arithmatic_expr() );
        }
        else if( mExprLexer.hasnext(Token.NE) )
        {
            mExprLexer.advance();
            sRoot = new ASTNode( Token.NE );
            sRight = new ASTNode( Token.NUM, arithmatic_expr() );
        }
        else
        {
            mExprLexer.occurError();
        }
        
        sRoot.setLeft( sLeft );
        sRoot.setRight( sRight );
        return sRoot;
    }

    private double arithmatic_expr() throws Exception
    {
        /*
         * arithmatic_expr -> term arithmatic_expr`
         * arithmatic_expr` -> add_op term arithmatic_expr` | epsilon
         * add_op -> '+' | '-'
         */
        double sResult = term();
        
        while( mExprLexer.hasnext(Token.PLUS) || mExprLexer.hasnext(Token.MINUS) )
        {
            switch( mExprLexer.lookahead() )
            {
                case PLUS:
                    mExprLexer.advance();
                    sResult += term();
                    break;
                case MINUS:
                    mExprLexer.advance();
                    sResult -= term();
                    break;
            }
        }
        
        return sResult;
    }
    
    private double term() throws Exception
    {
        /*
         * term -> factor term`
         * term` -> mul_op factor term` | epsilon
         * mul_op -> '*' | '/'
         */
        double sResult = factor();
        
        while( mExprLexer.hasnext(Token.TIMES) || mExprLexer.hasnext(Token.OVER) )
        {
            switch( mExprLexer.lookahead() )
            {
                case TIMES:
                    mExprLexer.advance();
                    sResult *= factor();
                    break;
                case OVER:
                    mExprLexer.advance();
                    sResult /= factor();
                    break;
            }
        }
        
        return sResult;
    }
    
    private double factor() throws Exception
    {
        /*
         * factor -> number
         *           | '(' arithmatic_expr ')'
         */
        double sResult = 0;
        
        if( mExprLexer.hasnext(Token.NUM) )
        {
            sResult = Double.parseDouble( mExprLexer.getTokenString() );
            mExprLexer.advance();
        }
        else if( mExprLexer.hasnext(Token.LPAREN) )
        {
            mExprLexer.advance();
            sResult = arithmatic_expr();
            mExprLexer.match(Token.RPAREN);
        }
        else
        {
            mExprLexer.occurError();
        }
        
        return sResult;
    }
    
    private long date() throws Exception
    {
        /* date -> relative_time | absolute_time | unix_time */
        /* relative_time -> number ('s'|'m'|'h'|'d'|'y') '-ago' */
        /* absolute_time -> yyyy '/' MM '/' dd '-' HH ':' mm ':' ss */
        /* unix_time -> number */

        final String sTime;

        if (hasnext(Token.RELTIME))
        {
            sTime = match(Token.RELTIME);
            return (System.currentTimeMillis() / 1000 - parseDuration(sTime
                    .substring(0, sTime.length() - 4)));
        }
        else if (hasnext(Token.ABSTIME))
        {
            sTime = match(Token.ABSTIME);
            final SimpleDateFormat sFmt = new SimpleDateFormat(
                    "yyyy/MM/dd-HH:mm:ss");
            return sFmt.parse(sTime).getTime() / 1000;
        }
        else if (hasnext(Token.UNIXTIME))
        {
            sTime = match(Token.UNIXTIME);
            return Long.parseLong(sTime);
        }
        else
        {
            return -1;
        }
    }

    private void metric() throws Exception
    {
        /*
         * metric -> METRICID tag?
         * tag -> 
         *          | '{' tagkey '=' tagvalue (',' tagkey '=' tagvalue)* '}'
         * tagkey -> id
         * tagvalue -> id ('|' id)* | '*'
         */
        
        String sMetricID = match(Token.METRICID);
        /* add metrics to symbol table */
        mSymbolT.setPlotMetric( mCurPlotind, sMetricID );

        if( hasnext(Token.TAGLIST) )
        {
            String sTagList = match(Token.TAGLIST);
            /* add tags to symbol table */
            mSymbolT.setPlotTags( mCurPlotind, sTagList );
            
        }
    }

    private String next()
    {
        // for SEMI
        if( mNextTok != Token.X )
        {
            return mNextTok.getToken();
        }
        else
        {
            String sTok = mScan.next();
            mCurtoknum++;
            if( sTok.endsWith(";") )
            {
                mNextTok = Token.SEMI;
                return sTok.substring(0, sTok.length() - 1);
            }
            return sTok;
        }
    }
    
    private boolean hasnext(Token aTok) throws Exception
    {
        boolean sRes;
        
        if (mNextTok != Token.X)
        {
            if (mNextTok != aTok)
            {
                mSuggestToklist.add(aTok);
                sRes = false;
            }
            else
            {
                sRes = true;
            }
        }
        else
        {
            String sRegex = "";
            if (mCaseless == true)
            {
                sRegex += "(?i)";
            }
            sRegex += "(" + aTok.getToken();

            // In case of "token;"
            if (mScan.hasNext(sRegex + Token.SEMI.getToken() + ")"))
            {
                // System.out.println( "REGX = " + sRegex +
                // Token.SEMI.getToken() + ")" );
                sRes = true;
            }
            else
            {
                // System.out.println( "REGX = " + sRegex + ")" );
                if( mScan.hasNext(sRegex + ")") != true )
                {
                    mSuggestToklist.add(aTok);
                    sRes = false;
                }
                else
                {
                    sRes = true;
                }
            }
        }
        return sRes;
    }

    private String match(Token aTok) throws Exception
    {
        try
        {
            if (mNextTok != Token.X)
            {
                if (mNextTok != aTok)
                {
                    throw new NoSuchElementException();
                }
                String sRes = aTok.getToken();
                mNextTok = Token.X;

                return sRes;
            }
            else
            {
                String sRegex = "";
                if (mCaseless == true)
                {
                    sRegex += "(?i)";
                }
                sRegex += "(" + aTok.getToken();

                // In case of "token;"
                if (mScan.hasNext(sRegex + Token.SEMI.getToken() + ")"))
                {
                    // System.out.println( "REGX = " + sRegex +
                    // Token.SEMI.getToken() + ")" );
                    mNextTok = Token.SEMI;
                    mCurtoknum++;
                    String sRes = mScan.next();
                    return sRes.substring(0, sRes.length() - 1);
                }
                else
                {
                    // System.out.println( "REGX = " + sRegex + ")" );
                    mCurtoknum++;
                    return mScan.next(sRegex + ")");
                }
            }
        } 
        catch (NoSuchElementException e)
        {
            int i = 1;
            
            mErrmsg = "Error token : " + mScan.next() + "\n => Tok type(Regular expr) list of suggested tokens : [ ";
            if( mSuggestToklist.size() == 0 )
            {
                mErrmsg += aTok.name() + "(" + aTok.getToken() + ")";
            }
            else
            {
                for( Token sTok : mSuggestToklist )
                {
                    mErrmsg += sTok.name() + "(" + sTok.getToken() + ")";
                    if( mSuggestToklist.size() > i++ )
                    {
                        mErrmsg += ", ";
                    }
                }
            }
            mErrmsg += " ]";
            throw e;
        }
        catch (Exception e)
        {
            throw e;
        }
        finally
        {
            mSuggestToklist.clear();
        }
    }

    private final int parseDuration(final String duration)
    {
        int interval;
        final int lastchar = duration.length() - 1;

        interval = Integer.parseInt(duration.substring(0, lastchar));

        /*
         * catch (NumberFormatException e) { throw new
         * BadRequestException("Invalid duration (number): " + duration); } if
         * (interval <= 0) { throw new
         * BadRequestException("Zero or negative duration: " + duration); }
         */
        switch (duration.toLowerCase().charAt(lastchar))
        {
            case 's':
                return interval; // seconds
            case 'm':
                return interval * 60; // minutes
            case 'h':
                return interval * 3600; // hours
            case 'd':
                return interval * 3600 * 24; // days
            case 'w':
                return interval * 3600 * 24 * 7; // weeks
            case 'y':
                return interval * 3600 * 24 * 365; // years (screw leap years)
            default:
                // never be here
                mErrmsg = "Relative_Time format is wrong. ( regex : "
                        + Token.RELTIME.getToken() + " )";
                throw new NoSuchElementException();
        }
        // throw new BadRequestException("Invalid duration (suffix): " +
        // duration);
    }

    private void test() throws Exception
    {
        // RELTIME("[0-9]{1-10}([s]|[m]|[h]|[d]|[y])-ago"),
        // ABSTIME("[0-9]{4}/[0-9]{2}/[0-9]{2}-[0-9]{2}:[0-9]{2}:[0-9]{2}"),
        // UNIXTIME("[0-9]{9,12}"),
        // METRICID("[^\\.,`=\\{\\};]+(\\.[^\\.,`=\\{\\};]+)*"),
        // NUM("(\\+|-)?[0-9]+((\\.[0-9]+)|(\\.))?");
        // RANGE("\\[([-+.0-9a-zA-Z\\(\\)]+|\\*)?:([-+.0-9a-zA-Z\\(\\)]+|\\*)?\\]"),
        // FORMATSTR("^(|.*%..*)$"),
        //TAGAXISVAL("\\((x|y),[^,=\\{\\};]+\\)"),
        //TAGAXISVAL("\\((x|y),[^,=\\{\\};]+\\)"),
        //KEYVAL("(in|out),(left|center|right)-(top|bottom|center)(,horiz)?(,box)?"),
        
        StringBuilder sSB = new StringBuilder("123");
        for(int i = 0 ; sSB.length() + 5 > i ; i++)
        {
            System.out.print( "[" + sSB.charAt(i) + "]" );
        }
        
        
        String sPattern = Token.TAGAXISVAL.getToken();
        String aV="(X,host)";

        try
        {
            if( aV.matches("(?i)" + Token.TAGAXISVAL.getToken()) == true )
            {
                System.out.println("it Matches!!");
            }

            //Scanner Stest = new Scanner(tmpstr);
            sPattern = "(?i)(" + sPattern + ")";
            while (true)
            {
                boolean match = mScan.hasNext(sPattern);
                String res = mScan.next(sPattern);
                System.out.println("Match > " + res + " : " + match);
                //break;
            }
        }
        catch (NoSuchElementException e)
        {
            System.out.println("Syntax error > " + sPattern);
        }
    }
}
