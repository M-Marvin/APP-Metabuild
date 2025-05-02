package test;

import java.util.List;

import de.m_marvin.basicxml.marshalling.annotations.XMLField;
import de.m_marvin.basicxml.marshalling.annotations.XMLField.FieldType;
import de.m_marvin.basicxml.marshalling.annotations.XMLType;

@XMLType
public class TestType {
	
	@XMLField(FieldType.ELEMENT)
	public TestSubType testsubtype;
	
	@XMLType
	public class TestSubType {

		@XMLField(FieldType.ATTRIBUTE)
		public String attribute1;

		@XMLField(FieldType.ATTRIBUTE)
		public String attribute2;
		
	}

	@XMLField(FieldType.ELEMENT)
	public List<TestItem> testlist;
	
	@XMLType
	public class TestItem extends TestSubType {

		@XMLField(FieldType.ELEMENT)
		public String value;
		
	}
	
}
