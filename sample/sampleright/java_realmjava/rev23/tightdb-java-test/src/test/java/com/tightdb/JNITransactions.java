package com.tightdb;

import static org.testng.AssertJUnit.assertEquals;

import java.io.File;
import java.util.Date;
import java.nio.ByteBuffer;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class JNITransactions {

    @Table(table="EmployeeTable")
    class employee {
        String firstName;
        String lastName;
        int salary;
        boolean driver;
    }

    protected SharedGroup db;

    protected String testFile = "transact.tightdb";
    
    protected void deleteFile(String filename)
    {
    	File f = new File(filename);
    	if (f.exists())
    		f.delete();
    }
    
	@BeforeMethod
	public void init() {
		deleteFile(testFile);
		db = new SharedGroup(testFile);
	}

	//@AfterMethod
	public void clear() {
		db.close();
		deleteFile(testFile);
	}

    protected void writeOneTransaction(long rows)
	{
        WriteTransaction trans = db.beginWrite();
        TableBase tbl = trans.getTable("EmployeeTable");
		TableSpec tableSpec = new TableSpec();
		tableSpec.addColumn(ColumnType.ColumnTypeString, "name");
		tableSpec.addColumn(ColumnType.ColumnTypeInt, "number");
		tbl.updateFromSpec(tableSpec);	

		for (long row=0; row < rows; row++) {
			tbl.addEmptyRow(); tbl.setString(0, row, "Hi"); tbl.setLong(1, 0, 1);
		}
		assertEquals(rows, tbl.size());
        trans.commit();
        
        // must throw exception as table is invalid now.
        try {
        	assertEquals(1, tbl.size());
        	assert(false);
        } catch (IllegalArgumentException e) {
        }
        
	}

	protected void checkRead(int rows)
	{
		// Read transaction
		ReadTransaction trans = db.beginRead();
    	TableBase tbl = trans.getTable("EmployeeTable");
    	assertEquals(true, tbl.isValid());
    	assertEquals(rows, tbl.size());
    	trans.endRead();
	}

	@Test
	public void mustWriteAndReadEmpty() {
		writeOneTransaction(0);
		checkRead(0);
		clear();
	}

	@Test
	public void mustWriteCommit() {
		writeOneTransaction(10);
		checkRead(10);
		clear();
	}

	@Test
	public void mustRollback() {
		writeOneTransaction(1);
		
        WriteTransaction trans = db.beginWrite();
        TableBase tbl = trans.getTable("EmployeeTable");

        tbl.addEmptyRow(); tbl.setString(0, 0, "Hello"); tbl.setLong(1, 0, 1);
		assertEquals(2, tbl.size());
		trans.rollback();
		
		checkRead(1); // Only 1 row now.
		
		clear();
	}

	// Test: exception at all mutable methods in TableBase, TableView,
	// Test: above in custom Typed Tables
	// TableQuery.... in ReadTransactions
	
	@Test
	public void mustFailOnWriteInReadTransactions() {
		writeOneTransaction(1);

 		ReadTransaction t = db.beginRead(); 
 		TableBase table = t.getTable("EmployeeTable");

 		ByteBuffer buf = ByteBuffer.allocate(1);
		try { table.insertBoolean(0, 0, false); 	assert(false);} catch (IllegalStateException e) {}		
		try { table.addEmptyRow(); 					assert(false);} catch (IllegalStateException e) {}
		try { table.addEmptyRows(1); 				assert(false);} catch (IllegalStateException e) {}
		try { table.addLong(0,0);					assert(false);} catch (IllegalStateException e) {}
		try { table.clear();						assert(false);} catch (IllegalStateException e) {}
		try { table.clearSubTable(0,0);				assert(false);} catch (IllegalStateException e) {}
		try { table.insertBinary(0,0,new byte[0]);	assert(false);} catch (IllegalStateException e) {}
		try { table.insertBinary(0,0,buf);			assert(false);} catch (IllegalStateException e) {}
		try { table.insertBoolean(0,0,true);		assert(false);} catch (IllegalStateException e) {}
		try { table.insertDate(0,0,new Date(0));	assert(false);} catch (IllegalStateException e) {}
		try { table.insertDone();					assert(false);} catch (IllegalStateException e) {}
		try { table.insertLong(0,0,0);				assert(false);} catch (IllegalStateException e) {}
		try { table.insertMixed(0,0,null);			assert(false);} catch (IllegalStateException e) {}
		try { table.insertString(0,0,"");			assert(false);} catch (IllegalStateException e) {}
		try { table.insertSubTable(0,0);			assert(false);} catch (IllegalStateException e) {}
		try { table.optimize();						assert(false);} catch (IllegalStateException e) {}
		try { table.remove(0);						assert(false);} catch (IllegalStateException e) {}
		try { table.removeLast();					assert(false);} catch (IllegalStateException e) {}
		try { table.setBinaryByteArray(0,0,null);	assert(false);} catch (IllegalStateException e) {}
		try { table.setBinaryByteBuffer(0,0,null);	assert(false);} catch (IllegalStateException e) {}
		try { table.setBoolean(0,0,false);			assert(false);} catch (IllegalStateException e) {}
		try { table.setDate(0,0,new Date(0));		assert(false);} catch (IllegalStateException e) {}
		try { table.setIndex(0);					assert(false);} catch (IllegalStateException e) {}
		try { table.setLong(0,0,0);					assert(false);} catch (IllegalStateException e) {}
		try { table.setMixed(0,0,null);				assert(false);} catch (IllegalStateException e) {}
		try { table.setString(0,0,"");				assert(false);} catch (IllegalStateException e) {}
		try { table.updateFromSpec(null);			assert(false);} catch (IllegalStateException e) {}
		
		TableQuery q = table.where();
		try { q.remove();		assert(false);} catch (IllegalStateException e) {}
		try { q.remove(0,0);	assert(false);} catch (IllegalStateException e) {}

		TableViewBase v = q.findAll();
		try { v.addLong(0, 0);						assert(false);} catch (IllegalStateException e) {}
		try { v.clear();							assert(false);} catch (IllegalStateException e) {}
		try { v.clearSubTable(0, 0);				assert(false);} catch (IllegalStateException e) {}
		try { v.remove(0);							assert(false);} catch (IllegalStateException e) {}
		try { v.removeLast();						assert(false);} catch (IllegalStateException e) {}
		try { v.setBinaryByteArray(0, 0, null);		assert(false);} catch (IllegalStateException e) {}
		try { v.setBinaryByteBuffer(0, 0, null);	assert(false);} catch (IllegalStateException e) {}
		try { v.setBoolean(0, 0, false);			assert(false);} catch (IllegalStateException e) {}
		try { v.setDate(0, 0, new Date());			assert(false);} catch (IllegalStateException e) {}
		try { v.setLong(0, 0, 0);					assert(false);} catch (IllegalStateException e) {}
		try { v.setString(0,0,"");					assert(false);} catch (IllegalStateException e) {}
		try { v.setMixed(0, 0, null);				assert(false);} catch (IllegalStateException e) {}
//		try { v.;	assert(false);} catch (IllegalStateException e) {}

		t.endRead();
		clear();
	}


/* 	ARM Only works for Java 1.7 - NOT available in Android.
 
	@Test(enabled=true)
	public void mustReadARM() {
		writeOneTransaction(1);
	
		// Read from table
		// System.out.println("mustReadARM.");
		try (ReadTransaction t = new ReadTransaction(db)) {
	    	EmployeeTable employees = new EmployeeTable(t);
	    	assertEquals(true, employees.isValid());
	    	assertEquals(1, employees.size());
		} 
		catch (Throwable e) {
		
		}
	}
*/

	
	@Test
	public void mustWriteCommit2() {
	    try {
	    	// Write to DB
	        WriteTransaction wt = db.beginWrite();
	        try {
	            EmployeeTable employees = new EmployeeTable(wt);
	            employees.clear();
	            employees.add("John", "Doe", 10000, true);
	    		assertEquals(1, employees.size());
	            wt.commit();
	    		// assertEquals(1, employees.size()); must set exception as employees is invalid now.
	        }
	        catch (Throwable e) {
	            wt.rollback();
	            throw new RuntimeException(e);
	        }

	        // Read from DB
	        ReadTransaction rt = db.beginRead();
		    try {
		    	EmployeeTable employees = new EmployeeTable(rt);
		    	assertEquals(1, employees.size());
		    	rt.endRead();
		    }
		    catch (Throwable e) {
	            rt.endRead();
	            throw new RuntimeException(e);
	        }

	    }
	    finally {
			clear();
	    }	    
	}
}
