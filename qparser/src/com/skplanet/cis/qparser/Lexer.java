package com.skplanet.cis.qparser;

import java.util.ArrayList;
import java.util.NoSuchElementException;

public class Lexer
{
    StringBuilder mExpr            = new StringBuilder();
    StringBuilder mTokenString = new StringBuilder();
    Token mCurrentToken        = Token.X;
    // input string index
    int mExprInd   = 0;
    // lexeme index
    //int mTokInd = 0;
    
    private ArrayList<Token> mSuggestToklist = new ArrayList<Token>();
    
    private enum LEXState { START, INNUM, INID, INLE, INME, INNE, DONE }
    
    public void setExpr( String aStr )
    {
        clearString();
        mExpr.append( aStr );
    }
    
    public String getTokenString()
    {
        return mTokenString.toString();
    }
    
    public Token lookahead() throws Exception
    {
        if( mCurrentToken == Token.X )
        {
            advance();
        }
        return mCurrentToken;
    }
    
    public boolean hasnext( Token aTok ) throws Exception
    {
        if( mCurrentToken == Token.X )
        {
            advance();
        }
        
        if( mCurrentToken == aTok )
        {
            return true;
        }
        else
        {
            mSuggestToklist.add(aTok);
            return false;
        }
    }
    
    public void match( Token aTok ) throws Exception
    {
        if( mCurrentToken == Token.X )
        {
            advance();
        }

        if( mCurrentToken == aTok )
        {
            // if true always advancing
            // we don't call advance() at all.
            advance();
        }
        else
        {
            String sErrmsg = "Error token : " + mTokenString.toString() + "\n" +
                                     " => Suggested token : [ " +
                                     aTok.name() + "(" + aTok.getToken() + ")" + " ]\n\n";
           
            sErrmsg += mExpr.toString() + "\n";
            for( int i = 0 ; (mExprInd - 1) > i ; i++ )
            {
                sErrmsg += "+";
            }
            sErrmsg += "^\n";
            
            QueryParser.setErrmsg( sErrmsg );
            throw new NoSuchElementException();
        }
    }
    
    public void advance() throws Exception
    {
        mSuggestToklist.clear();
        mCurrentToken = lex();
    }

    public void occurError() throws Exception
    {
        int i = 1;
        String sErrmsg = "Error token char : " + mTokenString.toString() + "\n" +
                                 " => Tok type(Regular expr) list of suggested tokens : [ ";

        if( mSuggestToklist.size() == 0 )
        {
            sErrmsg += "none";
        }
        else
        {
            for( Token sTok : mSuggestToklist )
            {
                sErrmsg += sTok.name() + "(" + sTok.getToken() + ")";
                if( mSuggestToklist.size() > i++ )
                {
                    sErrmsg += ", ";
                }
            }
        }
        sErrmsg += " ]\n\n";

        sErrmsg += mExpr.toString() + "\n";
        for( i = 0 ; (mExprInd - 1) > i ; i++ )
        {
            sErrmsg += "+";
        }
        sErrmsg += "^\n";
        
        QueryParser.setErrmsg( sErrmsg );
        throw new NoSuchElementException();
    }

    private void clearString()
    {
        mExpr.setLength(0);
        mTokenString.setLength(0);
    }

