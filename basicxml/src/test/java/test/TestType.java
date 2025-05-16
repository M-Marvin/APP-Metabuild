package test;

import java.util.ArrayList;
import java.util.HashMap;

import de.m_marvin.basicxml.marshalling.adapter.XMLClassFieldAdapter;
import de.m_marvin.basicxml.marshalling.annotations.XMLField;
import de.m_marvin.basicxml.marshalling.annotations.XMLField.FieldType;
import de.m_marvin.basicxml.marshalling.annotations.XMLType;
import de.m_marvin.basicxml.marshalling.annotations.XMLTypeAdapter;

@XMLType
public class TestType {
	
	@XMLField(FieldType.ATTRIBUTE)
	public boolean test;
	
	@XMLField(FieldType.ELEMENT)
	public TestSubType testsubtype;
	
	@XMLType
	public class TestSubType {

		@XMLField(FieldType.ATTRIBUTE)
		public String attribute1;

		@XMLField(FieldType.ATTRIBUTE)
		public String attribute2;
		
	}

	@XMLType
	public class TestList { @XMLField(value = FieldType.ELEMENT_COLLECTION, type = TestItem.class) public ArrayList<TestItem> testitem; }
	@XMLField(FieldType.ELEMENT)
	public TestList testlist;
	
	@XMLType
	public class TestItem extends TestSubType {

		@XMLField(FieldType.TEXT)
		public String value;
		
	}
	
	@XMLTypeAdapter(TestDataClass.class)
	public static class TestDataClass implements XMLClassFieldAdapter<TestDataClass, Void> {
		
		public String text;

		@Override
		public TestDataClass adaptType(String str, Void parentObject) {
			TestDataClass testData = new TestDataClass();
			testData.text = str;
			return testData;
		}

		@Override
		public String typeString(TestDataClass value) {
			// TODO Auto-generated method stub
			return null;
		}
		
	}
	
	@XMLField(value = FieldType.REMAINING_ELEMENT_MAP, type = TestDataClass.class)
	public HashMap<String, TestDataClass> remaining;
	
	@XMLField(FieldType.ELEMENT)
	public TestEnum zzz;
	
	public static enum TestEnum {
		
		TEST1,TEST2;
		
	}
	
}
