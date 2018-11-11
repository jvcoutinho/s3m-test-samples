/* This file was automatically generated by TightDB. */

package com.tightdb.generated;


import com.tightdb.*;
import com.tightdb.lib.*;

/**
 * This class represents a TightDB table and was automatically generated.
 */
public class TestTable extends AbstractTable<Test, TestView, TestQuery> {

	public static final EntityTypes<TestTable, TestView, Test, TestQuery> TYPES = new EntityTypes<TestTable, TestView, Test, TestQuery>(TestTable.class, TestView.class, Test.class, TestQuery.class); 

	public final LongRowsetColumn<Test, TestView, TestQuery> indexInt = new LongRowsetColumn<Test, TestView, TestQuery>(TYPES, table, 0, "indexInt");
	public final StringRowsetColumn<Test, TestView, TestQuery> second = new StringRowsetColumn<Test, TestView, TestQuery>(TYPES, table, 1, "second");
	public final LongRowsetColumn<Test, TestView, TestQuery> byteInt = new LongRowsetColumn<Test, TestView, TestQuery>(TYPES, table, 2, "byteInt");
	public final LongRowsetColumn<Test, TestView, TestQuery> smallInt = new LongRowsetColumn<Test, TestView, TestQuery>(TYPES, table, 3, "smallInt");

	public TestTable() {
		super(TYPES);
	}
	
	public TestTable(Group group) {
		super(TYPES, group);
	}

	@Override
	protected void specifyStructure(TableSpec spec) {
        registerLongColumn(spec, "indexInt");
        registerStringColumn(spec, "second");
        registerLongColumn(spec, "byteInt");
        registerLongColumn(spec, "smallInt");
    }

    public Test add(int indexInt, String second, int byteInt, int smallInt) {
        try {
        	long position = size();

        	insertLong(0, position, indexInt);
        	insertString(1, position, second);
        	insertLong(2, position, byteInt);
        	insertLong(3, position, smallInt);
        	insertDone();

        	return cursor(position);
        } catch (Exception e) {
        	throw addRowException(e);
        }

    }

    public Test insert(long position, int indexInt, String second, int byteInt, int smallInt) {
        try {
        	insertLong(0, position, indexInt);
        	insertString(1, position, second);
        	insertLong(2, position, byteInt);
        	insertLong(3, position, smallInt);
        	insertDone();

        	return cursor(position);
        } catch (Exception e) {
        	throw insertRowException(e);
        }


    }


}
