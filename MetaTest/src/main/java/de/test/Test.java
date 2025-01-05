package de.test;

import de.m_marvin.univec.impl.Vec2d;

public class Test {

	public static record RRR(String d) { }

	public static void main(String[] arg) {
		RRR r = new RRR("Hello World from Record!");
		System.out.println(r.d());
		System.out.println("test " + new Vec2d(0, 0).toString());
	}
	
}
