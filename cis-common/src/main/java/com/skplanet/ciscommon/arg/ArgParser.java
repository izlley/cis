package com.skplanet.ciscommon.arg;

import java.util.*;

/**
 * Simple arguments parser.
 * @author leejy
 *
 */
public final class ArgParser
{
    private final HashMap<String, String[]> mOptions
        = new HashMap<String, String[]>();
    
    private HashMap<String, String> mParsed;
    
    public ArgParser() {}
    
    /**
     * Registers an option in this argument parser.
     * @param name The name of the option to recognize (e.g. {@code --port}).
     * @param meta The meta-variable to associate with the value of the option.
     * @param help A short description of this option.
     * @throws IllegalArgumentException if the given name was already used.
     * @throws IllegalArgumentException if the name doesn't start with a dash.
     * @throws IllegalArgumentException if any of the given strings is empty.
     */
    public void addOption( final String aName,
                           final String aMeta,
                           final String aHelp )
    {
        if( aName.isEmpty() )
        {
            throw new IllegalArgumentException("Empty name.");
        }
        else if( aName.charAt(0) != '-' )
        {
            throw new IllegalArgumentException("Name must start with a '-': " + aName);
        }
        else if( aMeta != null && aMeta.isEmpty() )
        {
            throw new IllegalArgumentException("Empty meta.");
        }
        else if( aHelp.isEmpty() )
        {
            throw new IllegalArgumentException("Empty help.");
        }
        final String[] sOld = mOptions.put(aName, new String[] {aMeta, aHelp});
        if( sOld != null )
        {
            mOptions.put(aName, sOld);
            throw new IllegalArgumentException("Option " + aName + " already defined.");
        }
    }
    
    public void addOption( final String aName,
                           final String aHelp
                         )
    {
        addOption(aName, null, aHelp);
    }
    
    public boolean optionExists( final String aName )
    {
        return mOptions.containsKey(aName);
    }
    
    /**
     * Parses the command line given in argument.
     * @return The remaining words that weren't options
     * @throws IllegalArgumentException if the given command line wasn't valid.
     */
    public String[] parse( final String[] aArgs )
    {
        mParsed = new HashMap<String, String>(mOptions.size());
        ArrayList<String> sUnparsed = null;
        for( int i = 0 ; i < aArgs.length ; i++ )
        {
            final String sArg = aArgs[i];
            String[] sOpt = mOptions.get(sArg);
            if( sOpt != null )
            {
                if( sOpt[0] != null )
                {
                    // --opt value
                    if( ++i < aArgs.length )
                    {
                        mParsed.put(sArg, aArgs[i]);
                    }
                    else
                    {
                        throw new IllegalArgumentException("Missing argument for " + sArg);
                    }
                }
                else
                {
                    // no value option
                    mParsed.put(sArg, null);
                }
                continue;
            }
            // --opt=value
            final int sIndEQ = sArg.indexOf('=', 1);
            if( sIndEQ > 0 )
            {
                final String sName = sArg.substring(0, sIndEQ);
                sOpt = mOptions.get(sName);
                if( sOpt != null )
                {
                    mParsed.put(sName, sArg.substring(sIndEQ + 1, sArg.length()));
                    continue;
                }
            }
            
            if( sUnparsed == null )
            {
                sUnparsed = new ArrayList<String>(aArgs.length - i);
            }
            if( !sArg.isEmpty() && sArg.charAt(0) == '-' )
            {
                if( sArg.length() == 2 && sArg.charAt(1) == '-' )
                {
                    for( i++ ; i < aArgs.length ; i++ )
                    {
                        sUnparsed.add(aArgs[i]);
                    }
                    break;
                }
                throw new IllegalArgumentException("Unrecognized option " + sArg);
            }
            sUnparsed.add(sArg);
        } // for
        if( sUnparsed != null )
        {
            return sUnparsed.toArray(new String[sUnparsed.size()]);
        }
        else
        {
            return new String[0];
        }
    }
    
    /**
     * Returns the value of the given option, if it was given.
     * Returns {@code null} if the option wasn't given, or if the option doesn't
     * take a value (in which case you should use {@link #has} instead).
     * @param name The name of the option to recognize (e.g. {@code --foo}).
     * @throws IllegalArgumentException if this option wasn't registered with
     * {@link #addOption}.
     * @throws IllegalStateException if {@link #parse} wasn't called.
     */
    public String get(final String aName) 
    {
      if (!mOptions.containsKey(aName))
      {
        throw new IllegalArgumentException("Unknown option " + aName);
      }
      else if (mParsed == null)
      {
        throw new IllegalStateException("parse() wasn't called");
      }
      return mParsed.get(aName);
    }

    /**
     * Returns the value of the given option, or a default value.
     * @param name The name of the option to recognize (e.g. {@code --foo}).
     * @param defaultv The default value to return if the option wasn't given.
     * @throws IllegalArgumentException if this option wasn't registered with
     * {@link #addOption}.
     * @throws IllegalStateException if {@link #parse} wasn't called.
     */
    public String get(final String aName, final String aDefault)
    {
      final String sValue = get(aName);
      return sValue == null ? aDefault : sValue;
    }

    /**
     * Returns whether or not the given option was given.
     * @param name The name of the option to recognize (e.g. {@code --foo}).
     * @throws IllegalArgumentException if this option wasn't registered with
     * {@link #addOption}.
     * @throws IllegalStateException if {@link #parse} wasn't called.
     */
    public boolean exists(final String aName)
    {
      if( !mOptions.containsKey(aName) )
      {
        throw new IllegalArgumentException("Unknown option " + aName);
      }
      else if (mParsed == null)
      {
        throw new IllegalStateException("parse() wasn't called");
      }
      return mParsed.containsKey(aName);
    }

    /**
     * Appends the usage to the given buffer.
     * @param buf The buffer to write to.
     */
    public void addUsageTo(final StringBuilder aBuf)
    {
      final ArrayList<String> sNames = new ArrayList<String>(mOptions.keySet());
      Collections.sort(sNames);
      int sMaxlength = 0;
      for (final String name : sNames)
      {
        final String[] sOpt = mOptions.get(name);
        final int sLength = name.length()
          + (sOpt[0] == null ? 0 : sOpt[0].length() + 1);
        if (sLength > sMaxlength)
        {
          sMaxlength = sLength;
        }
      }
      for (final String name : sNames)
      {
        final String[] sOpt = mOptions.get(name);
        int sLength = name.length();
        aBuf.append("  ").append(name);
        if (sOpt[0] != null)
        {
          sLength += sOpt[0].length() + 1;
          aBuf.append('=').append(sOpt[0]);
        }
        for (int i = sLength; i <= sMaxlength; i++)
        {
          aBuf.append(' ');
        }
        aBuf.append(sOpt[1]).append('\n');
      }
    }

    /**
     * Returns a usage string.
     */
    public String usage()
    {
      final StringBuilder sBuf = new StringBuilder(16 * mOptions.size());
      addUsageTo(sBuf);
      return sBuf.toString();
    }

    public String toString()
    {
      final StringBuilder sBuf = new StringBuilder(16 * mOptions.size());
      sBuf.append("ArgP(");
      for( final String sName : mOptions.keySet() )
      {
        final String[] sOpt = mOptions.get(sName);
        sBuf.append(sName)
          .append("=(").append(sOpt[0]).append(", ").append(sOpt[1]).append(')')
          .append(", ");
      }
      sBuf.setLength(sBuf.length() - 2);
      sBuf.append(')');
      return sBuf.toString();
    }
}
