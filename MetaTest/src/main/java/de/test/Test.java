package de.test;

public class Test {

	public static record RRR(String d) { }

	public static void main(String[] arg) {
		RRR r = new RRR("Hello World from Record!");
		System.out.println(r.d());
	}
	
}