    private Token lex() throws Exception
    {
        char sCh;
        /* current state - always begins at START */
        LEXState sState = LEXState.START;
        /* flag to indicate save to tokenString */
        boolean sIsSave;
        boolean sIsError = false;
        /* init token buffer */
        mTokenString.setLength(0);
        
        while( sState != LEXState.DONE )
        {/*
            if( mExpr.length() <= mExprInd )
            {
                sState = LEXState.DONE;
                mCurrentToken = Token.EOI;
                break;
            }
            else
            {
                sCh = mExpr.charAt( mExprInd++ );
            }
            */
            try
            {
                sCh = mExpr.charAt( mExprInd++ );
            }
            catch( IndexOutOfBoundsException e )
            {
                sCh = '\0';
            }
            
            sIsSave = true;
            switch( sState )
            {
                case START:
                    if( Character.isDigit(sCh) )
                    {
                        sState = LEXState.INNUM;
                    }
                    else if( Character.isLetter(sCh) )
                    {
                        sState = LEXState.INID;
                    }
                    else if( sCh == '<' )
                    {
                        sState = LEXState.INLE;
                    }
                    else if( sCh == '>' )
                    {
                        sState = LEXState.INME;
                    }
                    else if( sCh == '!' )
                    {
                        sState = LEXState.INNE;
                    }
                    else if( (sCh == ' ') || (sCh == '\t' ) || (sCh == '\n') )
                    {
                        sIsSave = false;
                    }
                    else
                    {
                        sState = LEXState.DONE;
                        switch( sCh )
                        {
                            case '\0':
                                sIsSave = false;
                                mCurrentToken = Token.EOI;
                                break;
                            case '=':
                                mCurrentToken = Token.EQ;
                                break;
                            case '+':
                                mCurrentToken = Token.PLUS;
                                break;
                            case '-':
                                mCurrentToken = Token.MINUS;
                                break;
                            case '*':
                                mCurrentToken = Token.TIMES;
                                break;
                            case '/':
                                mCurrentToken = Token.OVER;
                                break;
                            case '(':
                                mCurrentToken = Token.LPAREN;
                                break;
                            case ')':
                                mCurrentToken = Token.RPAREN;
                                break;
                            case ';':
                                mCurrentToken = Token.SEMI;
                                break;
                            default:
                                /* unknown character */
                                sIsError = true;
                                break;
                        }
                    }
                    break;
                    //private enum LEXState { START, INNUM, INID, INLE, INME, INNE, DONE }
                case INLE:
                    sState = LEXState.DONE;
                    if( sCh == '=' )
                    {
                        mCurrentToken = Token.LE;
                    }
                    else
                    {
                        // unget char
                        --mExprInd;
                        sIsSave = false;
                        mCurrentToken = Token.LT;
                    }
                    break;
                case INME:
                    sState = LEXState.DONE;
                    if( sCh == '=' )
                    {
                        mCurrentToken = Token.ME;
                    }
                    else
                    {
                        // unget char
                        --mExprInd;
                        sIsSave = false;
                        mCurrentToken = Token.MT;
                    }
                    break;
                case INNE:
                    sState = LEXState.DONE;
                    if( sCh == '=' )
                    {
                        mCurrentToken = Token.NE;
                    }
                    else
                    {
                     // unget char
                        --mExprInd;
                        sIsSave = false;
                        sIsError = true;
                    }
                    break;
                case INNUM:
                    if( !Character.isDigit(sCh) )
                    {
                        // unget char
                        --mExprInd;
                        sIsSave = false;
                        sState = LEXState.DONE;
                        mCurrentToken = Token.NUM;
                    }
                    break;
                case INID:
                    if( !Character.isLetter(sCh) )
                    {
                        // unget char
                        --mExprInd;
                        sIsSave = false;
                        sState = LEXState.DONE;
                        mCurrentToken = Token.ID;
                    }
                    break;
                case DONE:
                default: /* should never happen */
                    String sErrmsg = "ERR] Lexer state is unacceptable: state= " + sState + "\n";
                    QueryParser.setErrmsg( sErrmsg );
                    throw new NoSuchElementException();
            }
            if( sIsSave )
            {
                mTokenString.append(sCh);
            }
            if( sState == LEXState.DONE )
            {
                if( mCurrentToken == Token.ID )
                {
                    mCurrentToken = reservedLookup( mTokenString.toString() );
                }
            }
        }
        
        if( sIsError == true )
        {
            String sErrmsg = "Error token char : " + mTokenString.toString() + "\n\n" +
                                      mExpr.toString() + "\n";
            for( int i = 0 ; (mExprInd - 1) > i ; i++ )
            {
                sErrmsg += "+";
            }
            sErrmsg += "^\n";
            
            QueryParser.setErrmsg( sErrmsg );
            throw new NoSuchElementException();
        }
        //
        
        //
        return mCurrentToken;
    }
    
    private Token reservedLookup( String aId )
    {
        if( aId.matches("(?i)" + Token.VALUE.getToken()) )
        {
            return Token.VALUE;
        }
        else
        {
            return Token.ID;
        }
    }
}

