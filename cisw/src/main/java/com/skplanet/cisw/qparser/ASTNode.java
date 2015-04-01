package com.skplanet.cisw.qparser;

public class ASTNode
{
    private Token mType = null;
    private Object mValue= null;
    private ASTNode mLeft = null;
    private ASTNode mRight = null;
    
    public ASTNode( final Token aType )
    {
        mType = aType;
    }
    
    public ASTNode( final Token aType, final Object aVal )
    {
        mType = aType;
        mValue = aVal;
    }
    
    public ASTNode( final Token aType, final Object aVal, 
            final ASTNode aLeft, final ASTNode aRight )
    {
        mType = aType;
        mValue = aVal;
        mLeft = aLeft;
        mRight = aRight;
    }
    public Token getType()
    {
        return mType;
    }
    public Object getValue()
    {
        return mValue;
    }
    public ASTNode getLeft()
    {
        return mLeft;
    }
    public ASTNode getRight()
    {
        return mRight;
    }
    public void setType( final Token aVal )
    {
        mType = aVal;
    }
    public void setValue( final Object aVal )
    {
        mValue = aVal;
    }
    public void setLeft( final ASTNode aNode )
    {
        mLeft = aNode;
    }
    public void setRight( final ASTNode aNode )
    {
        mRight = aNode;
    }
}
