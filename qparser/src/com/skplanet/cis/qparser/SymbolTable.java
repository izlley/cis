package com.skplanet.cis.qparser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SymbolTable
{
    private static final Logger LOG = LoggerFactory
            .getLogger(SymbolTable.class);
    
    enum KeyType { GET, PERIOD, PLOT, OPTION }
    
    private HashMap<KeyType, Object> mSymbolTable;
    
    private class PeriodTime
    {
        Long mStart = null;
        Long mEnd = null;
        void setStart( long aV) { mStart = aV; }
        void setEnd( long aV) { mEnd = aV; }
        Long getStart() { return mStart; }
        Long getEnd() { return mEnd; }
    }
    
    private class Plot
    {
        String mMetric = null;
        String mTags = null;
        String mAggfunc = null;
        boolean mIsRate = false;
        boolean mIsDownSample = false;
        String mDownSample = null;
        boolean mIsAxisY2 = false;
        
        ASTNode mWhereASTroot = null;
    }
    
    public SymbolTable()
    {
        mSymbolTable = new HashMap<KeyType, Object>();
    }

    public void setGetType( String aValue )
    {
        mSymbolTable.put( KeyType.GET, aValue);
    }
    
    public String getGetType()
    {
        return (String)mSymbolTable.get( KeyType.GET );
    }

    public void setPeriodStartTime( long aValue )
    {
        PeriodTime sPt = new PeriodTime();
        sPt.setStart(aValue);
        mSymbolTable.put( KeyType.PERIOD, sPt);
    }
    
    public Long getPeriodStartTime()
    {
        PeriodTime sPt = (PeriodTime)mSymbolTable.get( KeyType.PERIOD );
        if( sPt != null )
        {
            return sPt.getStart();
        }
        else
        {
            return null;
        }
    }
    
    public void setPeriodEndTime( long aValue )
    {
        PeriodTime sPt = (PeriodTime)mSymbolTable.get( KeyType.PERIOD );
        assert sPt != null;
        sPt.setEnd(aValue);
    }
    
    public Long getPeriodEndTime()
    {
        PeriodTime sPt = (PeriodTime)mSymbolTable.get( KeyType.PERIOD );
        if( sPt != null )
        {
            return sPt.getEnd();
        }
        else
        {
            return null;
        }
    }
    
    public void setPlotMetric( int aIndex, String aValue )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";

        if( aIndex == 0 )
        {
            ArrayList<Plot> sPlotList = new ArrayList<Plot>();
            Plot sPlot = new Plot();
            sPlot.mMetric = new String( aValue.toLowerCase() );
            sPlotList.add( sPlot );
            mSymbolTable.put( KeyType.PLOT, sPlotList );
        }
        else
        {
            @SuppressWarnings("unchecked")
            ArrayList<Plot> sPlotList = (ArrayList<Plot>)mSymbolTable.get( KeyType.PLOT );
            
            assert sPlotList != null;

            Plot sPlot = new Plot();
            sPlot.mMetric = new String( aValue.toLowerCase() );
            sPlotList.add( aIndex, sPlot );
        }
    }
    
    public String getPlotMetric( int aIndex )
    {
        @SuppressWarnings("unchecked")
        ArrayList<Plot> sPlotList = (ArrayList<Plot>)mSymbolTable.get( KeyType.PLOT );
        if( sPlotList == null )
        {
            return null;
        }
        else
        {
            Plot sPlot = sPlotList.get(aIndex);
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
        ArrayList<Plot> sPlotList = (ArrayList<Plot>)mSymbolTable.get( KeyType.PLOT );
        
        assert sPlotList != null;
        assert sPlotList.size() > aIndex;
        
        Plot sPlot = sPlotList.get( aIndex );
        sPlot.mTags = new String( aValue.toLowerCase() );
    }
    
    public String getPlotTags( int aIndex )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<Plot> sPlotList = (ArrayList<Plot>)mSymbolTable.get( KeyType.PLOT );
        if( sPlotList == null )
        {
            return null;
        }
        else
        {
            Plot sPlot = sPlotList.get(aIndex);
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
        ArrayList<Plot> sPlotList = (ArrayList<Plot>)mSymbolTable.get( KeyType.PLOT );
        
        assert sPlotList != null;
        assert sPlotList.size() > aIndex;
        
        Plot sPlot = sPlotList.get( aIndex );
        sPlot.mAggfunc = new String( aValue.toLowerCase() );
    }
    
    public String getPlotAggfunc( int aIndex, String aValue )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<Plot> sPlotList = (ArrayList<Plot>)mSymbolTable.get( KeyType.PLOT );
        if( sPlotList == null )
        {
            return null;
        }
        else
        {
            Plot sPlot = sPlotList.get(aIndex);
            if( sPlot != null )
            {
                return sPlot.mAggfunc;
            }
            return null;
        }
    }
    
    public void setPlotOption( String aK, String aV ) 
    {
        @SuppressWarnings("unchecked")
        HashMap<String, String> sOption = (HashMap<String, String>)mSymbolTable.get( KeyType.OPTION );
        
        if( sOption == null )
        {
            sOption = new HashMap<String, String>();
            mSymbolTable.put( KeyType.OPTION, sOption );
        }
        
        sOption.put(aK, aV);
    }
    
    public String getPlotOption( String aK )
    {
        @SuppressWarnings("unchecked")
        HashMap<String, String> sOption = (HashMap<String, String>)mSymbolTable.get( KeyType.OPTION );
        if( sOption == null )
        {
            return null;
        }
        else
        {
            return sOption.get( aK );
        }
    }
    
    public void setRateOption( int aIndex )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<Plot> sPlotList = (ArrayList<Plot>)mSymbolTable.get( KeyType.PLOT );
        
        assert sPlotList != null;
        assert sPlotList.size() > aIndex;
        
        Plot sPlot = sPlotList.get( aIndex );
        sPlot.mIsRate = true;
    }
    
    public boolean getRateOption( int aIndex )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<Plot> sPlotList = (ArrayList<Plot>)mSymbolTable.get( KeyType.PLOT );
        if( sPlotList == null )
        {
            return false;
        }
        else
        {
            Plot sPlot = sPlotList.get(aIndex);
            if( sPlot != null )
            {
                return sPlot.mIsRate;
            }
            return false;
        }
    }
    
    public void setDownSampleOption( int aIndex, String aVal )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<Plot> sPlotList = (ArrayList<Plot>)mSymbolTable.get( KeyType.PLOT );
        
        assert sPlotList != null;
        assert sPlotList.size() > aIndex;
        
        Plot sPlot = sPlotList.get( aIndex );
        sPlot.mIsDownSample = true;
        sPlot.mDownSample = new String( aVal );
    }
    
    public boolean getIsDownSampleOption( int aIndex )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<Plot> sPlotList = (ArrayList<Plot>)mSymbolTable.get( KeyType.PLOT );
        if( sPlotList == null )
        {
            return false;
        }
        else
        {
            Plot sPlot = sPlotList.get(aIndex);
            if( sPlot != null )
            {
                return sPlot.mIsDownSample;
            }
            return false;
        }
    }
    
    public String getDownSampleOption( int aIndex )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<Plot> sPlotList = (ArrayList<Plot>)mSymbolTable.get( KeyType.PLOT );
        if( sPlotList == null )
        {
            return null;
        }
        else
        {
            Plot sPlot = sPlotList.get(aIndex);
            if( sPlot != null )
            {
                return sPlot.mDownSample;
            }
            return null;
        }
    }
    
    public void setWhereAST( int aIndex, ASTNode aRoot )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<Plot> sPlotList = (ArrayList<Plot>)mSymbolTable.get( KeyType.PLOT );
        
        assert sPlotList != null;
        assert sPlotList.size() > aIndex;
        
        Plot sPlot = sPlotList.get( aIndex );
        sPlot.mWhereASTroot = aRoot;
    }
    
    public ASTNode getWhereAST( int aIndex )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<Plot> sPlotList = (ArrayList<Plot>)mSymbolTable.get( KeyType.PLOT );
        if( sPlotList == null )
        {
            return null;
        }
        else
        {
            Plot sPlot = sPlotList.get(aIndex);
            if( sPlot != null )
            {
                return sPlot.mWhereASTroot;
            }
            return null;
        }
    }
    
    public void setAxisy2( int aIndex, boolean aVal )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<Plot> sPlotList = (ArrayList<Plot>)mSymbolTable.get( KeyType.PLOT );
        
        assert sPlotList != null;
        assert sPlotList.size() > aIndex;
        
        Plot sPlot = sPlotList.get( aIndex );
        sPlot.mIsAxisY2 = aVal;
    }
    
    public boolean getAxisy2( int aIndex )
    {
        assert aIndex >= 0 : "Plot clause index never be negative!!";
        
        @SuppressWarnings("unchecked")
        ArrayList<Plot> sPlotList = (ArrayList<Plot>)mSymbolTable.get( KeyType.PLOT );
        if( sPlotList == null )
        {
            return false;
        }
        else
        {
            Plot sPlot = sPlotList.get(aIndex);
            if( sPlot != null )
            {
                return sPlot.mIsAxisY2;
            }
            return false;
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
        ArrayList<Plot> sPlotList = (ArrayList<Plot>)mSymbolTable.get( KeyType.PLOT );
        if( sPlotList != null )
        {
            int i = 1;
            for( Plot sP : sPlotList )
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