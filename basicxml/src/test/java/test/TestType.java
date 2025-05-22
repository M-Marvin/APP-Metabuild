package test;

import java.util.ArrayList;
import java.util.HashMap;

import de.m_marvin.basicxml.marshalling.adapter.XMLClassFieldAdapter;
import de.m_marvin.basicxml.marshalling.annotations.XMLField;
import de.m_marvin.basicxml.marshalling.annotations.XMLRootType;
import de.m_marvin.basicxml.marshalling.annotations.XMLField.FieldType;
import de.m_marvin.basicxml.marshalling.annotations.XMLType;
import de.m_marvin.basicxml.marshalling.annotations.XMLTypeAdapter;

@XMLType
@XMLRootType(value = "testtype", namespace = "")
public class TestType {
	
	public static final String NS = "";
	
	@XMLField(value = FieldType.ATTRIBUTE, namespace = NS)
	public boolean test;
	
	@XMLField(value = FieldType.ELEMENT, namespace = NS)
	public TestSubType testsubtype;
	
	@XMLType
	public class TestSubType {

		@XMLField(value = FieldType.ATTRIBUTE, namespace = NS)
		public String attribute1;

		@XMLField(value = FieldType.ATTRIBUTE, namespace = NS)
		public String attribute2;
		
	}

	@XMLType
	public class TestList { @XMLField(value = FieldType.ELEMENT_COLLECTION, type = TestItem.class, namespace = NS) public ArrayList<TestItem> testitem; }
	@XMLField(value = FieldType.ELEMENT, namespace = NS)
	public TestList testlist;
	
	@XMLType
	public class TestItem extends TestSubType {

		@XMLField(value = FieldType.TEXT, namespace = NS)
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
			return value.text;
		}
		
	}
	
	@XMLField(value = FieldType.REMAINING_ELEMENT_MAP, type = TestDataClass.class, namespace = NS)
	public HashMap<String, TestDataClass> remaining;
	
	@XMLField(value = FieldType.ELEMENT, namespace = NS)
	public TestEnum zzz;
	
	public static enum TestEnum {
		
		TEST1,TEST2;
		
	}
	
}
