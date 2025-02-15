package de.test;

import java.io.IOException;

import de.m_marvin.univec.impl.Vec2d;

public class Test {

	public static record RRR(String d) { }

	public static void main(String[] arg) throws IOException {
		RRR r = new RRR("Hello World from Record!");
		System.out.println(r.d());
		System.out.println("test " + new Vec2d(0, 0).toString());
		
		char i = (char) System.in.read();
		System.out.println(" -> " + i);
	}
	
}
