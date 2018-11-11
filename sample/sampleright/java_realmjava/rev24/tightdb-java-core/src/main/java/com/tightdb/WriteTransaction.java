package com.tightdb;

public class WriteTransaction extends Group {

    public void commit()
    {
        db.commit();
    }

    /**
     * Does the same thing as close().
     */
    public void rollback()
    {
        db.rollback();
    }

    public void close()
    {
        db.rollback();
    }


    WriteTransaction(SharedGroup db, long nativePtr)
    {
        super(nativePtr, false);    // Group is mutable
        this.db = db;
    }


    protected void finalize() {} // Nullify the actions of Group.finalize()


    private SharedGroup db;
}
