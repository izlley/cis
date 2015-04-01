package com.skplanet.cisw.qparser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SymbolTable
{
    private static final Logger LOG = LoggerFactory
            .getLogger(SymbolTable.class);
    
    public static enum KeyType { GET, PERIOD, PLOT, WHEN, OPTION }
    
    private HashMap<KeyType, Object> mSymbolTable;
    
    private class symPeriodTime
    {
        long mStart = -1;
        boolean mStartIsRelitive = false;
        long mEnd = -1;
        boolean mEndIsRelitive = false;
        void setStart( long aV) { mStart = aV; }
        void setEnd( long aV) { mEnd = aV; }
        void setStartIsRelitive( boolean aV) { mStartIsRelitive = aV; }
        void setEndIsRelitive( boolean aV)   { mEndIsRelitive = aV; }
        Long getStart() { return mStart; }
        Long getEnd() { return mEnd; }
        boolean getStartIsRelitive() { return mStartIsRelitive; }
        boolean getEndIsRelitive()   { return mEndIsRelitive; }
    }
    
    public class symPlot
    {
        String mMetric = null;
        String mTags = null;
        String mAggfunc = null;
        boolean mIsRate = false;
        boolean mIsDownSample = false;
        String mDownSample = null;
        boolean mIsAxisY2 = false;
        Token mGraphType = null;
        ASTNode mWhereASTroot = null;
        String mLegend = null;
    }
    
    public static enum EventType { DATA_EXISTS, DATA_NONEXISTS }
    public static enum ActionType { NOTIFY_EMAIL, NOTIFY_SMS }
    
    public class symWhen
    {
        EventType mEvent;
        ActionType mAction;
        String mTo = null;
    }
    
    public SymbolTable()
    {
        mSymbolTable = new HashMap<KeyType, Object>();
    }

    public void setGetType( String aValue )
    {
        mSymbolTable.put( KeyType.GET, aValue.toLowerCase());
    }
    
    public void removeKV( KeyType aType )
    {
        if( mSymbolTable == null )
        {
            return;
        }
        mSymbolTable.remove( aType );
    }
    
    public HashMap<KeyType, Object> getSymbolTable()
    {
        return mSymbolTable;
    }
    
    public ArrayList<symPlot> getPlotlist()
    {
        return (ArrayList<symPlot>)(mSymbolTable.get( KeyType.PLOT ));
    }
    
    public String getGetType()
    {
        return (String)mSymbolTable.get( KeyType.GET );
    }

    public void setPeriodStartTime( long aValue )
    {
        symPeriodTime sPt = (symPeriodTime)mSymbolTable.get( KeyType.PERIOD );
        if( sPt == null )
        {
            sPt = new symPeriodTime();
            sPt.setStart(aValue);
            mSymbolTable.put( KeyType.PERIOD, sPt);
        }
        else
        {
            sPt.setStart(aValue);
        }
    }
    
    public long getPeriodStartTime()
    {
        symPeriodTime sPt = (symPeriodTime)mSymbolTable.get( KeyType.PERIOD );
        if( sPt != null )
        {
            return sPt.getStart();
        }
        else
        {
            return -1;
        }
    }
    
    public void setPeriodStartTimeIsRelative( boolean aValue )
    {
        symPeriodTime sPt = (symPeriodTime)mSymbolTable.get( KeyType.PERIOD );
        if( sPt == null )
        {
            sPt = new symPeriodTime();
            sPt.setStartIsRelitive(aValue);
            mSymbolTable.put( KeyType.PERIOD, sPt);
        }
        else
        {
            sPt.setStartIsRelitive(aValue);
        }
    }
    
    public boolean getPeriodStartTimeIsRelative()
    {
        symPeriodTime sPt = (symPeriodTime)mSymbolTable.get( KeyType.PERIOD );
        if( sPt != null )
        {
            return sPt.getStartIsRelitive();
        }
        else
        {
            return false;
        }
    }
    
    public void setPeriodEndTime( long aValue )
    {
        symPeriodTime sPt = (symPeriodTime)mSymbolTable.get( KeyType.PERIOD );
        assert sPt != null;
        sPt.setEnd(aValue);
    }
    
    public long getPeriodEndTime()
    {
        symPeriodTime sPt = (symPeriodTime)mSymbolTable.get( KeyType.PERIOD );
        if( sPt != null )
        {
            return sPt.getEnd();
        }
        else
        {
            return -1;
        }
    }
    
    public void setPeriodEndTimeIsRelative( boolean aValue )
    {
        symPeriodTime sPt = (symPeriodTime)mSymbolTable.get( KeyType.PERIOD );
        assert sPt != null;
        sPt.setEndIsRelitive(aValue);
    }
    
    public boolean getPeriodEndTimeIsRelative()
    {
        symPeriodTime sPt = (symPeriodTime)mSymbolTable.get( KeyType.PERIOD );
        if( sPt != null )
        {
            return sPt.getEndIsRelitive();
        }
        else
        {
            return false;
        }
    }
    
    public void setPlotMetric( int aIndex, String aValue )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";

        if( aIndex == 0 )
        {
            ArrayList<symPlot> sPlotList = new ArrayList<symPlot>();
            symPlot sPlot = new symPlot();
            sPlot.mMetric = new String( aValue );
            sPlotList.add( sPlot );
            mSymbolTable.put( KeyType.PLOT, sPlotList );
        }
        else
        {
            @SuppressWarnings("unchecked")
            ArrayList<symPlot> sPlotList = (ArrayList<symPlot>)mSymbolTable.get( KeyType.PLOT );
            
            assert sPlotList != null;

            symPlot sPlot = new symPlot();
            sPlot.mMetric = new String( aValue );
            sPlotList.add( aIndex, sPlot );
        }
    }
    
    public String getPlotMetric( int aIndex )
    {
        @SuppressWarnings("unchecked")
        ArrayList<symPlot> sPlotList = (ArrayList<symPlot>)mSymbolTable.get( KeyType.PLOT );
        if( sPlotList == null )
        {
            return null;
        }
        else
        {
            symPlot sPlot = sPlotList.get(aIndex); // zero-base
            if( sPlot != null )
            {
                return sPlot.mMetric;
            }
            return null;
        }
    }
    
    public void setPlotTags( int aIndex, String aValue )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";

        @SuppressWarnings("unchecked")
        ArrayList<symPlot> sPlotList = (ArrayList<symPlot>)mSymbolTable.get( KeyType.PLOT );
        
        assert sPlotList != null;
        assert sPlotList.size() > aIndex;
        
        symPlot sPlot = sPlotList.get( aIndex );
        sPlot.mTags = new String( aValue );
    }
    
    public String getPlotTags( int aIndex )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<symPlot> sPlotList = (ArrayList<symPlot>)mSymbolTable.get( KeyType.PLOT );
        if( sPlotList == null )
        {
            return null;
        }
        else
        {
            symPlot sPlot = sPlotList.get(aIndex);
            if( sPlot != null )
            {
                return sPlot.mTags;
            }
            return null;
        }
    }
    
    public void setPlotAggfunc( int aIndex, String aValue )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";

        @SuppressWarnings("unchecked")
        ArrayList<symPlot> sPlotList = (ArrayList<symPlot>)mSymbolTable.get( KeyType.PLOT );
        
        assert sPlotList != null;
        assert sPlotList.size() > aIndex;
        
        symPlot sPlot = sPlotList.get( aIndex );
        sPlot.mAggfunc = new String( aValue.toLowerCase() );
    }
    
    public String getPlotAggfunc( int aIndex )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<symPlot> sPlotList = (ArrayList<symPlot>)mSymbolTable.get( KeyType.PLOT );
        if( sPlotList == null )
        {
            return null;
        }
        else
        {
            symPlot sPlot = sPlotList.get(aIndex);
            if( sPlot != null )
            {
                return sPlot.mAggfunc;
            }
            return null;
        }
    }
    
    public void setWhenEvent( EventType aEvent )
    {
        @SuppressWarnings("unchecked")
        symWhen sWhen = (symWhen)mSymbolTable.get( KeyType.WHEN );
        
        if( sWhen == null )
        {
            sWhen = new symWhen();
            mSymbolTable.put( KeyType.WHEN, sWhen);
        }
        
        sWhen.mEvent = aEvent;
    }
    
    public void setWhenAction( ActionType aAction )
    {
        @SuppressWarnings("unchecked")
        symWhen sWhen = (symWhen)mSymbolTable.get( KeyType.WHEN );
        
        if( sWhen == null )
        {
            sWhen = new symWhen();
            mSymbolTable.put( KeyType.WHEN, sWhen);
        }
        
        sWhen.mAction = aAction;
    }
    
    public void setWhenTo( String aTo )
    {
        @SuppressWarnings("unchecked")
        symWhen sWhen = (symWhen)mSymbolTable.get( KeyType.WHEN );
        
        if( sWhen == null )
        {
            sWhen = new symWhen();
            mSymbolTable.put( KeyType.WHEN, sWhen);
        }
        
        sWhen.mTo = aTo;
    }
    
    public EventType getWhenEvent()
    {
        @SuppressWarnings("unchecked")
        symWhen sWhen = (symWhen)mSymbolTable.get( KeyType.WHEN );
        
        if( sWhen == null )
        {
            return null;
        }
        else
        {
            return sWhen.mEvent;
        }
    }
    
    public ActionType getWhenAction()
    {
        @SuppressWarnings("unchecked")
        symWhen sWhen = (symWhen)mSymbolTable.get( KeyType.WHEN );
        
        if( sWhen == null )
        {
            return null;
        }
        else
        {
            return sWhen.mAction;
        }
    }
    
    public String getWhenTo()
    {
        @SuppressWarnings("unchecked")
        symWhen sWhen = (symWhen)mSymbolTable.get( KeyType.WHEN );
        
        if( sWhen == null )
        {
            return null;
        }
        else
        {
            return sWhen.mTo;
        }
    }
    
    public void setOption( String aK, String aV ) 
    {
        @SuppressWarnings("unchecked")
        HashMap<String, String> sOption = (HashMap<String, String>)mSymbolTable.get( KeyType.OPTION );
        
        if( sOption == null )
        {
            sOption = new HashMap<String, String>();
            mSymbolTable.put( KeyType.OPTION, sOption );
        }
        
        sOption.put( aK.toLowerCase(), aV.toLowerCase() );
    }
    
    public String getOption( String aK )
    {
        @SuppressWarnings("unchecked")
        HashMap<String, String> sOption = (HashMap<String, String>)mSymbolTable.get( KeyType.OPTION );
        if( sOption == null )
        {
            return null;
        }
        else
        {
            return sOption.get( aK.toLowerCase() );
        }
    }
    
    public boolean existOption( String aK )
    {
        @SuppressWarnings("unchecked")
        HashMap<String, String> sOption = (HashMap<String, String>)mSymbolTable.get( KeyType.OPTION );
        if( sOption == null )
        {
            return false;
        }
        else
        {
            return sOption.containsKey( aK.toLowerCase() );
        }
    }
    
    public void setPlotRateOption( int aIndex )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<symPlot> sPlotList = (ArrayList<symPlot>)mSymbolTable.get( KeyType.PLOT );
        
        assert sPlotList != null;
        assert sPlotList.size() > aIndex;
        
        symPlot sPlot = sPlotList.get( aIndex );
        sPlot.mIsRate = true;
    }
    
    public boolean getPlotRateOption( int aIndex )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<symPlot> sPlotList = (ArrayList<symPlot>)mSymbolTable.get( KeyType.PLOT );
        if( sPlotList == null )
        {
            return false;
        }
        else
        {
            symPlot sPlot = sPlotList.get(aIndex);
            if( sPlot != null )
            {
                return sPlot.mIsRate;
            }
            return false;
        }
    }
    
    public void setPlotDownSampleOption( int aIndex, String aVal )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<symPlot> sPlotList = (ArrayList<symPlot>)mSymbolTable.get( KeyType.PLOT );
        
        assert sPlotList != null;
        assert sPlotList.size() > aIndex;
        
        symPlot sPlot = sPlotList.get( aIndex );
        sPlot.mIsDownSample = true;
        sPlot.mDownSample = new String( aVal.toLowerCase() );
    }
    
    public boolean getPlotIsDownSampleOption( int aIndex )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<symPlot> sPlotList = (ArrayList<symPlot>)mSymbolTable.get( KeyType.PLOT );
        if( sPlotList == null )
        {
            return false;
        }
        else
        {
            symPlot sPlot = sPlotList.get(aIndex);
            if( sPlot != null )
            {
                return sPlot.mIsDownSample;
            }
            return false;
        }
    }
    
    public String getPlotDownSampleOption( int aIndex )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<symPlot> sPlotList = (ArrayList<symPlot>)mSymbolTable.get( KeyType.PLOT );
        if( sPlotList == null )
        {
            return null;
        }
        else
        {
            symPlot sPlot = sPlotList.get(aIndex);
            if( sPlot != null )
            {
                return sPlot.mDownSample;
            }
            return null;
        }
    }

    public void setPlotLegendOption( int aIndex, String aVal )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<symPlot> sPlotList = (ArrayList<symPlot>)mSymbolTable.get( KeyType.PLOT );
        
        assert sPlotList != null;
        assert sPlotList.size() > aIndex;
        
        symPlot sPlot = sPlotList.get( aIndex );
        sPlot.mLegend = new String( aVal );
    }
    
    public String getPlotLegendOption( int aIndex )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<symPlot> sPlotList = (ArrayList<symPlot>)mSymbolTable.get( KeyType.PLOT );
        if( sPlotList == null )
        {
            return null;
        }
        else
        {
            symPlot sPlot = sPlotList.get(aIndex);
            if( sPlot != null )
            {
                return sPlot.mLegend;
            }
            return null;
        }
    }
    
    public void setPlotWhereAST( int aIndex, ASTNode aRoot )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<symPlot> sPlotList = (ArrayList<symPlot>)mSymbolTable.get( KeyType.PLOT );
        
        assert sPlotList != null;
        assert sPlotList.size() > aIndex;
        
        symPlot sPlot = sPlotList.get( aIndex );
        sPlot.mWhereASTroot = aRoot;
    }
    
    public ASTNode getPlotWhereAST( int aIndex )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<symPlot> sPlotList = (ArrayList<symPlot>)mSymbolTable.get( KeyType.PLOT );
        if( sPlotList == null )
        {
            return null;
        }
        else
        {
            symPlot sPlot = sPlotList.get(aIndex);
            if( sPlot != null )
            {
                return sPlot.mWhereASTroot;
            }
            return null;
        }
    }
    
    public void setPlotIsAxisy2( int aIndex, boolean aVal )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<symPlot> sPlotList = (ArrayList<symPlot>)mSymbolTable.get( KeyType.PLOT );
        
        assert sPlotList != null;
        assert sPlotList.size() > aIndex;
        
        symPlot sPlot = sPlotList.get( aIndex );
        sPlot.mIsAxisY2 = aVal;
    }
    
    public boolean getPlotIsAxisy2( int aIndex )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<symPlot> sPlotList = (ArrayList<symPlot>)mSymbolTable.get( KeyType.PLOT );
        if( sPlotList == null )
        {
            return false;
        }
        else
        {
            symPlot sPlot = sPlotList.get(aIndex);
            if( sPlot != null )
            {
                return sPlot.mIsAxisY2;
            }
            return false;
        }
    }
    
    public void setPlotGraphtype( int aIndex, Token aVal )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<symPlot> sPlotList = (ArrayList<symPlot>)mSymbolTable.get( KeyType.PLOT );
        
        assert sPlotList != null;
        assert sPlotList.size() > aIndex;
        
        symPlot sPlot = sPlotList.get( aIndex );
        sPlot.mGraphType = aVal;
    }
    
    public Token getPlotGraphtype( int aIndex )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<symPlot> sPlotList = (ArrayList<symPlot>)mSymbolTable.get( KeyType.PLOT );
        if( sPlotList == null )
        {
            return Token.X;
        }
        else
        {
            symPlot sPlot = sPlotList.get(aIndex);
            if( sPlot != null )
            {
                if( sPlot.mGraphType != null )
                {
                    return sPlot.mGraphType;
                }
            }
            return Token.X;
        }
    }
    
    public void printAll()
    {
        StringBuilder sDebugSymT = new StringBuilder();
        sDebugSymT.append( "=========================================\n" );
        sDebugSymT.append( "GET      : " + getGetType() + "\n" );
        sDebugSymT.append( "PERIOD : " + "S=" + getPeriodStartTime() + ", E=" + getPeriodEndTime() + "\n" );
        @SuppressWarnings("unchecked")
        HashMap<String, String> sOption = (HashMap<String, String>)mSymbolTable.get( KeyType.OPTION );
        if( sOption != null )
        {
            sDebugSymT.append( "OPTION : " + sOption.toString() + "\n" );
        }
        @SuppressWarnings("unchecked")
        ArrayList<symPlot> sPlotList = (ArrayList<symPlot>)mSymbolTable.get( KeyType.PLOT );
        if( sPlotList != null )
        {
            int i = 1;
            for( symPlot sP : sPlotList )
            {
                sDebugSymT.append( "PLOT[" + (i++) + "]\n" );
                sDebugSymT.append( "   METRIC               : " + sP.mMetric + "\n" );
                sDebugSymT.append( "   TAGS                  : " + sP.mTags + "\n" );
                sDebugSymT.append( "   AGGFUNC            : " + sP.mAggfunc + "\n" );
                sDebugSymT.append( "   IS RATE               : " + sP.mIsRate + "\n" );
                sDebugSymT.append( "   IS DOWNSAMPLE : " + sP.mIsDownSample + "\n" );
                sDebugSymT.append( "   DOWNSAMPLE     : " + sP.mDownSample + "\n" );
                sDebugSymT.append( "   IS AXISY2             : " + sP.mIsAxisY2 + "\n" );
                if( sP.mWhereASTroot != null )
                {
                    List<ASTNode> sList = new ArrayList<ASTNode>();
                    sList.add(sP.mWhereASTroot);
                    sDebugSymT.append( "   WHERE               :\n" );
                    levelOrder(sList, 0, sDebugSymT);
                }
                else
                {
                    sDebugSymT.append( "   WHERE               : null\n" );
                }
            }
        }
        @SuppressWarnings("unchecked")
        symWhen sWhen = (symWhen)mSymbolTable.get( KeyType.WHEN );
        if( sWhen != null )
        {
            sDebugSymT.append( "   WHEN               :\n" );
            sDebugSymT.append( "       WHEN_EVENT   :" + getWhenEvent().toString() + "\n" );
            sDebugSymT.append( "       WHEN_ACTION :" + getWhenAction().toString() + "\n" );
            sDebugSymT.append( "       WHEN_TO        :" + getWhenTo() + "\n" );
        }
        else
        {
            sDebugSymT.append( "   WHEN               : null\n" );
        }
        
        sDebugSymT.append( "=========================================\n" );
        
        LOG.info( "[Check Symboltable]\n" + sDebugSymT);
    }
    
    public void levelOrder(List<ASTNode> n, int aLev, StringBuilder aDebugSymT )
    {
        List<ASTNode> next = new ArrayList<ASTNode>();
        for( int i = 5 ; aLev < i ; i--)
        {
            aDebugSymT.append("          ");
        }
        for (ASTNode t : n) 
        {
            if (t != null) 
            {
                aDebugSymT.append( "(" + t.getType().name() + ":" + 
                        ((t.getValue()!=null)?t.getValue():"") + ")     " );
                next.add(t.getLeft());
                next.add(t.getRight());
            }
        }
        aDebugSymT.append("\n");
        if(next.size() > 0)levelOrder(next, ++aLev, aDebugSymT);
    }
}